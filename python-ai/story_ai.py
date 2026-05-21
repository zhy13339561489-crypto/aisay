import time
import uuid
from collections.abc import Callable
from datetime import date
import re
from pathlib import Path
from urllib.request import urlopen

from fastapi import APIRouter, HTTPException
from langchain_core.prompts import PromptTemplate
from pydantic import BaseModel, Field

from ai_runtime import (
    ConsoleStreamingCallback,
    get_doubao_image_client,
    llm_temperature_0,
    log_progress,
    streaming_text_llm_base,
    structured_llm_base,
)
from prompt import (
    prompt_Outline,
    prompt_ReviseOutline,
    prompt_SectionAssetExtraction,
    prompt_VolumeCount,
    prompt_VolumeOutlineSingle,
    prompt_VolumeOutline_Editor,
    prompt_VolumeSectionCount,
    prompt_VolumeSectionSingle,
    prompt_VolumeStory,
)


router = APIRouter()

promptTemplate_Outline = PromptTemplate.from_template(prompt_Outline)
promptTemplate_ReviseOutline = PromptTemplate.from_template(prompt_ReviseOutline)
promptTemplate_VolumeCount = PromptTemplate.from_template(prompt_VolumeCount)
promptTemplate_VolumeOutlineSingle = PromptTemplate.from_template(prompt_VolumeOutlineSingle)
promptTemplate_VolumeOutlineEditor = PromptTemplate.from_template(prompt_VolumeOutline_Editor)
promptTemplate_VolumeSectionCount = PromptTemplate.from_template(prompt_VolumeSectionCount)
promptTemplate_VolumeSectionSingle = PromptTemplate.from_template(prompt_VolumeSectionSingle)
promptTemplate_VolumeStory = PromptTemplate.from_template(prompt_VolumeStory)
promptTemplate_SectionAssetExtraction = PromptTemplate.from_template(prompt_SectionAssetExtraction)


