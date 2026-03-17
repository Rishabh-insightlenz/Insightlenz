"""initial schema

Revision ID: 001
Revises:
Create Date: 2026-03-17

Tables:
- user_contexts    — identity and state of each InsightLenz user
- memories         — things InsightLenz remembers
- conversations    — full interaction history
- insights         — proactively generated insights
- app_usage        — Android attention tracking data
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = '001'
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # ── user_contexts ────────────────────────────────────────────────────────
    op.create_table(
        'user_contexts',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('name', sa.String(255), nullable=False),
        sa.Column('role', sa.String(255), nullable=False),

        # Identity
        sa.Column('core_values', postgresql.JSON, nullable=True),
        sa.Column('non_negotiables', postgresql.JSON, nullable=True),
        sa.Column('strengths', postgresql.JSON, nullable=True),
        sa.Column('blind_spots', postgresql.JSON, nullable=True),

        # Venture
        sa.Column('venture_description', sa.Text, nullable=True),
        sa.Column('venture_stage', sa.String(100), nullable=True),
        sa.Column('biggest_constraint', sa.Text, nullable=True),
        sa.Column('biggest_opportunity', sa.Text, nullable=True),

        # Current state
        sa.Column('current_priorities', postgresql.JSON, nullable=True),
        sa.Column('active_projects', postgresql.JSON, nullable=True),
        sa.Column('open_loops', postgresql.JSON, nullable=True),
        sa.Column('goals', postgresql.JSON, nullable=True),

        # Patterns
        sa.Column('known_reactive_triggers', postgresql.JSON, nullable=True),
        sa.Column('typical_distraction_apps', postgresql.JSON, nullable=True),
        sa.Column('peak_focus_times', postgresql.JSON, nullable=True),

        sa.Column('created_at', sa.DateTime, nullable=False, server_default=sa.func.now()),
        sa.Column('updated_at', sa.DateTime, nullable=False, server_default=sa.func.now()),
    )

    # ── memories ─────────────────────────────────────────────────────────────
    op.create_table(
        'memories',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('user_id', postgresql.UUID(as_uuid=True),
                  sa.ForeignKey('user_contexts.id', ondelete='CASCADE'), nullable=False),
        sa.Column('type', sa.String(50), nullable=False),
        sa.Column('content', sa.Text, nullable=False),
        sa.Column('context', sa.Text, nullable=True),
        sa.Column('tags', postgresql.JSON, nullable=True),
        sa.Column('created_at', sa.DateTime, nullable=False, server_default=sa.func.now()),
        sa.Column('expires_at', sa.DateTime, nullable=True),
    )
    op.create_index('ix_memories_user_id', 'memories', ['user_id'])
    op.create_index('ix_memories_type', 'memories', ['type'])

    # ── conversations ─────────────────────────────────────────────────────────
    op.create_table(
        'conversations',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('user_id', postgresql.UUID(as_uuid=True),
                  sa.ForeignKey('user_contexts.id', ondelete='CASCADE'), nullable=False),
        sa.Column('session_id', sa.String(255), nullable=False, server_default='default'),
        sa.Column('role', sa.String(50), nullable=False),
        sa.Column('content', sa.Text, nullable=False),
        sa.Column('source', sa.String(100), nullable=True, server_default='chat'),
        sa.Column('provider', sa.String(50), nullable=True),
        sa.Column('model', sa.String(100), nullable=True),
        sa.Column('tokens_used', sa.Integer, nullable=True, server_default='0'),
        sa.Column('created_at', sa.DateTime, nullable=False, server_default=sa.func.now()),
    )
    op.create_index('ix_conversations_user_id', 'conversations', ['user_id'])
    op.create_index('ix_conversations_session_id', 'conversations', ['session_id'])
    op.create_index('ix_conversations_created_at', 'conversations', ['created_at'])

    # ── insights ─────────────────────────────────────────────────────────────
    op.create_table(
        'insights',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('user_id', postgresql.UUID(as_uuid=True),
                  sa.ForeignKey('user_contexts.id', ondelete='CASCADE'), nullable=False),
        sa.Column('title', sa.String(500), nullable=False),
        sa.Column('body', sa.Text, nullable=False),
        sa.Column('trigger', sa.Text, nullable=False),
        sa.Column('urgency', sa.String(20), nullable=True, server_default='medium'),
        sa.Column('surfaced', sa.Boolean, nullable=False, server_default='false'),
        sa.Column('surfaced_at', sa.DateTime, nullable=True),
        sa.Column('created_at', sa.DateTime, nullable=False, server_default=sa.func.now()),
    )
    op.create_index('ix_insights_user_id', 'insights', ['user_id'])
    op.create_index('ix_insights_surfaced', 'insights', ['surfaced'])

    # ── app_usage ─────────────────────────────────────────────────────────────
    op.create_table(
        'app_usage',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('user_id', postgresql.UUID(as_uuid=True),
                  sa.ForeignKey('user_contexts.id', ondelete='CASCADE'), nullable=False),
        sa.Column('app_package', sa.String(255), nullable=False),
        sa.Column('app_name', sa.String(255), nullable=False),
        sa.Column('duration_seconds', sa.Integer, nullable=False),
        sa.Column('flagged_as_reactive', sa.Boolean, nullable=False, server_default='false'),
        sa.Column('started_at', sa.DateTime, nullable=False),
        sa.Column('ended_at', sa.DateTime, nullable=False),
    )
    op.create_index('ix_app_usage_user_id', 'app_usage', ['user_id'])
    op.create_index('ix_app_usage_started_at', 'app_usage', ['started_at'])
    op.create_index('ix_app_usage_flagged', 'app_usage', ['flagged_as_reactive'])


def downgrade() -> None:
    op.drop_table('app_usage')
    op.drop_table('insights')
    op.drop_table('conversations')
    op.drop_table('memories')
    op.drop_table('user_contexts')
