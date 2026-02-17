import json
from pathlib import Path

import pytest
import schemathesis
from hypothesis import HealthCheck, settings
from schemathesis.checks import not_a_server_error
from schemathesis.core import NOT_SET
from schemathesis.specs.openapi.checks import (
    content_type_conformance,
    response_schema_conformance,
    status_code_conformance,
)

from app.core.security import hash_password
from app.models.enums import UserRole
from app.models.user import User


CONTRACT_PATH = Path(__file__).resolve().parents[1] / "openapi" / "openapi.json"


def _load_contract_schema() -> schemathesis.schemas.BaseSchema:
    if not CONTRACT_PATH.exists():
        raise AssertionError(
            "Missing OpenAPI snapshot at backend/openapi/openapi.json. "
            "Run: python backend/scripts/export_openapi.py"
        )
    with CONTRACT_PATH.open("r", encoding="utf-8-sig") as fp:
        raw_schema = json.load(fp)
    return schemathesis.openapi.from_dict(raw_schema)


schema = _load_contract_schema()


@pytest.fixture
def auth_headers(db, client):
    password = "Pass123!"
    user = db.query(User).filter(User.email == "admin@example.com").first()
    if user is None:
        user = User(
            email="admin@example.com",
            username="admin",
            password_hash=hash_password(password),
            role=UserRole.ADMIN,
        )
        db.add(user)
        db.commit()

    login_response = client.post(
        "/auth/login",
        data={"username": "admin@example.com", "password": password},
    )
    if login_response.status_code != 200:
        pytest.fail(f"Auth failed before contract test: {login_response.text}")
    token = login_response.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


@schema.parametrize()
@settings(max_examples=1, suppress_health_check=[HealthCheck.function_scoped_fixture])
def test_api_contract(case: schemathesis.Case, auth_headers, client):
    case.headers = {**(case.headers or {}), **auth_headers}

    def sanitize(value):
        if value is NOT_SET:
            return None
        if isinstance(value, dict):
            return {k: v for k, v in value.items() if v is not NOT_SET}
        return value

    response = client.request(
        method=case.method,
        url=case.path,
        headers=sanitize(case.headers),
        params=sanitize(case.query),
        json=sanitize(case.body),
    )

    case.validate_response(
        response,
        checks=(
            not_a_server_error,
            status_code_conformance,
            content_type_conformance,
            response_schema_conformance,
        ),
    )
