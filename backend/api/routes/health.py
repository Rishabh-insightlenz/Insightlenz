from fastapi import APIRouter
from datetime import datetime
from config import settings
from db.database import check_db_connection

router = APIRouter()


@router.get("/health")
async def health():
    db_ok = await check_db_connection()
    return {
        "status": "alive",
        "service": "InsightLenz Backend",
        "version": "0.1.0",
        "ai_provider": settings.active_ai_provider,
        "ai_model": settings.active_ai_model,
        "database": "connected" if db_ok else "unavailable — run: docker-compose up -d",
        "timestamp": datetime.utcnow().isoformat(),
    }
