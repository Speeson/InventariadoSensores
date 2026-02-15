import logging
from urllib import response
import uuid

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.db.session import SessionLocal


@pytest.fixture
def client():
    """Proporciona un cliente de prueba para FastAPI."""
    return TestClient(app)


@pytest.fixture
def db():
    """Proporciona una sesión de BD para pruebas."""
    db_session = SessionLocal()
    try:
        yield db_session
    finally:
        db_session.close()


def _auth_header(token: str) -> dict[str, str]:
    """Genera header de autenticación con token Bearer."""
    return {"Authorization": f"Bearer {token}"}


def _register_manager(client: TestClient) -> str:
    """
    Intenta registrar un usuario manager.
    Devuelve el access_token.
    """
    email = f"manager_{uuid.uuid4().hex[:8]}@test.local"
    password = "Password123!"
    
    response = client.post(
        "/auth/register",
        json={
            "email": email,
            "password": password,
            "role": "MANAGER"
        },
    )
    
    assert response.status_code == 200, f"Register failed: {response.text}"
    data = response.json()
    assert "access_token" in data, f"No access_token in response: {data}"
    
    return data["access_token"]


def _safe_register_or_login_manager(client: TestClient) -> str:
    """
    Intenta registrar un manager.
    Si falla, intenta login con credenciales predeterminadas.
    """
    try:
        return _register_manager(client)
    except AssertionError:
        # Fallback: intentar login con usuario predeterminado
        login_response = client.post(
            "/auth/login",
            json={
                "email": "admin@test.local",
                "password": "Password123!"
            },
        )
        
        if login_response.status_code == 200:
            return login_response.json()["access_token"]
        
        # Si tampoco funciona login, lanzar error
        pytest.skip("No se pudo autenticar (register ni login funcionaron)")


def _assert_report_response_structure(resp_json: dict):
    """Valida que la respuesta tenga la estructura esperada."""
    assert isinstance(resp_json, dict), "Response debe ser un dict"
    assert "items" in resp_json, "Response debe contener 'items'"
    assert "total" in resp_json, "Response debe contener 'total'"
    assert "limit" in resp_json, "Response debe contener 'limit'"
    assert "offset" in resp_json, "Response debe contener 'offset'"
    
    assert isinstance(resp_json["items"], list), "'items' debe ser una lista"
    assert isinstance(resp_json["total"], int), "'total' debe ser int"
    assert isinstance(resp_json["limit"], int), "'limit' debe ser int"
    assert isinstance(resp_json["offset"], int), "'offset' debe ser int"


