"""
Intelligence API — the thinking endpoints.

These are what the Android app calls to get InsightLenz's
proactive intelligence, decision support, and workflow outputs.

Every exchange is persisted to PostgreSQL — InsightLenz remembers
every conversation and uses history to improve over time.

What happens on every /chat call:
  1. Load user context from DB
  2. Load recent memories (what InsightLenz has learned over time)
  3. Load today's phone usage (what the phone has been doing)
  4. Load conversation history for this session
  5. Build system prompt with ALL of the above
  6. Call AI
  7. Persist the exchange
  8. Fire memory extraction in background (learns from this conversation)
"""

from fastapi import APIRouter, BackgroundTasks, HTTPException, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import Session
from pydantic import BaseModel
from uuid import UUID

from core.context_engine import ContextEngine
from core.ai_orchestrator import AIOrchestrator, Message as AIMessage
from core.workflow_engine import WorkflowEngine
from core.memory_extractor import MemoryExtractor
from db.database import get_db, AsyncSessionLocal
from db.repository import (
    UserContextRepository,
    ConversationRepository,
    MemoryRepository,
    AppUsageRepository,
)

router = APIRouter(prefix="/intelligence", tags=["intelligence"])


class ChatRequest(BaseModel):
    message: str
    session_id: str = "default"


class DecisionRequest(BaseModel):
    decision: str


class ChatResponse(BaseModel):
    response: str
    provider: str
    model: str


# ── Bootstrap ─────────────────────────────────────────────────────────────────

async def _bootstrap(db: AsyncSession) -> tuple[ContextEngine, UUID]:
    """
    Load user context, memories, and today's phone usage from DB.
    Build a fully-enriched ContextEngine — this is what makes the AI aware
    of the user's real-world state, not just their static profile.
    """
    user_repo = UserContextRepository(db)
    db_ctx = await user_repo.get_first()
    if not db_ctx:
        raise HTTPException(
            status_code=404,
            detail="User context not initialized. POST /context/init first.",
        )

    context = UserContextRepository.to_pydantic(db_ctx)
    user_id = db_ctx.id

    # Load recent memories — what InsightLenz has learned from past conversations
    memory_repo = MemoryRepository(db)
    memories = await memory_repo.get_recent(user_id, limit=20)

    # Load today's phone usage — what apps, how long
    usage_repo = AppUsageRepository(db)
    today_usage = await usage_repo.get_today_summary(user_id)

    engine = ContextEngine(
        user_context=context,
        memories=memories,
        today_usage=today_usage,
    )
    return engine, user_id


# ── Background: memory extraction ─────────────────────────────────────────────

async def _extract_memories_background(
    user_id: UUID,
    user_message: str,
    assistant_response: str,
    orchestrator: AIOrchestrator,
):
    """
    Background task: extract memorable facts from this exchange and persist them.
    Runs after the chat response is already sent — zero latency impact.
    Uses a fresh DB session since the request session is closed by this point.
    """
    async with AsyncSessionLocal() as db:
        async with db.begin():
            memory_repo = MemoryRepository(db)
            extractor = MemoryExtractor(orchestrator)
            await extractor.extract_and_save(
                user_id=user_id,
                user_message=user_message,
                assistant_response=assistant_response,
                memory_repo=memory_repo,
            )


# ── Chat ──────────────────────────────────────────────────────────────────────

@router.post("/chat", response_model=ChatResponse)
async def chat(
    request: ChatRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
):
    """
    Core conversation endpoint.

    Injects: static user profile + learned memories + today's phone usage + conversation history.
    That's the full picture. Every response is grounded in who they are, what they've said
    before, and what they've actually been doing on their phone today.
    """
    engine, user_id = await _bootstrap(db)
    conv_repo = ConversationRepository(db)

    # Load recent conversation history for this session
    recent = await conv_repo.get_recent(user_id, limit=20, session_id=request.session_id)
    history = [AIMessage(role=msg.role, content=msg.content) for msg in recent]

    orchestrator = AIOrchestrator(engine)
    response = await orchestrator.think(request.message, conversation_history=history)

    # Persist the exchange
    await conv_repo.save_exchange(
        user_id=user_id,
        user_message=request.message,
        assistant_response=response.content,
        source="chat",
        provider=response.provider,
        model=response.model,
        session_id=request.session_id,
    )

    # Fire memory extraction in the background — doesn't block the response
    background_tasks.add_task(
        _extract_memories_background,
        user_id=user_id,
        user_message=request.message,
        assistant_response=response.content,
        orchestrator=orchestrator,
    )

    return ChatResponse(
        response=response.content,
        provider=response.provider,
        model=response.model,
    )


