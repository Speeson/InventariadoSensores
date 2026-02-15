"""merge heads

Revision ID: 6f8657d8911f
Revises: 2f4c1b7f1b0d, c51f9fca7313
Create Date: 2026-01-29

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "6f8657d8911f"
down_revision: Union[str, Sequence[str], None] = ("2f4c1b7f1b0d", "c51f9fca7313")
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    pass


def downgrade() -> None:
    pass
