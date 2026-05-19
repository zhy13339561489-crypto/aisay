import os
import time
import uuid
from pathlib import Path
from typing import Any

import uvicorn
import yaml
from fastapi import FastAPI
from langchain_community.chat_models import ChatTongyi
from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.prompts import PromptTemplate
from pydantic import BaseModel, Field

from prompt import prompt_Outline, prompt_VolumeOutline


def load_tongyi_api_key() -> str:
    """作用：从 python-ai/api.yml 读取通义千问 API Key。

    调用方：模块加载阶段初始化 tongyi_api_key，并写入 DASHSCOPE_API_KEY 环境变量。
    """
    api_config_path = Path(__file__).with_name("api.yml")
    if not api_config_path.exists():
        return ""

    config = yaml.safe_load(api_config_path.read_text(encoding="utf-8")) or {}
    return str(config.get("tongyi", {}).get("api_key", "")).strip()


tongyi_api_key = load_tongyi_api_key()
if tongyi_api_key:
    os.environ["DASHSCOPE_API_KEY"] = tongyi_api_key


promptTemplate_Outline = PromptTemplate.from_template(prompt_Outline)

promptTemplate_VolumeOutline = PromptTemplate.from_template(prompt_VolumeOutline)
prompt_ChatAgent = """
你是路由与工具调用代理（Routing & Tool-Calling Agent）。  
你运行在对话会话中，该会话已绑定到一个具体的故事项目。你的职责是：理解用户的聊天意图，将其转化为明确的指令，并决定调用哪条链路。

---

核心任务（必须完成以下两项）

1. 意图理解与指令重写 
   将用户的自然语言消息重写为一条清晰、完整、无歧义的独立指令。若用户意图模糊，按最合理的创作方向补全。

2. 路由决策  
   根据指令判断应调用哪条链路。只允许使用下方列出的方法。

---

输出格式要求

你必须返回一个标准 JSON 对象，包含以下字段：

```json
{
  "assistantMessage": "简短的中文回复，向用户说明执行了什么操作或给出友好回应",
  "javaMethod": "Java 方法名，必须是下方列表中的某一个",
  "javaMethodArgs": { ... }
}
"""
promptTemplate_ChatAgent = PromptTemplate.from_template(prompt_ChatAgent)

llm = ChatTongyi(
    model="qwen-max",
    temperature=0.5,
    top_p=0.8,
    streaming=True,
)

app = FastAPI(title="Aisay Python AI Engine")


class ConsoleStreamingCallback(BaseCallbackHandler):
    def __init__(self, trace_id: str, scope: str = "story-outline"):
        """作用：初始化一次大模型调用的控制台流式日志上下文。

        调用方：generate_story_outline、generate_volume_outline、run_chat_agent 在 chain.invoke 的 callbacks 中创建。
        """
        self.trace_id = trace_id
        self.scope = scope
        self.started_at = time.perf_counter()
        self.has_streamed_tokens = False

    def _elapsed(self) -> str:
        """作用：计算当前请求从开始到现在的耗时文本。

        调用方：本类的 on_chat_model_start、on_llm_start、on_llm_new_token、on_llm_end、on_llm_error。
        """
        return f"{time.perf_counter() - self.started_at:.1f}s"

    def on_chat_model_start(self, serialized, messages, **kwargs) -> None:
        """作用：在 LangChain ChatModel 请求开始时打印进度。

        调用方：LangChain 回调框架自动调用。
        """
        print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] chat model request started", flush=True)

    def on_llm_start(self, serialized, prompts, **kwargs) -> None:
        """作用：在 LangChain LLM 请求开始时打印进度。

        调用方：LangChain 回调框架自动调用。
        """
        print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] llm request started", flush=True)

    def on_llm_new_token(self, token: str, **kwargs) -> None:
        """作用：流式打印大模型生成中的 token，方便在 Python 控制台观察进度。

        调用方：LangChain 回调框架在模型流式返回 token 时自动调用。
        """
        if not token:
            return
        if not self.has_streamed_tokens:
            self.has_streamed_tokens = True
            print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] streaming tokens:", flush=True)
        print(token, end="", flush=True)

    def on_llm_end(self, response, **kwargs) -> None:
        """作用：在大模型返回结束时补换行并打印完成日志。

        调用方：LangChain 回调框架自动调用。
        """
        if self.has_streamed_tokens:
            print("", flush=True)
        print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] llm response finished", flush=True)

    def on_llm_error(self, error, **kwargs) -> None:
        """作用：在大模型调用异常时打印错误日志。

        调用方：LangChain 回调框架自动调用。
        """
        if self.has_streamed_tokens:
            print("", flush=True)
        print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] llm error: {error}", flush=True)


