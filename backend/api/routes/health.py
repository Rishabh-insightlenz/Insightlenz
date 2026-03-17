from fastapi import APIRouter
from datetime import datetime
from config import settings

router = APIRouter()


@router.get("/health")
async def health():
    return {
        "status": "alive",
        "service": "InsightLenz Backend",
        "ai_provider": settings.active_ai_provider,
        "ai_model": settings.active_ai_model,
        "timestamp": datetime.utcnow().isoformat(),
    }
