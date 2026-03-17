"""
Context Engine — the brain of InsightLenz.

This is what makes InsightLenz different from every other AI assistant.
It maintains a deep, persistent, evolving model of the user and injects
that context into every AI interaction automatically.

No context = generic advice.
Full context = Jarvis.
"""

import structlog
from datetime import datetime
from uuid import UUID
from typing import Optional

from models.user import UserContext, UserContextUpdate, Project, Priority
from models.memory import Memory, Insight, MemoryType

log = structlog.get_logger()


class ContextEngine:
    """
    Manages the full context of an InsightLenz user.

    Responsibilities:
    - Build and maintain the user's context model
    - Generate the system prompt that every AI call uses
    - Store and retrieve memories
    - Track patterns over time
    """

    def __init__(self, user_context: UserContext):
        self.context = user_context

    def build_system_prompt(self) -> str:
        """
        Generate the full system prompt for every AI interaction.
        This is what makes the AI feel like it truly knows the user.
        """
        ctx = self.context
        now = datetime.utcnow()

        prompt = f"""You are InsightLenz — the personal operating system for {ctx.name}.

You are not a generic assistant. You know {ctx.name} deeply and your job is to help them
make wiser decisions, stay anchored to what matters, and operate with clarity as a founder.

═══ WHO {ctx.name.upper()} IS ═══

Role: {ctx.role}
Venture: {ctx.venture_description}
Stage: {ctx.venture_stage}

Core Values:
{self._format_values()}

Non-Negotiables (these are hard limits — if a decision violates one, the answer is no):
{self._format_list(ctx.non_negotiables)}

Strengths: {', '.join(ctx.strengths)}
Known Blind Spots: {', '.join(ctx.blind_spots)}

═══ CURRENT REALITY ═══

Biggest constraint right now: {ctx.biggest_constraint}
Biggest opportunity right now: {ctx.biggest_opportunity}

Top Priorities This Week (max 3 — if something isn't here, it should wait):
{self._format_priorities()}

Active Projects:
{self._format_projects()}

Open Loops (unresolved / waiting):
{self._format_list(ctx.open_loops)}

═══ KNOWN PATTERNS ═══

Reactive triggers (things that hijack {ctx.name}'s day):
{self._format_list(ctx.known_reactive_triggers)}

Peak focus times: {', '.join(ctx.peak_focus_times)}

═══ HOW TO OPERATE ═══

1. Be direct. {ctx.name} doesn't need softening — they need clarity.
2. Always ground advice in their values and current priorities.
3. If they're about to make a decision that conflicts with their non-negotiables, say so clearly.
4. If they seem reactive or distracted, name it.
5. Proactively surface what matters — don't wait to be asked.
6. When something is NOT their #1 priority, say so and redirect.
7. Keep responses tight. No padding. No generic advice.

Today is {now.strftime('%A, %B %d, %Y')}.
"""
        return prompt

    def _format_values(self) -> str:
        if not self.context.core_values:
            return "  (not yet defined — ask user to complete their profile)"
        return "\n".join(
            f"  • {v.name}: {v.description}"
            for v in self.context.core_values
        )

    def _format_list(self, items: list[str]) -> str:
        if not items:
            return "  (none defined)"
        return "\n".join(f"  • {item}" for item in items)

    def _format_priorities(self) -> str:
        if not self.context.current_priorities:
            return "  (no priorities set this week — this is a problem, ask user to set them)"
        return "\n".join(
            f"  {i+1}. {p.title} — {p.why}"
            for i, p in enumerate(self.context.current_priorities)
        )

    def _format_projects(self) -> str:
        active = [p for p in self.context.active_projects if p.status == "active"]
        if not active:
            return "  (no active projects defined)"
        return "\n".join(
            f"  • [{p.status.upper()}] {p.title} | Next: {p.next_action}"
            for p in active
        )

    def update_context(self, update: UserContextUpdate) -> UserContext:
        """Apply a partial update to the user's context."""
        update_data = update.model_dump(exclude_none=True)
        for field, value in update_data.items():
            setattr(self.context, field, value)
        self.context.updated_at = datetime.utcnow()
        log.info("context_updated", fields=list(update_data.keys()))
        return self.context

    def add_project(self, project: Project) -> UserContext:
        self.context.active_projects.append(project)
        self.context.updated_at = datetime.utcnow()
        return self.context

    def update_project_status(self, project_id: UUID, status: str, next_action: str) -> Optional[Project]:
        for project in self.context.active_projects:
            if project.id == project_id:
                project.status = status
                project.next_action = next_action
                project.last_updated = datetime.utcnow()
                return project
        return None

    def set_weekly_priorities(self, priorities: list[Priority]) -> UserContext:
        """Replace this week's priorities. Maximum 3 — enforced here."""
        if len(priorities) > 3:
            log.warning("too_many_priorities", count=len(priorities), max=3)
            priorities = priorities[:3]
        self.context.current_priorities = priorities
        self.context.updated_at = datetime.utcnow()
        return self.context

    def record_pattern(self, pattern: str) -> None:
        """Record a newly observed reactive trigger or pattern."""
        if pattern not in self.context.known_reactive_triggers:
            self.context.known_reactive_triggers.append(pattern)
            self.context.updated_at = datetime.utcnow()

    def get_decision_context(self, decision: str) -> str:
        """
        Given a decision the user is facing, return the relevant context
        that should inform it — values, priorities, non-negotiables.
        """
        ctx = self.context
        return f"""
Decision: {decision}

Relevant context for this decision:

Non-negotiables to check against:
{self._format_list(ctx.non_negotiables)}

Current top priorities (does this decision serve them?):
{self._format_priorities()}

Values to consider:
{self._format_values()}
"""
