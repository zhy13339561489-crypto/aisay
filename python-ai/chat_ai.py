import time
import uuid
from typing import Any

from fastapi import APIRouter
from langchain_core.prompts import PromptTemplate
from pydantic import BaseModel, Field

from ai_runtime import ConsoleStreamingCallback, log_progress, structured_llm_base
from prompt import prompt_ChatAgent


router = APIRouter()

promptTemplate_ChatAgent = PromptTemplate.from_template(prompt_ChatAgent)


class ChatAgentRequest(BaseModel):
    """对话 Agent 请求体。
    对应 Java 端 AiEngineClient.runChatAgent 的入参。
    """

    user_id: int = Field(alias="userId")
    session_id: int = Field(alias="sessionId")
    story_id: int = Field(alias="storyId")
    title: str
    synopsis: str | None = None
    outline: str | None = None
    user_message: str = Field(alias="userMessage")


class ChatAgentOutput(BaseModel):
    """对话 Agent 结构化输出。
    包含重写后的用户问题、路由标签、目标 Java 方法名、方法参数和助手回复。
    """

    rewritten_question: str = Field(alias="rewrittenQuestion", description="Standalone rewritten user question")
    route: str = Field(description="Routing label, such as update_outline or no_action")
    java_method: str = Field(alias="javaMethod", description="One supported Java method name")
    java_method_args: dict[str, Any] = Field(default_factory=dict, alias="javaMethodArgs")
    assistant_message: str = Field(alias="assistantMessage", description="Short Chinese response to the user")


class ChatAgentResponse(ChatAgentOutput):
    """对话 Agent HTTP 响应体。
    继承 ChatAgentOutput，作为 FastAPI 接口的返回模型。
    """

    pass


@router.post("/api/chat/agent", response_model=ChatAgentResponse)
def run_chat_agent(request: ChatAgentRequest) -> ChatAgentResponse:
    """作用：执行对话 Agent，完成问题重写、路由分发，并返回 Java 需要调用的方法名和参数。
    调用方：Java AiEngineClient.runChatAgent，即 ChatServiceImpl#runAgentAndDispatch 的 Python 后端接口。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "chat-agent"
    log_progress(
        trace_id,
        f"request accepted, user_id={request.user_id}, session_id={request.session_id}, story_id={request.story_id}",
        started_at,
        scope,
    )

    structured_llm = structured_llm_base.with_structured_output(ChatAgentOutput)
    chain = promptTemplate_ChatAgent | structured_llm
    log_progress(trace_id, "rewriting question and routing to Java method in non-streaming mode", started_at, scope)
    result = chain.invoke(
        {
            "Title": request.title,
            "StorySummary": request.synopsis or "",
            "Outline": request.outline or "",
            "UserMessage": request.user_message,
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
    )
    log_progress(trace_id, f"agent selected method={result.java_method}, route={result.route}", started_at, scope)

    allowed_methods = {"story.updateOutline", "story.none"}
    if result.java_method not in allowed_methods:
        result.java_method = "story.none"
        result.java_method_args = {}
        result.assistant_message = "我理解了你的请求，但当前工具白名单还不支持这个操作。"

    return ChatAgentResponse(
        rewrittenQuestion=result.rewritten_question,
        route=result.route,
        javaMethod=result.java_method,
        javaMethodArgs=result.java_method_args,
        assistantMessage=result.assistant_message,
    )
