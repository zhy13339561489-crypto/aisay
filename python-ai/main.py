import os
import time
import uuid
from pathlib import Path
from typing import Any

import uvicorn
import yaml
from fastapi import FastAPI, HTTPException
from langchain_community.chat_models import ChatTongyi
from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.prompts import PromptTemplate
from pydantic import BaseModel, Field

from prompt import (
    prompt_ChatAgent,
    prompt_Outline,
    prompt_ReviseOutline,
    prompt_VolumeCount,
    prompt_VolumeOutline,
    prompt_VolumeOutlineSingle,
    prompt_VolumeOutline_Editor,
)


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

promptTemplate_ReviseOutline = PromptTemplate.from_template(prompt_ReviseOutline)

promptTemplate_VolumeOutline = PromptTemplate.from_template(prompt_VolumeOutline)

promptTemplate_VolumeCount = PromptTemplate.from_template(prompt_VolumeCount)

promptTemplate_VolumeOutlineSingle = PromptTemplate.from_template(prompt_VolumeOutlineSingle)

promptTemplate_VolumeOutlineEditor = PromptTemplate.from_template(prompt_VolumeOutline_Editor)

promptTemplate_ChatAgent = PromptTemplate.from_template(prompt_ChatAgent)

llm = ChatTongyi(
    model="qwen-max",
    temperature=0.5,
    top_p=0.8,
    streaming=True,
)

llm_temperature_0 = ChatTongyi(
    model="qwen-max",
    temperature=0,
    top_p=0.2,
    streaming=False,
)

structured_llm_base = ChatTongyi(
    model="qwen-max",
    temperature=0.5,
    top_p=0.8,
    streaming=False,
)

app = FastAPI(title="Aisay Python AI Engine")


class ConsoleStreamingCallback(BaseCallbackHandler):
    """LangChain 控制台流式回调处理器。

    在大模型流式生成过程中，将 token 逐个打印到 Python 控制台，便于开发调试时实时观察生成进度。
    同时记录每次调用的 trace_id 和耗时，用于日志追踪。
    """

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
    """剧情大纲生成请求体。

    对应 Java 端 AiEngineClient.generateStoryOutline 的入参，包含用户 ID、题材和可选剧情。
    """

    user_id: int = Field(alias="userId")
    genre: str
    plot: str | None = None


class MainCharacterSetting(BaseModel):
    """主要角色设定。

    描述一个核心角色的基本信息，包括姓名、角色定位、背景描述、性格特征和可选的外貌特征。
    用于剧情大纲和分卷大纲中的角色设定输出。
    """

    name: str = Field(description="Character name or codename")
    role: str = Field(description="Character role, such as protagonist, partner, antagonist, mentor, or hidden manipulator")
    description: str = Field(description="Character background, ability, and narrative function")
    personality: str = Field(description="Personality, core desire, weakness, or character arc")
    appearance: dict[str, str] | None = Field(
        default=None,
        description="Optional appearance traits, such as hairstyle, clothing, or signature item",
    )


