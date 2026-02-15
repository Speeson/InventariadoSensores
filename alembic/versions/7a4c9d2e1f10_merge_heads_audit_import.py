"""merge heads after audit/import enum update

Revision ID: 7a4c9d2e1f10
Revises: 3b2a1c9d7e10, 3f5d8b2a1e47
Create Date: 2026-02-15 01:40:00
"""

from typing import Sequence, Union

# revision identifiers, used by Alembic.
revision: str = "7a4c9d2e1f10"
down_revision: Union[str, Sequence[str], None] = ("3b2a1c9d7e10", "3f5d8b2a1e47")
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    pass


def downgrade() -> None:
    pass
