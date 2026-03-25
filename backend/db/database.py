"""
Database connection and session management.
Uses SQLAlchemy async engine with PostgreSQL.
Falls back gracefully if DB is not yet configured.
"""

from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy import text
import structlog

from config import settings

log = structlog.get_logger()


class Base(DeclarativeBase):
    pass


# Create async engine
# connect_args for Supabase compatibility:
#   - ssl="require"                  — Supabase mandates TLS
#   - prepared_statement_cache_size=0 — required when using Supabase connection pooler
#     (transaction/session pooler doesn't support named prepared statements)
engine = create_async_engine(
    settings.database_url,
    echo=settings.debug,
    pool_pre_ping=True,
    pool_size=5,
    max_overflow=10,
    connect_args={
        "ssl": "require",
        "prepared_statement_cache_size": 0,
    },
)

AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


async def get_db() -> AsyncSession:
    """Dependency injection for FastAPI routes."""
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


async def init_db():
    """Create all tables. Called on startup."""
    try:
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
        log.info("database_initialized")
    except Exception as e:
        log.warning("database_init_failed", error=str(e))
        log.warning("running_without_persistence")


async def check_db_connection() -> bool:
    """Health check for database."""
    try:
        async with AsyncSessionLocal() as session:
            await session.execute(text("SELECT 1"))
        return True
    except Exception:
        return False
