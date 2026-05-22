import uuid
import time
from datetime import date
from pathlib import Path
from urllib.request import urlopen

from fastapi import HTTPException

from ai_runtime import ConsoleStreamingCallback, get_doubao_image_client, llm_temperature_0, log_progress

from .formatters import build_characters_text
from .models import (
    ExistingStoryAsset,
    SectionAssetExtractionItem,
    SectionAssetExtractionOutput,
    SectionAssetItem,
    StorySectionAssetGenerateRequest,
    StoryVolumeSectionGenerateRequest,
    StoryVolumeSectionGenerateResponse,
    VolumeSectionItem,
)
from .router import router
from .templates import promptTemplate_SectionAssetExtraction


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
    storage_root = Path(__file__).resolve().parents[2] / "backend" / "storage"
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
    return Path(__file__).resolve().parents[2] / "backend" / "storage" / normalized_path


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
