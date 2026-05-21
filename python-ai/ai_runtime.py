import os
import time
from pathlib import Path

import yaml
from langchain_community.chat_models import ChatTongyi
from langchain_core.callbacks import BaseCallbackHandler


def load_tongyi_api_key() -> str:
    """作用：从 python-ai/api.yml 读取通义千问 API Key。
    调用方：模块加载阶段初始化 tongyi_api_key，并写入 DASHSCOPE_API_KEY 环境变量。
    """
    api_config_path = Path(__file__).with_name("api.yml")
    if not api_config_path.exists():
        return ""

    config = yaml.safe_load(api_config_path.read_text(encoding="utf-8")) or {}
    return str(config.get("tongyi", {}).get("api_key", "")).strip()


tongyi_api_key = load_tongyi_api_key()
if tongyi_api_key:
    os.environ["DASHSCOPE_API_KEY"] = tongyi_api_key


llm_temperature_0 = ChatTongyi(
    model="qwen-max",
    temperature=0,
    top_p=0.2,
    streaming=False,
)

structured_llm_base = ChatTongyi(
    model="qwen-max",
    temperature=0.8,
    streaming=False,
)

streaming_text_llm_base = ChatTongyi(
    model="qwen-max",
    temperature=0.8,
    streaming=True,
)


class ConsoleStreamingCallback(BaseCallbackHandler):
    """LangChain 控制台回调处理器。
    记录每次大模型调用的开始、结束、异常和可用 token 流，便于调试生成进度。
    """

    def __init__(self, trace_id: str, scope: str = "story-outline"):
        """作用：初始化一次大模型调用的控制台日志上下文。
        调用方：story_ai、chat_ai 中各个 chain.invoke 的 callbacks。
        """
        self.trace_id = trace_id
        self.scope = scope
        self.started_at = time.perf_counter()
        self.has_streamed_tokens = False

    def _elapsed(self) -> str:
        """作用：计算当前请求从开始到现在的耗时文本。
        调用方：本类的各个 LangChain callback 方法。
        """
        return f"{time.perf_counter() - self.started_at:.1f}s"

    def on_chat_model_start(self, serialized, messages, **kwargs) -> None:
        """作用：在 LangChain ChatModel 请求开始时打印进度。
        调用方：LangChain 回调框架自动调用。
        """
        print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] chat model request started", flush=True)

    def on_llm_start(self, serialized, prompts, **kwargs) -> None:
        """作用：在 LangChain LLM 请求开始时打印进度。
        调用方：LangChain 回调框架自动调用。
        """
        print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] llm request started", flush=True)

    def on_llm_new_token(self, token: str, **kwargs) -> None:
        """作用：流式打印模型生成 token。
        调用方：LangChain 在模型返回 token 时自动调用；结构化非流式调用通常不会触发。
        """
        if not token:
            return
        if not self.has_streamed_tokens:
            self.has_streamed_tokens = True
            print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] streaming tokens:", flush=True)
        print(token, end="", flush=True)

    def on_llm_end(self, response, **kwargs) -> None:
        """作用：在大模型返回结束时补换行并打印完成日志。
        调用方：LangChain 回调框架自动调用。
        """
        if self.has_streamed_tokens:
            print("", flush=True)
        print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] llm response finished", flush=True)

    def on_llm_error(self, error, **kwargs) -> None:
        """作用：在大模型调用异常时打印错误日志。
        调用方：LangChain 回调框架自动调用。
        """
        if self.has_streamed_tokens:
            print("", flush=True)
        print(f"[{self.scope}][{self.trace_id}][{self._elapsed()}] llm error: {error}", flush=True)


def log_progress(trace_id: str, message: str, started_at: float, scope: str = "story-outline") -> None:
    """作用：按 trace_id 和业务范围打印阶段性进度日志。
    调用方：story_ai 与 chat_ai 中的 FastAPI endpoint。
    """
    elapsed = time.perf_counter() - started_at
    print(f"[{scope}][{trace_id}][{elapsed:.1f}s] {message}", flush=True)
