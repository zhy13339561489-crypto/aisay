import time
import uuid

from ai_runtime import ConsoleStreamingCallback, log_progress, structured_llm_base

from .models import (
    NovelOutlineOutput,
    OutlineRevisionOutput,
    StoryOutlineGenerateRequest,
    StoryOutlineGenerateResponse,
    StoryOutlineReviseRequest,
    StoryOutlineReviseResponse,
)
from .router import router
from .templates import promptTemplate_Outline, promptTemplate_ReviseOutline


@router.post("/api/story/outline", response_model=StoryOutlineGenerateResponse)
def generate_story_outline(request: StoryOutlineGenerateRequest) -> StoryOutlineGenerateResponse:
    """作用：根据题材和可选剧情生成结构化剧情大纲、故事摘要和主要角色设定。
    调用方：rabbitmq_worker 故事生成任务；同时保留 HTTP 路由用于本地调试。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    log_progress(trace_id, f"request accepted, user_id={request.user_id}, genre={request.genre}", started_at)

    structured_llm = structured_llm_base.with_structured_output(NovelOutlineOutput)
    chain = promptTemplate_Outline | structured_llm
    log_progress(trace_id, "structured chain created, invoking Tongyi model in non-streaming mode", started_at)

    result = chain.invoke(
        {
            "Theme": request.genre,
            "StoryStyle": request.story_style or "未指定，按题材自然推导",
            "Plot": request.plot or "User did not provide a rough plot. Please create a story from the theme.",
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id)]},
    )

    log_progress(trace_id, "model returned structured output, preparing HTTP response", started_at)
    response = StoryOutlineGenerateResponse(
        novelName=result.novel_name,
        storySummary=result.story_summary,
        outline=result.outline,
        mainCharacters=result.main_characters,
    )
    log_progress(trace_id, "response ready", started_at)
    return response


@router.post("/api/story/outline/revise", response_model=StoryOutlineReviseResponse)
def revise_story_outline(request: StoryOutlineReviseRequest) -> StoryOutlineReviseResponse:
    """作用：根据用户修改意见调用 LangChain 修改剧情大纲，并返回结构化结果。
    调用方：rabbitmq_worker 大纲修改任务；同时保留 HTTP 路由用于本地调试。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "story-outline-revise"
    log_progress(
        trace_id,
        f"revision request accepted, user_id={request.user_id}, story_id={request.story_id}, title={request.title}",
        started_at,
        scope,
    )

    original_outline = (
        f"书名：{request.title}\n"
        f"故事摘要：{request.synopsis or ''}\n\n"
        f"剧情大纲：\n{request.outline}"
    )
    structured_llm = structured_llm_base.with_structured_output(OutlineRevisionOutput)
    chain = promptTemplate_ReviseOutline | structured_llm
    log_progress(trace_id, "structured revision chain created, invoking Tongyi model in non-streaming mode", started_at, scope)
    result = chain.invoke(
        {
            "OriginalOutline": original_outline,
            "RevisionNotes": request.suggestion,
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
    )

    log_progress(trace_id, "model returned revised outline, preparing HTTP response", started_at, scope)
    response = StoryOutlineReviseResponse(
        storySummary=result.story_summary,
        outline=result.outline,
        mainCharacters=result.main_characters,
    )
    log_progress(trace_id, "revision response ready", started_at, scope)
    return response