class StoryOutlineGenerateResponse(BaseModel):
    """剧情大纲生成响应体。

    返回大模型生成的小说名称、故事摘要、完整大纲和主要角色设定列表。
    """

    novel_name: str = Field(alias="novelName")
    story_summary: str = Field(alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class StoryOutlineReviseRequest(BaseModel):
    """剧情大纲修改请求体。

    对应 Java 端 AiEngineClient.reviseStoryOutline 的入参，包含用户 ID、故事 ID、当前大纲内容和用户的修改建议。
    """

    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    title: str
    synopsis: str | None = None
    outline: str
    suggestion: str


class StoryOutlineReviseResponse(BaseModel):
    """剧情大纲修改响应体。

    返回修改后的故事摘要、大纲内容和更新后的角色设定列表。
    """

    story_summary: str = Field(alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class StoryVolumeOutlineGenerateRequest(BaseModel):
    """分卷大纲生成请求体。

    对应 Java 端 AiEngineClient.generateVolumeOutline 的入参，包含用户 ID、故事 ID、书名、故事摘要、主线大纲和角色设定。
    """

    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    title: str
    story_summary: str | None = Field(default=None, alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class VolumeOutlineItem(BaseModel):
    """单卷大纲条目。

    描述一卷的核心信息：卷号、标题、摘要、详细大纲内容和卷末悬念钩子。
    """

    volume_number: int = Field(alias="volumeNumber", description="Volume number, starting from 1")
    title: str = Field(description="Volume title")
    summary: str = Field(description="Short summary of this volume")
    content: str = Field(description="Detailed volume outline with rich plot beats, conflicts, reversals, choices, and emotional hooks")
    ending_hook: str = Field(alias="endingHook", description="The cliffhanger or hook at the end of this volume")


class StoryVolumeOutlineReviseRequest(BaseModel):
    """分卷大纲自动修改请求体。
    对应 Java 端 AiEngineClient.reviseVolumeOutline 的入参，包含原故事上下文、现有分卷大纲和用户修改意见。
    """

    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    title: str
    story_summary: str | None = Field(default=None, alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")
    volume_outlines: list[VolumeOutlineItem] = Field(default_factory=list, alias="volumeOutlines")
    suggestion: str


class StoryVolumeOutlineGenerateResponse(BaseModel):
    """分卷大纲生成响应体。

    包含大模型生成的分卷大纲列表，每卷包含卷号、标题、摘要、详细内容和卷末钩子。
    """

    volumes: list[VolumeOutlineItem]


class ChatAgentRequest(BaseModel):
    """对话 Agent 请求体。

    对应 Java 端 AiEngineClient.runChatAgent 的入参，包含用户 ID、会话 ID、故事 ID、书名、摘要、大纲和用户消息。
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

    大模型返回的结构化结果，包含重写后的用户问题、路由标签、目标 Java 方法名、方法参数和助手回复。
    """

    rewritten_question: str = Field(alias="rewrittenQuestion", description="Standalone rewritten user question")
    route: str = Field(description="Routing label, such as update_outline or no_action")
    java_method: str = Field(alias="javaMethod", description="One supported Java method name")
    java_method_args: dict[str, Any] = Field(default_factory=dict, alias="javaMethodArgs")
    assistant_message: str = Field(alias="assistantMessage", description="Short Chinese response to the user")


class ChatAgentResponse(ChatAgentOutput):
    """对话 Agent HTTP 响应体。

    继承 ChatAgentOutput，作为 FastAPI 接口的返回模型，字段与输出一致。
    """

    pass


class NovelOutlineOutput(BaseModel):
    """剧情大纲结构化输出模型。

    定义大模型返回的剧情大纲结构，包括小说名称、故事摘要、完整大纲和主要角色设定。
    通过 LangChain 的 with_structured_output 绑定，确保模型输出可直接反序列化为 Python 对象。
    """

    novel_name: str = Field(description="Novel title, concise and recognizable")
    story_summary: str = Field(description="Story summary, 200-300 Chinese characters")
    outline: str = Field(description="Full story outline with setting, main plot, stages, and key characters")
    main_characters: list[MainCharacterSetting] = Field(description="Main character settings, usually 3-5 key characters")


class OutlineRevisionOutput(BaseModel):
    """剧情大纲修改结构化输出模型。

    定义大模型修改大纲后必须返回的结构，包括新的故事摘要、完整修改后大纲和主要角色设定。
    通过 LangChain 的 with_structured_output 绑定，确保 Java 端能稳定反序列化并保存到数据库。
    """

    story_summary: str = Field(description="Updated story summary after applying the user's revision notes")
    outline: str = Field(description="Complete revised story outline, including unchanged sections and revised sections")
    main_characters: list[MainCharacterSetting] = Field(
        default_factory=list,
        description="Updated main character settings inferred from the revised outline",
    )


class VolumeCountOutput(BaseModel):
    """分卷数量规划结构化输出模型。
    由温度为 0 的模型先判断总分卷数，再供逐卷生成链路使用。
    """

    volume_count: int = Field(
        ge=5,
        le=20,
        description="Recommended total volume count, constrained to an integer between 5 and 20",
    )


class VolumeOutlineOutput(BaseModel):
    """分卷大纲结构化输出模型。

    定义大模型返回的分卷大纲结构，承载多卷详细大纲列表。
    通过 LangChain 的 with_structured_output 绑定使用。
    """

    volumes: list[VolumeOutlineItem] = Field(description="Detailed volume outline list")


def build_characters_text(main_characters: list[MainCharacterSetting]) -> str:
    """作用：把结构化角色设定压缩为提示词可读文本。
    调用方：generate_volume_outline、revise_volume_outline。
    """
    return "\n".join(
        [
            (
                f"- {character.name}: {character.role or ''}; "
                f"{character.description or ''}; {character.personality or ''}; "
                f"appearance={character.appearance or {}}"
            )
            for character in main_characters
        ]
    ) or "No character settings were provided."


def format_volume_outline_context(volumes: list[VolumeOutlineItem]) -> str:
    """作用：把已经生成或已经存在的分卷大纲整理为下一次模型调用的上下文。
    调用方：generate_volume_outline、revise_volume_outline。
    """
    if not volumes:
        return "暂无"

    return "\n\n".join(
        [
            (
                f"第 {volume.volume_number} 卷：{volume.title}\n"
                f"摘要：{volume.summary}\n"
                f"详细大纲：\n{volume.content}\n"
                f"卷末钩子：{volume.ending_hook}"
            )
            for volume in volumes
        ]
    )


@app.post("/api/story/outline", response_model=StoryOutlineGenerateResponse)
def generate_story_outline(request: StoryOutlineGenerateRequest) -> StoryOutlineGenerateResponse:
    """作用：根据题材和可选剧情生成结构化剧情大纲、故事摘要和主要角色设定。

    调用方：Java AiEngineClient.generateStoryOutline，即 StoryServiceImpl#generateStory 的 Python 后端接口。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    log_progress(
        trace_id,
        f"request accepted, user_id={request.user_id}, genre={request.genre}",
        started_at,
    )

    structured_llm = structured_llm_base.with_structured_output(NovelOutlineOutput)
    chain = promptTemplate_Outline | structured_llm
    log_progress(trace_id, "structured chain created, invoking Tongyi model in non-streaming mode", started_at)

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
    """作用：根据用户修改意见调用 LangChain 修改剧情大纲，并返回结构化结果。

    调用方：Java AiEngineClient.reviseStoryOutline，即 StoryServiceImpl#reviseStoryOutline 的 Python 后端接口。
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


@app.post("/api/story/volume-outline", response_model=StoryVolumeOutlineGenerateResponse)
def generate_volume_outline(request: StoryVolumeOutlineGenerateRequest) -> StoryVolumeOutlineGenerateResponse:
    """作用：基于已保存的剧情大纲和角色设定生成 8 到 15 卷的详细分卷大纲。

    调用方：Java AiEngineClient.generateVolumeOutline，即 StoryServiceImpl#generateVolumeOutline 的 Python 后端接口。
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

    if not generated_volumes:
        raise HTTPException(status_code=502, detail="Tongyi model returned no volume outlines")

    log_progress(trace_id, f"model returned {len(generated_volumes)} volume outlines, preparing HTTP response", started_at, scope)
    response = StoryVolumeOutlineGenerateResponse(volumes=generated_volumes)
    log_progress(trace_id, "response ready", started_at, scope)
    return response


@app.post("/api/story/volume-outline/revise", response_model=StoryVolumeOutlineGenerateResponse)
def revise_volume_outline(request: StoryVolumeOutlineReviseRequest) -> StoryVolumeOutlineGenerateResponse:
    """作用：根据用户修改意见、原分卷大纲和故事上下文自动重写分卷大纲。
    调用方：Java AiEngineClient.reviseVolumeOutline，即 StoryServiceImpl#reviseVolumeOutline 的 Python 后端接口。
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


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)
