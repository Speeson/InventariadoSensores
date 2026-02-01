"""adjust events defaults and processed_at

Revision ID: b7a2c9d4e611
Revises: 6f8657d8911f, c8ce14e1e339
Create Date: 2026-02-01 17:10:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "b7a2c9d4e611"
down_revision: Union[str, Sequence[str], None] = ("6f8657d8911f", "c8ce14e1e339")
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.alter_column(
        "events",
        "processed_at",
        existing_type=sa.DateTime(timezone=True),
        server_default=None,
        existing_nullable=True,
    )
    op.alter_column(
        "events",
        "event_status",
        existing_type=sa.Enum("PROCESSED", "PENDING", "ERROR", name="eventstatus"),
        server_default=sa.text("'PENDING'"),
        existing_nullable=False,
    )
    op.alter_column(
        "events",
        "retry_count",
        existing_type=sa.Integer(),
        server_default=sa.text("0"),
        existing_nullable=False,
    )


def downgrade() -> None:
    op.alter_column(
        "events",
        "retry_count",
        existing_type=sa.Integer(),
        server_default=None,
        existing_nullable=False,
    )
    op.alter_column(
        "events",
        "event_status",
        existing_type=sa.Enum("PROCESSED", "PENDING", "ERROR", name="eventstatus"),
        server_default=None,
        existing_nullable=False,
    )
    op.alter_column(
        "events",
        "processed_at",
        existing_type=sa.DateTime(timezone=True),
        server_default=sa.text("now()"),
        existing_nullable=True,
    )
