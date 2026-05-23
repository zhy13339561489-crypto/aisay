import time
import uuid

from fastapi import APIRouter

from ai_runtime import log_progress
from chat_agent_pkg.memory import update_memory
from chat_agent_pkg.models import ChatAgentRequest, ChatAgentResponse
from chat_agent_pkg.permissions import module_required_role
from chat_agent_pkg.routing import fallback_general_response, resolve_coreference, route_message
from chat_agent_pkg.sub_agents import run_sub_agent


router = APIRouter()


@router.post("/api/chat/agent", response_model=ChatAgentResponse)
def run_chat_agent(request: ChatAgentRequest) -> ChatAgentResponse:
    """Run the intelligent chat pipeline.

    Flow:
    1. Resolve references from short-term memory, long-term memory and key facts.
    2. Route the resolved user input and extract intent plus important facts.
    3. Update three-part memory: recent messages stay in Redis, older messages are summarized, key facts are merged.
    4. Dispatch to a module-specific ReAct AgentExecutor.
    5. Return the Java method and arguments that the Java backend may execute.
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    scope = "chat-agent"
    log_progress(
        trace_id,
        f"request accepted, user_id={request.user_id}, session_id={request.session_id}, role={request.user_role}",
        started_at,
        scope,
    )

    try:
        coreference = resolve_coreference(request, trace_id, started_at)
        resolved_message = coreference.resolved_message
        log_progress(trace_id, f"resolved message: {resolved_message[:120]}", started_at, scope)

        route = route_message(request, resolved_message, trace_id, started_at)
        if not route.module:
            route = fallback_general_response(request, resolved_message)
        if not route.required_permission:
            route.required_permission = module_required_role(route.module)
        log_progress(
            trace_id,
            f"route module={route.module}, intent={route.intent}, required={route.required_permission}",
            started_at,
            scope,
        )

        memory = update_memory(request, route, resolved_message, trace_id, started_at)
        action = run_sub_agent(request, route, resolved_message, trace_id, started_at)
        summarized_count = request.summary_candidate_count if request.summary_candidate_messages else None

        log_progress(
            trace_id,
            f"sub-agent selected method={action.java_method}, missing={action.missing_info}",
            started_at,
            scope,
        )
        return ChatAgentResponse(
            rewrittenQuestion=resolved_message,
            resolvedQuestion=resolved_message,
            route=route.route,
            module=route.module,
            intent=route.intent,
            requiredPermission=route.required_permission,
            importantInfo=route.important_info,
            missingInfo=action.missing_info or route.missing_info,
            javaMethod=action.java_method,
            javaMethodArgs=action.java_method_args,
            assistantMessage=action.assistant_message or route.assistant_message or "我已经理解你的需求。",
            longTermMemory=memory.long_term_memory,
            keyFacts=memory.key_facts,
            summarizedMessageCount=summarized_count,
        )
    except Exception as exc:
        log_progress(trace_id, f"chat agent failed: {exc}", started_at, scope)
        return ChatAgentResponse(
            rewrittenQuestion=request.user_message,
            resolvedQuestion=request.user_message,
            route="fallback",
            module="general",
            intent="fallback_response",
            requiredPermission="USER",
            importantInfo={},
            missingInfo=[],
            javaMethod="story.none",
            javaMethodArgs={},
            assistantMessage="智能对话模块暂时没有处理成功，我先保留你的输入，请稍后再试或换一种更明确的说法。",
            longTermMemory=request.long_term_memory or "",
            keyFacts=request.key_facts or {},
            summarizedMessageCount=None,
        )
