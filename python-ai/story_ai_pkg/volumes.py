import time
import uuid
from collections.abc import Callable

from fastapi import HTTPException

from ai_runtime import ConsoleStreamingCallback, log_progress, llm_temperature_0, structured_llm_base

from .formatters import build_characters_text, extract_llm_text, format_volume_outline_context
from .models import (
    StoryVolumeOutlineGenerateRequest,
    StoryVolumeOutlineGenerateResponse,
    StoryVolumeOutlineReviseRequest,
    StoryVolumeStoryGenerateRequest,
    StoryVolumeStoryGenerateResponse,
    VolumeCountOutput,
    VolumeOutlineItem,
    VolumeOutlineOutput,
)
from .router import router
from .templates import (
    promptTemplate_VolumeCount,
    promptTemplate_VolumeOutlineEditor,
    promptTemplate_VolumeOutlineSingle,
    promptTemplate_VolumeStory,
)


@router.post("/api/story/volume-outline", response_model=StoryVolumeOutlineGenerateResponse)
def generate_volume_outline(
    request: StoryVolumeOutlineGenerateRequest,
    on_volume_generated: Callable[[VolumeOutlineItem], None] | None = None,
) -> StoryVolumeOutlineGenerateResponse:
    """作用：先规划总分卷数，再逐卷生成连续一致的详细分卷大纲。
    调用方：rabbitmq_worker 分卷生成任务；同时保留 HTTP 路由用于本地调试。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "volume-outline"
    if not request.outline or not request.outline.strip():
        raise HTTPException(status_code=400, detail="Story outline is required before generating volume outlines")

    log_progress(
        trace_id,
        f"request accepted, user_id={request.user_id}, story_id={request.story_id}, title={request.title}",
        started_at,
        scope,
    )

    characters_text = build_characters_text(request.main_characters)

    count_llm = llm_temperature_0.with_structured_output(VolumeCountOutput)
    count_chain = promptTemplate_VolumeCount | count_llm
    log_progress(trace_id, "planning total volume count with temperature=0 structured output", started_at, scope)
    count_result = count_chain.invoke(
        {
            "Title": request.title,
            "StorySummary": request.story_summary or "",
            "Outline": request.outline,
            "Characters": characters_text,
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
    )
    total_volumes = count_result.volume_count
    log_progress(trace_id, f"volume count planned: {total_volumes}", started_at, scope)

    single_volume_llm = structured_llm_base.with_structured_output(VolumeOutlineItem)
    single_volume_chain = promptTemplate_VolumeOutlineSingle | single_volume_llm
    generated_volumes: list[VolumeOutlineItem] = []
    for volume_number in range(1, total_volumes + 1):
        log_progress(trace_id, f"generating volume {volume_number}/{total_volumes}", started_at, scope)
        volume = single_volume_chain.invoke(
            {
                "Title": request.title,
                "StorySummary": request.story_summary or "",
                "Outline": request.outline,
                "Characters": characters_text,
                "TotalVolumes": total_volumes,
                "CurrentVolumeNumber": volume_number,
                "GeneratedVolumeOutlines": format_volume_outline_context(generated_volumes),
            },
            config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
        )
        volume.volume_number = volume_number
        generated_volumes.append(volume)
        log_progress(trace_id, f"volume {volume_number}/{total_volumes} generated: {volume.title}", started_at, scope)
        if on_volume_generated:
            on_volume_generated(volume)

    if not generated_volumes:
        raise HTTPException(status_code=502, detail="Tongyi model returned no volume outlines")

    log_progress(trace_id, f"model returned {len(generated_volumes)} volume outlines, preparing HTTP response", started_at, scope)
    response = StoryVolumeOutlineGenerateResponse(volumes=generated_volumes)
    log_progress(trace_id, "response ready", started_at, scope)
    return response


@router.post("/api/story/volume-outline/revise", response_model=StoryVolumeOutlineGenerateResponse)
def revise_volume_outline(request: StoryVolumeOutlineReviseRequest) -> StoryVolumeOutlineGenerateResponse:
    """作用：根据用户修改意见、原分卷大纲和故事上下文自动重写分卷大纲。
    调用方：rabbitmq_worker 分卷修改任务；同时保留 HTTP 路由用于本地调试。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "volume-outline-revise"
    if not request.outline or not request.outline.strip():
        raise HTTPException(status_code=400, detail="Story outline is required before revising volume outlines")
    if not request.volume_outlines:
        raise HTTPException(status_code=400, detail="Existing volume outlines are required before revision")
    if not request.suggestion or not request.suggestion.strip():
        raise HTTPException(status_code=400, detail="Modification request is required")

    log_progress(
        trace_id,
        f"revision request accepted, user_id={request.user_id}, story_id={request.story_id}, title={request.title}",
        started_at,
        scope,
    )

    characters_text = build_characters_text(request.main_characters)
    existing_volume_outline = format_volume_outline_context(request.volume_outlines)

    structured_llm = structured_llm_base.with_structured_output(VolumeOutlineOutput)
    chain = promptTemplate_VolumeOutlineEditor | structured_llm
    log_progress(trace_id, "structured volume revision chain created, invoking Tongyi model in non-streaming mode", started_at, scope)
    result = chain.invoke(
        {
            "Title": request.title,
            "StorySummary": request.story_summary or "",
            "Outline": request.outline,
            "Characters": characters_text,
            "ExistingVolumeOutline": existing_volume_outline,
            "ModificationRequest": request.suggestion,
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
    )

    if not result.volumes:
        raise HTTPException(status_code=502, detail="Tongyi model returned no revised volume outlines")

    log_progress(trace_id, f"model returned {len(result.volumes)} revised volume outlines", started_at, scope)
    response = StoryVolumeOutlineGenerateResponse(volumes=result.volumes)
    log_progress(trace_id, "revision response ready", started_at, scope)
    return response


@router.post("/api/story/volume-story", response_model=StoryVolumeStoryGenerateResponse)
def generate_volume_story(request: StoryVolumeStoryGenerateRequest) -> StoryVolumeStoryGenerateResponse:
    """作用：根据指定分卷大纲生成该卷详细完整故事正文。
    调用方：rabbitmq_worker 分卷正文生成任务；同时保留 HTTP 路由用于本地调试。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "volume-story"
    if not request.outline or not request.outline.strip():
        raise HTTPException(status_code=400, detail="Story outline is required before generating volume story")
    if not request.volume_outline.content or not request.volume_outline.content.strip():
        raise HTTPException(status_code=400, detail="Volume outline content is required before generating volume story")

    log_progress(
        trace_id,
        (
            f"request accepted, user_id={request.user_id}, story_id={request.story_id}, "
            f"volume_id={request.volume_id}, volume={request.volume_outline.volume_number}"
        ),
        started_at,
        scope,
    )

    chain = promptTemplate_VolumeStory | structured_llm_base
    log_progress(trace_id, "plain text volume story chain created, invoking Tongyi model", started_at, scope)
    raw_result = chain.invoke(
        {
            "Title": request.title,
            "StoryStyle": request.story_style or "高质量国漫/漫剧视觉",
            "StorySummary": request.story_summary or "",
            "Outline": request.outline,
            "Characters": build_characters_text(request.main_characters),
            "VolumeNumber": request.volume_outline.volume_number,
            "VolumeTitle": request.volume_outline.title,
            "VolumeSummary": request.volume_outline.summary or "",
            "VolumeContent": request.volume_outline.content,
            "EndingHook": request.volume_outline.ending_hook or "",
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
    )

    volume_story = extract_llm_text(raw_result)
    if not volume_story:
        raise HTTPException(status_code=502, detail="Tongyi model returned empty volume story")

    log_progress(trace_id, "model returned volume story text, preparing response", started_at, scope)
    response = StoryVolumeStoryGenerateResponse(volumeStory=volume_story)
    log_progress(trace_id, "volume story response ready", started_at, scope)
    return response
