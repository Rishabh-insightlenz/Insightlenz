"""
User & Context Models — the identity of an InsightLenz user.

This is the data model for everything the OS knows about you.
The richer this is, the smarter every decision InsightLenz makes.
"""

from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from uuid import UUID, uuid4


class Value(BaseModel):
    """A core value the user lives by."""
    name: str
    description: str  # What it means to this user specifically


class Goal(BaseModel):
    """A goal with a time horizon."""
    title: str
    description: str
    horizon: str  # "90_days" | "1_year" | "5_years"
    is_active: bool = True
    created_at: datetime = Field(default_factory=datetime.utcnow)


class Project(BaseModel):
    """An active project in the user's life."""
    id: UUID = Field(default_factory=uuid4)
    title: str
    description: str
    status: str  # "active" | "stalled" | "waiting" | "completed"
    next_action: str  # The specific next physical action
    owner: str  # "me" | "team" | name
    priority: int = 0  # 1 = highest
    last_updated: datetime = Field(default_factory=datetime.utcnow)


class Priority(BaseModel):
    """What matters most right now — max 3."""
    title: str
    why: str  # Why this matters this week
    week_of: datetime = Field(default_factory=datetime.utcnow)


class UserContext(BaseModel):
    """
    The full context of an InsightLenz user.
    This is what separates InsightLenz from every other AI assistant.
    Every decision, every proactive insight, every morning ritual
    is grounded in this model.
    """
    id: UUID = Field(default_factory=uuid4)
    name: str
    role: str  # e.g. "Founder"

    # Identity
    core_values: list[Value] = []
    non_negotiables: list[str] = []
    strengths: list[str] = []
    blind_spots: list[str] = []

    # What they're building
    venture_description: str = ""
    venture_stage: str = ""  # "ideation" | "building" | "growing" | "scaling"
    biggest_constraint: str = ""
    biggest_opportunity: str = ""

    # Current state
    current_priorities: list[Priority] = []  # Max 3
    active_projects: list[Project] = []
    open_loops: list[str] = []  # Things unresolved / waiting

    # Goals
    goals: list[Goal] = []

    # Patterns InsightLenz has learned
    known_reactive_triggers: list[str] = []  # What hijacks their day
    typical_distraction_apps: list[str] = []
    peak_focus_times: list[str] = []  # e.g. ["09:00-11:00"]

    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)


class UserContextUpdate(BaseModel):
    """Partial update to user context — only send what's changing."""
    core_values: Optional[list[Value]] = None
    non_negotiables: Optional[list[str]] = None
    current_priorities: Optional[list[Priority]] = None
    active_projects: Optional[list[Project]] = None
    open_loops: Optional[list[str]] = None
    goals: Optional[list[Goal]] = None
    biggest_constraint: Optional[str] = None
    biggest_opportunity: Optional[str] = None
