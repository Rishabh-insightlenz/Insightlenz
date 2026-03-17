"""
Memory Models — how InsightLenz remembers and learns.

Short-term: recent conversations, decisions, events (last 30 days)
Long-term: patterns, commitments, recurring themes (lifetime)
"""

from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from uuid import UUID, uuid4
from enum import Enum


class MemoryType(str, Enum):
    DECISION = "decision"          # A decision the user made
    COMMITMENT = "commitment"      # Something they committed to
    INSIGHT = "insight"            # Something InsightLenz observed
    CONVERSATION = "conversation"  # A meaningful exchange
    PATTERN = "pattern"            # A recurring behaviour
    WIN = "win"                    # Something that went well
    MISS = "miss"                  # Something that didn't


class Memory(BaseModel):
    """A single unit of memory InsightLenz retains about the user."""
    id: UUID = Field(default_factory=uuid4)
    type: MemoryType
    content: str                       # What happened / what was said
    context: str = ""                  # Why it matters
    tags: list[str] = []               # For retrieval
    embedding: Optional[list[float]] = None  # Vector for semantic search
    created_at: datetime = Field(default_factory=datetime.utcnow)
    expires_at: Optional[datetime] = None   # None = permanent memory


class Insight(BaseModel):
    """
    A proactively generated insight.
    InsightLenz generates these in the background — not when you ask,
    but when it notices something worth surfacing.
    """
    id: UUID = Field(default_factory=uuid4)
    title: str
    body: str
    trigger: str          # What caused this insight to be generated
    urgency: str          # "low" | "medium" | "high"
    surfaced: bool = False
    created_at: datetime = Field(default_factory=datetime.utcnow)
    surfaced_at: Optional[datetime] = None
