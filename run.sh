#!/bin/bash
# ══════════════════════════════════════════════════════
#  InsightLenz — One-command startup script
#  Usage: bash run.sh
# ══════════════════════════════════════════════════════

set -e

BACKEND_DIR="$(cd "$(dirname "$0")/backend" && pwd)"
VENV_DIR="$BACKEND_DIR/.venv"

echo ""
echo "  ██╗███╗   ██╗███████╗██╗ ██████╗ ██╗  ██╗████████╗██╗     ███████╗███╗   ██╗███████╗"
echo "  ██║████╗  ██║██╔════╝██║██╔════╝ ██║  ██║╚══██╔══╝██║     ██╔════╝████╗  ██║╚══███╔╝"
echo "  ██║██╔██╗ ██║███████╗██║██║  ███╗███████║   ██║   ██║     █████╗  ██╔██╗ ██║  ███╔╝ "
echo "  ██║██║╚██╗██║╚════██║██║██║   ██║██╔══██║   ██║   ██║     ██╔══╝  ██║╚██╗██║ ███╔╝  "
echo "  ██║██║ ╚████║███████║██║╚██████╔╝██║  ██║   ██║   ███████╗███████╗██║ ╚████║███████╗"
echo "  ╚═╝╚═╝  ╚═══╝╚══════╝╚═╝ ╚═════╝ ╚═╝  ╚═╝   ╚═╝   ╚══════╝╚══════╝╚═╝  ╚═══╝╚══════╝"
echo ""
echo "  The OS that knows you."
echo ""

# ── Step 1: Virtual environment ──────────────────────────────────────────────
if [ ! -d "$VENV_DIR" ]; then
  echo "→ Creating virtual environment..."
  python3 -m venv "$VENV_DIR"
fi

source "$VENV_DIR/bin/activate"

# ── Step 2: Install dependencies ─────────────────────────────────────────────
echo "→ Upgrading pip and setuptools (Python 3.13 compatibility)..."
pip install --upgrade pip setuptools wheel -q

echo "→ Installing dependencies..."
pip install -r "$BACKEND_DIR/requirements.txt" -q

# ── Step 3: Check .env ───────────────────────────────────────────────────────
if [ ! -f "$BACKEND_DIR/.env" ]; then
  echo "⚠️  No .env file found. Copying from .env.example..."
  cp "$BACKEND_DIR/.env.example" "$BACKEND_DIR/.env"
  echo "   Edit $BACKEND_DIR/.env and add your API keys, then re-run."
  exit 1
fi

# Load env
set -a && source "$BACKEND_DIR/.env" && set +a

# ── Step 4: Wait for PostgreSQL ───────────────────────────────────────────────
echo "→ Waiting for PostgreSQL..."
MAX_RETRIES=15
COUNT=0
until python3 -c "
import asyncio, asyncpg, os
async def check():
    url = os.getenv('DATABASE_URL','').replace('postgresql+asyncpg://', '')
    await asyncpg.connect(url)
asyncio.run(check())
" 2>/dev/null; do
  COUNT=$((COUNT + 1))
  if [ $COUNT -ge $MAX_RETRIES ]; then
    echo "❌ PostgreSQL not reachable. Is Docker running?"
    echo "   Run: docker-compose up -d"
    exit 1
  fi
  echo "   Retrying... ($COUNT/$MAX_RETRIES)"
  sleep 2
done
echo "✅ PostgreSQL connected"

# ── Step 5: Run migrations ────────────────────────────────────────────────────
echo "→ Running database migrations..."
cd "$BACKEND_DIR"
alembic upgrade head
echo "✅ Database ready"

# ── Step 6: Start backend ─────────────────────────────────────────────────────
echo ""
echo "✅ InsightLenz backend starting on http://localhost:8000"
echo "   API docs: http://localhost:8000/docs"
echo ""

cd "$BACKEND_DIR"
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
