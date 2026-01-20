import os

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql+psycopg2://inventory_user:inventory_pass@localhost:5432/inventory_db"
)
