# 提示词模板初始化模块
# 本文件集中初始化所有 LangChain PromptTemplate 实例。
# PromptTemplate 将 prompt.py 中的原始字符串转为可调用的模板对象，
# 在运行时通过 .invoke() 方法替换 {变量名} 占位符。

# LangChain 提示词模板类
from langchain_core.prompts import PromptTemplate

# 从 prompt 模块导入所有原始提示词字符串
from prompt import (
    prompt_Outline,                  # 剧情大纲生成提示词
    prompt_ReviseOutline,            # 剧情大纲修改提示词
    prompt_SectionAssetExtraction,   # 小节资产识别提示词
    prompt_SectionScriptGenerate,    # 分镜脚本生成提示词
    prompt_VolumeCount,              # 分卷数量规划提示词
    prompt_VolumeOutlineSingle,      # 单卷大纲生成提示词
    prompt_VolumeOutline_Editor,     # 分卷大纲修改提示词
    prompt_VolumeSectionCount,       # 小节数量规划提示词
    prompt_VolumeSectionSingle,      # 单节故事生成提示词
    prompt_VolumeStory,              # 分卷正文生成提示词
)

# ── 剧情大纲相关模板 ──────────────────────────────────────────────────

# 剧情大纲生成模板：输入 {Theme} 题材、{StoryStyle} 风格、{Plot} 剧情
promptTemplate_Outline = PromptTemplate.from_template(prompt_Outline)

# 剧情大纲修改模板：输入 {OriginalOutline} 原始大纲、{RevisionNotes} 修改意见
promptTemplate_ReviseOutline = PromptTemplate.from_template(prompt_ReviseOutline)

# ── 分卷大纲相关模板 ──────────────────────────────────────────────────

# 分卷数量规划模板：输入 {Title}、{StorySummary}、{Outline}、{Characters}
promptTemplate_VolumeCount = PromptTemplate.from_template(prompt_VolumeCount)

# 单卷大纲生成模板：输入 {Title}、{StorySummary}、{Outline}、{Characters}、{TotalVolumes}、{CurrentVolumeNumber}、{GeneratedVolumeOutlines}
promptTemplate_VolumeOutlineSingle = PromptTemplate.from_template(prompt_VolumeOutlineSingle)

# 分卷大纲修改模板：输入 {Title}、{StorySummary}、{Outline}、{Characters}、{ExistingVolumeOutline}、{ModificationRequest}
promptTemplate_VolumeOutlineEditor = PromptTemplate.from_template(prompt_VolumeOutline_Editor)

# ── 小节相关模板 ──────────────────────────────────────────────────────

# 小节数量规划模板：输入 {Title}、{StorySummary}、{Outline}、{Characters}、{VolumeNumber}、{VolumeTitle}、{VolumeSummary}、{VolumeContent}、{EndingHook}
promptTemplate_VolumeSectionCount = PromptTemplate.from_template(prompt_VolumeSectionCount)

# 单节故事生成模板：输入 {Title}、{StoryStyle}、{StorySummary}、{Outline}、{Characters}、{VolumeNumber}、{VolumeTitle}、{VolumeSummary}、{VolumeContent}、{EndingHook}、{TotalSections}、{CurrentSectionNumber}、{GeneratedSections}
promptTemplate_VolumeSectionSingle = PromptTemplate.from_template(prompt_VolumeSectionSingle)

# ── 分卷正文模板 ──────────────────────────────────────────────────────

# 分卷正文生成模板：输入 {Title}、{StoryStyle}、{StorySummary}、{Outline}、{Characters}、{VolumeNumber}、{VolumeTitle}、{VolumeSummary}、{VolumeContent}、{EndingHook}
promptTemplate_VolumeStory = PromptTemplate.from_template(prompt_VolumeStory)

# ── 资产和脚本模板 ────────────────────────────────────────────────────

# 小节资产识别模板：输入 {Title}、{StoryStyle}、{StorySummary}、{Outline}、{Characters}、{VolumeNumber}、{VolumeTitle}、{SectionNumber}、{SectionTitle}、{SectionSummary}、{SectionContent}、{ExistingAssets}
promptTemplate_SectionAssetExtraction = PromptTemplate.from_template(prompt_SectionAssetExtraction)

# 分镜脚本生成模板：输入 {Title}、{StoryStyle}、{StorySummary}、{Outline}、{Characters}、{VolumeNumber}、{VolumeTitle}、{VolumeSummary}、{VolumeContent}、{EndingHook}、{SectionNumber}、{SectionTitle}、{SectionSummary}、{SectionContent}、{SectionEndingHook}
promptTemplate_SectionScriptGenerate = PromptTemplate.from_template(prompt_SectionScriptGenerate)
