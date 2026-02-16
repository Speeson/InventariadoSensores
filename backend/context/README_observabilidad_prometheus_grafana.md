# Guia rapida: Prometheus y Grafana

## Que hace cada uno

- Prometheus:
  - Lee (scrapea) periodicamente las metricas de la API en `/metrics`.
  - Guarda series temporales (requests, errores, latencias).
  - Permite consultar con PromQL.

- Grafana:
  - Se conecta a Prometheus como fuente de datos.
  - Muestra dashboards y paneles para visualizar las metricas.
  - Sirve para seguimiento operativo (picos, errores, latencia).

## Funcionalidades que teneis ahora

- Metricas backend expuestas en `GET /metrics`.
- Scrape automatico de Prometheus al servicio `api:8000`.
- Datasource de Prometheus provisionado automaticamente en Grafana.
- Dashboard inicial `Inventory API Observability` con paneles de:
  - Requests/s
  - Errores 5xx/s
  - Latencia media
  - Total requests
  - Breakdown por ruta/metodo/status

## Donde esta en el repo

- Compose (servicios):
  - `backend/docker-compose.yml`

- Config Prometheus:
  - `backend/observability/prometheus/prometheus.yml`

- Provisioning Grafana:
  - `backend/observability/grafana/provisioning/datasources/datasource.yml`
  - `backend/observability/grafana/provisioning/dashboards/dashboard.yml`

- Dashboard JSON:
  - `backend/observability/grafana/dashboards/inventory-observability.json`

- Middleware y endpoint de metricas (API):
  - `backend/app/core/observability.py`
  - `backend/app/main.py` (registro middleware + ruta `/metrics`)

## URLs locales

- API metrics: `http://localhost:8000/metrics`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001` (admin/admin por defecto)

## Uso basico

1. Levantar stack:
   - `docker compose -f backend/docker-compose.yml up -d --build`
2. Revisar target en Prometheus:
   - `Status > Targets` y comprobar `inventory-api` en `UP`.
3. Abrir Grafana y dashboard:
   - `Dashboards > Inventory API Observability`.
4. Generar trafico (app Android o curl) y refrescar paneles.

## Nota importante

- Si reinicias el contenedor `api`, los contadores de metricas de la API se reinician (porque estan en memoria del proceso). Es comportamiento esperado.


## FORZAR ERRORES 5xx

docker compose -f backend/docker-compose.yml stop db

docker compose -f backend/docker-compose.yml start db