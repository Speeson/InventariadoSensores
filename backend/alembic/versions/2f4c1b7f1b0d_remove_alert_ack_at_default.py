"""remove default for alerts.ack_at

Revision ID: 2f4c1b7f1b0d
Revises: 9e75fa04121a
Create Date: 2026-01-29 16:10:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "2f4c1b7f1b0d"
down_revision: Union[str, Sequence[str], None] = "9e75fa04121a"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.alter_column("alerts", "ack_at", server_default=None)


def downgrade() -> None:
    op.alter_column("alerts", "ack_at", server_default=sa.text("now()"))
