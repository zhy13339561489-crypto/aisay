import json
import os
import threading
import time
from pathlib import Path
from typing import Any

import yaml

from story_ai import (
    StoryOutlineGenerateRequest,
    StoryOutlineReviseRequest,
    StoryVolumeOutlineGenerateRequest,
    StoryVolumeOutlineReviseRequest,
    StoryVolumeSectionGenerateRequest,
    StoryVolumeStoryGenerateRequest,
    generate_story_outline,
    generate_volume_outline,
    generate_volume_sections,
    generate_volume_story,
    revise_story_outline,
    revise_volume_outline,
)


EXCHANGE = "aisay.ai.exchange"
REQUEST_QUEUE = "aisay.ai.story.request"
RESULT_QUEUE = "aisay.ai.story.result"
REQUEST_ROUTING_KEY = "ai.story.request"
RESULT_ROUTING_KEY = "ai.story.result"

TASK_STORY_GENERATE = "STORY_GENERATE"
TASK_STORY_REVISE = "STORY_REVISE"
TASK_VOLUME_GENERATE = "VOLUME_GENERATE"
TASK_VOLUME_REVISE = "VOLUME_REVISE"
TASK_VOLUME_STORY_GENERATE = "VOLUME_STORY_GENERATE"
TASK_VOLUME_SECTION_GENERATE = "VOLUME_SECTION_GENERATE"

_worker_thread: threading.Thread | None = None


def start_rabbitmq_worker() -> None:
    """作用：启动非对话类 AI 任务的 RabbitMQ 后台消费线程。
    调用方：main.py 的 FastAPI startup 生命周期。
    """
    global _worker_thread
    if _worker_thread and _worker_thread.is_alive():
        return

    _worker_thread = threading.Thread(target=_worker_loop, name="aisay-story-ai-worker", daemon=True)
    _worker_thread.start()
    print("[rabbitmq-worker] background worker started")


def _worker_loop() -> None:
    """作用：保持 RabbitMQ 消费连接，异常断开后自动重连。
    调用方：start_rabbitmq_worker 创建的后台线程。
    """
    while True:
        try:
            _consume_forever()
        except Exception as exc:
            print(f"[rabbitmq-worker] connection failed: {exc}; retrying in 5s")
            time.sleep(5)


def _consume_forever() -> None:
    """作用：声明队列拓扑并持续消费 Java 投递的故事 AI 任务。
    调用方：_worker_loop。
    """
    import pika

    config = _load_rabbitmq_config()
    credentials = pika.PlainCredentials(config["username"], config["password"])
    parameters = pika.ConnectionParameters(
        host=config["host"],
        port=config["port"],
        credentials=credentials,
        heartbeat=600,
        blocked_connection_timeout=300,
    )
    connection = pika.BlockingConnection(parameters)
    channel = connection.channel()
    channel.exchange_declare(exchange=EXCHANGE, exchange_type="direct", durable=True)
    channel.queue_declare(queue=REQUEST_QUEUE, durable=True)
    channel.queue_declare(queue=RESULT_QUEUE, durable=True)
    channel.queue_bind(queue=REQUEST_QUEUE, exchange=EXCHANGE, routing_key=REQUEST_ROUTING_KEY)
    channel.queue_bind(queue=RESULT_QUEUE, exchange=EXCHANGE, routing_key=RESULT_ROUTING_KEY)
    channel.basic_qos(prefetch_count=1)

    def on_message(ch: Any, method: Any, properties: Any, body: bytes) -> None:
        task = json.loads(body.decode("utf-8"))
        print(f"[rabbitmq-worker] received task {task.get('taskId')} type={task.get('taskType')}")

        def publish_result(result: dict[str, Any]) -> None:
            ch.basic_publish(
                exchange=EXCHANGE,
                routing_key=RESULT_ROUTING_KEY,
                body=json.dumps(result, ensure_ascii=False).encode("utf-8"),
                properties=pika.BasicProperties(content_type="application/json", delivery_mode=2),
            )
            print(
                "[rabbitmq-worker] published result "
                f"{result.get('taskId')} success={result.get('success')} "
                f"partial={result.get('partial')} completed={result.get('completed')}"
            )

        result = _handle_story_task(task, publish_result)
        publish_result(result)
        ch.basic_ack(delivery_tag=method.delivery_tag)

    channel.basic_consume(queue=REQUEST_QUEUE, on_message_callback=on_message)
    print(f"[rabbitmq-worker] consuming queue={REQUEST_QUEUE} host={config['host']}:{config['port']}")
    channel.start_consuming()


