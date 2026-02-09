"""Make alert stock_id nullable.

Revision ID: a3b4c5d6e7f8
Revises: 9b8c7d6e5f40
Create Date: 2026-02-09 22:05:00.000000
"""

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "a3b4c5d6e7f8"
down_revision = "9b8c7d6e5f40"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.alter_column("alerts", "stock_id", existing_type=sa.Integer(), nullable=True)


def downgrade() -> None:
    op.alter_column("alerts", "stock_id", existing_type=sa.Integer(), nullable=False)
