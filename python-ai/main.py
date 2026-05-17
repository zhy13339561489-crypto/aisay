import os
import time
import uuid
from pathlib import Path

import uvicorn
import yaml
from fastapi import FastAPI
from langchain_community.chat_models import ChatTongyi
from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.prompts import PromptTemplate
from pydantic import BaseModel, Field

from prompt import prompt_Outline


def load_tongyi_api_key() -> str:
    api_config_path = Path(__file__).with_name("api.yml")
    if not api_config_path.exists():
        return ""

    config = yaml.safe_load(api_config_path.read_text(encoding="utf-8")) or {}
    return str(config.get("tongyi", {}).get("api_key", "")).strip()


tongyi_api_key = load_tongyi_api_key()
if tongyi_api_key:
    os.environ["DASHSCOPE_API_KEY"] = tongyi_api_key


promptTemplate_Outline = PromptTemplate.from_template(prompt_Outline)

llm = ChatTongyi(
    model="qwen-max",
    temperature=0.5,
    top_p=0.8,
    streaming=True,
)

app = FastAPI(title="Aisay Python AI Engine")


class ConsoleStreamingCallback(BaseCallbackHandler):
    def __init__(self, trace_id: str):
        self.trace_id = trace_id
        self.started_at = time.perf_counter()
        self.has_streamed_tokens = False

    def _elapsed(self) -> str:
        return f"{time.perf_counter() - self.started_at:.1f}s"

    def on_chat_model_start(self, serialized, messages, **kwargs) -> None:
        print(f"[story-outline][{self.trace_id}][{self._elapsed()}] chat model request started", flush=True)

    def on_llm_start(self, serialized, prompts, **kwargs) -> None:
        print(f"[story-outline][{self.trace_id}][{self._elapsed()}] llm request started", flush=True)

    def on_llm_new_token(self, token: str, **kwargs) -> None:
        if not token:
            return
        if not self.has_streamed_tokens:
            self.has_streamed_tokens = True
            print(f"[story-outline][{self.trace_id}][{self._elapsed()}] streaming tokens:", flush=True)
        print(token, end="", flush=True)

    def on_llm_end(self, response, **kwargs) -> None:
        if self.has_streamed_tokens:
            print("", flush=True)
        print(f"[story-outline][{self.trace_id}][{self._elapsed()}] llm response finished", flush=True)

    def on_llm_error(self, error, **kwargs) -> None:
        if self.has_streamed_tokens:
            print("", flush=True)
        print(f"[story-outline][{self.trace_id}][{self._elapsed()}] llm error: {error}", flush=True)


def log_progress(trace_id: str, message: str, started_at: float) -> None:
    elapsed = time.perf_counter() - started_at
    print(f"[story-outline][{trace_id}][{elapsed:.1f}s] {message}", flush=True)


class StoryOutlineGenerateRequest(BaseModel):
    user_id: int = Field(alias="userId")
    session_id: int = Field(alias="sessionId")
    session_title: str | None = Field(default=None, alias="sessionTitle")
    genre: str
    plot: str | None = None


class MainCharacterSetting(BaseModel):
    name: str = Field(description="Character name or codename")
    role: str = Field(description="Character role, such as protagonist, partner, antagonist, mentor, or hidden manipulator")
    description: str = Field(description="Character background, ability, and narrative function")
    personality: str = Field(description="Personality, core desire, weakness, or character arc")
    appearance: dict[str, str] | None = Field(
        default=None,
        description="Optional appearance traits, such as hairstyle, clothing, or signature item",
    )


class StoryOutlineGenerateResponse(BaseModel):
    novel_name: str = Field(alias="novelName")
    story_summary: str = Field(alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class StoryOutlineReviseRequest(BaseModel):
    user_id: int = Field(alias="userId")
    story_id: int = Field(alias="storyId")
    title: str
    synopsis: str | None = None
    outline: str
    suggestion: str


class StoryOutlineReviseResponse(BaseModel):
    story_summary: str = Field(alias="storySummary")
    outline: str
    main_characters: list[MainCharacterSetting] = Field(default_factory=list, alias="mainCharacters")


class NovelOutlineOutput(BaseModel):
    novel_name: str = Field(description="Novel title, concise and recognizable")
    story_summary: str = Field(description="Story summary, 200-300 Chinese characters")
    outline: str = Field(description="Full story outline with setting, main plot, stages, and key characters")
    main_characters: list[MainCharacterSetting] = Field(description="Main character settings, usually 3-5 key characters")


@app.post("/api/story/outline", response_model=StoryOutlineGenerateResponse)
def generate_story_outline(request: StoryOutlineGenerateRequest) -> StoryOutlineGenerateResponse:
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    log_progress(
        trace_id,
        f"request accepted, user_id={request.user_id}, session_id={request.session_id}, genre={request.genre}",
        started_at,
    )

    structured_llm = llm.with_structured_output(NovelOutlineOutput)
    chain = promptTemplate_Outline | structured_llm
    log_progress(trace_id, "structured chain created, invoking Tongyi model without a generation time limit", started_at)

    result = chain.invoke(
        {
            "Theme": request.genre,
            "Plot": request.plot or "User did not provide a rough plot. Please create a story from the theme.",
        },
        config={"callbacks": [ConsoleStreamingCallback(trace_id)]},
    )

    log_progress(trace_id, "model returned structured output, preparing HTTP response", started_at)
    response = StoryOutlineGenerateResponse(
        novelName=result.novel_name,
        storySummary=result.story_summary,
        outline=result.outline,
        mainCharacters=result.main_characters,
    )
    log_progress(trace_id, "response ready", started_at)
    return response


@app.post("/api/story/outline/revise", response_model=StoryOutlineReviseResponse)
def revise_story_outline(request: StoryOutlineReviseRequest) -> StoryOutlineReviseResponse:
    """Scaffold for outline revision.

    TODO: replace this placeholder with a LangChain revision chain.
    """
    trace_id = uuid.uuid4().hex[:8]
    started_at = time.perf_counter()
    log_progress(trace_id, f"revision request accepted, user_id={request.user_id}, story_id={request.story_id}", started_at)
    log_progress(trace_id, "revision LangChain implementation is pending; returning scaffold response", started_at)

    revised_outline = (
        f"{request.outline}\n\n"
        "[Pending outline revision instruction]\n"
        f"{request.suggestion}\n\n"
        "The frontend -> Java -> Python revision call chain is connected. "
        "Real LangChain revision logic will be implemented later."
    )
    response = StoryOutlineReviseResponse(
        storySummary=request.synopsis or "Outline revision request received. Waiting for LangChain revision implementation.",
        outline=revised_outline,
        mainCharacters=[],
    )
    log_progress(trace_id, "revision scaffold response ready", started_at)
    return response


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)