def _handle_story_task(
    task: dict[str, Any],
    publish_progress: Any | None = None,
) -> dict[str, Any]:
    """作用：按任务类型调用 story_ai 中已有的大模型函数，并封装 Java 可消费的结果消息。
    调用方：_consume_forever 的 RabbitMQ 消息回调。
    """
    result: dict[str, Any] = {
        "taskId": task.get("taskId"),
        "taskType": task.get("taskType"),
        "userId": task.get("userId"),
        "storyId": task.get("storyId"),
        "volumeId": task.get("volumeId"),
        "volumeNumber": None,
        "success": False,
        "errorMessage": None,
        "storyOutline": None,
        "volumeOutline": None,
        "volumeSection": None,
        "volumeStory": None,
        "partial": False,
        "completed": False,
    }

    try:
        task_type = task.get("taskType")
        if task_type == TASK_STORY_GENERATE:
            response = generate_story_outline(
                StoryOutlineGenerateRequest(
                    userId=task.get("userId"),
                    genre=task.get("genre"),
                    plot=task.get("plot"),
                )
            )
            result["storyOutline"] = response.model_dump(by_alias=True)
        elif task_type == TASK_STORY_REVISE:
            response = revise_story_outline(
                StoryOutlineReviseRequest(
                    userId=task.get("userId"),
                    storyId=task.get("storyId"),
                    title=task.get("title") or "",
                    synopsis=task.get("storySummary"),
                    outline=task.get("outline") or "",
                    suggestion=task.get("suggestion") or "",
                )
            )
            result["storyOutline"] = response.model_dump(by_alias=True)
        elif task_type == TASK_VOLUME_GENERATE:
            def publish_volume(volume: Any) -> None:
                if not publish_progress:
                    return
                progress_result = _base_result(task)
                progress_result["success"] = True
                progress_result["partial"] = True
                progress_result["completed"] = False
                progress_result["volumeOutline"] = {"volumes": [volume.model_dump(by_alias=True)]}
                publish_progress(progress_result)

            response = generate_volume_outline(
                StoryVolumeOutlineGenerateRequest(
                    userId=task.get("userId"),
                    storyId=task.get("storyId"),
                    title=task.get("title") or "",
                    storySummary=task.get("storySummary"),
                    outline=task.get("outline") or "",
                    mainCharacters=task.get("mainCharacters") or [],
                ),
                on_volume_generated=publish_volume,
            )
            result["completed"] = True
            result["volumeOutline"] = None
        elif task_type == TASK_VOLUME_REVISE:
            response = revise_volume_outline(
                StoryVolumeOutlineReviseRequest(
                    userId=task.get("userId"),
                    storyId=task.get("storyId"),
                    title=task.get("title") or "",
                    storySummary=task.get("storySummary"),
                    outline=task.get("outline") or "",
                    mainCharacters=task.get("mainCharacters") or [],
                    volumeOutlines=task.get("volumeOutlines") or [],
                    suggestion=task.get("suggestion") or "",
                )
            )
            result["volumeOutline"] = response.model_dump(by_alias=True)
            result["completed"] = True
        elif task_type == TASK_VOLUME_STORY_GENERATE:
            volume_outline = (task.get("volumeOutlines") or [None])[0]
            if not volume_outline:
                raise ValueError("Volume outline is required for volume story generation")
            response = generate_volume_story(
                StoryVolumeStoryGenerateRequest(
                    userId=task.get("userId"),
                    storyId=task.get("storyId"),
                    volumeId=task.get("volumeId"),
                    title=task.get("title") or "",
                    storySummary=task.get("storySummary"),
                    outline=task.get("outline") or "",
                    mainCharacters=task.get("mainCharacters") or [],
                    volumeOutline=volume_outline,
                )
            )
            result["volumeId"] = task.get("volumeId")
            result["volumeNumber"] = volume_outline.get("volumeNumber")
            result["volumeStory"] = response.volume_story
            result["completed"] = True
        elif task_type == TASK_VOLUME_SECTION_GENERATE:
            volume_outline = (task.get("volumeOutlines") or [None])[0]
            if not volume_outline:
                raise ValueError("Volume outline is required for volume section generation")

            def publish_section(section: Any) -> None:
                if not publish_progress:
                    return
                progress_result = _base_result(task)
                progress_result["success"] = True
                progress_result["partial"] = True
                progress_result["completed"] = False
                progress_result["volumeId"] = task.get("volumeId")
                progress_result["volumeNumber"] = volume_outline.get("volumeNumber")
                progress_result["volumeSection"] = {"sections": [section.model_dump(by_alias=True)]}
                publish_progress(progress_result)

            generate_volume_sections(
                StoryVolumeSectionGenerateRequest(
                    userId=task.get("userId"),
                    storyId=task.get("storyId"),
                    volumeId=task.get("volumeId"),
                    title=task.get("title") or "",
                    storySummary=task.get("storySummary"),
                    outline=task.get("outline") or "",
                    mainCharacters=task.get("mainCharacters") or [],
                    volumeOutline=volume_outline,
                ),
                on_section_generated=publish_section,
            )
            result["volumeId"] = task.get("volumeId")
            result["volumeNumber"] = volume_outline.get("volumeNumber")
            result["completed"] = True
        else:
            raise ValueError(f"Unsupported AI story task type: {task_type}")

        result["success"] = True
        if task_type in {TASK_STORY_GENERATE, TASK_STORY_REVISE}:
            result["completed"] = True
    except Exception as exc:
        result["errorMessage"] = str(exc)
        print(f"[rabbitmq-worker] task {task.get('taskId')} failed: {exc}")

    return result


