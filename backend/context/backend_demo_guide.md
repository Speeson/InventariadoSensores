# Mini guia de demo backend

Este guion sirve para comprobar rapido el backend sin esperar a los jobs.

## 1) Arranque y health
- Levantar servicios:
  - docker compose -f backend/docker-compose.yml up -d --build
- Health:
  - http://localhost:8000/health

## 2) Login y token
Semilla por defecto (seed_db.py):
- admin / manager / user con password: Pass123!

Ejemplo curl (form-data):
```
curl -X POST http://localhost:8000/auth/login ^
  -F "email=admin@demo.local" ^
  -F "password=Pass123!"
```
Guarda el access_token.

En PowerShell:
```
$token = (Invoke-RestMethod -Method Post -Uri "http://localhost:8000/auth/login" -Form @{ email="admin@demo.local"; password="Pass123!" }).access_token
$auth = @{ Authorization = "Bearer $token" }
```

## 3) Locations (para desplegable del front)
```
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/locations"
```

## 4) Crear evento (async con Celery)
```
$body = @{
  event_type = "SENSOR_IN"
  product_id = 1
  delta = 1
  source = "scan"
  location = "Oficina Central"
  idempotency_key = [guid]::NewGuid().ToString()
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Headers $auth -Uri "http://localhost:8000/events/" -ContentType "application/json" -Body $body
```
Luego comprobar que pasa a PROCESSED:
```
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/events/"
```

## 5) Forzar alerta de stock bajo (sin esperar 5 min)
Opcion A: subir threshold y ejecutar scan:
```
# Listar thresholds y coger un id
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/thresholds"

# Subir min_quantity de uno existente
$patch = @{ min_quantity = 999 } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Headers $auth -Uri "http://localhost:8000/thresholds/1" -ContentType "application/json" -Body $patch

# Ejecutar scan manual
docker compose -f backend/docker-compose.yml exec worker python -c "from app.tasks import scan_low_stock; print(scan_low_stock())"
```

## 6) Ver alertas
```
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/alerts"
```
En Mailtrap deberia aparecer el email si hay SMTP configurado.

## 7) Reportes
Top consumidos:
```
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/reports/top-consumed?date_from=2026-01-01&date_to=2026-02-01&limit=5"
```
Turnover:
```
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/reports/turnover?date_from=2026-01-01&date_to=2026-02-01&limit=5"
```

---
Notas:
- Ajusta ids/productos segun tu BD.
- `idempotency_key` debe ser unico por envio.
