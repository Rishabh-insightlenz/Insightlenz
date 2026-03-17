"""
SQLAlchemy ORM models — what gets persisted in PostgreSQL.

These are the database representations of the Pydantic models.
Pydantic = API validation. SQLAlchemy = persistence.
"""

from sqlalchemy import (
    Column, String, Text, Boolean, Integer, Float,
    DateTime, ForeignKey, JSON, Enum as SAEnum
)
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import relationship
from datetime import datetime
import uuid

from db.database import Base


class UserContextDB(Base):
    """Persistent user context — the identity of an InsightLenz user."""
    __tablename__ = "user_contexts"

    id = Column(PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(String(255), nullable=False)
    role = Column(String(255), nullable=False)

    # Identity — stored as JSON arrays
    core_values = Column(JSON, default=list)
    non_negotiables = Column(JSON, default=list)
    strengths = Column(JSON, default=list)
    blind_spots = Column(JSON, default=list)

    # Venture
    venture_description = Column(Text, default="")
    venture_stage = Column(String(100), default="")
    biggest_constraint = Column(Text, default="")
    biggest_opportunity = Column(Text, default="")

    # Current state
    current_priorities = Column(JSON, default=list)
    active_projects = Column(JSON, default=list)
    open_loops = Column(JSON, default=list)
    goals = Column(JSON, default=list)

    # Learned patterns
    known_reactive_triggers = Column(JSON, default=list)
    typical_distraction_apps = Column(JSON, default=list)
    peak_focus_times = Column(JSON, default=list)

    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    memories = relationship("MemoryDB", back_populates="user", cascade="all, delete-orphan")
    conversations = relationship("ConversationDB", back_populates="user", cascade="all, delete-orphan")
    insights = relationship("InsightDB", back_populates="user", cascade="all, delete-orphan")


class MemoryDB(Base):
    """Persistent memory — things InsightLenz remembers about the user."""
    __tablename__ = "memories"

    id = Column(PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(PGUUID(as_uuid=True), ForeignKey("user_contexts.id"), nullable=False)

    type = Column(String(50), nullable=False)  # decision, commitment, insight, pattern, win, miss
    content = Column(Text, nullable=False)
    context = Column(Text, default="")
    tags = Column(JSON, default=list)

    created_at = Column(DateTime, default=datetime.utcnow)
    expires_at = Column(DateTime, nullable=True)

    user = relationship("UserContextDB", back_populates="memories")


class ConversationDB(Base):
    """
    Persistent conversation history.
    Every interaction with InsightLenz is stored.
    This is how it builds context over time.
    """
    __tablename__ = "conversations"

    id = Column(PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(PGUUID(as_uuid=True), ForeignKey("user_contexts.id"), nullable=False)
    session_id = Column(String(255), nullable=False, default="default")

    role = Column(String(50), nullable=False)   # "user" | "assistant"
    content = Column(Text, nullable=False)

    # Which workflow/feature triggered this
    source = Column(String(100), default="chat")  # chat, morning_brief, decision, weekly_review
    provider = Column(String(50), default="")      # which AI provider responded
    model = Column(String(100), default="")
    tokens_used = Column(Integer, default=0)

    created_at = Column(DateTime, default=datetime.utcnow)

    user = relationship("UserContextDB", back_populates="conversations")


class InsightDB(Base):
    """
    Proactively generated insights.
    InsightLenz generates these in the background and surfaces them.
    """
    __tablename__ = "insights"

    id = Column(PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(PGUUID(as_uuid=True), ForeignKey("user_contexts.id"), nullable=False)

    title = Column(String(500), nullable=False)
    body = Column(Text, nullable=False)
    trigger = Column(Text, nullable=False)
    urgency = Column(String(20), default="medium")  # low, medium, high

    surfaced = Column(Boolean, default=False)
    surfaced_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    user = relationship("UserContextDB", back_populates="insights")


class AppUsageDB(Base):
    """
    Android app usage tracking.
    The attention layer feeds data here — what apps, how long, when.
    This powers pattern detection over time.
    """
    __tablename__ = "app_usage"

    id = Column(PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(PGUUID(as_uuid=True), ForeignKey("user_contexts.id"), nullable=False)

    app_package = Column(String(255), nullable=False)  # e.g. com.instagram.android
    app_name = Column(String(255), nullable=False)
    duration_seconds = Column(Integer, nullable=False)

    # Was this app usage intentional or reactive?
    flagged_as_reactive = Column(Boolean, default=False)

    started_at = Column(DateTime, nullable=False)
    ended_at = Column(DateTime, nullable=False)
