def test_health(client):
    response = client.get("/health")
    assert response.status_code in {200, 503}
    payload = response.json()
    assert payload["status"] in {"ok", "degraded"}
    assert payload["checks"]["api"] == "ok"
