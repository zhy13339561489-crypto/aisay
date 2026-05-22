from langchain_core.prompts import PromptTemplate

from prompt import (
    prompt_Outline,
    prompt_ReviseOutline,
    prompt_SectionAssetExtraction,
    prompt_SectionScriptGenerate,
    prompt_VolumeCount,
    prompt_VolumeOutlineSingle,
    prompt_VolumeOutline_Editor,
    prompt_VolumeSectionCount,
    prompt_VolumeSectionSingle,
    prompt_VolumeStory,
)

promptTemplate_Outline = PromptTemplate.from_template(prompt_Outline)
promptTemplate_ReviseOutline = PromptTemplate.from_template(prompt_ReviseOutline)
promptTemplate_VolumeCount = PromptTemplate.from_template(prompt_VolumeCount)
promptTemplate_VolumeOutlineSingle = PromptTemplate.from_template(prompt_VolumeOutlineSingle)
promptTemplate_VolumeOutlineEditor = PromptTemplate.from_template(prompt_VolumeOutline_Editor)
promptTemplate_VolumeSectionCount = PromptTemplate.from_template(prompt_VolumeSectionCount)
promptTemplate_VolumeSectionSingle = PromptTemplate.from_template(prompt_VolumeSectionSingle)
promptTemplate_VolumeStory = PromptTemplate.from_template(prompt_VolumeStory)
promptTemplate_SectionAssetExtraction = PromptTemplate.from_template(prompt_SectionAssetExtraction)
promptTemplate_SectionScriptGenerate = PromptTemplate.from_template(prompt_SectionScriptGenerate)
