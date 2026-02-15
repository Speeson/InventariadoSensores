import os
from datetime import timedelta

from celery import Celery


def _get_env(name: str, default: str) -> str:
    value = os.getenv(name)
    return value if value else default


broker_url = _get_env(
    "CELERY_BROKER_URL",
    _get_env("REDIS_URL", "redis://redis:6379/0"),
)
result_backend = _get_env("CELERY_RESULT_BACKEND", broker_url)

celery_app = Celery("app", broker=broker_url, backend=result_backend)
celery_app.conf.update(
    timezone=_get_env("CELERY_TIMEZONE", "UTC"),
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    task_track_started=True,
    broker_connection_retry_on_startup=True,
    beat_schedule={
        "scan-low-stock": {
            "task": "app.tasks.scan_low_stock",
            "schedule": timedelta(minutes=int(_get_env("LOW_STOCK_SCAN_MINUTES", "5"))),
        },
        "requeue-pending-events": {
            "task": "app.tasks.requeue_pending_events",
            "schedule": timedelta(minutes=int(_get_env("PENDING_EVENTS_REQUEUE_MINUTES", "2"))),
        },
    },
)

celery_app.autodiscover_tasks(["app"])
