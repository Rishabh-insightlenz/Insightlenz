"""
Intelligence API — the thinking endpoints.

These are what the Android app calls to get InsightLenz's
proactive intelligence, decision support, and workflow outputs.
"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from core.context_engine import ContextEngine
from core.ai_orchestrator import AIOrchestrator
from core.workflow_engine import WorkflowEngine
from api.routes.context import get_engine

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


def get_orchestrator(engine: ContextEngine = None) -> AIOrchestrator:
    if engine is None:
        engine = get_engine()
    return AIOrchestrator(engine)


@router.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """
    Core conversation endpoint.
    Full user context is injected automatically — this is InsightLenz, not ChatGPT.
    """
    orchestrator = get_orchestrator()
    response = await orchestrator.think(request.message)
    return ChatResponse(
        response=response.content,
        provider=response.provider,
        model=response.model,
    )


@router.post("/decide", response_model=ChatResponse)
async def support_decision(request: DecisionRequest):
    """
    Decision support — runs the decision through values, priorities, non-negotiables.
    This is the 5-filter test, automated.
    """
    orchestrator = get_orchestrator()
    response = await orchestrator.think_about_decision(request.decision)
    return ChatResponse(
        response=response.content,
        provider=response.provider,
        model=response.model,
    )


@router.get("/morning-brief", response_model=ChatResponse)
async def morning_brief():
    """
    Generate today's morning brief.
    Called by the Android OS layer at the user's configured morning time.
    InsightLenz reaches out — the user doesn't have to ask.
    """
    orchestrator = get_orchestrator()
    workflow = WorkflowEngine(orchestrator)
    result = await workflow.run_morning_brief()
    return ChatResponse(
        response=result.content,
        provider=orchestrator.provider,
        model=orchestrator.model,
    )


@router.get("/evening-review", response_model=ChatResponse)
async def evening_review():
    """Generate the evening review prompt."""
    orchestrator = get_orchestrator()
    workflow = WorkflowEngine(orchestrator)
    result = await workflow.run_evening_review()
    return ChatResponse(
        response=result.content,
        provider=orchestrator.provider,
        model=orchestrator.model,
    )


@router.get("/weekly-review", response_model=ChatResponse)
async def weekly_review():
    """
    Generate the weekly review.
    The most important ritual in InsightLenz — called every Friday.
    """
    orchestrator = get_orchestrator()
    workflow = WorkflowEngine(orchestrator)
    result = await workflow.run_weekly_review()
    return ChatResponse(
        response=result.content,
        provider=orchestrator.provider,
        model=orchestrator.model,
    )
