"""
Repository layer — all database operations live here.

Routes and services never touch SQLAlchemy directly.
They call the repository, which handles persistence.
This keeps the AI and business logic clean and testable.
"""

from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, desc
from sqlalchemy.orm import selectinload
from datetime import datetime
from uuid import UUID
import structlog

from db.models import UserContextDB, MemoryDB, ConversationDB, InsightDB, AppUsageDB
from models.user import UserContext, UserContextUpdate
from models.memory import Memory, Insight, MemoryType

log = structlog.get_logger()


class UserContextRepository:
    """Persistence for user context — the identity and state of an InsightLenz user."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def create(self, context: UserContext) -> UserContextDB:
        """Persist a new user context to the database."""
        db_context = UserContextDB(
            id=context.id,
            name=context.name,
            role=context.role,
            core_values=[v.model_dump() for v in context.core_values],
            non_negotiables=context.non_negotiables,
            strengths=context.strengths,
            blind_spots=context.blind_spots,
            venture_description=context.venture_description,
            venture_stage=context.venture_stage,
            biggest_constraint=context.biggest_constraint,
            biggest_opportunity=context.biggest_opportunity,
            current_priorities=[p.model_dump() for p in context.current_priorities],
            active_projects=[p.model_dump() for p in context.active_projects],
            open_loops=context.open_loops,
            goals=[g.model_dump() for g in context.goals],
            known_reactive_triggers=context.known_reactive_triggers,
            typical_distraction_apps=context.typical_distraction_apps,
            peak_focus_times=context.peak_focus_times,
        )
        self.db.add(db_context)
        await self.db.flush()
        log.info("user_context_created", user_id=str(context.id), name=context.name)
        return db_context

    async def get(self, user_id: UUID) -> UserContextDB | None:
        result = await self.db.execute(
            select(UserContextDB).where(UserContextDB.id == user_id)
        )
        return result.scalar_one_or_none()

    async def get_by_name(self, name: str) -> UserContextDB | None:
        result = await self.db.execute(
            select(UserContextDB).where(UserContextDB.name == name)
        )
        return result.scalar_one_or_none()

    async def update(self, user_id: UUID, update_data: dict) -> UserContextDB | None:
        update_data["updated_at"] = datetime.utcnow()
        await self.db.execute(
            update(UserContextDB)
            .where(UserContextDB.id == user_id)
            .values(**update_data)
        )
        return await self.get(user_id)

    @staticmethod
    def to_pydantic(db_obj: UserContextDB) -> UserContext:
        """Convert DB model back to Pydantic model."""
        from models.user import Value, Priority, Project, Goal
        return UserContext(
            id=db_obj.id,
            name=db_obj.name,
            role=db_obj.role,
            core_values=[Value(**v) for v in (db_obj.core_values or [])],
            non_negotiables=db_obj.non_negotiables or [],
            strengths=db_obj.strengths or [],
            blind_spots=db_obj.blind_spots or [],
            venture_description=db_obj.venture_description or "",
            venture_stage=db_obj.venture_stage or "",
            biggest_constraint=db_obj.biggest_constraint or "",
            biggest_opportunity=db_obj.biggest_opportunity or "",
            current_priorities=[Priority(**p) for p in (db_obj.current_priorities or [])],
            active_projects=[Project(**p) for p in (db_obj.active_projects or [])],
            open_loops=db_obj.open_loops or [],
            goals=[Goal(**g) for g in (db_obj.goals or [])],
            known_reactive_triggers=db_obj.known_reactive_triggers or [],
            typical_distraction_apps=db_obj.typical_distraction_apps or [],
            peak_focus_times=db_obj.peak_focus_times or [],
            created_at=db_obj.created_at,
            updated_at=db_obj.updated_at,
        )


class ConversationRepository:
    """Persistence for conversation history."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def save_exchange(
        self,
        user_id: UUID,
        user_message: str,
        assistant_response: str,
        source: str = "chat",
        provider: str = "",
        model: str = "",
        tokens_used: int = 0,
        session_id: str = "default",
    ):
        """Save a full user→assistant exchange (2 rows)."""
        user_turn = ConversationDB(
            user_id=user_id,
            session_id=session_id,
            role="user",
            content=user_message,
            source=source,
        )
        assistant_turn = ConversationDB(
            user_id=user_id,
            session_id=session_id,
            role="assistant",
            content=assistant_response,
            source=source,
            provider=provider,
            model=model,
            tokens_used=tokens_used,
        )
        self.db.add(user_turn)
        self.db.add(assistant_turn)
        await self.db.flush()
        log.info("conversation_saved", user_id=str(user_id), source=source, tokens=tokens_used)

    async def get_recent(
        self,
        user_id: UUID,
        limit: int = 20,
        session_id: str | None = None,
    ) -> list[ConversationDB]:
        """Get recent conversation history for context injection."""
        query = (
            select(ConversationDB)
            .where(ConversationDB.user_id == user_id)
            .order_by(desc(ConversationDB.created_at))
            .limit(limit)
        )
        if session_id:
            query = query.where(ConversationDB.session_id == session_id)
        result = await self.db.execute(query)
        rows = result.scalars().all()
        return list(reversed(rows))  # chronological order


