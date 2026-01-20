# Usa Python 3.13 slim
FROM python:3.13-slim

# Directorio de trabajo
WORKDIR /app

# Copia requirements e instala
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Instalar netcat para el entrypoint
RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*

# Copia la app
COPY app ./app
COPY alembic ./alembic
COPY entrypoint.sh ./entrypoint.sh
COPY alembic.ini ./alembic.ini

# Permisos para el entrypoint
RUN chmod +x entrypoint.sh

# CMD será nuestro entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]