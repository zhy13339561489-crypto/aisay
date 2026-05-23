from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ChatMemoryMessage(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    role: str
    content: str
    created_at: str | None = Field(default=None, alias="createdAt")


class ChatAgentRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    user_id: int = Field(alias="userId")
    session_id: int = Field(alias="sessionId")
    story_id: int | None = Field(default=None, alias="storyId")
    title: str | None = None
    genre: str | None = None
    story_style: str | None = Field(default=None, alias="storyStyle")
    synopsis: str | None = None
    outline: str | None = None
    user_message: str = Field(alias="userMessage")
    user_role: str = Field(default="USER", alias="userRole")
    recent_messages: list[ChatMemoryMessage] = Field(default_factory=list, alias="recentMessages")
    long_term_memory: str | None = Field(default=None, alias="longTermMemory")
    key_facts: dict[str, Any] = Field(default_factory=dict, alias="keyFacts")
    summary_candidate_messages: list[ChatMemoryMessage] = Field(
        default_factory=list,
        alias="summaryCandidateMessages",
    )
    summary_candidate_count: int = Field(default=0, alias="summaryCandidateCount")


class CoreferenceOutput(BaseModel):
    resolved_message: str = Field(alias="resolvedMessage")
    resolution_notes: str = Field(default="", alias="resolutionNotes")


class RouteOutput(BaseModel):
    module: str = Field(description="manga, outline_config, prompt_management, user_permission, or general")
    intent: str
    route: str
    required_permission: str = Field(default="USER", alias="requiredPermission")
    important_info: dict[str, Any] = Field(default_factory=dict, alias="importantInfo")
    missing_info: list[str] = Field(default_factory=list, alias="missingInfo")
    assistant_message: str = Field(default="", alias="assistantMessage")


class MemoryUpdateOutput(BaseModel):
    long_term_memory: str = Field(default="", alias="longTermMemory")
    key_facts: dict[str, Any] = Field(default_factory=dict, alias="keyFacts")


class SubAgentAction(BaseModel):
    java_method: str = Field(default="story.none", alias="javaMethod")
    java_method_args: dict[str, Any] = Field(default_factory=dict, alias="javaMethodArgs")
    assistant_message: str = Field(default="", alias="assistantMessage")
    missing_info: list[str] = Field(default_factory=list, alias="missingInfo")


class ChatAgentResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    rewritten_question: str = Field(alias="rewrittenQuestion")
    resolved_question: str = Field(alias="resolvedQuestion")
    route: str
    module: str
    intent: str
    required_permission: str = Field(alias="requiredPermission")
    important_info: dict[str, Any] = Field(default_factory=dict, alias="importantInfo")
    missing_info: list[str] = Field(default_factory=list, alias="missingInfo")
    java_method: str = Field(alias="javaMethod")
    java_method_args: dict[str, Any] = Field(default_factory=dict, alias="javaMethodArgs")
    assistant_message: str = Field(alias="assistantMessage")
    long_term_memory: str | None = Field(default=None, alias="longTermMemory")
    key_facts: dict[str, Any] = Field(default_factory=dict, alias="keyFacts")
    summarized_message_count: int | None = Field(default=None, alias="summarizedMessageCount")