class MemoryRepository:
    """Persistence for InsightLenz memories."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def save(self, user_id: UUID, memory: Memory) -> MemoryDB:
        db_memory = MemoryDB(
            id=memory.id,
            user_id=user_id,
            type=memory.type.value,
            content=memory.content,
            context=memory.context,
            tags=memory.tags,
            expires_at=memory.expires_at,
        )
        self.db.add(db_memory)
        await self.db.flush()
        return db_memory

    async def get_recent(self, user_id: UUID, limit: int = 50) -> list[MemoryDB]:
        result = await self.db.execute(
            select(MemoryDB)
            .where(MemoryDB.user_id == user_id)
            .order_by(desc(MemoryDB.created_at))
            .limit(limit)
        )
        return list(result.scalars().all())


class InsightRepository:
    """Persistence for proactively generated insights."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def save(self, user_id: UUID, insight: Insight) -> InsightDB:
        db_insight = InsightDB(
            id=insight.id,
            user_id=user_id,
            title=insight.title,
            body=insight.body,
            trigger=insight.trigger,
            urgency=insight.urgency,
        )
        self.db.add(db_insight)
        await self.db.flush()
        return db_insight

    async def get_unsurfaced(self, user_id: UUID) -> list[InsightDB]:
        """Get insights that haven't been shown to the user yet."""
        result = await self.db.execute(
            select(InsightDB)
            .where(InsightDB.user_id == user_id)
            .where(InsightDB.surfaced == False)
            .order_by(desc(InsightDB.created_at))
        )
        return list(result.scalars().all())

    async def mark_surfaced(self, insight_id: UUID):
        await self.db.execute(
            update(InsightDB)
            .where(InsightDB.id == insight_id)
            .values(surfaced=True, surfaced_at=datetime.utcnow())
        )


class AppUsageRepository:
    """Persistence for Android app usage — feeds the attention layer."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def log_usage(
        self,
        user_id: UUID,
        app_package: str,
        app_name: str,
        duration_seconds: int,
        started_at: datetime,
        ended_at: datetime,
        flagged_as_reactive: bool = False,
    ) -> AppUsageDB:
        record = AppUsageDB(
            user_id=user_id,
            app_package=app_package,
            app_name=app_name,
            duration_seconds=duration_seconds,
            started_at=started_at,
            ended_at=ended_at,
            flagged_as_reactive=flagged_as_reactive,
        )
        self.db.add(record)
        await self.db.flush()
        return record

    async def get_recent(self, user_id: UUID, limit: int = 100) -> list[AppUsageDB]:
        result = await self.db.execute(
            select(AppUsageDB)
            .where(AppUsageDB.user_id == user_id)
            .order_by(desc(AppUsageDB.started_at))
            .limit(limit)
        )
        return list(result.scalars().all())

    async def get_reactive_summary(self, user_id: UUID) -> dict:
        """
        Summarize reactive app usage — how much time was spent
        on apps the user flagged as distractions.
        This feeds the pattern detection engine.
        """
        records = await self.get_recent(user_id, limit=500)
        total = sum(r.duration_seconds for r in records)
        reactive = sum(r.duration_seconds for r in records if r.flagged_as_reactive)
        apps: dict[str, int] = {}
        for r in records:
            apps[r.app_name] = apps.get(r.app_name, 0) + r.duration_seconds

        return {
            "total_screen_time_seconds": total,
            "reactive_time_seconds": reactive,
            "reactive_percentage": round((reactive / total * 100) if total > 0 else 0, 1),
            "top_apps": sorted(apps.items(), key=lambda x: x[1], reverse=True)[:10],
        }
