import json
from collections.abc import Callable
from typing import Any

from langchain_core.tools import Tool


def to_json(data: dict[str, Any]) -> str:
    return json.dumps(data, ensure_ascii=False)


def parse_tool_input(raw_input: str) -> dict[str, Any]:
    if not raw_input:
        return {}
    if isinstance(raw_input, dict):
        return raw_input
    try:
        parsed = json.loads(raw_input)
        return parsed if isinstance(parsed, dict) else {}
    except json.JSONDecodeError:
        return {"rawText": str(raw_input)}


def ask_missing_info(raw_input: str) -> str:
    data = parse_tool_input(raw_input)
    missing = data.get("missingInfo") or data.get("missing") or []
    if isinstance(missing, str):
        missing = [missing]
    question = data.get("question") or data.get("assistantMessage")
    if not question:
        question = "还缺少一些必要信息，请补充：" + "、".join(missing or ["必要参数"])
    return to_json({
        "javaMethod": "story.none",
        "javaMethodArgs": {},
        "assistantMessage": question,
        "missingInfo": missing,
    })


def deny_permission(raw_input: str) -> str:
    data = parse_tool_input(raw_input)
    required = data.get("requiredPermission") or data.get("requiredRole") or "更高权限"
    return to_json({
        "javaMethod": "story.none",
        "javaMethodArgs": {},
        "assistantMessage": f"当前账号没有执行该操作的权限，需要 {required} 权限。",
        "missingInfo": [],
    })


def make_action_tool(
        name: str,
        description: str,
        java_method: str,
        required_fields: list[str] | None = None,
        optional_fields: list[str] | None = None,
        aliases: dict[str, str] | None = None,
) -> Tool:
    required = required_fields or []
    optional = optional_fields or []
    field_aliases = aliases or {}

    def run(raw_input: str) -> str:
        data = parse_tool_input(raw_input)
        normalized = normalize_args(data, field_aliases)
        missing = [field for field in required if is_blank(normalized.get(field))]
        if missing:
            return to_json({
                "javaMethod": "story.none",
                "javaMethodArgs": {},
                "assistantMessage": "执行这个操作还缺少：" + "、".join(missing) + "。请补充后我会继续。",
                "missingInfo": missing,
            })

        args = {
            field: normalized.get(field)
            for field in required + optional
            if normalized.get(field) is not None
        }
        return to_json({
            "javaMethod": java_method,
            "javaMethodArgs": args,
            "assistantMessage": "我已经准备好执行该操作。",
            "missingInfo": [],
        })

    return Tool.from_function(
        name=name,
        description=description,
        func=run,
    )


def normalize_args(data: dict[str, Any], aliases: dict[str, str]) -> dict[str, Any]:
    normalized = dict(data)
    for source, target in aliases.items():
        if source in normalized and target not in normalized:
            normalized[target] = normalized[source]
    return normalized


def is_blank(value: Any) -> bool:
    return value is None or (isinstance(value, str) and not value.strip())


def system_tools() -> list[Tool]:
    return [
        Tool.from_function(
            name="ask_missing_info",
            description="缺少必要信息时使用。输入 JSON：missingInfo 字符串数组，question 可选。",
            func=ask_missing_info,
        ),
        Tool.from_function(
            name="deny_permission",
            description="用户权限不足时使用。输入 JSON：requiredPermission。",
            func=deny_permission,
        ),
    ]


def manga_tools() -> list[Tool]:
    return system_tools() + [
        make_action_tool(
            "story_list",
            "查询当前用户的漫剧列表。输入 JSON：page 可选，size 可选。",
            "story.list",
            optional_fields=["page", "size"],
        ),
        make_action_tool(
            "story_detail",
            "查询指定漫剧详情。输入 JSON：storyId 必填。",
            "story.detail",
            required_fields=["storyId"],
            aliases={"id": "storyId"},
        ),
        make_action_tool(
            "story_generate_outline",
            "生成新的漫剧剧情大纲并录入数据库。输入 JSON：genre 必填，style 必填，plot 可选。",
            "story.generate",
            required_fields=["genre", "style"],
            optional_fields=["plot"],
            aliases={"theme": "genre", "storyStyle": "style"},
        ),
        make_action_tool(
            "story_update_basic",
            "修改漫剧基础信息。输入 JSON：storyId 必填，title/genre/style/synopsis 可选。",
            "story.updateBasic",
            required_fields=["storyId"],
            optional_fields=["title", "genre", "style", "synopsis"],
        ),
        make_action_tool(
            "story_update_detail",
            "手动保存漫剧摘要、大纲或角色设定。输入 JSON：storyId 必填，synopsis/fullContent/characters 可选。",
            "story.updateDetail",
            required_fields=["storyId"],
            optional_fields=["synopsis", "fullContent", "characters"],
        ),
        make_action_tool(
            "story_revise_outline",
            "根据修改意见自动修改剧情大纲。输入 JSON：storyId 必填，suggestion 必填。",
            "story.reviseOutline",
            required_fields=["storyId", "suggestion"],
        ),
        make_action_tool(
            "story_generate_volume_outline",
            "为指定漫剧生成分卷大纲。输入 JSON：storyId 必填。",
            "story.generateVolumeOutline",
            required_fields=["storyId"],
        ),
        make_action_tool(
            "story_revise_volume_outline",
            "根据意见自动修改分卷大纲。输入 JSON：storyId 必填，suggestion 必填。",
            "story.reviseVolumeOutline",
            required_fields=["storyId", "suggestion"],
        ),
        make_action_tool(
            "story_generate_volume_sections",
            "根据某卷分卷大纲生成小节故事。输入 JSON：storyId 必填，volumeId 必填。",
            "story.generateVolumeSections",
            required_fields=["storyId", "volumeId"],
        ),
        make_action_tool(
            "story_generate_section_assets",
            "根据小节故事生成或复用人物/场景图片资产。输入 JSON：storyId 必填，sectionId 必填。",
            "story.generateSectionAssets",
            required_fields=["storyId", "sectionId"],
        ),
        make_action_tool(
            "story_generate_section_script",
            "根据小节故事生成分镜故事脚本。输入 JSON：storyId 必填，sectionId 必填。",
            "story.generateSectionScript",
            required_fields=["storyId", "sectionId"],
        ),
        make_action_tool(
            "story_delete",
            "删除指定漫剧。输入 JSON：storyId 必填。",
            "story.delete",
            required_fields=["storyId"],
        ),
    ]


