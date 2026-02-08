"""merge import heads

Revision ID: 6a1d2c3e4f50
Revises: ef8cae6fe367, 3b2a1c9d7e10
Create Date: 2026-02-08
"""

from typing import Sequence, Union

from alembic import op  # noqa: F401

# revision identifiers, used by Alembic.
revision: str = "6a1d2c3e4f50"
down_revision: Union[str, Sequence[str], None] = ("ef8cae6fe367", "3b2a1c9d7e10")
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    pass


def downgrade() -> None:
    """Downgrade schema."""
    pass
