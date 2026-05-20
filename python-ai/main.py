import uvicorn
from fastapi import FastAPI

from chat_ai import router as chat_router
from story_ai import router as story_router


app = FastAPI(title="Aisay Python AI Engine")
app.include_router(story_router)
app.include_router(chat_router)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)
