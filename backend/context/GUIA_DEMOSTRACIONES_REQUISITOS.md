# Guia de demostraciones de requisitos

Este documento se ira ampliando con todas las demostraciones de requisitos.

## 1) Observabilidad en Grafana: errores controlados (2xx, 4xx, 5xx)

### Objetivo
Demostrar que el dashboard refleja correctamente exito, errores de cliente y errores de servidor.

### Prerrequisitos
1. Levantar stack completo:
   - `docker compose -f backend/docker-compose.yml up -d`
2. Abrir Grafana:
   - `http://localhost:3001`
3. Verificar API:
   - `http://localhost:8000/health`

### Ejecucion automatizada
1. Lanzar script:
   - `powershell -ExecutionPolicy Bypass -File backend/scripts/demo_grafana_errors.ps1 -Include403`
2. Perfil corto recomendado (menos duracion, buen volumen):
   - `powershell -ExecutionPolicy Bypass -File backend/scripts/demo_grafana_errors.ps1 -Quick1m -Include403`
3. Esperar a que termine (el script baja y vuelve a subir DB automaticamente).

### Que debe verse en Grafana
1. `HTTP 2xx` sube en tramos de trafico normal.
2. `HTTP 4xx` sube durante la fase de errores controlados (401, 400 y opcional 403).
3. `HTTP 5xx` sube durante la ventana en la que DB esta parada.
4. `Success %` baja temporalmente y luego se recupera al restaurar DB.

### Notas
1. Si no existe `user@example.com`, el tramo 403 se omite y el script sigue.
2. Para visualizarlo mejor, usar rango temporal `Last 2 minutes`.

## 2) Observabilidad en Grafana: prueba de rendimiento (carga sostenida)

### Objetivo
Demostrar comportamiento de latencia, `Req/s` y volumen total bajo carga concurrente.

### Prerrequisitos
1. Stack arriba:
   - `docker compose -f backend/docker-compose.yml up -d`
2. Docker instalado (k6 se ejecuta en contenedor, no requiere instalacion local).

### Archivos de soporte
1. Script k6:
   - `backend/scripts/k6_grafana_load.js`
2. Lanzador PowerShell:
   - `backend/scripts/demo_grafana_load.ps1`

### Ejecucion automatizada
1. Carga base (recomendada para demo):
   - `powershell -ExecutionPolicy Bypass -File backend/scripts/demo_grafana_load.ps1 -VUs 20 -Duration 60s`
2. Carga media:
   - `powershell -ExecutionPolicy Bypass -File backend/scripts/demo_grafana_load.ps1 -VUs 40 -Duration 90s`
3. Carga alta:
   - `powershell -ExecutionPolicy Bypass -File backend/scripts/demo_grafana_load.ps1 -VUs 60 -Duration 120s`

### Que debe verse en Grafana
1. `Req/s` y `requests (2m)` suben de forma sostenida.
2. `Total Requests` crece rapidamente.
3. `Avg latency` sube segun intensidad.
4. `HTTP 2xx` debe dominar si el sistema esta estable.

### Interpretacion rapida para defensa
1. Si sube carga y latencia permanece controlada, hay buen margen operativo.
2. Si aparecen picos 4xx/5xx en carga alta, sirve para justificar limites y hardening.
3. El dashboard permite detectar degradacion en tiempo real.

## 3) Orden recomendado de demo

1. Ejecutar primero errores controlados:
   - `demo_grafana_errors.ps1`
2. Ejecutar despues rendimiento:
   - `demo_grafana_load.ps1`

Con este orden se muestran primero los tipos de error y despues la capacidad bajo carga.
