import json
import re

from langchain.agents import AgentExecutor, create_react_agent
from langchain_core.prompts import PromptTemplate

from ai_runtime import Router, invoke_llm_with_retry
from story_ai_pkg.prompt_repository import get_prompt_template

from .models import ChatAgentRequest, RouteOutput, SubAgentAction
from .permissions import has_permission, module_required_role
from .prompts import prompt_SubAgentReact
from .tools import get_tools_for_module, to_json


def run_sub_agent(
        request: ChatAgentRequest,
        route: RouteOutput,
        resolved_message: str,
        trace_id: str,
        started_at: float,
) -> SubAgentAction:
    required_role = route.required_permission or module_required_role(route.module)
    if not has_permission(request.user_role, required_role):
        return SubAgentAction(
            javaMethod="story.none",
            javaMethodArgs={},
            assistantMessage=f"当前账号是 {request.user_role}，这个操作需要 {required_role} 权限。",
            missingInfo=[],
        )

    if route.module == "general":
        return SubAgentAction(
            javaMethod="story.none",
            javaMethodArgs={},
            assistantMessage=route.assistant_message or "我理解了，我们可以继续聊这个创作方向。",
            missingInfo=[],
        )

    tools = get_tools_for_module(route.module)
    prompt = get_prompt_template(
        "chat_sub_agent_react",
        prompt_SubAgentReact,
        genre=request.genre,
        story_style=request.story_style,
    )
    if not isinstance(prompt, PromptTemplate):
        prompt = PromptTemplate.from_template(prompt_SubAgentReact)

    agent = create_react_agent(Router, tools, prompt)
    executor = AgentExecutor(
        agent=agent,
        tools=tools,
        verbose=True,
        handle_parsing_errors=True,
        max_iterations=4,
        return_intermediate_steps=False,
    )
    result = invoke_llm_with_retry(
        executor,
        {
            "input": resolved_message,
            "ModuleName": route.module,
            "UserRole": request.user_role,
            "ResolvedMessage": resolved_message,
            "Intent": route.intent,
            "ImportantInfo": to_json(route.important_info or {}),
            "MissingInfo": to_json({"missingInfo": route.missing_info or []}),
            "LongTermMemory": request.long_term_memory or "无",
            "KeyFacts": json.dumps(request.key_facts or {}, ensure_ascii=False),
        },
        trace_id=trace_id,
        started_at=started_at,
        scope=f"chat-sub-agent:{route.module}",
    )
    output = result.get("output", "") if isinstance(result, dict) else str(result)
    return parse_sub_agent_output(output, route)


def parse_sub_agent_output(output: str, route: RouteOutput) -> SubAgentAction:
    data = extract_json_object(output)
    if not data:
        return SubAgentAction(
            javaMethod="story.none",
            javaMethodArgs={},
            assistantMessage=route.assistant_message or "我理解了你的需求，但还没有形成可执行操作。请补充更明确的信息。",
            missingInfo=route.missing_info or [],
        )
    return SubAgentAction(
        javaMethod=data.get("javaMethod") or data.get("java_method") or "story.none",
        javaMethodArgs=data.get("javaMethodArgs") or data.get("java_method_args") or {},
        assistantMessage=data.get("assistantMessage") or route.assistant_message or "已完成处理。",
        missingInfo=data.get("missingInfo") or [],
    )


def extract_json_object(text: str) -> dict | None:
    if not text:
        return None
    stripped = text.strip()
    try:
        parsed = json.loads(stripped)
        return parsed if isinstance(parsed, dict) else None
    except json.JSONDecodeError:
        pass

    match = re.search(r"\{.*\}", stripped, re.DOTALL)
    if not match:
        return None
    try:
        parsed = json.loads(match.group(0))
        return parsed if isinstance(parsed, dict) else None
    except json.JSONDecodeError:
        return None
