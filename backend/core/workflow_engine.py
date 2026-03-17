"""
Workflow Engine — the rituals that keep InsightLenz proactive.

This is what separates InsightLenz from a chat interface.
It doesn't wait to be asked. It has a schedule. It reaches out.

Workflows:
- Morning brief (daily, user's chosen time)
- Evening review (daily)
- Weekly review (Friday)
- Insight generation (background, continuous)
- Pattern detection (background, continuous)
"""

import structlog
from datetime import datetime
from enum import Enum

log = structlog.get_logger()


class WorkflowType(str, Enum):
    MORNING_BRIEF = "morning_brief"
    EVENING_REVIEW = "evening_review"
    WEEKLY_REVIEW = "weekly_review"
    INSIGHT_GENERATION = "insight_generation"
    PATTERN_DETECTION = "pattern_detection"


class WorkflowResult(object):
    def __init__(self, workflow: WorkflowType, content: str, success: bool = True):
        self.workflow = workflow
        self.content = content
        self.success = success
        self.executed_at = datetime.utcnow()


class WorkflowEngine:
    """
    Executes scheduled and triggered workflows.
    Each workflow uses the AI Orchestrator with full user context.
    """

    def __init__(self, ai_orchestrator):
        self.ai = ai_orchestrator

    async def run_morning_brief(self) -> WorkflowResult:
        """
        The daily morning ritual. Called at the user's configured time.
        InsightLenz reaches out — the user doesn't have to ask.
        """
        log.info("workflow_started", type=WorkflowType.MORNING_BRIEF)
        response = await self.ai.generate_morning_brief()
        return WorkflowResult(
            workflow=WorkflowType.MORNING_BRIEF,
            content=response.content,
        )

    async def run_evening_review(self) -> WorkflowResult:
        """Evening check-in — what happened, what carries forward."""
        prompt = """Generate the evening review. Ask the user:

1. Did they do their ONE thing today? If not, what got in the way?
2. What needs to carry forward to tomorrow?
3. What is the first task for tomorrow? (Set it now so tomorrow starts with momentum.)

Keep it tight. 3 questions. No padding."""

        response = await self.ai.think(prompt)
        return WorkflowResult(
            workflow=WorkflowType.EVENING_REVIEW,
            content=response.content,
        )

    async def run_weekly_review(self) -> WorkflowResult:
        """
        Friday's 30-minute operating review.
        The most important habit in the InsightLenz system.
        """
        prompt = """Run the weekly review. Structure it in 4 parts:

CLEAR — What open loops need to be processed or closed?
REVIEW — What actually moved this week vs what didn't? Be honest.
LEARN — What pattern showed up? Where did reactive behaviour win?
PLAN — What is the ONE priority next week? What are the 3 key outcomes?

Be specific about their actual projects and priorities.
This isn't a template fill-in — it's a real operating review."""

        response = await self.ai.think(prompt)
        return WorkflowResult(
            workflow=WorkflowType.WEEKLY_REVIEW,
            content=response.content,
        )

    async def detect_pattern(self, recent_behaviour: str) -> WorkflowResult:
        """
        Analyse recent app usage / conversation patterns and surface insights.
        Called by the background pattern detection job.
        """
        trigger = f"Pattern detected in recent behaviour: {recent_behaviour}"
        response = await self.ai.generate_insight(trigger)
        return WorkflowResult(
            workflow=WorkflowType.PATTERN_DETECTION,
            content=response.content,
        )
