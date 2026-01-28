import os

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
)

celery_app.autodiscover_tasks(["app"])
