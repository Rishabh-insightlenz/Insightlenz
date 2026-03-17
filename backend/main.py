"""
InsightLenz Backend — main entry point.

The brain of the OS. Runs as a FastAPI server.
The Android device is a thin client — all intelligence lives here.
"""

import structlog
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from config import settings
from api.routes import health, context, intelligence

log = structlog.get_logger()


@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info(
        "insightlenz_starting",
        env=settings.app_env,
        ai_provider=settings.active_ai_provider,
        ai_model=settings.active_ai_model,
    )
    yield
    log.info("insightlenz_shutdown")


app = FastAPI(
    title="InsightLenz OS — Backend",
    description="The brain. The Android device is just the interface.",
    version="0.1.0",
    lifespan=lifespan,
)

# CORS — Android app needs to reach this
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Lock down in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routes
app.include_router(health.router)
app.include_router(context.router)
app.include_router(intelligence.router)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.debug,
        log_level="info",
    )
