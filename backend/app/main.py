import os

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
import redis

from app.db.session import SessionLocal

from app.api.routes import auth, users, products, stocks, movements, events, alerts

app = FastAPI(title="Sistema Inventariado Sensores")

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

app.include_router(auth.router)
app.include_router(users.router)
app.include_router(products.router)
app.include_router(stocks.router)
app.include_router(movements.router)
app.include_router(events.router)
app.include_router(alerts.router)


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
