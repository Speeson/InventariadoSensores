#!/bin/sh
# entrypoint.sh

echo "Esperando a que la base de datos este lista..."
while ! nc -z "$DB_HOST" 5432; do
  sleep 0.1
done
echo "Base de datos lista!"

# Ejecuta migraciones automaticamente
echo "Ejecutando migraciones Alembic..."
alembic upgrade head

# Ejecuta el script de seed
echo "Ejecutando seed..."
python -m scripts.seed_db

# Arranca la API
echo "Iniciando Uvicorn..."
exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
