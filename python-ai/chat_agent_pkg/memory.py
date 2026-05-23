import json

from ai_runtime import Router, invoke_llm_with_retry
from story_ai_pkg.prompt_repository import get_prompt_template

from .models import ChatAgentRequest, MemoryUpdateOutput, RouteOutput
from .prompts import prompt_ChatMemorySummary
from .routing import format_messages


def update_memory(
        request: ChatAgentRequest,
        route: RouteOutput,
        resolved_message: str,
        trace_id: str,
        started_at: float,
) -> MemoryUpdateOutput:
    if request.summary_candidate_messages:
        chain = get_prompt_template(
            "chat_memory_summary",
            prompt_ChatMemorySummary,
            genre=request.genre,
            story_style=request.story_style,
        ) | Router.with_structured_output(MemoryUpdateOutput)
        return invoke_llm_with_retry(
            chain,
            {
                "ExistingLongTermMemory": request.long_term_memory or "无",
                "ExistingKeyFacts": json.dumps(request.key_facts or {}, ensure_ascii=False),
                "SummaryCandidateMessages": format_messages(request.summary_candidate_messages),
                "CurrentMessage": resolved_message,
                "ImportantInfo": json.dumps(route.important_info or {}, ensure_ascii=False),
            },
            trace_id=trace_id,
            started_at=started_at,
            scope="chat-memory",
        )

    return MemoryUpdateOutput(
        longTermMemory=request.long_term_memory or "",
        keyFacts=merge_key_facts(request.key_facts, route.important_info),
    )


def merge_key_facts(existing_facts: dict | None, important_info: dict | None) -> dict:
    facts = dict(existing_facts or {})
    for key, value in (important_info or {}).items():
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        facts[key] = value
    return facts