class StoryOutlineGenerateRequest(BaseModel):
    """剧情大纲生成请求体。
    对应 Java 端 RabbitMQ 故事生成任务的入参，包含用户 ID、题材、用户设定风格和可选剧情。
    """

    user_id: int = Field(alias="userId")
    genre: str
    story_style: str | None = Field(default=None, alias="storyStyle")
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
    对应 Java 端 RabbitMQ 故事大纲修改任务的入参。
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
    对应 Java 端 RabbitMQ 分卷大纲生成任务的入参。
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
    对应 Java 端 RabbitMQ 分卷大纲修改任务的入参。
    """

    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    title: str
    story_summary: str | None = Field(default=None, alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")
    volume_outlines: list[VolumeOutlineItem] = Field(default_factory=list, alias="volumeOutlines")
    suggestion: str


class StoryVolumeStoryGenerateRequest(BaseModel):
    """分卷详细故事正文生成请求体。
    调用方：rabbitmq_worker 分卷正文生成任务；同时保留 HTTP 路由用于本地调试。
    """

    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    volume_id: int = Field(alias="volumeId")
    title: str
    story_style: str | None = Field(default=None, alias="storyStyle")
    story_summary: str | None = Field(default=None, alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")
    volume_outline: VolumeOutlineItem = Field(alias="volumeOutline")


class ExistingStoryAsset(BaseModel):
    """Java 已有故事资产引用，用于判断人物/场景是否第一次出现。"""

    asset_type: str = Field(alias="assetType")
    name: str
    description: str | None = None
    image_prompt: str | None = Field(default=None, alias="imagePrompt")
    image_path: str | None = Field(default=None, alias="imagePath")
    audio_path: str | None = Field(default=None, alias="audioPath")


class SectionAssetItem(BaseModel):
    """当前小节出现的人物或场景资产。"""

    asset_type: str = Field(alias="assetType", description="CHARACTER or SCENE")
    name: str
    description: str | None = None
    image_prompt: str | None = Field(default=None, alias="imagePrompt")
    image_path: str | None = Field(default=None, alias="imagePath")
    audio_path: str | None = Field(default=None, alias="audioPath")
    first_appearance: bool = Field(default=False, alias="firstAppearance")
    generation_error: str | None = Field(default=None, alias="generationError")


class StoryVolumeSectionGenerateRequest(BaseModel):
    """分卷小节生成请求体。
    调用方：rabbitmq_worker 分卷小节生成任务；同时保留 HTTP 路由用于本地调试。
    """

    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    volume_id: int = Field(alias="volumeId")
    title: str
    story_style: str | None = Field(default=None, alias="storyStyle")
    story_summary: str | None = Field(default=None, alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")
    volume_outline: VolumeOutlineItem = Field(alias="volumeOutline")
    existing_assets: list[ExistingStoryAsset] = Field(default_factory=list, alias="existingAssets")


class StoryVolumeOutlineGenerateResponse(BaseModel):
    """分卷大纲生成响应体。
    包含大模型生成的分卷大纲列表。
    """

    volumes: list[VolumeOutlineItem]


class StoryVolumeStoryGenerateResponse(BaseModel):
    """分卷详细故事正文生成响应体。"""

    volume_story: str = Field(alias="volumeStory")


class VolumeSectionItem(BaseModel):
    """单个分卷小节故事细节。"""

    section_number: int = Field(alias="sectionNumber", description="Section number, starting from 1")
    title: str = Field(description="Section title")
    summary: str = Field(description="Short summary of this section")
    content: str = Field(description="Detailed story beats for this section, including scene, action, dialogue, conflict, and emotion")
    ending_hook: str = Field(alias="endingHook", description="Hook or pressure that leads into the next section")
    assets: list[SectionAssetItem] = Field(default_factory=list, description="Characters and scenes that appear in this section")


class StoryVolumeSectionGenerateResponse(BaseModel):
    """分卷小节生成响应体。"""

    sections: list[VolumeSectionItem]


class StorySectionAssetGenerateRequest(BaseModel):
    """单个小节人物/场景资产生成请求体。
    调用方：rabbitmq_worker 小节资产生成任务；同时保留 HTTP 路由用于本地调试。
    """

    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    volume_id: int = Field(alias="volumeId")
    title: str
    story_style: str | None = Field(default=None, alias="storyStyle")
    story_summary: str | None = Field(default=None, alias="storySummary")
    outline: str | None = None
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")
    volume_outline: VolumeOutlineItem = Field(alias="volumeOutline")
    section: VolumeSectionItem
    existing_assets: list[ExistingStoryAsset] = Field(default_factory=list, alias="existingAssets")


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


class VolumeSectionCountOutput(BaseModel):
    """分卷小节数量规划结构化输出模型。"""

    section_count: int = Field(
        ge=4,
        le=12,
        description="Recommended section count, constrained to an integer between 4 and 12",
    )


class SectionAssetExtractionItem(BaseModel):
    """LLM 从小节中识别出的单个人物或场景。"""

    name: str = Field(description="Stable character or scene name")
    description: str = Field(description="Short reusable description")
    image_prompt: str = Field(alias="imagePrompt", description="Prompt for Doubao image generation")
    matched_existing_name: str | None = Field(
        default=None,
        alias="matchedExistingName",
        description="Exact existing asset name when this item is the same character or scene as one existing asset",
    )


class SectionAssetExtractionOutput(BaseModel):
    """小节人物/场景识别结构化输出模型。"""

    characters: list[SectionAssetExtractionItem] = Field(default_factory=list)
    scenes: list[SectionAssetExtractionItem] = Field(default_factory=list)


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


def format_volume_section_context(sections: list[VolumeSectionItem]) -> str:
    """作用：把已经生成的小节整理为下一节生成时的上下文。
    调用方：generate_volume_sections。
    """
    if not sections:
        return "暂无"

    return "\n\n".join(
        [
            (
                f"第 {section.section_number} 节：{section.title}\n"
                f"摘要：{section.summary}\n"
                f"具体故事细节：\n{section.content}\n"
                f"小节钩子：{section.ending_hook}"
            )
            for section in sections
        ]
    )


def normalize_asset_key(asset_type: str, name: str) -> str:
    """作用：把资产类型和名称归一化为查重 key。
    调用方：build_asset_registry、resolve_section_assets。
    """
    return f"{asset_type.strip().upper()}::{name.strip().lower()}"


def build_asset_registry(existing_assets: list[ExistingStoryAsset]) -> dict[str, SectionAssetItem]:
    """作用：把 Java 已有资产列表转为可快速判断首次出现的字典。
    调用方：generate_volume_sections。
    """
    registry: dict[str, SectionAssetItem] = {}
    for asset in existing_assets:
        if not asset.name:
            continue
        asset_type = asset.asset_type.strip().upper()
        registry[normalize_asset_key(asset_type, asset.name)] = SectionAssetItem(
            assetType=asset_type,
            name=asset.name,
            description=asset.description,
            imagePrompt=asset.image_prompt,
            imagePath=asset.image_path,
            audioPath=asset.audio_path,
            firstAppearance=False,
        )
    return registry


def format_existing_asset_context(asset_registry: dict[str, SectionAssetItem]) -> str:
    """作用：把已有故事资产整理为 LLM 识别人物/场景时的上下文。
    调用方：resolve_section_assets。
    """
    if not asset_registry:
        return "暂无"

    return "\n".join(
        [
            (
                f"- [{asset.asset_type}] {asset.name}：{asset.description or '无描述'}；"
                f"图片提示词：{asset.image_prompt or '暂无'}；"
                f"图片：{asset.image_path or '暂无'}；音频：{asset.audio_path or '暂无'}"
            )
            for asset in asset_registry.values()
        ]
    )


def save_doubao_image_file(prompt: str) -> str:
    """作用：调用豆包图片生成接口并把返回图片保存到共享本地 storage 目录。
    调用方：resolve_single_section_asset。
    """
    client = get_doubao_image_client()

    print("------------------------------------------------------------------------------")
    images_response = client.images.generate(
        model="doubao-seedream-4-5-251128",
        prompt=prompt,
        size="2K",
        response_format="url",
        extra_body={
            "watermark": True,
        },
    )

    print(prompt)
    print(images_response)
    image_url = images_response.data[0].url
    if not image_url:
        raise RuntimeError("Doubao image response does not contain url")

    category = "generated-assets"
    current_date = date.today().isoformat()
    filename = f"{uuid.uuid4()}.png"
    storage_root = Path(__file__).resolve().parent.parent / "backend" / "storage"
    target_directory = storage_root / category / current_date
    target_directory.mkdir(parents=True, exist_ok=True)
    target_file = target_directory / filename

    with urlopen(image_url, timeout=180) as response:
        target_file.write_bytes(response.read())

    return f"{category}/{current_date}/{filename}"


def resolve_storage_file(relative_path: str | None) -> Path | None:
    """作用：把 Java storage 中保存的相对路径解析为本地文件路径。
    调用方：has_local_image_file。
    """
    if not relative_path or not relative_path.strip():
        return None
    normalized_path = relative_path.strip().replace("\\", "/").lstrip("/")
    return Path(__file__).resolve().parent.parent / "backend" / "storage" / normalized_path


def has_local_image_file(relative_path: str | None) -> bool:
    """作用：判断资产图片路径是否真的已经落在本地文件系统，而不是只在数据库里有字符串。
    调用方：resolve_single_section_asset。
    """
    file_path = resolve_storage_file(relative_path)
    return bool(file_path and file_path.is_file())


def generate_asset_image_path(
    title: str,
    story_style: str | None,
    asset_type: str,
    asset_name: str,
    image_prompt: str,
    trace_id: str,
    started_at: float,
    scope: str,
) -> tuple[str | None, str | None, bool]:
    """作用：调用豆包为指定人物/场景生成图片。
    调用方：resolve_single_section_asset。
    """
    log_progress(trace_id, f"calling doubao image generation for {asset_type}: {asset_name}", started_at, scope)
    try:
        prompt_doubao = build_doubao_prompt(title, story_style, asset_type, asset_name, image_prompt)

        image_path = save_doubao_image_file(
            prompt_doubao
        )
        return image_path, None, True
    except Exception as exc:
        print("图片生成失败")
        return None, str(exc), True


def build_doubao_prompt(
    title: str,
    story_style: str | None,
    asset_type: str,
    asset_name: str,
    image_prompt: str,
) -> str:
    """作用：根据资产类型补全豆包图片生成提示词，统一漫剧资源视觉风格。
    调用方：resolve_single_section_asset。
    """
    resolved_style = story_style.strip() if story_style and story_style.strip() else "高质量国漫/漫剧视觉"
    normalized_type = asset_type.strip().upper()
    if normalized_type == "CHARACTER":
        return build_character_three_view_prompt(title, resolved_style, asset_name, image_prompt)
    return build_scene_concept_prompt(title, resolved_style, asset_name, image_prompt)


def build_character_three_view_prompt(
    title: str,
    resolved_style: str,
    asset_name: str,
    image_prompt: str,
) -> str:
    """作用：构造人物三视图提示词，用于角色首次成图和后续视频制作参考。
    调用方：build_doubao_prompt。
    """
    return (
        f"为漫剧《{title}》生成角色“{asset_name}”的人物三视图设定图。\n"
        f"用户设定的统一漫剧风格：{resolved_style}\n"
        f"角色描述：{image_prompt}\n"
        "画面要求：同一角色必须在同一张图中展示正面、侧面、背面三视图，三视图保持完全一致的发型、脸型、服装、配饰、身材比例和色彩方案。\n"
        "构图要求：白色或浅灰纯色背景，角色站姿自然，正面/侧面/背面横向排列，比例统一，无遮挡，不要出现多名不同角色。\n"
        "细节要求：强调可复用的角色设计、服装结构、标志性道具、面部特征和配色，适合后续分镜、建模、视频生成和角色一致性参考。\n"
        f"最终画面必须严格遵循“{resolved_style}”。"
    )


def build_scene_concept_prompt(
    title: str,
    resolved_style: str,
    asset_name: str,
    image_prompt: str,
) -> str:
    """作用：构造场景概念图提示词，用于环境资产首次成图和后续视频制作参考。
    调用方：build_doubao_prompt。
    """
    return (
        f"为漫剧《{title}》生成场景“{asset_name}”的环境概念图。\n"
        f"用户设定的统一漫剧风格：{resolved_style}\n"
        f"场景描述：{image_prompt}\n"
        "画面要求：突出空间结构、时代质感、光影氛围、关键道具、可复用背景元素和镜头调度空间。\n"
        "构图要求：单一完整场景，不要画成人物三视图，不要把角色作为主体；可以保留少量远景人物作为尺度参考，但重点必须是环境。\n"
        "细节要求：适合后续分镜、视频生成和同场景复用，画面层次清晰，色彩和材质统一。\n"
        f"最终画面必须严格遵循“{resolved_style}”。"
    )


def resolve_single_section_asset(
    title: str,
    story_style: str | None,
    asset_type: str,
    extracted: SectionAssetExtractionItem,
    asset_registry: dict[str, SectionAssetItem],
    trace_id: str,
    started_at: float,
    scope: str,
) -> SectionAssetItem:
    """作用：判断单个资产是否首次出现，首次出现时调用豆包生成图片。
    调用方：resolve_section_assets。
    """
    normalized_type = asset_type.strip().upper()
    if extracted.matched_existing_name:
        matched_key = normalize_asset_key(normalized_type, extracted.matched_existing_name)
        matched = asset_registry.get(matched_key)
        if matched and has_local_image_file(matched.image_path):
            log_progress(trace_id, f"reuse matched {normalized_type} image: {matched.name} -> {matched.image_path}", started_at, scope)
            return SectionAssetItem(
                assetType=normalized_type,
                name=matched.name,
                description=matched.description or extracted.description,
                imagePrompt=matched.image_prompt or extracted.image_prompt,
                imagePath=matched.image_path,
                audioPath=matched.audio_path,
                firstAppearance=False,
            )
        if matched:
            log_progress(
                trace_id,
                f"matched {normalized_type} asset record but image is missing, regenerating: {matched.name}, image_path={matched.image_path}",
                started_at,
                scope,
            )
            image_path, generation_error, _ = generate_asset_image_path(
                title,
                story_style,
                normalized_type,
                matched.name,
                extracted.image_prompt,
                trace_id,
                started_at,
                scope,
            )
            return SectionAssetItem(
                assetType=normalized_type,
                name=matched.name,
                description=matched.description or extracted.description,
                imagePrompt=matched.image_prompt or extracted.image_prompt,
                imagePath=image_path,
                audioPath=matched.audio_path,
                firstAppearance=False,
                generationError=generation_error,
            )

    key = normalize_asset_key(normalized_type, extracted.name)
    existing = asset_registry.get(key)
    if existing and has_local_image_file(existing.image_path):
        log_progress(trace_id, f"reuse existing {normalized_type} image: {existing.name} -> {existing.image_path}", started_at, scope)
        return SectionAssetItem(
            assetType=normalized_type,
            name=existing.name,
            description=existing.description or extracted.description,
            imagePrompt=existing.image_prompt or extracted.image_prompt,
            imagePath=existing.image_path,
            audioPath=existing.audio_path,
            firstAppearance=False,
        )
    if existing:
        log_progress(
            trace_id,
            f"existing {normalized_type} asset record has no usable image, regenerating: {existing.name}, image_path={existing.image_path}",
            started_at,
            scope,
        )
        image_path, generation_error, _ = generate_asset_image_path(
            title,
            story_style,
            normalized_type,
            existing.name,
            extracted.image_prompt,
            trace_id,
            started_at,
            scope,
        )
        return SectionAssetItem(
            assetType=normalized_type,
            name=existing.name,
            description=existing.description or extracted.description,
            imagePrompt=existing.image_prompt or extracted.image_prompt,
            imagePath=image_path,
            audioPath=existing.audio_path,
            firstAppearance=False,
            generationError=generation_error,
        )

    log_progress(trace_id, f"no existing {normalized_type} asset record, generating image: {extracted.name}", started_at, scope)
    image_path, generation_error, _ = generate_asset_image_path(
        title,
        story_style,
        normalized_type,
        extracted.name,
        extracted.image_prompt,
        trace_id,
        started_at,
        scope,
    )

    asset = SectionAssetItem(
        assetType=normalized_type,
        name=extracted.name,
        description=extracted.description,
        imagePrompt=extracted.image_prompt,
        imagePath=image_path,
        audioPath=None,
        firstAppearance=True,
        generationError=generation_error,
    )
    asset_registry[key] = asset
    return asset


def resolve_section_assets(
    request: StoryVolumeSectionGenerateRequest | StorySectionAssetGenerateRequest,
    section: VolumeSectionItem,
    asset_registry: dict[str, SectionAssetItem],
    characters_text: str,
    trace_id: str,
    started_at: float,
    scope: str,
) -> list[SectionAssetItem]:
    """作用：用温度为 0 的结构化 LLM 识别本节人物/场景，并生成或复用本地资产。
    调用方：generate_volume_sections。
    """
    extraction_llm = llm_temperature_0.with_structured_output(SectionAssetExtractionOutput)
    extraction_chain = promptTemplate_SectionAssetExtraction | extraction_llm
    log_progress(trace_id, f"extracting assets for section {section.section_number}", started_at, scope)
    extraction = extraction_chain.invoke(
        {
            "Title": request.title,
            "StoryStyle": request.story_style or "高质量国漫/漫剧视觉",
            "StorySummary": request.story_summary or "",
            "Outline": request.outline,
            "Characters": characters_text,
            "VolumeNumber": request.volume_outline.volume_number,
            "VolumeTitle": request.volume_outline.title,
            "SectionNumber": section.section_number,
            "SectionTitle": section.title,
            "SectionSummary": section.summary or "",
            "SectionContent": section.content,
            "ExistingAssets": format_existing_asset_context(asset_registry),
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id, scope)]},
    )

    section_assets: list[SectionAssetItem] = []
    log_progress(
        trace_id,
        f"asset extraction result: characters={len(extraction.characters)}, scenes={len(extraction.scenes)}",
        started_at,
        scope,
    )
    for character in extraction.characters:
        section_assets.append(resolve_single_section_asset(request.title, request.story_style, "CHARACTER", character, asset_registry, trace_id, started_at, scope))
    for scene in extraction.scenes:
        section_assets.append(resolve_single_section_asset(request.title, request.story_style, "SCENE", scene, asset_registry, trace_id, started_at, scope))

    log_progress(trace_id, f"section {section.section_number} assets resolved: {len(section_assets)}", started_at, scope)
    return section_assets


def extract_llm_text(raw_result: object) -> str:
    """作用：从普通 ChatModel 响应中提取文本内容。
    调用方：generate_volume_story。长篇正文不再使用 structured output，避免 tool/json 解析失败返回 None。
    """
    if raw_result is None:
        return ""

    content = getattr(raw_result, "content", raw_result)
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict):
                text = item.get("text") or item.get("content")
                if text:
                    parts.append(str(text))
        return "\n".join(parts).strip()
    return str(content).strip()


def extract_tagged_text(raw_text: str, tag: str) -> str:
    """作用：从普通文本输出中提取指定 XML 风格标签内容。
    调用方：parse_volume_section_text。
    """
    pattern = rf"<{tag}>(.*?)</{tag}>"
    match = re.search(pattern, raw_text, flags=re.DOTALL | re.IGNORECASE)
    if not match:
        return ""
    return match.group(1).strip()


def parse_volume_section_text(raw_text: str, section_number: int) -> VolumeSectionItem:
    """作用：把单节普通文本输出解析成小节结构，避免长正文 tool JSON 转义失败。
    调用方：generate_volume_sections。
    """
    title = extract_tagged_text(raw_text, "title")
    summary = extract_tagged_text(raw_text, "summary")
    content = extract_tagged_text(raw_text, "content")
    ending_hook = extract_tagged_text(raw_text, "endingHook")
    if not content:
        raise HTTPException(status_code=502, detail="Tongyi model returned malformed volume section text")

    return VolumeSectionItem(
        sectionNumber=section_number,
        title=title or f"第 {section_number} 节",
        summary=summary,
        content=content,
        endingHook=ending_hook,
    )


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


@router.post("/api/story/volume-sections", response_model=StoryVolumeSectionGenerateResponse)
def generate_volume_sections_endpoint(request: StoryVolumeSectionGenerateRequest) -> StoryVolumeSectionGenerateResponse:
    """作用：提供 HTTP 调试入口，实际生成逻辑委托给内部函数。
    调用方：本地调试或直接 HTTP 调用。
    """
    return generate_volume_sections(request)


@router.post("/api/story/section-assets", response_model=StoryVolumeSectionGenerateResponse)
def generate_section_assets(request: StorySectionAssetGenerateRequest) -> StoryVolumeSectionGenerateResponse:
    """作用：只为指定小节识别并生成/复用人物与场景资产。
    调用方：rabbitmq_worker 小节资产生成任务；同时保留 HTTP 路由用于本地调试。
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "section-assets"
    if not request.section.content or not request.section.content.strip():
        raise HTTPException(status_code=400, detail="Section content is required before generating section assets")

    log_progress(
        trace_id,
        (
            f"request accepted, user_id={request.user_id}, story_id={request.story_id}, "
            f"volume_id={request.volume_id}, section={request.section.section_number}"
        ),
        started_at,
        scope,
    )

    asset_registry = build_asset_registry(request.existing_assets)
    characters_text = build_characters_text(request.main_characters)
    section = request.section
    section.assets = resolve_section_assets(
        request,
        section,
        asset_registry,
        characters_text,
        trace_id,
        started_at,
        scope,
    )
    log_progress(trace_id, f"section assets generated: {len(section.assets)}", started_at, scope)

    return StoryVolumeSectionGenerateResponse(sections=[section])


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
