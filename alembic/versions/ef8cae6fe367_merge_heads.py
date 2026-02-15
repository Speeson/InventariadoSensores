"""merge heads

Revision ID: ef8cae6fe367
Revises: 4f3c2a9b7e2c, f1c2d3e4b5a6
Create Date: 2026-02-06 15:20:39.085725

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'ef8cae6fe367'
down_revision: Union[str, Sequence[str], None] = ('4f3c2a9b7e2c', 'f1c2d3e4b5a6')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    pass


def downgrade() -> None:
    """Downgrade schema."""
    pass
