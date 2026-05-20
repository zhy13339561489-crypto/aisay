import time
import uuid

from fastapi import APIRouter, HTTPException
from langchain_core.prompts import PromptTemplate
from pydantic import BaseModel, Field

from ai_runtime import ConsoleStreamingCallback, llm_temperature_0, log_progress, structured_llm_base
from prompt import (
    prompt_Outline,
    prompt_ReviseOutline,
    prompt_VolumeCount,
    prompt_VolumeOutlineSingle,
    prompt_VolumeOutline_Editor,
)


router = APIRouter()

promptTemplate_Outline = PromptTemplate.from_template(prompt_Outline)
promptTemplate_ReviseOutline = PromptTemplate.from_template(prompt_ReviseOutline)
promptTemplate_VolumeCount = PromptTemplate.from_template(prompt_VolumeCount)
promptTemplate_VolumeOutlineSingle = PromptTemplate.from_template(prompt_VolumeOutlineSingle)
promptTemplate_VolumeOutlineEditor = PromptTemplate.from_template(prompt_VolumeOutline_Editor)


class StoryOutlineGenerateRequest(BaseModel):
    """剧情大纲生成请求体。
    对应 Java 端 AiEngineClient.generateStoryOutline 的入参，包含用户 ID、题材和可选剧情。
    """

    user_id: int = Field(alias="userId")
    genre: str
    plot: str | None = None


class MainCharacterSetting(BaseModel):
    """主要角色设定。
    描述一个核心角色的基本信息，用于剧情大纲、分卷大纲和修改链路。
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
    对应 Java 端 AiEngineClient.reviseStoryOutline 的入参。
    """

    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    title: str
    synopsis: str | None = None
    outline: str
    suggestion: str


class StoryOutlineReviseResponse(BaseModel):
    """剧情大纲修改响应体。
    返回修改后的故事摘要、大纲内容和更新后的角色设定。
    """

    story_summary: str = Field(alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class StoryVolumeOutlineGenerateRequest(BaseModel):
    """分卷大纲生成请求体。
    对应 Java 端 AiEngineClient.generateVolumeOutline 的入参。
    """

    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    title: str
    story_summary: str | None = Field(default=None, alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class VolumeOutlineItem(BaseModel):
    """单卷大纲条目。
    描述一卷的卷号、标题、摘要、详细大纲内容和卷末悬念钩子。
    """

    volume_number: int = Field(alias="volumeNumber", description="Volume number, starting from 1")
    title: str = Field(description="Volume title")
    summary: str = Field(description="Short summary of this volume")
    content: str = Field(description="Detailed volume outline with rich plot beats, conflicts, reversals, choices, and emotional hooks")
    ending_hook: str = Field(alias="endingHook", description="The cliffhanger or hook at the end of this volume")


class StoryVolumeOutlineReviseRequest(BaseModel):
    """分卷大纲自动修改请求体。
    对应 Java 端 AiEngineClient.reviseVolumeOutline 的入参。
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
    包含大模型生成的分卷大纲列表。
    """

    volumes: list[VolumeOutlineItem]


class NovelOutlineOutput(BaseModel):
    """剧情大纲结构化输出模型。
    通过 LangChain with_structured_output 绑定，确保输出可反序列化。
    """

    novel_name: str = Field(description="Novel title, concise and recognizable")
    story_summary: str = Field(description="Story summary, 200-300 Chinese characters")
    outline: str = Field(description="Full story outline with setting, main plot, stages, and key characters")
    main_characters: list[MainCharacterSetting] = Field(description="Main character settings, usually 3-5 key characters")


class OutlineRevisionOutput(BaseModel):
    """剧情大纲修改结构化输出模型。
    定义模型修改大纲后必须返回的结构。
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
    定义大模型返回的多卷大纲列表结构。
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


@router.post("/api/story/outline", response_model=StoryOutlineGenerateResponse)
def generate_story_outline(request: StoryOutlineGenerateRequest) -> StoryOutlineGenerateResponse:
    """作用：根据题材和可选剧情生成结构化剧情大纲、故事摘要和主要角色设定。
    调用方：Java AiEngineClient.generateStoryOutline，即 StoryServiceImpl#generateStory 的 Python 后端接口。
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


@router.post("/api/story/volume-outline", response_model=StoryVolumeOutlineGenerateResponse)
def generate_volume_outline(request: StoryVolumeOutlineGenerateRequest) -> StoryVolumeOutlineGenerateResponse:
    """作用：先规划总分卷数，再逐卷生成连续一致的详细分卷大纲。
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


@router.post("/api/story/volume-outline/revise", response_model=StoryVolumeOutlineGenerateResponse)
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
