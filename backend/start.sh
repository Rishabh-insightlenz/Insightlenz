#!/usr/bin/env bash
# InsightLenz backend startup script — used by Render (and locally if needed)
set -e

# ── DATABASE_URL normalization ────────────────────────────────────────────────
# Render injects DATABASE_URL as postgres:// or postgresql://.
# SQLAlchemy + asyncpg requires postgresql+asyncpg://.
if [ -n "$DATABASE_URL" ]; then
    DATABASE_URL=$(echo "$DATABASE_URL" \
        | sed 's|^postgres://|postgresql+asyncpg://|' \
        | sed 's|^postgresql://|postgresql+asyncpg://|')
    export DATABASE_URL
    echo "start: DATABASE_URL scheme normalised to asyncpg"
fi

# ── Alembic migrations ────────────────────────────────────────────────────────
echo "start: running database migrations..."
alembic upgrade head
echo "start: migrations complete"

# ── Launch server ─────────────────────────────────────────────────────────────
echo "start: starting uvicorn on port ${PORT:-8000}"
exec uvicorn main:app --host 0.0.0.0 --port "${PORT:-8000}"
