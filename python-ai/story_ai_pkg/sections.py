import time
import uuid
from collections.abc import Callable

from fastapi import HTTPException

from ai_runtime import ConsoleStreamingCallback, llm_temperature_0, log_progress, streaming_text_llm_base

from .formatters import (
    build_characters_text,
    extract_llm_text,
    format_volume_section_context,
    parse_volume_section_text,
)
from .models import (
    StoryVolumeSectionGenerateRequest,
    StoryVolumeSectionGenerateResponse,
    VolumeSectionCountOutput,
    VolumeSectionItem,
)
from .router import router
from .templates import promptTemplate_VolumeSectionCount, promptTemplate_VolumeSectionSingle


@router.post("/api/story/volume-sections", response_model=StoryVolumeSectionGenerateResponse)
def generate_volume_sections_endpoint(request: StoryVolumeSectionGenerateRequest) -> StoryVolumeSectionGenerateResponse:
    """作用：提供 HTTP 调试入口，实际生成逻辑委托给内部函数。
    调用方：本地调试或直接 HTTP 调用。
    """
    return generate_volume_sections(request)


def generate_volume_sections(
    request: StoryVolumeSectionGenerateRequest,
    on_section_generated: Callable[[VolumeSectionItem], None] | None = None,
) -> StoryVolumeSectionGenerateResponse:
    """作用：先规划某一卷的小节数量，再逐小节生成具体故事细节。
    调用方：rabbitmq_worker 分卷小节生成任务；同时保留 HTTP 路由用于本地调试。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "volume-sections"
    if not request.outline or not request.outline.strip():
        raise HTTPException(status_code=400, detail="Story outline is required before generating volume sections")
    if not request.volume_outline.content or not request.volume_outline.content.strip():
        raise HTTPException(status_code=400, detail="Volume outline content is required before generating sections")

    log_progress(
        trace_id,
        (
            f"request accepted, user_id={request.user_id}, story_id={request.story_id}, "
            f"volume_id={request.volume_id}, volume={request.volume_outline.volume_number}"
        ),
        started_at,
        scope,
    )

    characters_text = build_characters_text(request.main_characters)
    count_llm = llm_temperature_0.with_structured_output(VolumeSectionCountOutput)
    count_chain = promptTemplate_VolumeSectionCount | count_llm
    log_progress(trace_id, "planning section count with temperature=0 structured output", started_at, scope)
    count_result = count_chain.invoke(
        {
            "Title": request.title,
            "StorySummary": request.story_summary or "",
            "Outline": request.outline,
            "Characters": characters_text,
            "VolumeNumber": request.volume_outline.volume_number,
            "VolumeTitle": request.volume_outline.title,
            "VolumeSummary": request.volume_outline.summary or "",
            "VolumeContent": request.volume_outline.content,
            "EndingHook": request.volume_outline.ending_hook or "",
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
    )
    total_sections = count_result.section_count
    log_progress(trace_id, f"section count planned: {total_sections}", started_at, scope)

    section_chain = promptTemplate_VolumeSectionSingle | streaming_text_llm_base
    generated_sections: list[VolumeSectionItem] = []
    for section_number in range(1, total_sections + 1):
        log_progress(trace_id, f"generating section {section_number}/{total_sections}", started_at, scope)
        raw_section = section_chain.invoke(
            {
                "Title": request.title,
                "StoryStyle": request.story_style or "高质量国漫/漫剧视觉",
                "StorySummary": request.story_summary or "",
                "Outline": request.outline,
                "Characters": characters_text,
                "VolumeNumber": request.volume_outline.volume_number,
                "VolumeTitle": request.volume_outline.title,
                "VolumeSummary": request.volume_outline.summary or "",
                "VolumeContent": request.volume_outline.content,
                "EndingHook": request.volume_outline.ending_hook or "",
                "TotalSections": total_sections,
                "CurrentSectionNumber": section_number,
                "GeneratedSections": format_volume_section_context(generated_sections),
            },
            config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
        )
        section = parse_volume_section_text(extract_llm_text(raw_section), section_number)
        generated_sections.append(section)
        log_progress(trace_id, f"section {section_number}/{total_sections} generated: {section.title}", started_at, scope)
        if on_section_generated:
            on_section_generated(section)

    if not generated_sections:
        raise HTTPException(status_code=502, detail="Tongyi model returned no volume sections")

    log_progress(trace_id, f"model returned {len(generated_sections)} sections, preparing response", started_at, scope)
    response = StoryVolumeSectionGenerateResponse(sections=generated_sections)
    log_progress(trace_id, "volume sections response ready", started_at, scope)
    return response
