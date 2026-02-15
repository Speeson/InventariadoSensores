import os
import logging
import time
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from starlette.middleware.base import BaseHTTPMiddleware
from prometheus_client import Counter, Histogram, generate_latest  # Importación correcta
from sqlalchemy import text
import redis
import asyncio

from app.db.session import SessionLocal
from app.api.routes import auth, users, products, stocks, movements, events, alerts, categories, thresholds, reports, locations, imports
from app.api.routes import ws_alerts
from app.ws.alerts_ws import start_redis_listener

# Configuración de logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Métricas de Prometheus (definidas antes del middleware)
REQUESTS = Counter("http_requests_total", "Total number of HTTP requests", ["method", "endpoint", "status_code"])
RESPONSE_TIME = Histogram("http_request_duration_seconds", "Histogram of response durations (seconds)", ["method", "endpoint"])

# Middleware personalizado para registrar logs
class LoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        start_time = time.time()
        response = await call_next(request)
        process_time = time.time() - start_time
        logger.info(f"Request {request.method} {request.url} | Status: {response.status_code} | Process time: {process_time:.2f} sec")
        return response

app = FastAPI(title="Sistema Inventariado Sensores")

# Añadir el middleware de logging
app.add_middleware(LoggingMiddleware)

# Configuración de CORS (esto no debe tocarse)
cors_origins_env = os.getenv("CORS_ORIGINS", "")
allowed_origins = [o.strip() for o in cors_origins_env.split(",") if o.strip()] or [
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://10.0.2.2:3000",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)

# Middleware para métricas (ahora con las métricas definidas antes)
@app.middleware("http")
async def record_metrics(request: Request, call_next):
    method = request.method
    endpoint = request.url.path
    start_time = time.time()

    response = await call_next(request)

    # Registrar las métricas de tiempo de respuesta
    RESPONSE_TIME.labels(method=method, endpoint=endpoint).observe(time.time() - start_time)
    # Registrar las métricas de solicitudes
    REQUESTS.labels(method=method, endpoint=endpoint, status_code=response.status_code).inc()

    return response

# Endpoint de métricas
@app.get("/metrics")
async def metrics():
    return generate_latest()

# Rutas de la API
app.include_router(auth.router)
app.include_router(users.router)
app.include_router(products.router)
app.include_router(stocks.router)
app.include_router(movements.router)
app.include_router(events.router)
app.include_router(alerts.router)
app.include_router(categories.router)
app.include_router(thresholds.router)
app.include_router(reports.router)
app.include_router(locations.router)
app.include_router(imports.router)
app.include_router(ws_alerts.router)

# Endpoint de salud (health check)
@app.get("/health")
def health():
    details = {"api": "ok"}
    failures: list[str] = []

    try:
        with SessionLocal() as db:
            db.execute(text("SELECT 1"))
        details["db"] = "ok"
    except Exception as exc:
        details["db"] = "error"
        details["db_error"] = str(exc)
        failures.append("db")

    try:
        redis_url = os.getenv("REDIS_URL") or f"redis://{os.getenv('REDIS_HOST', 'redis')}:{os.getenv('REDIS_PORT', '6379')}/0"
        redis_client = redis.Redis.from_url(redis_url)
        redis_client.ping()
        details["redis"] = "ok"
    except Exception as exc:
        details["redis"] = "error"
        details["redis_error"] = str(exc)
        failures.append("redis")

    try:
        from app.celery_app import celery_app
        replies = celery_app.control.ping(timeout=1.0)
        worker_count = len(replies or [])
        details["celery"] = "ok" if worker_count > 0 else "error"
        details["celery_workers"] = worker_count
        if worker_count == 0:
            failures.append("celery")
    except Exception as exc:
        details["celery"] = "error"
        details["celery_error"] = str(exc)
        failures.append("celery")

    status = "ok" if not failures else "degraded"
    payload = {"status": status, "checks": details}
    status_code = 200 if not failures else 503
    return JSONResponse(payload, status_code=status_code)

# Función de inicio para el listener de WebSocket
@app.on_event("startup")
async def startup_ws_listener():
    asyncio.create_task(start_redis_listener())