def outline_config_tools() -> list[Tool]:
    return system_tools() + [
        make_action_tool(
            "outline_config_list",
            "查询大纲配置。输入 JSON：type 可选 GENRE/STYLE，enabled 可选。",
            "outlineConfig.list",
            optional_fields=["type", "enabled"],
            aliases={"optionType": "type"},
        ),
        make_action_tool(
            "outline_config_create",
            "新增题材或漫剧风格配置。输入 JSON：type 必填 GENRE/STYLE，name 必填，description/sortOrder/enabled 可选。",
            "outlineConfig.create",
            required_fields=["type", "name"],
            optional_fields=["description", "sortOrder", "enabled"],
            aliases={"optionType": "type", "optionName": "name"},
        ),
        make_action_tool(
            "outline_config_update",
            "修改题材或漫剧风格配置。输入 JSON：id 必填，type 必填，name 必填，description/sortOrder/enabled 可选。",
            "outlineConfig.update",
            required_fields=["id", "type", "name"],
            optional_fields=["description", "sortOrder", "enabled"],
            aliases={"optionId": "id", "optionType": "type", "optionName": "name"},
        ),
        make_action_tool(
            "outline_config_delete",
            "删除题材或漫剧风格配置。输入 JSON：id 必填。",
            "outlineConfig.delete",
            required_fields=["id"],
            aliases={"optionId": "id"},
        ),
    ]


def prompt_management_tools() -> list[Tool]:
    return system_tools() + [
        make_action_tool(
            "prompt_list",
            "查询 Prompt 列表。输入 JSON：category/enabled/keyword 可选。",
            "prompt.list",
            optional_fields=["category", "enabled", "keyword"],
        ),
        make_action_tool(
            "prompt_detail",
            "查询 Prompt 详情。输入 JSON：id 必填。",
            "prompt.detail",
            required_fields=["id"],
            aliases={"promptId": "id"},
        ),
        make_action_tool(
            "prompt_create",
            "新增 Prompt。输入 JSON：promptKey、promptName、templateContent 必填；promptScope/basePromptKey/matchGenre/matchStyle/priority/category/description/enabled/parameters 可选。",
            "prompt.create",
            required_fields=["promptKey", "promptName", "templateContent"],
            optional_fields=[
                "promptScope", "basePromptKey", "matchGenre", "matchStyle", "priority",
                "category", "description", "enabled", "parameters",
            ],
        ),
        make_action_tool(
            "prompt_update",
            "修改 Prompt。输入 JSON：id、promptKey、promptName、templateContent 必填；其他字段同新增。",
            "prompt.update",
            required_fields=["id", "promptKey", "promptName", "templateContent"],
            optional_fields=[
                "promptScope", "basePromptKey", "matchGenre", "matchStyle", "priority",
                "category", "description", "enabled", "parameters",
            ],
            aliases={"promptId": "id"},
        ),
        make_action_tool(
            "prompt_delete",
            "删除 Prompt。输入 JSON：id 必填。",
            "prompt.delete",
            required_fields=["id"],
            aliases={"promptId": "id"},
        ),
    ]


def user_permission_tools() -> list[Tool]:
    return system_tools() + [
        make_action_tool(
            "user_list",
            "查询用户及权限列表。输入 JSON 可以为空。",
            "user.list",
        ),
        make_action_tool(
            "user_update_role",
            "修改用户权限等级。输入 JSON：targetUserId 必填，role 必填，role 只能是 ROOT/ADMIN/USER。",
            "user.updateRole",
            required_fields=["targetUserId", "role"],
            aliases={"userId": "targetUserId", "id": "targetUserId"},
        ),
    ]


MODULE_TOOL_FACTORY: dict[str, Callable[[], list[Tool]]] = {
    "manga": manga_tools,
    "outline_config": outline_config_tools,
    "prompt_management": prompt_management_tools,
    "user_permission": user_permission_tools,
}


def get_tools_for_module(module: str) -> list[Tool]:
    factory = MODULE_TOOL_FACTORY.get((module or "").strip().lower())
    if factory is None:
        return system_tools()
    return factory()
