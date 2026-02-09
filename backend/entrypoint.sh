#!/bin/sh
# entrypoint.sh

set -e

ROLE="${APP_ROLE:-api}"

wait_for_service() {
  host="$1"
  port="$2"
  name="$3"
  echo "Esperando a que $name este listo..."
  while ! nc -z "$host" "$port"; do
    sleep 0.2
  done
  echo "$name listo!"
}

if [ -n "$DB_HOST" ]; then
  wait_for_service "$DB_HOST" "${DB_PORT:-5432}" "la base de datos"
fi

if [ -n "$REDIS_HOST" ]; then
  wait_for_service "$REDIS_HOST" "${REDIS_PORT:-6379}" "Redis"
fi

if [ "$ROLE" = "api" ]; then
  echo "Ejecutando migraciones Alembic..."
  alembic upgrade head

  if [ "${RUN_SEED:-1}" = "1" ]; then
    echo "Ejecutando seed..."
    python -m scripts.seed_db
  fi

  echo "Iniciando Uvicorn..."
  if [ "${UVICORN_RELOAD:-0}" = "1" ]; then
    exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
  else
    exec uvicorn app.main:app --host 0.0.0.0 --port 8000
  fi
elif [ "$ROLE" = "worker" ]; then
  echo "Iniciando Celery worker..."
  WORKER_OPTS=""
  if [ -n "$CELERY_WORKER_CONCURRENCY" ]; then
    WORKER_OPTS="$WORKER_OPTS --concurrency=$CELERY_WORKER_CONCURRENCY"
  fi
  exec celery -A app.celery_app worker -l "${CELERY_LOG_LEVEL:-info}" $WORKER_OPTS
elif [ "$ROLE" = "beat" ]; then
  echo "Iniciando Celery beat..."
  exec celery -A app.celery_app beat -l "${CELERY_LOG_LEVEL:-info}"
else
  echo "APP_ROLE invalido: $ROLE"
  exit 1
fi
