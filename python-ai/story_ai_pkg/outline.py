# 剧情大纲业务模块
# 本文件负责剧情大纲的生成和修改，包含两个 FastAPI 端点：
# - POST /api/story/outline：生成剧情大纲
# - POST /api/story/outline/revise：修改剧情大纲
# 这些端点同时被 RabbitMQ Worker 和 HTTP 调试使用。

# time：用于计算请求耗时
import time

# uuid：用于生成请求追踪 ID
import uuid

from typing import Any

from pydantic import ValidationError

# 从 ai_runtime 导入共享的 LLM 实例和工具函数
from ai_runtime import ConsoleStreamingCallback, invoke_llm_with_retry, log_progress, structured_llm_base

# 从 models 导入请求/响应/输出模型
from .models import (
    NovelOutlineOutput,                # 大模型剧情大纲输出约束
    OutlineRevisionOutput,             # 大模型大纲修改输出约束
    StoryOutlineGenerateRequest,       # 剧情大纲生成请求
    StoryOutlineGenerateResponse,      # 剧情大纲生成响应
    StoryOutlineReviseRequest,         # 剧情大纲修改请求
    StoryOutlineReviseResponse,        # 剧情大纲修改响应
)

# 从 router 导入路由器，用于注册端点
from .router import router

# 从 templates 导入提示词模板加载函数
from .templates import load_prompt_template


def _build_story_outline_payload(request: StoryOutlineGenerateRequest, *, strict_output: bool = False) -> dict[str, str]:
    """Build prompt variables for story outline generation.

    This function is called by generate_story_outline before every LLM invocation.
    strict_output=True is used only after structured parsing fails once, adding a stronger
    instruction to reduce cases where the model echoes input fields instead of the target schema.
    """
    plot = request.plot or "User did not provide a rough plot. Please create a story from the theme."
    if strict_output:
        plot = (
            f"{plot}\n\n"
            "重要输出要求：你必须生成全新的漫剧剧情大纲，不要复述输入参数。"
            "最终结构化输出必须完整填写 novel_name、story_summary、outline、main_characters，"
            "main_characters 至少包含 3 个主要角色。"
        )

    return {
        "Theme": request.genre,
        "StoryStyle": request.story_style or "未指定，按题材自然推导",
        "Plot": plot,
    }


def _coerce_story_outline_output(raw_result: Any) -> NovelOutlineOutput:
    """Validate and normalize an LLM result as NovelOutlineOutput.

    Called by generate_story_outline after LangChain returns. LangChain normally returns the
    Pydantic model directly, but some providers may return a dict-like object, so we accept both.
    """
    if isinstance(raw_result, NovelOutlineOutput):
        return raw_result

    if hasattr(raw_result, "model_dump"):
        raw_result = raw_result.model_dump()

    if isinstance(raw_result, dict):
        normalized = dict(raw_result)
        alias_map = {
            "novelName": "novel_name",
            "storySummary": "story_summary",
            "mainCharacters": "main_characters",
        }
        for source_key, target_key in alias_map.items():
            if source_key in normalized and target_key not in normalized:
                normalized[target_key] = normalized[source_key]
        return NovelOutlineOutput.model_validate(normalized)

    raise TypeError(f"Unexpected story outline output type: {type(raw_result).__name__}")


def _extract_validation_input(error: Exception) -> dict[str, Any] | None:
    """Extract the malformed payload from a Pydantic/LangChain validation error when possible.

    Called by generate_story_outline only for diagnostics and fallback generation.
    """
    visited: set[int] = set()
    current: BaseException | None = error
    while current and id(current) not in visited:
        visited.add(id(current))
        errors = getattr(current, "errors", None)
        if callable(errors):
            try:
                if not isinstance(current, ValidationError) and current.__class__.__name__ != "ValidationError":
                    current = getattr(current, "__cause__", None) or getattr(current, "__context__", None)
                    continue
                for item in errors():
                    input_value = item.get("input") if isinstance(item, dict) else None
                    if isinstance(input_value, dict):
                        return input_value
            except Exception:
                pass
        current = getattr(current, "__cause__", None) or getattr(current, "__context__", None)
    return None


