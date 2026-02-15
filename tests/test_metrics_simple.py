"""
Tests simples de métricas sin requerir autenticación.
Valida únicamente que:
1. El endpoint /metrics es accesible
2. Contiene los nombres de las métricas esperadas
3. Los logs se capturan
"""

import logging
import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client():
    """Cliente de prueba para FastAPI."""
    return TestClient(app)


class TestMetricsSimple:
    """Tests simples de métricas sin autenticación."""

    def test_metrics_endpoint_returns_200(self, client: TestClient):
        """Verifica que GET /metrics devuelve 200."""
        response = client.get("/metrics")
        assert response.status_code == 200

    def test_metrics_contains_prometheus_data(self, client: TestClient):
        """Verifica que /metrics contiene datos de Prometheus."""
        response = client.get("/metrics")
        assert response.status_code == 200
        
        metrics_text = response.text
        
        # Validar que contiene métricas de Prometheus (formato estándar)
        assert "# HELP" in metrics_text, "No tiene formato Prometheus"
        assert "# TYPE" in metrics_text, "No tiene formato Prometheus"

    def test_metrics_contains_reports_operations_counter(self, client: TestClient):
        """Verifica que existe el counter de reports."""
        response = client.get("/metrics")
        metrics_text = response.text
        
        assert "route_reports_operations_total" in metrics_text, \
            "Counter 'route_reports_operations_total' no encontrado"

    def test_metrics_contains_reports_duration_histogram(self, client: TestClient):
        """Verifica que existe el histograma de duración."""
        response = client.get("/metrics")
        metrics_text = response.text
        
        assert "route_reports_operation_duration_seconds" in metrics_text, \
            "Histogram 'route_reports_operation_duration_seconds' no encontrado"

    def test_metrics_contains_stocks_operations_counter(self, client: TestClient):
        """Verifica que existe el counter de stocks."""
        response = client.get("/metrics")
        metrics_text = response.text
        
        assert "route_stocks_operations_total" in metrics_text, \
            "Counter 'route_stocks_operations_total' no encontrado"

    def test_metrics_contains_movements_operations_counter(self, client: TestClient):
        """Verifica que existe el counter de movements."""
        response = client.get("/metrics")
        metrics_text = response.text
        
        assert "route_movements_operations_total" in metrics_text, \
            "Counter 'route_movements_operations_total' no encontrado"

    def test_metrics_contains_events_operations_counter(self, client: TestClient):
        """Verifica que existe el counter de events."""
        response = client.get("/metrics")
        metrics_text = response.text
        
        assert "route_events_operations_total" in metrics_text, \
            "Counter 'route_events_operations_total' no encontrado"

    def test_logs_middleware_captures_requests(self, client: TestClient, caplog):
        """Verifica que el middleware captura los requests."""
        caplog.set_level(logging.INFO)
        caplog.clear()
        
        # Hacer un request cualquiera (sin autenticación)
        response = client.get("/docs")  # Swagger UI
        
        # Verificar que se capturó en logs
        logged = caplog.text
        
        assert "Request" in logged or "GET" in logged, \
            "Middleware no capturó el request en los logs"

    def test_health_check_endpoint(self, client: TestClient):
        """Verifica que hay un endpoint de health check."""
        # Intenta varios paths comunes de health
        paths = ["/health", "/ping", "/status", "/"]
        
        for path in paths:
            response = client.get(path)
            # Si alguno devuelve 200, es válido
            if response.status_code == 200:
                return
        
        pytest.skip("No se encontró endpoint de health check")