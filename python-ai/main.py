import uvicorn
from fastapi import FastAPI

from chat_ai import router as chat_router
from rabbitmq_worker import start_rabbitmq_worker
from story_ai import router as story_router


app = FastAPI(title="Aisay Python AI Engine")
app.include_router(story_router)
app.include_router(chat_router)


@app.on_event("startup")
def start_background_workers() -> None:
    """作用：启动 RabbitMQ 后台 worker，让非对话 AI 任务以异步方式执行。
    调用方：FastAPI 应用启动时自动调用。
    """
    start_rabbitmq_worker()


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)