def _base_result(task: dict[str, Any]) -> dict[str, Any]:
    """作用：创建 RabbitMQ AI 结果消息的公共字段骨架。
    调用方：_handle_story_task 以及分卷逐卷进度回调。
    """
    return {
        "taskId": task.get("taskId"),
        "taskType": task.get("taskType"),
        "userId": task.get("userId"),
        "storyId": task.get("storyId"),
        "volumeId": task.get("volumeId"),
        "volumeNumber": None,
        "success": False,
        "errorMessage": None,
        "storyOutline": None,
        "volumeOutline": None,
        "volumeSection": None,
        "volumeStory": None,
        "partial": False,
        "completed": False,
    }


def _load_rabbitmq_config() -> dict[str, Any]:
    """作用：从环境变量或仓库根目录 config.txt 读取 RabbitMQ 地址与账号。
    调用方：_consume_forever。
    """
    config = {
        "host": os.getenv("RABBITMQ_HOST", "127.0.0.1"),
        "port": int(os.getenv("RABBITMQ_PORT", "5672")),
        "username": os.getenv("RABBITMQ_USERNAME", "guest"),
        "password": os.getenv("RABBITMQ_PASSWORD", "guest"),
    }
    config_path = Path(__file__).resolve().parents[1] / "config.txt"
    if not config_path.exists():
        return config

    raw_config = yaml.safe_load(config_path.read_text(encoding="utf-8")) or {}
    rabbit_config = raw_config.get("rabbitmq") or {}
    name_server = rabbit_config.get("name-server")
    if name_server:
        host, _, port = str(name_server).partition(":")
        config["host"] = host or config["host"]
        config["port"] = int(port or config["port"])
    config["username"] = rabbit_config.get("username") or config["username"]
    config["password"] = rabbit_config.get("password") or config["password"]
    return config
