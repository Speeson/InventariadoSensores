import os

from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi
from fastapi.responses import JSONResponse, PlainTextResponse
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
import redis

from app.db.session import SessionLocal
from app.core.observability import MetricsRegistry, ObservabilityMiddleware

from app.api.routes import auth, users, products, stocks, movements, events, alerts, categories, thresholds, reports, locations, imports, audit
from app.api.routes import ws_alerts
from app.ws.alerts_ws import start_redis_listener
import asyncio

app = FastAPI(title="Sistema Inventariado Sensores")
metrics_registry = MetricsRegistry()
ws_listener_task: asyncio.Task | None = None
COMMON_ERROR_RESPONSES = {
    "400": "Bad Request",
    "401": "Unauthorized",
    "403": "Forbidden",
    "404": "Not Found",
    "409": "Conflict",
    "503": "Service Unavailable",
}


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
app.add_middleware(
    ObservabilityMiddleware,
    registry=metrics_registry,
    exclude_paths={"/metrics", "/health"},
)

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
app.include_router(audit.router)
app.include_router(ws_alerts.router)



def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema

    openapi_schema = get_openapi(
        title=app.title,
        version=app.version,
        description=app.description,
        routes=app.routes,
    )
    components = openapi_schema.setdefault("components", {})
    schemas = components.setdefault("schemas", {})
    schemas.setdefault(
        "ErrorResponse",
        {
            "title": "ErrorResponse",
            "type": "object",
            "properties": {
                "detail": {
                    "anyOf": [
                        {"type": "string"},
                        {"type": "array", "items": {"type": "object"}},
                        {"type": "object"},
                    ]
                }
            },
            "required": ["detail"],
        },
    )

    for path, path_item in openapi_schema.get("paths", {}).items():
        for method, operation in path_item.items():
            if method not in {"get", "post", "put", "patch", "delete", "options", "head"}:
                continue
            responses = operation.setdefault("responses", {})
            # /health can legitimately return 503 with a custom payload {"status","checks"}.
            # Keep that response schema aligned with runtime output instead of generic ErrorResponse.
            if path == "/health":
                responses["503"] = {
                    "description": "Service Unavailable",
                    "content": {
                        "application/json": {
                            "schema": {
                                "type": "object",
                                "required": ["status", "checks"],
                                "properties": {
                                    "status": {"type": "string"},
                                    "checks": {
                                        "type": "object",
                                        "additionalProperties": {},
                                    },
                                },
                            }
                        }
                    },
                }
            for status_code, description in COMMON_ERROR_RESPONSES.items():
                if path == "/health" and status_code == "503":
                    continue
                responses.setdefault(
                    status_code,
                    {
                        "description": description,
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/ErrorResponse"}
                            }
                        },
                    },
                )

    app.openapi_schema = openapi_schema
    return app.openapi_schema


app.openapi = custom_openapi


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
    except Exception as exc:
        details["celery"] = "error"
        details["celery_error"] = str(exc)
        # Celery health is reported for observability, but does not mark API as unavailable.

    status = "ok" if not failures else "degraded"
    payload = {"status": status, "checks": details}
    status_code = 200 if not failures else 503
    return JSONResponse(payload, status_code=status_code)


@app.get("/metrics", response_class=PlainTextResponse)
def metrics():
    return PlainTextResponse(
        metrics_registry.render_prometheus(),
        media_type="text/plain; version=0.0.4; charset=utf-8",
    )


@app.on_event("startup")
async def startup_ws_listener():
    global ws_listener_task
    if os.getenv("DISABLE_WS_LISTENER", "0") == "1":
        return
    ws_listener_task = asyncio.create_task(start_redis_listener())


@app.on_event("shutdown")
async def shutdown_ws_listener():
    global ws_listener_task
    if ws_listener_task is None:
        return
    ws_listener_task.cancel()
    try:
        await ws_listener_task
    except asyncio.CancelledError:
        pass
    ws_listener_task = None
