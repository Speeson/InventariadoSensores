#!/bin/sh
# entrypoint.sh

echo "Esperando a que la base de datos esté lista..."
while ! nc -z $DB_HOST 5432; do
  sleep 0.1
done
echo "Base de datos lista!"

# Ejecuta migraciones automáticamente
echo "Ejecutando migraciones Alembic..."
alembic upgrade head

# Arranca la API
# echo "Iniciando Uvicorn..."
# exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
