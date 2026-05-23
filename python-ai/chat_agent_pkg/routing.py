# 指代消解与意图路由模块
# 本文件负责对话系统的前两个阶段：
# 1. 指代消解：将用户输入中的"它"、"这个"等指代词替换为具体实体
# 2. 意图路由：判断用户意图，提取关键信息，路由到对应模块

# json：用于序列化 keyFacts 字典
import json

# 从 ai_runtime 导入 LLM 实例和重试调用函数
from ai_runtime import Router, invoke_llm_with_retry

# 从 prompt 导入兜底提示词
from prompt import prompt_ChatAgent

# 从 prompt_repository 导入数据库提示词加载函数
from story_ai_pkg.prompt_repository import get_prompt_template

# 从 models 导入数据模型
from .models import ChatAgentRequest, CoreferenceOutput, RouteOutput

# 从 prompts 导入指代消解和路由提示词
from .prompts import prompt_ChatCoreference, prompt_ChatRouter


def format_messages(messages) -> str:
    """将消息列表格式化为提示词可读的文本。

    每条消息一行，格式为 "role: content"。
    用于短期记忆的文本化。

    Args:
        messages: 消息列表，每条消息有 role 和 content 属性。

    Returns:
        str: 格式化的消息文本。如果列表为空，返回 "无"。
    """
    if not messages:
        return "无"

    rows = []
    for message in messages:
        rows.append(f"{message.role}: {message.content}")
    return "\n".join(rows)


def resolve_coreference(request: ChatAgentRequest, trace_id: str, started_at: float) -> CoreferenceOutput:
    """执行指代消解，将用户输入中的指代词替换为具体实体。

    例如：
    - "帮我修改它" → "帮我修改《星际迷航》的剧情大纲"
    - "继续生成" → "继续为《星际迷航》生成分卷大纲"

    Args:
        request:    对话 Agent 请求体。
        trace_id:   追踪 ID。
        started_at: 请求开始时间。

    Returns:
        CoreferenceOutput: 消解后的消息和消解说明。
    """
    # 构建指代消解链：提示词模板 → 结构化 LLM
    chain = get_prompt_template(
        "chat_coreference",           # 提示词 key
        prompt_ChatCoreference,       # 兜底提示词
        genre=request.genre,          # 按题材匹配特定提示词
        story_style=request.story_style,  # 按风格匹配特定提示词
    ) | Router.with_structured_output(CoreferenceOutput)

    # 调用大模型
    return invoke_llm_with_retry(
        chain,
        {
            "RecentMessages": format_messages(request.recent_messages),  # 近期消息
            "LongTermMemory": request.long_term_memory or "无",         # 长期记忆
            "KeyFacts": json.dumps(request.key_facts or {}, ensure_ascii=False),  # 关键事实
            "UserMessage": request.user_message,                        # 用户消息
        },
        trace_id=trace_id,
        started_at=started_at,
        scope="chat-coreference",  # 日志前缀
    )


def route_message(
        request: ChatAgentRequest,
        resolved_message: str,
        trace_id: str,
        started_at: float,
) -> RouteOutput:
    """执行意图路由，判断用户意图并路由到对应模块。

    路由结果包含：
    - module: 目标模块（manga/outline_config/prompt_management/user_permission/general）
    - intent: 用户意图（如 story_generate、prompt_update）
    - importantInfo: 提取的关键信息
    - missingInfo: 缺失的必要信息
    - requiredPermission: 所需权限

    Args:
        request:          对话 Agent 请求体。
        resolved_message: 指代消解后的消息。
        trace_id:         追踪 ID。
        started_at:       请求开始时间。

    Returns:
        RouteOutput: 路由结果。
    """
    # 构建路由链：提示词模板 → 结构化 LLM
    chain = get_prompt_template(
        "chat_router",           # 提示词 key
        prompt_ChatRouter,       # 兜底提示词
        genre=request.genre,     # 按题材匹配
        story_style=request.story_style,  # 按风格匹配
    ) | Router.with_structured_output(RouteOutput)

    # 调用大模型
    return invoke_llm_with_retry(
        chain,
        {
            "UserRole": request.user_role,                                      # 用户角色
            "LongTermMemory": request.long_term_memory or "无",                 # 长期记忆
            "KeyFacts": json.dumps(request.key_facts or {}, ensure_ascii=False),  # 关键事实
            "ResolvedMessage": resolved_message,                                # 消解后消息
            "AvailableModules": "manga, outline_config, prompt_management, user_permission, general",  # 可用模块
        },
        trace_id=trace_id,
        started_at=started_at,
        scope="chat-router",  # 日志前缀
    )


def normalize_important_info(route: RouteOutput) -> str:
    """将路由结果中的 importantInfo 序列化为 JSON 字符串。

    Args:
        route: 路由输出。

    Returns:
        str: JSON 格式的关键信息。
    """
    return json.dumps(route.important_info or {}, ensure_ascii=False)


def fallback_general_response(request: ChatAgentRequest, resolved_message: str) -> RouteOutput:
    """生成兜底的通用回复。

    当路由模块为空时使用，返回 general 模块的友好回复。

    Args:
        request:          对话 Agent 请求体。
        resolved_message: 消解后的消息。

    Returns:
        RouteOutput: 兜底路由结果。
    """
    return RouteOutput(
        module="general",           # 通用模块
        intent="general_chat",      # 闲聊意图
        route="no_action",          # 不执行操作
        requiredPermission="USER",  # 普通用户权限
        importantInfo={},           # 无关键信息
        missingInfo=[],             # 无缺失信息
        assistantMessage=(
            "我可以帮你梳理创意、剧情、人物、Prompt 或系统操作。"
            f"这次我理解你的问题是：{resolved_message}"
        ),
    )
