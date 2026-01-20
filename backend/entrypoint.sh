#!/bin/sh
# entrypoint.sh

# Espera a que la base de datos esté disponible
echo "Esperando a que la base de datos esté lista..."
while ! pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER"; do
  sleep 1
done

# Corre migraciones
alembic upgrade head

# Arranca la API
uvicorn app.main:app --host 0.0.0.0 --port 8000