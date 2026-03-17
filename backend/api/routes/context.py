"""
Context API — manage what InsightLenz knows about the user.

These endpoints are how the Android app keeps the backend's
understanding of the user current and accurate.
"""

from fastapi import APIRouter, HTTPException
from models.user import UserContext, UserContextUpdate, Project, Priority
from core.context_engine import ContextEngine

router = APIRouter(prefix="/context", tags=["context"])

# In-memory store for now — will move to PostgreSQL in next iteration
_user_context: UserContext | None = None


def get_engine() -> ContextEngine:
    if _user_context is None:
        raise HTTPException(status_code=404, detail="User context not initialized. POST /context/init first.")
    return ContextEngine(_user_context)


@router.post("/init", response_model=UserContext)
async def initialize_context(context: UserContext):
    """
    Initialize InsightLenz for a user.
    Called once on first setup of the OS.
    """
    global _user_context
    _user_context = context
    return _user_context


@router.get("/", response_model=UserContext)
async def get_context():
    """Get the full current context of the user."""
    engine = get_engine()
    return engine.context


@router.patch("/", response_model=UserContext)
async def update_context(update: UserContextUpdate):
    """Partially update user context — only send what's changing."""
    engine = get_engine()
    return engine.update_context(update)


@router.post("/priorities", response_model=UserContext)
async def set_priorities(priorities: list[Priority]):
    """
    Set this week's priorities. Maximum 3 enforced.
    This is the single most important context signal InsightLenz has.
    """
    engine = get_engine()
    return engine.set_weekly_priorities(priorities)


@router.post("/projects", response_model=UserContext)
async def add_project(project: Project):
    """Add a new active project."""
    engine = get_engine()
    return engine.add_project(project)


@router.patch("/projects/{project_id}", response_model=Project)
async def update_project(project_id: str, status: str, next_action: str):
    """Update a project's status and next action."""
    from uuid import UUID
    engine = get_engine()
    project = engine.update_project_status(UUID(project_id), status, next_action)
    if not project:
        raise HTTPException(status_code=404, detail="Project not found")
    return project


@router.get("/system-prompt")
async def get_system_prompt():
    """
    Debug endpoint — see exactly what context InsightLenz is injecting
    into every AI call. This is what Jarvis knows about you.
    """
    engine = get_engine()
    return {"system_prompt": engine.build_system_prompt()}