# ── Decision support ──────────────────────────────────────────────────────────

@router.post("/decide", response_model=ChatResponse)
async def support_decision(
    request: DecisionRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
):
    """
    Decision support — runs the decision through values, priorities, non-negotiables.
    """
    engine, user_id = await _bootstrap(db)
    orchestrator = AIOrchestrator(engine)
    response = await orchestrator.think_about_decision(request.decision)

    conv_repo = ConversationRepository(db)
    await conv_repo.save_exchange(
        user_id=user_id,
        user_message=request.decision,
        assistant_response=response.content,
        source="decide",
        provider=response.provider,
        model=response.model,
    )

    background_tasks.add_task(
        _extract_memories_background,
        user_id=user_id,
        user_message=request.decision,
        assistant_response=response.content,
        orchestrator=orchestrator,
    )

    return ChatResponse(
        response=response.content,
        provider=response.provider,
        model=response.model,
    )


# ── Workflows ─────────────────────────────────────────────────────────────────

@router.get("/morning-brief", response_model=ChatResponse)
async def morning_brief(db: AsyncSession = Depends(get_db)):
    """
    Generate today's morning brief.
    Called by the Android OS layer at 6am — InsightLenz reaches out first.
    Full context (memories + phone usage from yesterday) injected automatically.
    """
    engine, user_id = await _bootstrap(db)
    orchestrator = AIOrchestrator(engine)
    workflow = WorkflowEngine(orchestrator)
    result = await workflow.run_morning_brief()

    conv_repo = ConversationRepository(db)
    await conv_repo.save_exchange(
        user_id=user_id,
        user_message="[morning_brief_trigger]",
        assistant_response=result.content,
        source="morning_brief",
        provider=orchestrator.provider,
        model=orchestrator.model,
    )

    return ChatResponse(
        response=result.content,
        provider=orchestrator.provider,
        model=orchestrator.model,
    )


@router.get("/evening-review", response_model=ChatResponse)
async def evening_review(db: AsyncSession = Depends(get_db)):
    """
    Generate the evening review.
    Has access to today's full phone usage — the review knows what you actually did.
    """
    engine, user_id = await _bootstrap(db)
    orchestrator = AIOrchestrator(engine)
    workflow = WorkflowEngine(orchestrator)
    result = await workflow.run_evening_review()

    conv_repo = ConversationRepository(db)
    await conv_repo.save_exchange(
        user_id=user_id,
        user_message="[evening_review_trigger]",
        assistant_response=result.content,
        source="evening_review",
        provider=orchestrator.provider,
        model=orchestrator.model,
    )

    return ChatResponse(
        response=result.content,
        provider=orchestrator.provider,
        model=orchestrator.model,
    )


@router.get("/weekly-review", response_model=ChatResponse)
async def weekly_review(db: AsyncSession = Depends(get_db)):
    """
    Generate the weekly review. Called every Friday at 5pm.
    """
    engine, user_id = await _bootstrap(db)
    orchestrator = AIOrchestrator(engine)
    workflow = WorkflowEngine(orchestrator)
    result = await workflow.run_weekly_review()

    conv_repo = ConversationRepository(db)
    await conv_repo.save_exchange(
        user_id=user_id,
        user_message="[weekly_review_trigger]",
        assistant_response=result.content,
        source="weekly_review",
        provider=orchestrator.provider,
        model=orchestrator.model,
    )

    return ChatResponse(
        response=result.content,
        provider=orchestrator.provider,
        model=orchestrator.model,
    )
