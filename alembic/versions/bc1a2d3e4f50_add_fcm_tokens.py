"""Add FCM tokens table.

Revision ID: bc1a2d3e4f50
Revises: a3b4c5d6e7f8
Create Date: 2026-02-10 01:20:00.000000
"""

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "bc1a2d3e4f50"
down_revision = "a3b4c5d6e7f8"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "fcm_tokens",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("token", sa.String(length=255), nullable=False, unique=True),
        sa.Column("device_id", sa.String(length=100), nullable=True),
        sa.Column("platform", sa.String(length=30), nullable=False, server_default="android"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=True),
    )
    op.create_index("ix_fcm_tokens_user", "fcm_tokens", ["user_id"])
    op.create_index("ix_fcm_tokens_device", "fcm_tokens", ["device_id"])


def downgrade() -> None:
    op.drop_index("ix_fcm_tokens_device", table_name="fcm_tokens")
    op.drop_index("ix_fcm_tokens_user", table_name="fcm_tokens")
    op.drop_table("fcm_tokens")