def _build_fallback_story_outline(
    request: StoryOutlineGenerateRequest,
    malformed_input: dict[str, Any] | None = None,
) -> NovelOutlineOutput:
    """Create a safe, valid outline when the model repeatedly returns an invalid schema.

    Called by generate_story_outline as the final safety net so RabbitMQ tasks do not fail with
    missing-field validation errors. The content is intentionally marked as temporary in the
    outline text, making it clear to the user that regeneration is recommended.
    """
    malformed_input = malformed_input or {}
    genre = _first_non_empty(request.genre, malformed_input.get("genre"), malformed_input.get("Theme"), "未指定题材")
    story_style = _first_non_empty(
        request.story_style,
        malformed_input.get("visual_style"),
        malformed_input.get("story_style"),
        malformed_input.get("StoryStyle"),
        "未指定风格",
    )
    plot = _first_non_empty(
        request.plot,
        malformed_input.get("partial_plot"),
        malformed_input.get("plot"),
        malformed_input.get("Plot"),
        "用户暂未提供具体剧情，系统先根据题材生成临时方向。",
    )

    novel_name = f"{genre}漫剧大纲"
    story_summary = (
        f"这是一个{genre}题材、{story_style}风格的漫剧故事。"
        f"核心剧情将围绕用户给出的方向“{plot}”展开，"
        "后续建议重新生成或使用大纲修改功能补全更丰富的世界观、冲突和角色弧光。"
    )
    outline = (
        "【临时降级大纲】本次大模型没有返回完整结构化字段，系统已生成可落库的临时大纲，"
        "建议稍后重新生成以获得更完整结果。\n\n"
        f"题材：{genre}\n"
        f"漫剧风格：{story_style}\n"
        f"剧情方向：{plot}\n\n"
        "开端：主角在既有秩序中发现异常事件，被迫卷入更大的危机。\n"
        "发展：主角与同伴不断追查真相，逐步揭开世界规则、敌对势力和自身命运之间的联系。\n"
        "高潮：核心矛盾集中爆发，主角必须在个人愿望、同伴安危和世界秩序之间做出选择。\n"
        "结局：主角完成关键成长，旧秩序被打破或重塑，同时留下可继续扩展的悬念。"
    )
    return NovelOutlineOutput(
        novel_name=novel_name,
        story_summary=story_summary,
        outline=outline,
        main_characters=[
            {
                "name": "待定主角",
                "role": "主角",
                "description": f"{genre}故事的核心推动者，承担发现危机、破解规则和完成成长的叙事功能。",
                "personality": "有明确目标，但仍需要在剧情推进中细化性格弱点、欲望和人物弧光。",
                "appearance": {"note": "待后续根据漫剧风格细化外观。"},
            },
            {
                "name": "关键同伴",
                "role": "同伴",
                "description": "帮助主角理解世界规则，并在关键节点提供行动支持或情感牵引。",
                "personality": "可靠但有隐藏压力，可在后续大纲中补充独立目标。",
                "appearance": {"note": "待后续根据漫剧风格细化外观。"},
            },
            {
                "name": "主要对手",
                "role": "反派",
                "description": "代表故事中的主要阻力，与主角在理念、资源或命运层面形成持续冲突。",
                "personality": "目标明确，行动强势，具体动机可在后续修改中深化。",
                "appearance": {"note": "待后续根据漫剧风格细化外观。"},
            },
        ],
    )


def _first_non_empty(*values: Any) -> str:
    """Return the first non-empty value as text."""
    for value in values:
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


