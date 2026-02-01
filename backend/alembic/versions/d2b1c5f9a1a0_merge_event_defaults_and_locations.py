"""merge event defaults and location_id changes

Revision ID: d2b1c5f9a1a0
Revises: b7a2c9d4e611, 3373e6b81640
Create Date: 2026-02-01 17:20:00.000000

"""
from typing import Sequence, Union


# revision identifiers, used by Alembic.
revision: str = "d2b1c5f9a1a0"
down_revision: Union[str, Sequence[str], None] = ("b7a2c9d4e611", "3373e6b81640")
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    pass


def downgrade() -> None:
    pass
