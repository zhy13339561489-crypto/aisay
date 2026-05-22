import time
import uuid

from fastapi import HTTPException

from ai_runtime import ConsoleStreamingCallback, log_progress, structured_llm_base

from .formatters import build_characters_text
from .models import StorySectionScriptGenerateRequest, StorySectionScriptGenerateResponse
from .router import router
from .templates import promptTemplate_SectionScriptGenerate


@router.post("/api/story/section-script", response_model=StorySectionScriptGenerateResponse)
def generate_section_script(request: StorySectionScriptGenerateRequest) -> StorySectionScriptGenerateResponse:
    """作用：根据单个小节故事生成分镜故事脚本。
    调用方：rabbitmq_worker 小节脚本生成任务；同时保留 HTTP 路由用于本地调试。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "section-script"
    if not request.section.content or not request.section.content.strip():
        raise HTTPException(status_code=400, detail="Section content is required before generating section script")

    log_progress(
        trace_id,
        (
            f"request accepted, user_id={request.user_id}, story_id={request.story_id}, "
            f"volume_id={request.volume_id}, section={request.section.section_number}"
        ),
        started_at,
        scope,
    )

    structured_llm = structured_llm_base.with_structured_output(StorySectionScriptGenerateResponse)
    chain = promptTemplate_SectionScriptGenerate | structured_llm
    log_progress(trace_id, "structured section script chain created, invoking Tongyi model", started_at, scope)
    result = chain.invoke(
        {
            "Title": request.title,
            "StoryStyle": request.story_style or "高质量国漫/漫剧视觉",
            "StorySummary": request.story_summary or "",
            "Outline": request.outline or "",
            "Characters": build_characters_text(request.main_characters),
            "VolumeNumber": request.volume_outline.volume_number,
            "VolumeTitle": request.volume_outline.title,
            "VolumeSummary": request.volume_outline.summary or "",
            "VolumeContent": request.volume_outline.content or "",
            "EndingHook": request.volume_outline.ending_hook or "",
            "SectionNumber": request.section.section_number,
            "SectionTitle": request.section.title,
            "SectionSummary": request.section.summary or "",
            "SectionContent": request.section.content,
            "SectionEndingHook": request.section.ending_hook or "",
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
    )

    if not result.shots:
        raise HTTPException(status_code=502, detail="Tongyi model returned no section script shots")
    result.section_number = request.section.section_number
    if not result.total_duration_seconds:
        result.total_duration_seconds = sum(max(shot.duration_seconds or 0, 0) for shot in result.shots)
    log_progress(trace_id, f"section script generated: {len(result.shots)} shots", started_at, scope)
    return result