@router.post("/api/story/outline", response_model=StoryOutlineGenerateResponse)
def generate_story_outline(request: StoryOutlineGenerateRequest) -> StoryOutlineGenerateResponse:
    """根据题材和可选剧情生成结构化剧情大纲、故事摘要和主要角色设定。

    处理流程：
    1. 生成追踪 ID，记录请求开始
    2. 创建结构化输出链：promptTemplate_Outline → structured_llm → NovelOutlineOutput
    3. 调用大模型，传入题材、风格和剧情
    4. 将结构化输出映射为响应模型

    Args:
        request: 剧情大纲生成请求体，包含用户 ID、题材、风格和可选剧情。

    Returns:
        StoryOutlineGenerateResponse: 包含小说名、摘要、大纲和角色设定。
    """
    # 生成 8 位十六进制追踪 ID
    trace_id = uuid.uuid4().hex[:8]

    # 记录请求开始时间
    started_at = time.perf_counter()

    # 打印请求接收日志
    log_progress(trace_id, f"request accepted, user_id={request.user_id}, genre={request.genre}", started_at)

    # 创建结构化输出 LLM：绑定 NovelOutlineOutput 模型，约束大模型输出格式
    structured_llm = structured_llm_base.with_structured_output(NovelOutlineOutput)

    # 构建 LangChain 链：提示词模板 → 结构化 LLM
    chain = load_prompt_template(
        "generate_story_outline",
        genre=request.genre,
        story_style=request.story_style,
    ) | structured_llm

    # 打印链创建完成日志
    log_progress(trace_id, "structured chain created, invoking Tongyi model in non-streaming mode", started_at)

    # 调用大模型，传入提示词变量。结构化输出偶发会返回输入字段或缺失字段，
    # 因此这里显式做规整、重试和兜底，避免 RabbitMQ 任务直接失败。
    try:
        raw_result = invoke_llm_with_retry(
            chain,
            _build_story_outline_payload(request),
            # 传入控制台回调，打印调用进度
            config={"callbacks": [ConsoleStreamingCallback(trace_id)]},
            trace_id=trace_id,
            started_at=started_at,
            scope="story-outline",
        )
        result = _coerce_story_outline_output(raw_result)
    except Exception as first_error:
        malformed_input = _extract_validation_input(first_error)
        log_progress(
            trace_id,
            f"structured output invalid, retrying with stricter prompt: {first_error}",
            started_at,
        )
        try:
            raw_result = invoke_llm_with_retry(
                chain,
                _build_story_outline_payload(request, strict_output=True),
                config={"callbacks": [ConsoleStreamingCallback(trace_id)]},
                trace_id=trace_id,
                started_at=started_at,
                scope="story-outline",
            )
            result = _coerce_story_outline_output(raw_result)
        except Exception as second_error:
            malformed_input = _extract_validation_input(second_error) or malformed_input
            log_progress(
                trace_id,
                f"structured output still invalid, using fallback outline: {second_error}",
                started_at,
            )
            result = _build_fallback_story_outline(request, malformed_input)

    # 打印模型返回日志
    log_progress(trace_id, "model returned structured output, preparing HTTP response", started_at)

    # 将结构化输出映射为响应模型（snake_case → camelCase）
    response = StoryOutlineGenerateResponse(
        novelName=result.novel_name,              # 小说名称
        storySummary=result.story_summary,        # 故事摘要
        outline=result.outline,                   # 完整大纲
        mainCharacters=result.main_characters,    # 主要角色设定
    )

    # 打印响应就绪日志
    log_progress(trace_id, "response ready", started_at)
    return response


@router.post("/api/story/outline/revise", response_model=StoryOutlineReviseResponse)
def revise_story_outline(request: StoryOutlineReviseRequest) -> StoryOutlineReviseResponse:
    """根据用户修改意见调用大模型修改剧情大纲，并返回结构化结果。

    处理流程：
    1. 生成追踪 ID，记录请求开始
    2. 将原始故事信息组装为文本
    3. 创建结构化输出链：promptTemplate_ReviseOutline → structured_llm → OutlineRevisionOutput
    4. 调用大模型，传入原始大纲和修改意见
    5. 返回修改后的结构化结果

    Args:
        request: 剧情大纲修改请求体，包含故事信息和修改意见。

    Returns:
        StoryOutlineReviseResponse: 包含修改后的摘要、大纲和角色设定。
    """
    # 生成追踪 ID
    trace_id = uuid.uuid4().hex[:8]

    # 记录请求开始时间
    started_at = time.perf_counter()

    # 业务范围标识
    scope = "story-outline-revise"

    # 打印请求接收日志
    log_progress(
        trace_id,
        f"revision request accepted, user_id={request.user_id}, story_id={request.story_id}, title={request.title}",
        started_at,
        scope,
    )

    # 将原始故事信息组装为文本，作为大模型的上下文
    original_outline = (
        f"书名：{request.title}\n"
        f"故事摘要：{request.synopsis or ''}\n\n"
        f"剧情大纲：\n{request.outline}"
    )

    # 创建结构化输出 LLM：绑定 OutlineRevisionOutput 模型
    structured_llm = structured_llm_base.with_structured_output(OutlineRevisionOutput)

    # 构建 LangChain 链
    chain = load_prompt_template(
        "revise_story_outline",
        genre=request.genre,
        story_style=request.story_style,
    ) | structured_llm

    # 打印链创建日志
    log_progress(trace_id, "structured revision chain created, invoking Tongyi model in non-streaming mode", started_at, scope)

    # 调用大模型
    result = invoke_llm_with_retry(
        chain,
        {
            "Theme": request.genre or "未指定",
            "StoryStyle": request.story_style or "未指定，按题材自然推导",
            "OriginalOutline": original_outline,   # 原始大纲文本
            "RevisionNotes": request.suggestion,   # 用户修改意见
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
        trace_id=trace_id,
        started_at=started_at,
        scope=scope,
    )

    # 打印模型返回日志
    log_progress(trace_id, "model returned revised outline, preparing HTTP response", started_at, scope)

    # 构建响应
    response = StoryOutlineReviseResponse(
        storySummary=result.story_summary,        # 修改后的摘要
        outline=result.outline,                   # 修改后的大纲
        mainCharacters=result.main_characters,    # 更新后的角色设定
    )

    # 打印响应就绪日志
    log_progress(trace_id, "revision response ready", started_at, scope)
    return response
