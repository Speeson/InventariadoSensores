"""add delta to movements

Revision ID: f1c2d3e4b5a6
Revises: d2b1c5f9a1a0
Create Date: 2026-02-05 21:10:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "f1c2d3e4b5a6"
down_revision: Union[str, Sequence[str], None] = "d2b1c5f9a1a0"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("movements", sa.Column("delta", sa.Integer(), nullable=True))
    op.execute(
        "UPDATE movements SET delta = CASE WHEN movement_type = 'OUT' THEN -quantity ELSE quantity END"
    )
    op.alter_column("movements", "delta", nullable=False)


def downgrade() -> None:
    op.drop_column("movements", "delta")
