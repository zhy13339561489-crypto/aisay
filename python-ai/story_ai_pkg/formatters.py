import re

from fastapi import HTTPException

from .models import MainCharacterSetting, VolumeOutlineItem, VolumeSectionItem


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
