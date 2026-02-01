import os
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker


ROOT = Path(__file__).resolve().parents[1]
DB_PATH = (ROOT / "test.db").resolve()
SQLITE_URL = f"sqlite:///{DB_PATH.as_posix()}"
sys.path.append(str(ROOT))

os.environ["DATABASE_URL"] = SQLITE_URL
os.environ["JWT_SECRET"] = "test-secret"
os.environ["JWT_ALGORITHM"] = "HS256"

from app.db import session as db_session  # noqa: E402
from app.db.base import Base  # noqa: E402
import app.models  # noqa: E402
from app.main import app  # noqa: E402


db_url = os.environ["DATABASE_URL"]
connect_args = {}
if db_url.startswith("sqlite"):
    connect_args = {"check_same_thread": False}

engine = create_engine(
    db_url,
    connect_args=connect_args,
)
db_session.engine = engine
db_session.SessionLocal = sessionmaker(
    bind=engine,
    autoflush=False,
    autocommit=False,
)


@pytest.fixture()
def db():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    session = db_session.SessionLocal()
    try:
        yield session
    finally:
        session.close()


@pytest.fixture()
def client(db):
    with TestClient(app) as test_client:
        yield test_client
