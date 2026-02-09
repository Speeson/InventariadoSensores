"""add alert_type to alerts

Revision ID: 9b8c7d6e5f40
Revises: 6a1d2c3e4f50
Create Date: 2026-02-09
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "9b8c7d6e5f40"
down_revision: Union[str, Sequence[str], None] = "6a1d2c3e4f50"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    alert_type_enum = sa.Enum(
        "LOW_STOCK",
        "OUT_OF_STOCK",
        "LARGE_MOVEMENT",
        "TRANSFER_COMPLETE",
        "IMPORT_ISSUES",
        name="alerttype",
    )
    alert_type_enum.create(op.get_bind(), checkfirst=True)
    op.add_column(
        "alerts",
        sa.Column(
            "alert_type",
            alert_type_enum,
            nullable=False,
            server_default="LOW_STOCK",
        ),
    )


def downgrade() -> None:
    op.drop_column("alerts", "alert_type")
    alert_type_enum = sa.Enum(
        "LOW_STOCK",
        "OUT_OF_STOCK",
        "LARGE_MOVEMENT",
        "TRANSFER_COMPLETE",
        "IMPORT_ISSUES",
        name="alerttype",
    )
    alert_type_enum.drop(op.get_bind(), checkfirst=True)
