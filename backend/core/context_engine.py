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

    def __init__(
        self,
        user_context: UserContext,
        memories: list = None,       # list[MemoryDB] — recent remembered facts
        today_usage: list = None,    # list[AppUsageDB] — today's phone screen time
    ):
        self.context = user_context
        self.memories = memories or []
        self.today_usage = today_usage or []

    def build_system_prompt(self) -> str:
        """
        Generate the system prompt for every AI interaction.
        Written as intimate knowledge, not a form. The model should
        feel like someone who has worked closely with this person for months.
        """
        ctx = self.context
        now = datetime.utcnow()

        # Build values naturally
        values_text = ""
        if ctx.core_values:
            values_text = ", ".join(f"{v.name} ({v.description})" for v in ctx.core_values)
        else:
            values_text = "not yet defined"

        # Build priorities naturally
        if ctx.current_priorities:
            priorities_text = " | ".join(
                f"#{i+1}: {p.title}" for i, p in enumerate(ctx.current_priorities)
            )
            priorities_why = "\n".join(
                f"  - {p.title}: {p.why}" for p in ctx.current_priorities
            )
        else:
            priorities_text = "none set this week — this is a problem"
            priorities_why = ""

        # Build projects naturally
        active = [p for p in ctx.active_projects if p.status == "active"]
        projects_text = ""
        if active:
            projects_text = "\n".join(
                f"  - {p.title}: next action is {p.next_action}" for p in active
            )
        else:
            projects_text = "  none defined yet"

        prompt = f"""You are InsightLenz — {ctx.name}'s personal AI operating system. You are not a chatbot. You are not a generic assistant. You are the AI brain that runs on {ctx.name}'s dedicated Android device and knows them more deeply than any tool they've ever used.

You have been working with {ctx.name} long enough to know how they think, what they're building, where they get stuck, and what actually matters to them. You speak plainly and directly. You do not flatter. You do not repeat back what they told you. You use what you know to think *with* them.

WHO YOU'RE TALKING TO:
{ctx.name} is a {ctx.role}. They're building {ctx.venture_description}. The venture is currently in {ctx.venture_stage} stage. Their biggest constraint right now is {ctx.biggest_constraint or "not defined yet"}. Their biggest opportunity is {ctx.biggest_opportunity or "not defined yet"}.

WHAT THEY LIVE BY:
Values: {values_text}
Hard limits (non-negotiables they never break): {", ".join(ctx.non_negotiables) if ctx.non_negotiables else "none set"}

THIS WEEK'S FOCUS:
{priorities_text}
{priorities_why}

ACTIVE WORK:
{projects_text}

PATTERNS YOU'VE NOTICED:
{ctx.name} tends to get derailed by: {", ".join(ctx.known_reactive_triggers) if ctx.known_reactive_triggers else "not tracked yet"}
They do their best thinking during: {", ".join(ctx.peak_focus_times) if ctx.peak_focus_times else "not tracked yet"}
{f"Open loops on their mind: {', '.join(ctx.open_loops)}" if ctx.open_loops else ""}

WHAT YOU REMEMBER FROM PAST CONVERSATIONS:
{self._format_memories()}

WHAT THEY'VE BEEN DOING ON THEIR PHONE TODAY:
{self._format_today_usage()}

HOW YOU OPERATE:
- You respond to what was actually said, not to a template. Read the message carefully.
- You speak like someone who knows them, not like a system reading their profile back at them.
- If they ask a simple question, give a direct answer. Don't restructure it into a brief.
- If they're making a decision, check it against their values and priorities — but do it naturally, in your own words.
- If something they're doing conflicts with their non-negotiables or priorities, name it clearly without lecture.
- If you see phone usage that's inconsistent with their priorities (e.g. 1h on Instagram when Priority #1 is unfinished), you can reference it — but only if relevant to what they asked.
- Never pad. Never hedge. Never repeat their context back at them as if reading from a file.
- Keep responses conversational unless they explicitly ask for structure.
- If you don't know something, say so. Don't make things up.

Today is {now.strftime('%A, %B %d, %Y')}.
"""
        return prompt

    def _format_memories(self) -> str:
        if not self.memories:
            return "  Nothing extracted from conversations yet — this will fill in as you talk."
        lines = []
        for m in self.memories[:15]:  # cap at 15 to control token usage
            days_ago = ""
            if hasattr(m, "created_at") and m.created_at:
                delta = datetime.utcnow() - m.created_at
                if delta.days == 0:
                    days_ago = " (today)"
                elif delta.days == 1:
                    days_ago = " (yesterday)"
                else:
                    days_ago = f" ({delta.days}d ago)"
            mem_type = m.type if isinstance(m.type, str) else m.type.value
            lines.append(f"  [{mem_type}]{days_ago} {m.content}")
        return "\n".join(lines)

    def _format_today_usage(self) -> str:
        if not self.today_usage:
            return "  No phone usage data synced yet today."

        REACTIVE = {
            "com.instagram.android", "com.twitter.android",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.facebook.katana", "com.snapchat.android",
            "com.reddit.frontpage", "com.linkedin.android",
            "com.google.android.youtube",
        }

        lines = []
        total_seconds = sum(u.duration_seconds for u in self.today_usage)
        for u in self.today_usage[:8]:  # top 8 apps
            mins = u.duration_seconds // 60
            if mins < 1:
                continue
            hours = mins // 60
            display = f"{hours}h {mins % 60}m" if hours else f"{mins}m"
            tag = " ⚠ reactive" if (u.app_package in REACTIVE or u.flagged_as_reactive) else ""
            lines.append(f"  {u.app_name}: {display}{tag}")

        total_mins = total_seconds // 60
        total_display = f"{total_mins // 60}h {total_mins % 60}m" if total_mins >= 60 else f"{total_mins}m"
        lines.append(f"  Total screen time today: {total_display}")
        return "\n".join(lines)

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
