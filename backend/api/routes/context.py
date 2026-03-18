"""
Context API — manage what InsightLenz knows about the user.

These endpoints are how the Android app keeps the backend's
understanding of the user current and accurate.

All context is persisted to PostgreSQL — survives restarts.
"""

from uuid import UUID
from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from pydantic import BaseModel

from db.database import get_db
from db.repository import UserContextRepository, AppUsageRepository
from models.user import UserContext, UserContextUpdate, Project, Priority
from core.context_engine import ContextEngine

# ── App Usage Sync models ─────────────────────────────────────────────────────

REACTIVE_PACKAGES = {
    "com.instagram.android", "com.twitter.android",
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
    "com.facebook.katana", "com.snapchat.android",
    "com.reddit.frontpage", "com.linkedin.android",
    "com.google.android.youtube",
}


class AppUsageEntry(BaseModel):
    package_name: str
    app_name: str
    total_time_ms: int   # milliseconds from UsageStatsManager


class AppUsageSync(BaseModel):
    stats: list[AppUsageEntry]

router = APIRouter(prefix="/context", tags=["context"])


async def get_current_context(db: AsyncSession = Depends(get_db)) -> UserContext:
    """
    FastAPI dependency — loads user context from DB.
    Raises 404 if not initialized yet.
    """
    repo = UserContextRepository(db)
    db_ctx = await repo.get_first()
    if not db_ctx:
        raise HTTPException(
            status_code=404,
            detail="User context not initialized. POST /context/init first.",
        )
    return UserContextRepository.to_pydantic(db_ctx)


@router.post("/init", response_model=UserContext)
async def initialize_context(context: UserContext, db: AsyncSession = Depends(get_db)):
    """
    Initialize InsightLenz for a user.
    Called once on first setup of the OS.
    If already initialized, returns the existing context (idempotent).
    """
    repo = UserContextRepository(db)
    existing = await repo.get_first()
    if existing:
        return UserContextRepository.to_pydantic(existing)
    db_ctx = await repo.create(context)
    return UserContextRepository.to_pydantic(db_ctx)


@router.get("/", response_model=UserContext)
async def get_context(context: UserContext = Depends(get_current_context)):
    """Get the full current context of the user."""
    return context


@router.patch("/", response_model=UserContext)
async def update_context(
    update: UserContextUpdate,
    db: AsyncSession = Depends(get_db),
):
    """Partially update user context — only send what's changing."""
    repo = UserContextRepository(db)
    db_ctx = await repo.get_first()
    if not db_ctx:
        raise HTTPException(status_code=404, detail="User context not initialized.")

    context = UserContextRepository.to_pydantic(db_ctx)
    engine = ContextEngine(context)
    updated = engine.update_context(update)

    # Serialize nested Pydantic objects to dicts for JSONB columns
    # mode='json' converts datetime → ISO string so JSONB can serialize it
    update_dict = update.model_dump(exclude_none=True, mode='json')
    if update.core_values is not None:
        update_dict["core_values"] = [v.model_dump(mode='json') for v in update.core_values]
    if update.current_priorities is not None:
        update_dict["current_priorities"] = [p.model_dump(mode='json') for p in update.current_priorities]
    if update.active_projects is not None:
        update_dict["active_projects"] = [p.model_dump(mode='json') for p in update.active_projects]
    if update.goals is not None:
        update_dict["goals"] = [g.model_dump(mode='json') for g in update.goals]

    await repo.update(db_ctx.id, update_dict)
    return updated


@router.post("/priorities", response_model=UserContext)
async def set_priorities(
    priorities: list[Priority],
    db: AsyncSession = Depends(get_db),
):
    """
    Set this week's priorities. Maximum 3 enforced.
    This is the single most important context signal InsightLenz has.
    """
    repo = UserContextRepository(db)
    db_ctx = await repo.get_first()
    if not db_ctx:
        raise HTTPException(status_code=404, detail="User context not initialized.")

    context = UserContextRepository.to_pydantic(db_ctx)
    engine = ContextEngine(context)
    updated = engine.set_weekly_priorities(priorities)
    await repo.update(db_ctx.id, {
        "current_priorities": [p.model_dump(mode='json') for p in updated.current_priorities]
    })
    return updated


@router.post("/projects", response_model=UserContext)
async def add_project(project: Project, db: AsyncSession = Depends(get_db)):
    """Add a new active project."""
    repo = UserContextRepository(db)
    db_ctx = await repo.get_first()
    if not db_ctx:
        raise HTTPException(status_code=404, detail="User context not initialized.")

    context = UserContextRepository.to_pydantic(db_ctx)
    engine = ContextEngine(context)
    updated = engine.add_project(project)
    await repo.update(db_ctx.id, {
        "active_projects": [p.model_dump(mode='json') for p in updated.active_projects]
    })
    return updated


@router.patch("/projects/{project_id}", response_model=Project)
async def update_project(
    project_id: str,
    status: str,
    next_action: str,
    db: AsyncSession = Depends(get_db),
):
    """Update a project's status and next action."""
    repo = UserContextRepository(db)
    db_ctx = await repo.get_first()
    if not db_ctx:
        raise HTTPException(status_code=404, detail="User context not initialized.")

    context = UserContextRepository.to_pydantic(db_ctx)
    engine = ContextEngine(context)
    project = engine.update_project_status(UUID(project_id), status, next_action)
    if not project:
        raise HTTPException(status_code=404, detail="Project not found")

    await repo.update(db_ctx.id, {
        "active_projects": [p.model_dump(mode='json') for p in engine.context.active_projects]
    })
    return project


@router.post("/app-usage")
async def sync_app_usage(
    payload: AppUsageSync,
    db: AsyncSession = Depends(get_db),
):
    """
    Receive today's app usage snapshot from the Android device.
    Called every 30 minutes by UsageSyncWorker.
    Replaces the existing today snapshot — always shows latest data.
    """
    repo = UserContextRepository(db)
    db_ctx = await repo.get_first()
    if not db_ctx:
        raise HTTPException(status_code=404, detail="User context not initialized.")

    usage_repo = AppUsageRepository(db)
    entries = [
        {
            "app_package": e.package_name,
            "app_name": e.app_name,
            "duration_seconds": e.total_time_ms // 1000,
            "flagged_as_reactive": e.package_name in REACTIVE_PACKAGES,
        }
        for e in payload.stats
        if e.total_time_ms > 60_000  # skip anything under 1 minute
    ]
    await usage_repo.replace_today(db_ctx.id, entries)
    return {"synced": len(entries)}


@router.get("/system-prompt")
async def get_system_prompt(context: UserContext = Depends(get_current_context)):
    """
    Debug endpoint — see exactly what context InsightLenz is injecting
    into every AI call. This is what Jarvis knows about you.
    """
    engine = ContextEngine(context)
    return {"system_prompt": engine.build_system_prompt()}
