from pydantic import BaseModel, Field


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


class StorySectionScriptGenerateRequest(BaseModel):
    """单个小节故事脚本生成请求体。
    调用方：rabbitmq_worker 小节脚本生成任务，同时保留 HTTP 路由用于本地调试。
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


class ScriptShotItem(BaseModel):
    """单个故事脚本分镜。"""

    shot_number: int = Field(alias="shotNumber", ge=1, description="Shot number, starting from 1")
    duration_seconds: int = Field(alias="durationSeconds", ge=1, description="Duration of this shot in seconds")
    shot_type: str = Field(alias="shotType", description="Shot type, such as 全景, 近景, 特写, 推拉, 摇移")
    camera_movement: str | None = Field(default=None, alias="cameraMovement", description="Camera movement for this shot")
    action: str = Field(description="Visible action, emotion, scene change, and cinematic direction")
    dialogue: str | None = Field(default=None, description="Spoken dialogue in this shot, empty when no dialogue")


class StorySectionScriptGenerateResponse(BaseModel):
    """小节故事脚本生成响应体。"""

    section_number: int = Field(alias="sectionNumber")
    total_duration_seconds: int = Field(alias="totalDurationSeconds", ge=1)
    shots: list[ScriptShotItem]


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
