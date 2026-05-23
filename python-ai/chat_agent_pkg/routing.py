import json

from ai_runtime import Router, invoke_llm_with_retry
from prompt import prompt_ChatAgent
from story_ai_pkg.prompt_repository import get_prompt_template

from .models import ChatAgentRequest, CoreferenceOutput, RouteOutput
from .prompts import prompt_ChatCoreference, prompt_ChatRouter


def format_messages(messages) -> str:
    if not messages:
        return "无"
    rows = []
    for message in messages:
        rows.append(f"{message.role}: {message.content}")
    return "\n".join(rows)


def resolve_coreference(request: ChatAgentRequest, trace_id: str, started_at: float) -> CoreferenceOutput:
    chain = get_prompt_template(
        "chat_coreference",
        prompt_ChatCoreference,
        genre=request.genre,
        story_style=request.story_style,
    ) | Router.with_structured_output(CoreferenceOutput)
    return invoke_llm_with_retry(
        chain,
        {
            "RecentMessages": format_messages(request.recent_messages),
            "LongTermMemory": request.long_term_memory or "无",
            "KeyFacts": json.dumps(request.key_facts or {}, ensure_ascii=False),
            "UserMessage": request.user_message,
        },
        trace_id=trace_id,
        started_at=started_at,
        scope="chat-coreference",
    )


def route_message(
        request: ChatAgentRequest,
        resolved_message: str,
        trace_id: str,
        started_at: float,
) -> RouteOutput:
    chain = get_prompt_template(
        "chat_router",
        prompt_ChatRouter,
        genre=request.genre,
        story_style=request.story_style,
    ) | Router.with_structured_output(RouteOutput)
    return invoke_llm_with_retry(
        chain,
        {
            "UserRole": request.user_role,
            "LongTermMemory": request.long_term_memory or "无",
            "KeyFacts": json.dumps(request.key_facts or {}, ensure_ascii=False),
            "ResolvedMessage": resolved_message,
            "AvailableModules": "manga, outline_config, prompt_management, user_permission, general",
        },
        trace_id=trace_id,
        started_at=started_at,
        scope="chat-router",
    )


def normalize_important_info(route: RouteOutput) -> str:
    return json.dumps(route.important_info or {}, ensure_ascii=False)


def fallback_general_response(request: ChatAgentRequest, resolved_message: str) -> RouteOutput:
    return RouteOutput(
        module="general",
        intent="general_chat",
        route="no_action",
        requiredPermission="USER",
        importantInfo={},
        missingInfo=[],
        assistantMessage=(
            "我可以帮你梳理创意、剧情、人物、Prompt 或系统操作。"
            f"这次我理解你的问题是：{resolved_message}"
        ),
    )
