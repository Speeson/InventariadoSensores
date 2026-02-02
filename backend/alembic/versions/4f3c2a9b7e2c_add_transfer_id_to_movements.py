"""add transfer_id to movements

Revision ID: 4f3c2a9b7e2c
Revises: d2b1c5f9a1a0
Create Date: 2026-02-02 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "4f3c2a9b7e2c"
down_revision: Union[str, Sequence[str], None] = "d2b1c5f9a1a0"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("movements", sa.Column("transfer_id", sa.String(length=36), nullable=True))
    op.create_index("ix_movements_transfer", "movements", ["transfer_id"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_movements_transfer", table_name="movements")
    op.drop_column("movements", "transfer_id")
