# 对话 Agent 模块
# 负责处理用户在聊天页面发送的消息，通过大模型理解意图并路由到对应的 Java 方法
# 采用"Java 接入 → Python 决策 → Java 执行"的工具调用模式

# time：用于计算请求耗时
import time

# uuid：用于生成本次请求的唯一追踪 ID
import uuid

# typing.Any：用于声明动态类型字典的值类型
from typing import Any

# FastAPI 工具导入
from fastapi import APIRouter                    # APIRouter：用于组织路由，最终挂载到主 app
from pydantic import BaseModel, Field             # BaseModel/Field：Pydantic 数据模型定义

# 从 ai_runtime 导入共享的 LLM 实例和工具函数
from ai_runtime import ConsoleStreamingCallback, invoke_llm_with_retry, log_progress, structured_llm_base

from prompt import prompt_ChatAgent
from story_ai_pkg.prompt_repository import get_prompt_template


# 创建对话 Agent 路由器，最终在 main.py 中挂载到 app
router = APIRouter()

class ChatAgentRequest(BaseModel):
    """对话 Agent 请求体。

    对应 Java 端 AiEngineClient.runChatAgent 的入参，
    包含用户 ID、会话 ID、可选故事上下文和用户消息。
    """

    # 用户 ID，用于标识当前操作的用户
    user_id: int = Field(alias="userId")

    # 会话 ID，标识当前对话会话
    session_id: int = Field(alias="sessionId")

    # 故事 ID。当前对话默认不绑定具体漫剧，因此通常为空。
    story_id: int | None = Field(default=None, alias="storyId")

    # 可选标题；未绑定漫剧时使用会话标题
    title: str | None = None

    # 当前漫剧题材，用于匹配特定 Prompt
    genre: str | None = None

    # 当前漫剧视觉风格，用于匹配特定 Prompt
    story_style: str | None = Field(default=None, alias="storyStyle")

    # 故事摘要，可选，用于 Agent 理解故事背景
    synopsis: str | None = None

    # 故事大纲，可选，用于 Agent 理解故事结构
    outline: str | None = None

    # 用户发送的消息内容，Agent 需要理解其意图并路由
    user_message: str = Field(alias="userMessage")


class ChatAgentOutput(BaseModel):
    """对话 Agent 结构化输出。

    大模型返回的结构化结果，包含重写后的用户问题、路由标签、
    目标 Java 方法名、方法参数和助手回复。
    """

    # 重写后的独立问题，去除了上下文依赖，便于后续处理
    rewritten_question: str = Field(alias="rewrittenQuestion", description="Standalone rewritten user question")

    # 路由标签，如 update_outline、no_action，用于分类处理
    route: str = Field(description="Routing label, such as update_outline or no_action")

    # 目标 Java 方法名，必须是白名单中的方法
    java_method: str = Field(alias="javaMethod", description="One supported Java method name")

    # Java 方法的参数，JSON 对象格式
    java_method_args: dict[str, Any] = Field(default_factory=dict, alias="javaMethodArgs")

    # 助手回复用户的简短中文消息
    assistant_message: str = Field(alias="assistantMessage", description="Short Chinese response to the user")


class ChatAgentResponse(ChatAgentOutput):
    """对话 Agent HTTP 响应体。

    继承 ChatAgentOutput，作为 FastAPI 接口的返回模型。
    字段与输出一致，无需额外定义。
    """

    pass


@router.post("/api/chat/agent", response_model=ChatAgentResponse)
def run_chat_agent(request: ChatAgentRequest) -> ChatAgentResponse:
    """执行对话 Agent，完成问题重写、路由分发，并返回 Java 需要调用的方法名和参数。

    处理流程：
    1. 生成追踪 ID，记录请求开始
    2. 构建 LangChain 结构化输出链（prompt → LLM → 结构化 JSON）
    3. 调用大模型，传入故事上下文和用户消息
    4. 校验返回的 javaMethod 是否在白名单中
    5. 返回结构化响应给 Java 后端

    Args:
        request: 对话 Agent 请求体，包含用户消息和故事上下文。

    Returns:
        ChatAgentResponse: 包含重写问题、路由、Java 方法名、参数和助手回复。
    """
    # 生成 8 位十六进制追踪 ID，用于日志关联
    trace_id = uuid.uuid4().hex[:8]

    # 记录请求开始时间
    started_at = time.perf_counter()

    # 业务范围标识，用于日志前缀
    scope = "chat-agent"

    # 打印请求接收日志
    log_progress(
        trace_id,
        f"request accepted, user_id={request.user_id}, session_id={request.session_id}, story_id={request.story_id or 'none'}",
        started_at,
        scope,
    )

    # 创建结构化输出 LLM：将 ChatTongyi 绑定到 ChatAgentOutput 模型
    # 这样大模型返回的内容会被自动解析为 ChatAgentOutput 结构
    structured_llm = structured_llm_base.with_structured_output(ChatAgentOutput)

    # 构建 LangChain 链：提示词模板 → 结构化 LLM
    chain = get_prompt_template(
        "chat_agent",
        prompt_ChatAgent,
        genre=request.genre,
        story_style=request.story_style,
    ) | structured_llm

    # 打印模型调用开始日志
    log_progress(trace_id, "rewriting question and routing to Java method in non-streaming mode", started_at, scope)

    # 调用大模型，传入故事上下文和用户消息
    result = invoke_llm_with_retry(
        chain,
        {
            "Title": request.title or "未绑定具体漫剧的通用对话",  # 会话标题或故事标题
            "Theme": request.genre or "未指定",             # 当前题材
            "StoryStyle": request.story_style or "未指定",  # 当前漫剧风格
            "StorySummary": request.synopsis or "",        # 故事摘要
            "Outline": request.outline or "",              # 故事大纲
            "UserMessage": request.user_message,           # 用户消息
        },
        # 传入控制台回调，打印流式 token（如果模型支持）
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
        trace_id=trace_id,
        started_at=started_at,
        scope=scope,
    )

    # 打印模型返回的方法名和路由
    log_progress(trace_id, f"agent selected method={result.java_method}, route={result.route}", started_at, scope)

    # 白名单校验：只允许执行预定义的 Java 方法
    allowed_methods = {"story.updateOutline", "story.none"}

    # 如果返回的方法名不在白名单中，降级为 story.none（不执行任何操作）
    if result.java_method not in allowed_methods:
        result.java_method = "story.none"           # 降级为无操作
        result.java_method_args = {}                 # 清空参数
        result.assistant_message = "我理解了你的请求，但当前工具白名单还不支持这个操作。"

    # 当前对话系统不绑定具体漫剧，没有明确目标故事时禁止返回故事修改方法。
    if request.story_id is None and result.java_method != "story.none":
        result.java_method = "story.none"
        result.java_method_args = {}
        result.assistant_message = "当前对话没有绑定具体漫剧，我可以先帮你梳理想法；需要落库时请到对应漫剧详情页操作。"

    # 构建并返回响应
    return ChatAgentResponse(
        rewrittenQuestion=result.rewritten_question,   # 重写后的独立问题
        route=result.route,                            # 路由标签
        javaMethod=result.java_method,                 # Java 方法名
        javaMethodArgs=result.java_method_args,        # Java 方法参数
        assistantMessage=result.assistant_message,     # 助手回复
    )
