"""add import tables

Revision ID: 3b2a1c9d7e10
Revises: 4f3c2a9b7e2c
Create Date: 2026-02-08
"""

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "3b2a1c9d7e10"
down_revision = "4f3c2a9b7e2c"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "import_batches",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("kind", sa.String(length=20), nullable=False),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("dry_run", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("total_rows", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("ok_rows", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("error_rows", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("review_rows", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_import_batches_user", "import_batches", ["user_id"])
    op.create_index("ix_import_batches_kind", "import_batches", ["kind"])
    op.create_index("ix_import_batches_created", "import_batches", ["created_at"])

    op.create_table(
        "import_errors",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("batch_id", sa.Integer(), sa.ForeignKey("import_batches.id"), nullable=False),
        sa.Column("row_number", sa.Integer(), nullable=False),
        sa.Column("error_code", sa.String(length=50), nullable=False),
        sa.Column("message", sa.String(length=255), nullable=False),
        sa.Column("payload", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_import_errors_batch", "import_errors", ["batch_id"])
    op.create_index("ix_import_errors_created", "import_errors", ["created_at"])

    op.create_table(
        "import_reviews",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("batch_id", sa.Integer(), sa.ForeignKey("import_batches.id"), nullable=False),
        sa.Column("row_number", sa.Integer(), nullable=False),
        sa.Column("reason", sa.String(length=255), nullable=False),
        sa.Column("payload", sa.JSON(), nullable=False),
        sa.Column("suggestions", sa.JSON(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_import_reviews_batch", "import_reviews", ["batch_id"])
    op.create_index("ix_import_reviews_created", "import_reviews", ["created_at"])


def downgrade() -> None:
    op.drop_index("ix_import_reviews_created", table_name="import_reviews")
    op.drop_index("ix_import_reviews_batch", table_name="import_reviews")
    op.drop_table("import_reviews")

    op.drop_index("ix_import_errors_created", table_name="import_errors")
    op.drop_index("ix_import_errors_batch", table_name="import_errors")
    op.drop_table("import_errors")

    op.drop_index("ix_import_batches_created", table_name="import_batches")
    op.drop_index("ix_import_batches_kind", table_name="import_batches")
    op.drop_index("ix_import_batches_user", table_name="import_batches")
    op.drop_table("import_batches")