def log_progress(trace_id: str, message: str, started_at: float, scope: str = "story-outline") -> None:
    """作用：按 trace_id 和业务范围打印阶段性进度日志。

    调用方：generate_story_outline、revise_story_outline、generate_volume_outline、run_chat_agent。
    """
    elapsed = time.perf_counter() - started_at
    print(f"[{scope}][{trace_id}][{elapsed:.1f}s] {message}", flush=True)


class StoryOutlineGenerateRequest(BaseModel):
    user_id: int = Field(alias="userId")
    session_id: int = Field(alias="sessionId")
    session_title: str | None = Field(default=None, alias="sessionTitle")
    genre: str
    plot: str | None = None


class MainCharacterSetting(BaseModel):
    name: str = Field(description="Character name or codename")
    role: str = Field(description="Character role, such as protagonist, partner, antagonist, mentor, or hidden manipulator")
    description: str = Field(description="Character background, ability, and narrative function")
    personality: str = Field(description="Personality, core desire, weakness, or character arc")
    appearance: dict[str, str] | None = Field(
        default=None,
        description="Optional appearance traits, such as hairstyle, clothing, or signature item",
    )


class StoryOutlineGenerateResponse(BaseModel):
    novel_name: str = Field(alias="novelName")
    story_summary: str = Field(alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class StoryOutlineReviseRequest(BaseModel):
    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    title: str
    synopsis: str | None = None
    outline: str
    suggestion: str


class StoryOutlineReviseResponse(BaseModel):
    story_summary: str = Field(alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class StoryVolumeOutlineGenerateRequest(BaseModel):
    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    title: str
    story_summary: str | None = Field(default=None, alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class VolumeOutlineItem(BaseModel):
    volume_number: int = Field(alias="volumeNumber", description="Volume number, starting from 1")
    title: str = Field(description="Volume title")
    summary: str = Field(description="Short summary of this volume")
    content: str = Field(description="Detailed volume outline with rich plot beats, conflicts, reversals, choices, and emotional hooks")
    ending_hook: str = Field(alias="endingHook", description="The cliffhanger or hook at the end of this volume")


class StoryVolumeOutlineGenerateResponse(BaseModel):
    volumes: list[VolumeOutlineItem]


class ChatAgentRequest(BaseModel):
    user_id: int = Field(alias="userId")
    session_id: int = Field(alias="sessionId")
    story_id: int = Field(alias="storyId")
    title: str
    synopsis: str | None = None
    outline: str | None = None
    user_message: str = Field(alias="userMessage")


class ChatAgentOutput(BaseModel):
    rewritten_question: str = Field(alias="rewrittenQuestion", description="Standalone rewritten user question")
    route: str = Field(description="Routing label, such as update_outline or no_action")
    java_method: str = Field(alias="javaMethod", description="One supported Java method name")
    java_method_args: dict[str, Any] = Field(default_factory=dict, alias="javaMethodArgs")
    assistant_message: str = Field(alias="assistantMessage", description="Short Chinese response to the user")


class ChatAgentResponse(ChatAgentOutput):
    pass


class NovelOutlineOutput(BaseModel):
    novel_name: str = Field(description="Novel title, concise and recognizable")
    story_summary: str = Field(description="Story summary, 200-300 Chinese characters")
    outline: str = Field(description="Full story outline with setting, main plot, stages, and key characters")
    main_characters: list[MainCharacterSetting] = Field(description="Main character settings, usually 3-5 key characters")


class VolumeOutlineOutput(BaseModel):
    volumes: list[VolumeOutlineItem] = Field(description="5-8 detailed volume outlines")


@app.post("/api/story/outline", response_model=StoryOutlineGenerateResponse)
def generate_story_outline(request: StoryOutlineGenerateRequest) -> StoryOutlineGenerateResponse:
    """作用：根据题材和可选剧情生成结构化剧情大纲、故事摘要和主要角色设定。

    调用方：Java AiEngineClient.generateStoryOutline，即 StoryServiceImpl#generateStory 的 Python 后端接口。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    log_progress(
        trace_id,
        f"request accepted, user_id={request.user_id}, session_id={request.session_id}, genre={request.genre}",
        started_at,
    )

    structured_llm = llm.with_structured_output(NovelOutlineOutput)
    chain = promptTemplate_Outline | structured_llm
    log_progress(trace_id, "structured chain created, invoking Tongyi model without a generation time limit", started_at)

    result = chain.invoke(
        {
            "Theme": request.genre,
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


@app.post("/api/story/outline/revise", response_model=StoryOutlineReviseResponse)
def revise_story_outline(request: StoryOutlineReviseRequest) -> StoryOutlineReviseResponse:
    """作用：接收大纲修改请求并返回临时脚手架结果。

    调用方：Java AiEngineClient.reviseStoryOutline，即 StoryServiceImpl#reviseStoryOutline 的 Python 后端接口。
    TODO：后续替换为真正的 LangChain 大纲修改链。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    log_progress(trace_id, f"revision request accepted, user_id={request.user_id}, story_id={request.story_id}", started_at)
    log_progress(trace_id, "revision LangChain implementation is pending; returning scaffold response", started_at)

    revised_outline = (
        f"{request.outline}\n\n"
        "[Pending outline revision instruction]\n"
        f"{request.suggestion}\n\n"
        "The frontend -> Java -> Python revision call chain is connected. "
        "Real LangChain revision logic will be implemented later."
    )
    response = StoryOutlineReviseResponse(
        storySummary=request.synopsis or "Outline revision request received. Waiting for LangChain revision implementation.",
        outline=revised_outline,
        mainCharacters=[],
    )
    log_progress(trace_id, "revision scaffold response ready", started_at)
    return response


@app.post("/api/story/volume-outline", response_model=StoryVolumeOutlineGenerateResponse)
def generate_volume_outline(request: StoryVolumeOutlineGenerateRequest) -> StoryVolumeOutlineGenerateResponse:
    """作用：基于已保存的剧情大纲和角色设定生成 5 到 8 卷的详细分卷大纲。

    调用方：Java AiEngineClient.generateVolumeOutline，即 StoryServiceImpl#generateVolumeOutline 的 Python 后端接口。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "volume-outline"
    log_progress(
        trace_id,
        f"request accepted, user_id={request.user_id}, story_id={request.story_id}, title={request.title}",
        started_at,
        scope,
    )

    characters_text = "\n".join(
        [
            f"- {character.name}: {character.role or ''}; {character.description or ''}; {character.personality or ''}"
            for character in request.main_characters
        ]
    ) or "No character settings were provided."

    structured_llm = llm.with_structured_output(VolumeOutlineOutput)
    chain = promptTemplate_VolumeOutline | structured_llm
    log_progress(trace_id, "structured volume chain created, invoking Tongyi model", started_at, scope)
    result = chain.invoke(
        {
            "Title": request.title,
            "StorySummary": request.story_summary or "",
            "Outline": request.outline,
            "Characters": characters_text,
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
    )

    log_progress(trace_id, "model returned volume outline, preparing HTTP response", started_at, scope)
    response = StoryVolumeOutlineGenerateResponse(volumes=result.volumes)
    log_progress(trace_id, "response ready", started_at, scope)
    return response


@app.post("/api/chat/agent", response_model=ChatAgentResponse)
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

    structured_llm = llm.with_structured_output(ChatAgentOutput)
    chain = promptTemplate_ChatAgent | structured_llm
    log_progress(trace_id, "rewriting question and routing to Java method", started_at, scope)
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


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)