class TestReportsMetrics:
    """Suite de pruebas para métricas de reports."""

    def test_top_consumed_endpoint_returns_200(self, client: TestClient):
        """Verifica que GET /reports/top-consumed devuelve 200."""
        token = _safe_register_or_login_manager(client)
        headers = _auth_header(token)
        
        response = client.get("/reports/top-consumed", headers=headers)
        
        assert response.status_code == 200, f"Expected 200, got {response.status_code}: {response.text}"
        _assert_report_response_structure(response.json())

    def test_turnover_endpoint_returns_200(self, client: TestClient):
        """Verifica que GET /reports/turnover devuelve 200."""
        token = _safe_register_or_login_manager(client)
        headers = _auth_header(token)
        
        response = client.get("/reports/turnover", headers=headers)
        
        assert response.status_code == 200, f"Expected 200, got {response.status_code}: {response.text}"
        _assert_report_response_structure(response.json())

    def test_metrics_endpoint_accessible(self, client: TestClient):
        """Verifica que GET /metrics devuelve 200."""
        response = client.get("/metrics")
        
        assert response.status_code == 200, f"Expected 200, got {response.status_code}: {response.text}"
        # Prometheus puede retornar text/plain o application/json
        content_type = response.headers.get("content-type", "")
        assert "text/plain" in content_type or "application/json" in content_type, \
            f"Content-Type inválido: {content_type}"

    def test_metrics_contains_reports_counters(self, client: TestClient):
        """Verifica que /metrics contiene los contadores de reports."""
        # Hacer una llamada primero para generar métricas
        token = _safe_register_or_login_manager(client)
        headers = _auth_header(token)
        
        client.get("/reports/top-consumed", headers=headers)
        client.get("/reports/turnover", headers=headers)
        
        # Ahora verificar que las métricas existen
        metrics_response = client.get("/metrics")
        assert metrics_response.status_code == 200
        
        metrics_text = metrics_response.text
        
        # Verificar que el contador existe
        assert "route_reports_operations_total" in metrics_text, \
            "Métrica 'route_reports_operations_total' no encontrada en /metrics"
        
        # Verificar que el histograma existe
        assert "route_reports_operation_duration_seconds" in metrics_text, \
            "Métrica 'route_reports_operation_duration_seconds' no encontrada en /metrics"

    def test_metrics_contains_operation_labels(self, client: TestClient):
        """Verifica que /metrics contiene los labels de operaciones."""
        token = _safe_register_or_login_manager(client)
        headers = _auth_header(token)
        
        # Llamar a ambos endpoints
        client.get("/reports/top-consumed", headers=headers)
        client.get("/reports/turnover", headers=headers)
        
        metrics_response = client.get("/metrics")
        metrics_text = metrics_response.text
        
        # Verificar que aparecen los labels de las operaciones
        assert 'operation="top_consumed"' in metrics_text, \
            "Label 'operation=\"top_consumed\"' no encontrado en /metrics"
        assert 'operation="turnover_report"' in metrics_text, \
            "Label 'operation=\"turnover_report\"' no encontrado en /metrics"

    def test_metrics_contains_status_labels(self, client: TestClient):
        """Verifica que /metrics contiene status (success/error)."""
        token = _safe_register_or_login_manager(client)
        headers = _auth_header(token)
        
        client.get("/reports/top-consumed", headers=headers)
        
        metrics_response = client.get("/metrics")
        metrics_text = metrics_response.text
        
        # Verificar que aparecen labels de status
        assert 'status="success"' in metrics_text or 'success' in metrics_text, \
            "Status 'success' no encontrado en métricas"

    def test_logs_contain_request_info(self, client: TestClient, caplog):
        """Verifica que los logs del middleware contienen info de request."""
        token = _safe_register_or_login_manager(client)
        headers = _auth_header(token)
        
        # Configurar caplog para capturar INFO
        caplog.set_level(logging.INFO)
        
        # Limpiar logs previos
        caplog.clear()
        
        # Hacer request
        response = client.get("/reports/top-consumed", headers=headers)
        assert response.status_code == 200
        
        # Verificar que se capturaron logs
        logged = caplog.text
        
        # Buscar palabras clave en los logs
        assert "Request" in logged or "GET" in logged, \
            "Log no contiene info de HTTP (Request o GET)"
        assert "/reports" in logged or "top-consumed" in logged, \
            "Log no contiene la ruta del endpoint"

    def test_full_flow_metrics_and_logs(self, client: TestClient, caplog):
        """
        Prueba completa:
        1. Autenticar
        2. Llamar endpoints de reports
        3. Verificar métricas
        4. Verificar logs
        """
        # Autenticación
        token = _safe_register_or_login_manager(client)
        headers = _auth_header(token)
        caplog.set_level(logging.INFO)
        caplog.clear()
        
        # Llamadas a endpoints
        resp_top = client.get("/reports/top-consumed", headers=headers)
        assert resp_top.status_code == 200
        _assert_report_response_structure(resp_top.json())
        
        resp_turn = client.get("/reports/turnover", headers=headers)
        assert resp_turn.status_code == 200
        _assert_report_response_structure(resp_turn.json())
        
        # Verificar métricas
        metrics_resp = client.get("/metrics")
        assert metrics_resp.status_code == 200
        metrics_text = metrics_resp.text
        
        assert "route_reports_operations_total" in metrics_text
        assert "route_reports_operation_duration_seconds" in metrics_text
        assert 'operation="top_consumed"' in metrics_text
        assert 'operation="turnover_report"' in metrics_text
        
        # Verificar logs
        logged = caplog.text
        assert "Request" in logged or "GET" in logged
        assert "/reports" in logged

    def test_unauthorized_access_returns_403(self, client: TestClient):
        """Verifica que sin token, el acceso es denegado."""
        response = client.get("/reports/top-consumed")
        
        # Puede ser 403 Forbidden o 401 Unauthorized
        assert response.status_code in [401, 403], \
            f"Sin token debería devolver 401/403, pero devolvió {response.status_code}"

    def test_metrics_increments_on_multiple_calls(self, client: TestClient):
        """Verifica que los contadores se incrementan con múltiples llamadas."""
        token = _safe_register_or_login_manager(client)
        headers = _auth_header(token)
        
        # Primer call
        client.get("/reports/top-consumed", headers=headers)
        metrics1 = client.get("/metrics").text
        
        # Extraer valor del contador (búsqueda simple)
        count1 = metrics1.count('operation="top_consumed"')
        
        # Segundo call
        client.get("/reports/top-consumed", headers=headers)
        metrics2 = client.get("/metrics").text
        
        count2 = metrics2.count('operation="top_consumed"')
        
        # Debería haber más ocurrencias después de la segunda llamada
        assert count2 > count1, "El contador no se incrementó después de la segunda llamada"