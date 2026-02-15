"""add IMPORT to entity enum

Revision ID: 3f5d8b2a1e47
Revises: bc1a2d3e4f50
Create Date: 2026-02-15 00:00:00
"""

from typing import Sequence, Union

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "3f5d8b2a1e47"
down_revision: Union[str, Sequence[str], None] = "bc1a2d3e4f50"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    bind = op.get_bind()
    if bind.dialect.name == "postgresql":
        op.execute("ALTER TYPE entity ADD VALUE IF NOT EXISTS 'IMPORT'")


def downgrade() -> None:
    # Enum value removal is not supported in PostgreSQL.
    pass
