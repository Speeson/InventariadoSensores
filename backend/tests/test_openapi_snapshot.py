import json
from pathlib import Path

from app.main import app


CONTRACT_PATH = Path(__file__).resolve().parents[1] / "openapi" / "openapi.json"


def test_openapi_snapshot_exists_and_is_valid_json():
    assert CONTRACT_PATH.exists(), (
        "Missing OpenAPI snapshot at backend/openapi/openapi.json. "
        "Run: python backend/scripts/export_openapi.py"
    )
    with CONTRACT_PATH.open("r", encoding="utf-8-sig") as fp:
        payload = json.load(fp)
    assert isinstance(payload, dict)
    assert "openapi" in payload
    assert "paths" in payload


def test_openapi_snapshot_matches_runtime():
    with CONTRACT_PATH.open("r", encoding="utf-8-sig") as fp:
        snapshot = json.load(fp)
    runtime = app.openapi()
    assert runtime == snapshot, (
        "OpenAPI snapshot is out of date. "
        "Run: python backend/scripts/export_openapi.py and commit backend/openapi/openapi.json"
    )
