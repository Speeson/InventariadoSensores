"""add user_id to events for audit

Revision ID: e4b7c2d1a9f0
Revises: 7a4c9d2e1f10, f1c2d3e4b5a6
Create Date: 2026-03-26 10:30:00
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "e4b7c2d1a9f0"
down_revision: Union[str, Sequence[str], None] = ("7a4c9d2e1f10", "f1c2d3e4b5a6")
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("events", sa.Column("user_id", sa.Integer(), nullable=True))
    op.create_foreign_key("fk_events_user_id_users", "events", "users", ["user_id"], ["id"])
    op.create_index("ix_events_user", "events", ["user_id"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_events_user", table_name="events")
    op.drop_constraint("fk_events_user_id_users", "events", type_="foreignkey")
    op.drop_column("events", "user_id")
