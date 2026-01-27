FROM python:3.13-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*

# Copia la app
COPY . .

RUN chmod +x entrypoint.sh

EXPOSE 8000
ENTRYPOINT ["/app/entrypoint.sh"]
