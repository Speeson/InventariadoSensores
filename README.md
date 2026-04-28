# 📦 IoTrack - Sistema de Gestión de Inventario con Sensores IoT (simulados)

Proyecto desarrollado íntegramente por **Esteban Garcés Pérez**, alumno de **2º DAM** en **Pro2FP**.  
**Actividad 4:** *Inventario con sensores IoT simulados*.

---

## 🧩 Descripción

Sistema para **gestionar inventario y stock** de productos, registrando movimientos por:
- **Escaneo de códigos de barras** desde la app Android (cámara).
- **Impresión de etiquetas** desde la propia app Android.
- **Eventos de sensores IoT simulados** (entradas/salidas automáticas).
- **Movimientos manuales** (entrada/salida/ajuste) desde la API.

El objetivo es entregar una solución **segura, documentada y desplegable en contenedores**, con una base preparada para extender:
- consumidor de eventos en background,
- alertas de stock bajo,
- reportes,
- importación CSV,
- auditoría de cambios.

La aplicación no solo escanea códigos de barras, sino que también permite **imprimir etiquetas** mediante la integración directa del **SDK oficial de Niimbot** para etiquetadoras Bluetooth, dentro del propio flujo de la app.

---

## 🏗️ Arquitectura general (MVC)

- **Vista (Frontend):** Android (Kotlin)
- **Controlador (Backend):** API REST (FastAPI)
- **Modelo (Datos):** PostgreSQL + SQLAlchemy (migraciones Alembic)

Comunicación: **JSON sobre HTTP** y autenticación **JWT Bearer**.

---

## 🛠️ Stack tecnológico

### Backend (Python)
- FastAPI (ASGI) — Sistema Inventariado Sensores
- SQLAlchemy + Alembic
- PostgreSQL
- JWT (OAuth2PasswordRequestForm) + hash de contraseñas
- Docker + Docker Compose


### Android
- Kotlin + AndroidX
- Retrofit + OkHttp
- CameraX + ML Kit (barcode scanning)
- Persistencia local con Room sobre SQLite (`app_cache.db`) para caché y soporte offline
- Sesión persistente (token)
- Integración del SDK oficial de Niimbot (impresión Bluetooth B1)

**SDK (según Gradle detectado):**
- compileSdk: 34
- minSdk: 24
- targetSdk: 34
- applicationId: com.example.inventoryapp

---
## ✅ Funcionalidades implementadas (estado actual)

### Seguridad / Auth
- Registro y login con JWT:
  - `POST /auth/register`
  - `POST /auth/login`
- Roles: `USER`, `MANAGER`, `ADMIN`
- Endpoints protegidos con `Authorization: Bearer <token>`

### Roles y permisos (resumen)
| Endpoint | USER | MANAGER | ADMIN |
|---|:---:|:---:|:---:|
| `GET /products`, `GET /products/{id}` | ✅ | ✅ | ✅ |
| `POST /products`, `PATCH /products/{id}`, `DELETE /products/{id}` | ❌ | ✅ | ✅ |
| `GET /stocks`, `GET /stocks/{id}` | ✅ | ✅ | ✅ |
| `POST /stocks`, `PATCH /stocks/{id}` | ❌ | ✅ | ✅ |
| `GET /movements` | ✅ | ✅ | ✅ |
| `POST /movements/in`, `/out`, `/adjust` | ❌ | ✅ | ✅ |
| `GET /events`, `POST /events` | ✅ | ✅ | ✅ |
| `GET /users/me` | ✅ | ✅ | ✅ |
| `GET /users/admin-only` | ❌ | ❌ | ✅ |
| `GET/POST/PATCH/DELETE /thresholds` | ❌ | ✅ | ✅ |

Notas:
- El registro fuerza el rol `USER`. Roles altos se asignan manualmente.

### Inventario
- **Productos** (con filtros y paginación):
  - `GET /products?sku&name&barcode&category_id&active&limit&offset`
  - `POST /products` (MANAGER/ADMIN)
  - `PATCH /products/{id}` (MANAGER/ADMIN)
  - `DELETE /products/{id}` (MANAGER/ADMIN)
- **Stock** (por ubicación):
  - `GET /stocks?product_id&location&limit&offset`
  - `POST /stocks` (MANAGER/ADMIN)
  - `PATCH /stocks/{id}` (MANAGER/ADMIN)
- **Movimientos** (histórico + operaciones):
  - `GET /movements` (filtros por fechas, tipo, usuario, etc.)
  - `POST /movements/in` (MANAGER/ADMIN)
  - `POST /movements/out` (MANAGER/ADMIN)
  - `POST /movements/adjust` (MANAGER/ADMIN)

### Eventos (sensores simulados)
- `GET /events?event_type&product_id&processed&limit&offset`
- `POST /events` (requiere token)
  - En Sprint 2, `POST /events` solo registra y encola; el worker procesa y genera el movimiento.

### Alertas de stock bajo
- Job programado (Celery Beat): `scan_low_stock()` cada `LOW_STOCK_SCAN_MINUTES` (default 5).
- `GET /alerts?status&product_id&location&date_from&date_to&limit&offset` (usuarios autenticados)
- `POST /alerts/{id}/ack` (MANAGER/ADMIN)
- Notificación por email (Mailtrap) al disparar alerta.

### Umbrales de stock (thresholds)
- CRUD completo:
  - `GET /thresholds`
  - `POST /thresholds`
  - `PATCH /thresholds/{id}`
  - `DELETE /thresholds/{id}`

### Locations
- `GET /locations` (lista de ubicaciones disponibles).

### Reportes
- Endpoints de reporte para top consumidos y turnover (por fecha/ubicación/límite).

### Android
- Login/registro contra la API
- Listado y detalle de productos
- Escaneo con cámara (ML Kit)
- Registro de movimiento desde barcode y ubicación
- Pantallas de stocks/eventos (según implementación)
- Pantalla de eventos con estado y cola offline
- Pantalla de confirmación de escaneo (IN/OUT + cantidad/ubicación)
- Pantalla de rotación con agregados por producto
- Integración con etiquetadora Niimbot B1:
  - impresión directa por SDK oficial (Bluetooth),
  - fallback para abrir app oficial Niimbot.

---

## 📌 Requisitos del enunciado (Actividad 4) — estado

| Requisito | Estado | Comentario |
|---|---:|---|
| Auth con JWT + roles | ✅ | Implementado en backend |
| CRUD productos/stocks | ✅ | Incluye filtros + paginación |
| Escaneo móvil | ✅ | Android con ML Kit |
| Integración API/SDK externo (Niimbot) | ✅ | SDK oficial integrado para impresión B1 |
| Simulación de sensores | ✅ | Endpoints de eventos |
| Procesamiento de eventos | ✅ | Asíncrono con Redis + Celery (cola + worker) |
| Historial de movimientos | ✅ | Endpoint + filtros |
| Auditoría de cambios | ✅ | Endpoint `/audit` (solo ADMIN) + trazabilidad por entidad |
| Alertas stock bajo | ✅ | Celery Beat + notificación por email |
| Importación CSV | ✅ | Endpoints `/imports/*` + flujo de review (approve/reject) |
| Reportes | ✅ | Top-consumed y turnover |
| Tests/CI | ✅ | Pytest + Contract tests + GitHub Actions |
| Contrato OpenAPI documentado | ✅ | Snapshot `openapi.json` + examples/responses en Swagger |

Leyenda: ✅ hecho · ⚠️ parcial · ⏳ planificado

---

## 🚀 Puesta en marcha

### Backend + PostgreSQL (Docker)

**Requisitos:**
- Docker + Docker Compose

**Arranque:**
```bash
cd backend
docker compose up --build
```

**Servicios detectados (según docker-compose):**
```json
[
  {
    "path": "InventariadoSensores-offline/backend/docker-compose.yml",
    "services_guess": [
      "db",
      "api",
      "redis",
      "worker",
      "beat",
      "postgres_data"
    ],
    "ports": [
      "5432:5432",
      "8000:8000"
    ]
  }
]
```

**Swagger/OpenAPI:**
- `http://localhost:8000/docs`

**Healthcheck:**
- `GET /health` devuelve estado de API + DB + Redis + Celery
- Si falla algo, responde 503 con detalles en `checks`

**Servicios de background (Celery):**
- `worker`: procesa tareas en segundo plano.
- `beat`: dispara tareas programadas.

**Variables de entorno clave:**
- `REDIS_URL` / `CELERY_BROKER_URL` / `CELERY_RESULT_BACKEND`
- `APP_ROLE` = `api` | `worker` | `beat`
- `CELERY_WORKER_CONCURRENCY`


**Reset de entorno (borra datos y volúmenes):**
```bash
cd backend
docker compose down -v
docker compose up --build
```

### Tests y CI/CD (Sprint 3)

**Tests backend (local en contenedor):**
```bash
docker compose -f backend/docker-compose.yml exec -T api sh -lc "python -m pytest -q tests"
```

**Contrato OpenAPI + snapshot:**
```bash
docker compose -f backend/docker-compose.yml exec -T api sh -lc "python -m pytest -q tests/test_openapi_snapshot.py tests/test_contract.py"
```

**Contrato OpenAPI + snapshot guardando log y XML (PowerShell):**
```powershell
New-Item -ItemType Directory -Force backend/test-reports | Out-Null
docker compose -f backend/docker-compose.yml exec -T api sh -lc "python -m pytest -q tests/test_openapi_snapshot.py tests/test_contract.py --junitxml=/tmp/contract-latest.xml" | Tee-Object -FilePath backend/test-reports/contract-latest.log
docker compose -f backend/docker-compose.yml cp api:/tmp/contract-latest.xml backend/test-reports/contract-latest.xml
```

**Build de imágenes backend (validación de empaquetado):**
```bash
docker compose -f backend/docker-compose.yml build api worker beat
```

**GitHub Actions (automático):**
- `backend-contract.yml`: snapshot + contract tests.
- `backend-ci.yml`: suite `pytest` + build Docker.
- Se ejecutan en `push` / `pull_request` cuando hay cambios en `backend/**` o en los workflows.


**Scripts de demo (observabilidad y flujo):**
```powershell
powershell -ExecutionPolicy Bypass -File backend/scripts/demo_grafana_errors.ps1 -Quick1m -Include403
powershell -ExecutionPolicy Bypass -File backend/scripts/demo_grafana_load.ps1 -VUs 20 -Duration 60s
powershell -ExecutionPolicy Bypass -File backend/scripts/generate_flowchart_png.ps1
```
- Script base de carga k6: `backend/scripts/k6_grafana_load.js`
---

## 🔐 Ejemplos rápidos (curl)

**Register (JSON):**
```bash
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@demo.com","password":"admin123"}'
```

**Login (form-urlencoded):**
```bash
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin@demo.com&password=admin123"
```

**Me:**
```bash
curl http://localhost:8000/users/me \
  -H "Authorization: Bearer <TOKEN>"
```

**Credenciales seed (seed_db.py):**
- admin / manager / user
- password: `Pass123!`

**Demo rápida (PowerShell):**
```powershell
$token = (Invoke-RestMethod -Method Post -Uri "http://localhost:8000/auth/login" -Form @{ email="admin@demo.local"; password="Pass123!" }).access_token
$auth = @{ Authorization = "Bearer $token" }

# Locations (para desplegable del front)
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/locations"

# Crear evento (async con Celery)
$body = @{
  event_type = "SENSOR_IN"
  product_id = 1
  delta = 1
  source = "scan"
  location = "Oficina Central"
  idempotency_key = [guid]::NewGuid().ToString()
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Headers $auth -Uri "http://localhost:8000/events/" -ContentType "application/json" -Body $body
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/events/"
```

**Forzar alerta de stock bajo (sin esperar 5 min):**
```powershell
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/thresholds"
$patch = @{ min_quantity = 999 } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Headers $auth -Uri "http://localhost:8000/thresholds/1" -ContentType "application/json" -Body $patch
docker compose -f backend/docker-compose.yml exec worker python -c "from app.tasks import scan_low_stock; print(scan_low_stock())"
```

**Reportes (ejemplo):**
```powershell
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/reports/top-consumed?date_from=2026-01-01&date_to=2026-02-01&limit=5"
Invoke-RestMethod -Headers $auth -Uri "http://localhost:8000/reports/turnover?date_from=2026-01-01&date_to=2026-02-01&limit=5"
```

---

## 📱 Android (Android Studio)

**Requisitos:**
- Android Studio
- Emulador o dispositivo físico

**URL de la API:**
- Emulador → `http://10.0.2.2:8000/`
- Dispositivo físico → IP local del PC en la LAN (ej. `http://192.168.1.50:8000/`)

Detectado en el repo:
- BASE_URL: `http://10.0.2.2:8000/`

**Cambiar IP desde el móvil (sin recompilar):**
- Long-press en el logo de Login o en la toolbar de Home.
- Escribe la IP del PC (host) y guarda.
- Se aplica solo en ese dispositivo.

### Conectar móvil/tablet por ADB (USB o Wi‑Fi)

**Requisitos previos (móvil/tablet):**
- Activar *Opciones de desarrollador*.
- Activar *Depuración USB*.
- En Wi‑Fi: mismo router/red que el PC.

**Comandos básicos (PowerShell / CMD):**
```bash
adb devices
```
Si el dispositivo aparece en la lista, ya está conectado por USB.

**Pasar a Wi‑Fi (ADB over TCP):**
1) Conecta por USB primero.
2) Obtén la IP del dispositivo (Wi‑Fi):
   - En el móvil: Ajustes → Wi‑Fi → tu red → IP.

```bash
adb tcpip 5555
adb connect <IP_DEL_DISPOSITIVO>:5555
adb devices
```

Ahora puedes desconectar el cable y seguir por Wi‑Fi.

**Volver a USB / desconectar Wi‑Fi:**
```bash
adb disconnect <IP_DEL_DISPOSITIVO>:5555
adb usb
```

**Emparejar por Wi‑Fi (Android 11+ / ADB pair):**
1) En el móvil: Opciones de desarrollador → *Depuración inalámbrica* → Emparejar dispositivo.
2) Anota **IP:puerto** y el **código de emparejamiento**.

```bash
adb pair <IP_DEL_DISPOSITIVO>:<PUERTO_PAIR>
adb connect <IP_DEL_DISPOSITIVO>:<PUERTO_CONNECT>
adb devices
```

**Notas útiles:**
- Si `adb` no se reconoce, añade la ruta de `platform-tools` al PATH.
- Si no conecta, revisa firewall y que PC y móvil estén en la misma red.

**Pasos:**
1. Abrir carpeta `android/` en Android Studio
2. Sync Gradle
3. Ejecutar en emulador/dispositivo
4. Probar: login → productos → escaneo → movimiento → stocks/eventos

---

## 🧠 Nota técnica (evitar duplicación de stock)

Si en el flujo de escaneo se registra **evento** y luego **movimiento** para la misma acción, el stock puede actualizarse 2 veces.

✅ Recomendación para Sprint 2:
- Opción A: `/events` solo registra y un consumidor procesa/actualiza.
- Opción B: Android solo llama a `/movements` y el backend crea el evento internamente (una sola fuente de verdad).

---

## 🧪 Metodología de trabajo (Scrum)

Trabajo gestionado con Scrum:
- Epics / Historias de usuario / subtareas
- Sprints con entregables
- Definition of Done común

### ✅ Definition of Done (resumen)
Una historia se considera terminada cuando:
- Funcionalidad demostrable
- Pasa pruebas mínimas y no rompe otras pantallas/endpoints
- Cumple seguridad básica (auth/roles)
- Está documentada (README / Swagger)
- Integrada en rama principal (merge sin conflictos)

---

## 🗓️ Planificación por sprints

- **Sprint 1:** productos/stocks CRUD, escaneo móvil, eventos básicos.
- **Sprint 2:** consumidor de eventos, alertas, reportes.
- **Sprint 3:** importación CSV, auditoría, optimizaciones, contrato OpenAPI y CI/CD.

### Sprint 2 (implementado)

Backend:
- Procesamiento asíncrono de eventos con Redis + Celery (worker/beat).
- Endpoint de eventos desacoplado: crea evento + cola, worker genera movimientos.
- Estados de evento (PENDING/PROCESSED/FAILED), reintentos y last_error.
- Idempotencia por idempotency_key / event_id.
- Nuevos endpoints: locations, thresholds, alerts, reports.
- Alertas de stock bajo con job periódico (Celery Beat) y notificación por email (Mailtrap).
- Tests y ajustes de migraciones para nuevos modelos.

Android:
- Pantalla de eventos con estado, offline queue y reintentos.
- Flujo de escaneo actualizado con pantalla de confirmación.
- Pantalla de rotación (IN/OUT/stock agregados por producto).
- Dropdown de locations en formularios (events/scan/movements/stock).
- Mejoras de sesión (validación y feedback de errores).

Android (UI/UX y nuevas pantallas):
- Rediseño completo del login con fondo degradado, tarjeta central, iconos en inputs,
  boton con degradado y enlaces de registro/recuperacion.
- Nuevo menu principal con tarjetas e iconos personalizados, drawer lateral con perfil,
  accesos (estado del sistema, errores offline, alertas) y logout.
- Soporte de tema claro/oscuro con toggle en el menu (sin cerrar el drawer).
- Listados en tarjetas (productos, movimientos, stock, eventos, errores offline),
  con colores adaptados al tema y jerarquia visual mejorada.
- Nueva pantalla de categorias (listar, crear, editar, eliminar y filtrar por id).
- Nueva pantalla de umbrales (thresholds) con layout tipo login y acceso directo desde home.
- Nueva pantalla de alertas con pestañas (alertas del sistema / pendientes offline),
  limpieza rápida por sección y eventos fallidos.
- Alertas del sistema con diálogo central ante caídas de servicios y guardado en historial.

### Sprint 3 (implementado)

Backend:
- Importación CSV completa (`/imports/events/csv`, `/imports/transfers/csv`) con cuarentena y review.
- Auditoría (`/audit`) con filtros por entidad/acción/usuario/fecha (solo ADMIN).
- Contrato OpenAPI:
  - snapshot versionado en `backend/openapi/openapi.json`,
  - test de snapshot (`test_openapi_snapshot.py`),
  - test de contrato Schemathesis (`test_contract.py`).
- Documentación OpenAPI enriquecida con `examples` y `responses` de error por ruta.
- Observabilidad operativa:
  - métricas `/metrics`,
  - stack Prometheus + Grafana provisionado.

Calidad y CI/CD:
- Workflow `backend-contract.yml`: valida snapshot + contrato OpenAPI.
- Workflow `backend-ci.yml`: ejecuta tests backend y build Docker (`api/worker/beat`).
- Reportes de tests en formato JUnit como artefacto de CI.

Android:
- Consolidación de UX offline/online con colas de sincronización y avisos globales.
- Diálogos unificados para errores y estados de sincronización.
- Integración de impresión Niimbot y mejoras de feedback visual.

Documentacion de apoyo (backend/context):
- `backend/context/README_tests_contrato_openapi.md`
- `backend/context/README_observabilidad_prometheus_grafana.md`
- `backend/context/README_import_swagger.md`
- `backend/context/GUIA_DEFENSA_REQUISITOS_ACTIVIDAD4.md`

---

## Estructura completa del proyecto (todas las carpetas y archivos)

```


InventariadoSensores/
    ├── .github/
    │   ├── workflows/
    │   │   ├── backend-ci.yml
    │   │   ├── backend-contract.yml
    ├── android/
    │   ├── .idea/
    │   │   ├── codeStyles/
    │   │   │   ├── codeStyleConfig.xml
    │   │   │   ├── Project.xml
    │   │   ├── inspectionProfiles/
    │   │   │   ├── Project_Default.xml
    │   │   ├── .gitignore
    │   │   ├── .name
    │   │   ├── AndroidProjectSystem.xml
    │   │   ├── appInsightsSettings.xml
    │   │   ├── compiler.xml
    │   │   ├── deploymentTargetSelector.xml
    │   │   ├── deviceManager.xml
    │   │   ├── gradle.xml
    │   │   ├── markdown.xml
    │   │   ├── migrations.xml
    │   │   ├── misc.xml
    │   │   ├── runConfigurations.xml
    │   │   ├── studiobot.xml
    │   │   ├── vcs.xml
    │   ├── app/
    │   │   ├── libs/
    │   │   │   ├── 4.0.2-release.aar
    │   │   │   ├── image-1.9.5-20260121.aar
    │   │   │   ├── LPAPI-2019-11-20-R.jar
    │   │   ├── src/
    │   │   │   ├── androidTest/
    │   │   │   │   ├── java/
    │   │   │   │   │   ├── com/
    │   │   │   │   │   │   ├── example/
    │   │   │   │   │   │   │   ├── inventoryapp/
    │   │   │   │   │   │   │   │   ├── ExampleInstrumentedTest.kt
    │   │   │   ├── main/
    │   │   │   │   ├── java/
    │   │   │   │   │   ├── com/
    │   │   │   │   │   │   ├── example/
    │   │   │   │   │   │   │   ├── inventoryapp/
    │   │   │   │   │   │   │   │   ├── data/
    │   │   │   │   │   │   │   │   │   ├── local/
    │   │   │   │   │   │   │   │   │   │   ├── cache/
    │   │   │   │   │   │   │   │   │   │   │   ├── CacheDao.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── CacheDatabase.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── CacheEntry.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── CacheKeys.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── CacheStore.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── ProductNameCache.kt
    │   │   │   │   │   │   │   │   │   │   ├── EventAlertDismissStore.kt
    │   │   │   │   │   │   │   │   │   │   ├── OfflineQueue.kt
    │   │   │   │   │   │   │   │   │   │   ├── OfflineSyncer.kt
    │   │   │   │   │   │   │   │   │   │   ├── OfflineSyncScheduler.kt
    │   │   │   │   │   │   │   │   │   │   ├── OfflineSyncWorker.kt
    │   │   │   │   │   │   │   │   │   │   ├── SessionManager.kt
    │   │   │   │   │   │   │   │   │   │   ├── SystemAlertStore.kt
    │   │   │   │   │   │   │   │   │   ├── remote/
    │   │   │   │   │   │   │   │   │   │   ├── model/
    │   │   │   │   │   │   │   │   │   │   │   ├── AlertDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── AuditDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── CategoryDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── EventDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── FcmDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── HealthResponseDto.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── ImportDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── LocationDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── MovementDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── ProductDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── ReportDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── StockDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── ThresholdDtos.kt
    │   │   │   │   │   │   │   │   │   │   │   ├── TokenResponse.kt
    │   │   │   │   │   │   │   │   │   │   ├── AlertsWebSocketManager.kt
    │   │   │   │   │   │   │   │   │   │   ├── AuthInterceptor.kt
    │   │   │   │   │   │   │   │   │   │   ├── FcmService.kt
    │   │   │   │   │   │   │   │   │   │   ├── FcmTokenManager.kt
    │   │   │   │   │   │   │   │   │   │   ├── InventoryApi.kt
    │   │   │   │   │   │   │   │   │   │   ├── NetworkModule.kt
    │   │   │   │   │   │   │   │   │   ├── repository/
    │   │   │   │   │   │   │   │   │   │   ├── remote/
    │   │   │   │   │   │   │   │   │   │   │   ├── EventRepository.kt
    │   │   │   │   │   │   │   │   │   │   │   └── RemoteScanRepository.kt
    │   │   │   │   │   │   │   │   │   │   ├── MovementRepository.kt
    │   │   │   │   │   │   │   │   ├── domain/
    │   │   │   │   │   │   │   │   │   ├── model/
    │   │   │   │   │   │   │   │   │   │   ├── EventMovementResult.kt
    │   │   │   │   │   │   │   │   │   │   ├── EventMovementStatus.kt
    │   │   │   │   │   │   │   │   │   │   ├── Movement.kt
    │   │   │   │   │   │   │   │   │   │   ├── MovementType.kt
    │   │   │   │   │   │   │   │   │   │   ├── Product.kt
    │   │   │   │   │   │   │   │   ├── ui/
    │   │   │   │   │   │   │   │   │   ├── alerts/
    │   │   │   │   │   │   │   │   │   │   ├── AlertListAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── AlertsActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── AlertsListFragment.kt
    │   │   │   │   │   │   │   │   │   │   ├── AlertsPagerAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── OfflinePendingAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── OfflinePendingFragment.kt
    │   │   │   │   │   │   │   │   │   │   ├── SystemAlertAdapter.kt
    │   │   │   │   │   │   │   │   │   ├── audit/
    │   │   │   │   │   │   │   │   │   │   ├── AuditActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── AuditAdapter.kt
    │   │   │   │   │   │   │   │   │   ├── auth/
    │   │   │   │   │   │   │   │   │   │   ├── LoginActivity.kt
    │   │   │   │   │   │   │   │   │   ├── categories/
    │   │   │   │   │   │   │   │   │   │   ├── CategoriesActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── CategoryListAdapter.kt
    │   │   │   │   │   │   │   │   │   ├── common/
    │   │   │   │   │   │   │   │   │   │   ├── ActivityTracker.kt
    │   │   │   │   │   │   │   │   │   │   ├── AlertsBadgeUtil.kt
    │   │   │   │   │   │   │   │   │   │   ├── ApiErrorFormatter.kt
    │   │   │   │   │   │   │   │   │   │   ├── CreateUiFeedback.kt
    │   │   │   │   │   │   │   │   │   │   ├── GradientIconUtil.kt
    │   │   │   │   │   │   │   │   │   │   ├── NetworkStatusBar.kt
    │   │   │   │   │   │   │   │   │   │   ├── SendSnack.kt
    │   │   │   │   │   │   │   │   │   │   ├── SystemAlertManager.kt
    │   │   │   │   │   │   │   │   │   │   ├── UiNotifier.kt
    │   │   │   │   │   │   │   │   │   ├── events/
    │   │   │   │   │   │   │   │   │   │   ├── EventAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── EventRowUi.kt
    │   │   │   │   │   │   │   │   │   │   ├── EventsActivity.kt
    │   │   │   │   │   │   │   │   │   ├── home/
    │   │   │   │   │   │   │   │   │   │   ├── HomeActivity.kt
    │   │   │   │   │   │   │   │   │   ├── imports/
    │   │   │   │   │   │   │   │   │   │   ├── ImportErrorAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── ImportEventsFragment.kt
    │   │   │   │   │   │   │   │   │   │   ├── ImportFormFragment.kt
    │   │   │   │   │   │   │   │   │   │   ├── ImportReviewAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── ImportReviewsFragment.kt
    │   │   │   │   │   │   │   │   │   │   ├── ImportsActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── ImportsPagerAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── ImportTransfersFragment.kt
    │   │   │   │   │   │   │   │   │   ├── movements/
    │   │   │   │   │   │   │   │   │   │   ├── MovementsListAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── MovementsMenuActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── ResultActivity.kt
    │   │   │   │   │   │   │   │   │   ├── offline/
    │   │   │   │   │   │   │   │   │   │   ├── OfflineErrorsAdapter.kt
    │   │   │   │   │   │   │   │   │   ├── products/
    │   │   │   │   │   │   │   │   │   │   ├── LabelPreviewActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── NiimbotSdkManager.kt
    │   │   │   │   │   │   │   │   │   │   ├── ProductAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── ProductListActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── ProductListAdapter.kt
    │   │   │   │   │   │   │   │   │   ├── reports/
    │   │   │   │   │   │   │   │   │   │   ├── ReportsActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── TopConsumedActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── TopConsumedAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── TurnoverAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── TurnoverReportActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── TurnoverRow.kt
    │   │   │   │   │   │   │   │   │   ├── rotation/
    │   │   │   │   │   │   │   │   │   │   ├── RotationActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── RotationAdapter.kt
    │   │   │   │   │   │   │   │   │   │   ├── RotationRow.kt
    │   │   │   │   │   │   │   │   │   ├── scan/
    │   │   │   │   │   │   │   │   │   │   ├── ConfirmScanActivity.kt
    │   │   │   │   │   │   │   │   │   ├── stock/
    │   │   │   │   │   │   │   │   │   │   ├── StockActivity.kt
    │   │   │   │   │   │   │   │   │   │   ├── StockListAdapter.kt
    │   │   │   │   │   │   │   │   │   ├── thresholds/
    │   │   │   │   │   │   │   │   │   │   ├── ThresholdListAdapter.kt
    │   │   │   │   │   │   │   │   │   │   └── ThresholdsActivity.kt
    │   │   │   │   │   │   │   │   │   └── ScanActivity.kt
    │   │   │   │   │   │   │   │   ├── InventoryApp.kt
    │   │   │   │   │   │   │   │   ├── MainActivity.kt
    │   │   │   │   ├── res/
    │   │   │   │   │   ├── anim/
    │   │   │   │   │   │   ├── alert_icon_pulse.xml
    │   │   │   │   │   │   ├── alert_popup_in.xml
    │   │   │   │   │   │   ├── alert_popup_out.xml
    │   │   │   │   │   │   ├── screen_enter_soft.xml
    │   │   │   │   │   │   ├── screen_exit_soft.xml
    │   │   │   │   │   │   ├── screen_pop_enter_soft.xml
    │   │   │   │   │   │   ├── screen_pop_exit_soft.xml
    │   │   │   │   │   ├── drawable/
    │   │   │   │   │   │   ├── add.png
    │   │   │   │   │   │   ├── addfile.png
    │   │   │   │   │   │   ├── adjust.png
    │   │   │   │   │   │   ├── ajustes.png
    │   │   │   │   │   │   ├── alert_blue.png
    │   │   │   │   │   │   ├── alert_green.png
    │   │   │   │   │   │   ├── alert_red.png
    │   │   │   │   │   │   ├── alert_violet.png
    │   │   │   │   │   │   ├── alert_yellow.png
    │   │   │   │   │   │   ├── api.png
    │   │   │   │   │   │   ├── back.png
    │   │   │   │   │   │   ├── baseline_account_circle_24.xml
    │   │   │   │   │   │   ├── bg_avatar_circle.xml
    │   │   │   │   │   │   ├── bg_back_shadow.xml
    │   │   │   │   │   │   ├── bg_badge_red.xml
    │   │   │   │   │   │   ├── bg_button_danger.xml
    │   │   │   │   │   │   ├── bg_button_gradient.xml
    │   │   │   │   │   │   ├── bg_button_soft_purple.xml
    │   │   │   │   │   │   ├── bg_circle_icon.xml
    │   │   │   │   │   │   ├── bg_event_id_badge.xml
    │   │   │   │   │   │   ├── bg_home_gradient.xml
    │   │   │   │   │   │   ├── bg_login_blue_gradient.xml
    │   │   │   │   │   │   ├── bg_snackbar.xml
    │   │   │   │   │   │   ├── bg_toggle_active.xml
    │   │   │   │   │   │   ├── bg_toggle_idle.xml
    │   │   │   │   │   │   ├── category.png
    │   │   │   │   │   │   ├── celery.png
    │   │   │   │   │   │   ├── clear.png
    │   │   │   │   │   │   ├── close.png
    │   │   │   │   │   │   ├── copy.png
    │   │   │   │   │   │   ├── correct.png
    │   │   │   │   │   │   ├── database.png
    │   │   │   │   │   │   ├── down.png
    │   │   │   │   │   │   ├── events.png
    │   │   │   │   │   │   ├── expired.png
    │   │   │   │   │   │   ├── export.png
    │   │   │   │   │   │   ├── ic_bell.xml
    │   │   │   │   │   │   ├── ic_category.xml
    │   │   │   │   │   │   ├── ic_check_green.xml
    │   │   │   │   │   │   ├── ic_close_red.xml
    │   │   │   │   │   │   ├── ic_copy.xml
    │   │   │   │   │   │   ├── ic_error_red.xml
    │   │   │   │   │   │   ├── ic_launcher_background.xml
    │   │   │   │   │   │   ├── ic_launcher_foreground.xml
    │   │   │   │   │   │   ├── ic_lock.xml
    │   │   │   │   │   │   ├── ic_moon.xml
    │   │   │   │   │   │   ├── ic_print.xml
    │   │   │   │   │   │   ├── ic_profile.xml
    │   │   │   │   │   │   ├── ic_status.xml
    │   │   │   │   │   │   ├── ic_sun.xml
    │   │   │   │   │   │   ├── ic_user_avatar.xml
    │   │   │   │   │   │   ├── iotrack_adaptative.png
    │   │   │   │   │   │   ├── iotrack_icon.png
    │   │   │   │   │   │   ├── iotrack.png
    │   │   │   │   │   │   ├── loaded.png
    │   │   │   │   │   │   ├── lote.png
    │   │   │   │   │   │   ├── menu.png
    │   │   │   │   │   │   ├── movements.png
    │   │   │   │   │   │   ├── niimbot.png
    │   │   │   │   │   │   ├── offline.png
    │   │   │   │   │   │   ├── online.png
    │   │   │   │   │   │   ├── orderby.png
    │   │   │   │   │   │   ├── print_label.png
    │   │   │   │   │   │   ├── print.png
    │   │   │   │   │   │   ├── products.png
    │   │   │   │   │   │   ├── redis.png
    │   │   │   │   │   │   ├── reports.png
    │   │   │   │   │   │   ├── rotation.png
    │   │   │   │   │   │   ├── rotations.png
    │   │   │   │   │   │   ├── scaner.png
    │   │   │   │   │   │   ├── search.png
    │   │   │   │   │   │   ├── splash_empty_icon.xml
    │   │   │   │   │   │   ├── splash_iotrack_icon.xml
    │   │   │   │   │   │   ├── splash_login_style_icon.png
    │   │   │   │   │   │   ├── stock.png
    │   │   │   │   │   │   ├── sync.png
    │   │   │   │   │   │   ├── system.png
    │   │   │   │   │   │   ├── threshold.png
    │   │   │   │   │   │   ├── transfer.png
    │   │   │   │   │   │   ├── triangle_down_lg.xml
    │   │   │   │   │   │   ├── triangle_down.xml
    │   │   │   │   │   │   ├── triangle_up.xml
    │   │   │   │   │   │   ├── umbral.png
    │   │   │   │   │   │   ├── up.png
    │   │   │   │   │   │   ├── user.png
    │   │   │   │   │   ├── drawable-night/
    │   │   │   │   │   │   ├── splash_login_style_icon.xml
    │   │   │   │   │   ├── layout/
    │   │   │   │   │   │   ├── activity_alerts.xml
    │   │   │   │   │   │   ├── activity_audit.xml
    │   │   │   │   │   │   ├── activity_categories.xml
    │   │   │   │   │   │   ├── activity_confirm_scan.xml
    │   │   │   │   │   │   ├── activity_events.xml
    │   │   │   │   │   │   ├── activity_home.xml
    │   │   │   │   │   │   ├── activity_imports.xml
    │   │   │   │   │   │   ├── activity_label_preview.xml
    │   │   │   │   │   │   ├── activity_login.xml
    │   │   │   │   │   │   ├── activity_main.xml
    │   │   │   │   │   │   ├── activity_movements_menu.xml
    │   │   │   │   │   │   ├── activity_offline_errors.xml
    │   │   │   │   │   │   ├── activity_product_list.xml
    │   │   │   │   │   │   ├── activity_reports.xml
    │   │   │   │   │   │   ├── activity_result.xml
    │   │   │   │   │   │   ├── activity_rotation.xml
    │   │   │   │   │   │   ├── activity_scan.xml
    │   │   │   │   │   │   ├── activity_stock.xml
    │   │   │   │   │   │   ├── activity_thresholds.xml
    │   │   │   │   │   │   ├── activity_top_consumed.xml
    │   │   │   │   │   │   ├── activity_turnover_report.xml
    │   │   │   │   │   │   ├── dialog_alert_popup.xml
    │   │   │   │   │   │   ├── dialog_audit_detail.xml
    │   │   │   │   │   │   ├── dialog_create_failure.xml
    │   │   │   │   │   │   ├── dialog_create_loading.xml
    │   │   │   │   │   │   ├── dialog_create_success.xml
    │   │   │   │   │   │   ├── dialog_edit_category.xml
    │   │   │   │   │   │   ├── dialog_edit_product.xml
    │   │   │   │   │   │   ├── dialog_edit_stock.xml
    │   │   │   │   │   │   ├── dialog_edit_threshold.xml
    │   │   │   │   │   │   ├── dialog_import_review_bottom_sheet.xml
    │   │   │   │   │   │   ├── dialog_important_notice.xml
    │   │   │   │   │   │   ├── dialog_list_loading.xml
    │   │   │   │   │   │   ├── dialog_logout_confirm.xml
    │   │   │   │   │   │   ├── dialog_niimbot_actions.xml
    │   │   │   │   │   │   ├── dialog_niimbot_bluetooth.xml
    │   │   │   │   │   │   ├── dialog_niimbot_printing.xml
    │   │   │   │   │   │   ├── dialog_permission_denied.xml
    │   │   │   │   │   │   ├── dialog_register.xml
    │   │   │   │   │   │   ├── dialog_system_status.xml
    │   │   │   │   │   │   ├── fragment_alerts_list.xml
    │   │   │   │   │   │   ├── fragment_import_form.xml
    │   │   │   │   │   │   ├── fragment_import_reviews.xml
    │   │   │   │   │   │   ├── fragment_offline_pending.xml
    │   │   │   │   │   │   ├── item_alert_card.xml
    │   │   │   │   │   │   ├── item_audit_log.xml
    │   │   │   │   │   │   ├── item_category_card.xml
    │   │   │   │   │   │   ├── item_event_row.xml
    │   │   │   │   │   │   ├── item_import_error.xml
    │   │   │   │   │   │   ├── item_import_review.xml
    │   │   │   │   │   │   ├── item_movement_card.xml
    │   │   │   │   │   │   ├── item_offline_error_card.xml
    │   │   │   │   │   │   ├── item_offline_pending_card.xml
    │   │   │   │   │   │   ├── item_product_card.xml
    │   │   │   │   │   │   ├── item_product.xml
    │   │   │   │   │   │   ├── item_rotation_row.xml
    │   │   │   │   │   │   ├── item_stock_card.xml
    │   │   │   │   │   │   ├── item_system_alert_card.xml
    │   │   │   │   │   │   ├── item_threshold_card.xml
    │   │   │   │   │   │   ├── item_top_consumed_row.xml
    │   │   │   │   │   │   ├── item_turnover_row.xml
    │   │   │   │   │   │   ├── nav_header_home.xml
    │   │   │   │   │   ├── menu/
    │   │   │   │   │   │   ├── drawer_menu_bottom.xml
    │   │   │   │   │   │   ├── drawer_menu.xml
    │   │   │   │   │   │   ├── home_menu.xml
    │   │   │   │   │   ├── mipmap-anydpi-v26/
    │   │   │   │   │   │   ├── ic_launcher_round.xml
    │   │   │   │   │   │   ├── ic_launcher.xml
    │   │   │   │   │   ├── mipmap-hdpi/
    │   │   │   │   │   │   ├── ic_launcher_foreground.png
    │   │   │   │   │   │   ├── ic_launcher_round.png
    │   │   │   │   │   │   ├── ic_launcher.png
    │   │   │   │   │   ├── mipmap-mdpi/
    │   │   │   │   │   │   ├── ic_launcher_foreground.png
    │   │   │   │   │   │   ├── ic_launcher_round.png
    │   │   │   │   │   │   ├── ic_launcher.png
    │   │   │   │   │   ├── mipmap-xhdpi/
    │   │   │   │   │   │   ├── ic_launcher_foreground.png
    │   │   │   │   │   │   ├── ic_launcher_round.png
    │   │   │   │   │   │   ├── ic_launcher.png
    │   │   │   │   │   ├── mipmap-xxhdpi/
    │   │   │   │   │   │   ├── ic_launcher_foreground.png
    │   │   │   │   │   │   ├── ic_launcher_round.png
    │   │   │   │   │   │   ├── ic_launcher.png
    │   │   │   │   │   ├── mipmap-xxxhdpi/
    │   │   │   │   │   │   ├── ic_launcher_foreground.png
    │   │   │   │   │   │   ├── ic_launcher_round.png
    │   │   │   │   │   │   ├── ic_launcher.png
    │   │   │   │   │   ├── raw/
    │   │   │   │   │   │   ├── bluetooth.json
    │   │   │   │   │   │   ├── camera.json
    │   │   │   │   │   │   ├── connect_print.json
    │   │   │   │   │   │   ├── correct_create.json
    │   │   │   │   │   │   ├── error.json
    │   │   │   │   │   │   ├── file.json
    │   │   │   │   │   │   ├── loading_list.json
    │   │   │   │   │   │   ├── loading.json
    │   │   │   │   │   │   ├── locked.json
    │   │   │   │   │   │   ├── logout.json
    │   │   │   │   │   │   ├── notfound.json
    │   │   │   │   │   │   ├── print_error.json
    │   │   │   │   │   │   ├── printing.json
    │   │   │   │   │   │   ├── question.json
    │   │   │   │   │   │   ├── send.json
    │   │   │   │   │   │   ├── sync.json
    │   │   │   │   │   │   ├── wrong.json
    │   │   │   │   │   ├── values/
    │   │   │   │   │   │   ├── colors.xml
    │   │   │   │   │   │   ├── strings.xml
    │   │   │   │   │   │   ├── styles.xml
    │   │   │   │   │   │   ├── themes.xml
    │   │   │   │   │   ├── values-night/
    │   │   │   │   │   │   ├── themes.xml
    │   │   │   │   │   ├── xml/
    │   │   │   │   │   │   ├── backup_rules.xml
    │   │   │   │   │   │   ├── data_extraction_rules.xml
    │   │   │   │   │   │   ├── file_paths.xml
    │   │   │   │   ├── AndroidManifest.xml
    │   │   │   │   ├── ic_launcher-playstore.png
    │   │   │   ├── test/
    │   │   │   │   ├── java/
    │   │   │   │   │   ├── com/
    │   │   │   │   │   │   └── example/
    │   │   │   │   │   │       └── inventoryapp/
    │   │   │   │   │   │           └── ExampleUnitTest.kt
    │   │   ├── .gitignore
    │   │   ├── build.gradle.kts
    │   │   ├── google-services.json
    │   │   ├── proguard-rules.pro
    │   ├── gradle/
    │   │   ├── wrapper/
    │   │   │   ├── gradle-wrapper.jar
    │   │   │   ├── gradle-wrapper.properties
    │   │   ├── libs.versions.toml
    │   ├── .gitignore
    │   ├── build.gradle.kts
    │   ├── Documentacion.md
    │   ├── DocumentacionFront.md
    │   ├── gradle.properties
    │   ├── gradlew
    │   ├── gradlew.bat
    │   ├── settings.gradle.kts
    ├── backend/
    │   ├── alembic/
    │   │   ├── versions/
    │   │   │   ├── 2f4c1b7f1b0d_remove_alert_ack_at_default.py
    │   │   │   ├── 3b2a1c9d7e10_add_import_tables.py
    │   │   │   ├── 3f5d8b2a1e47_add_import_entity_to_audit_log.py
    │   │   │   ├── 4f3c2a9b7e2c_add_transfer_id_to_movements.py
    │   │   │   ├── 6a1d2c3e4f50_merge_import_heads.py
    │   │   │   ├── 6f8657d8911f_merge_heads.py
    │   │   │   ├── 7a4c9d2e1f10_merge_heads_audit_import.py
    │   │   │   ├── 8ec94a38e7f4_add_alerts_notifications.py
    │   │   │   ├── 9b8c7d6e5f40_add_alert_type.py
    │   │   │   ├── 9e75fa04121a_add_stock_thresholds_and_alerts_add_.py
    │   │   │   ├── 3373e6b81640_add_location_id_to_movements_and_.py
    │   │   │   ├── a3b4c5d6e7f8_make_alert_stock_id_nullable.py
    │   │   │   ├── b7a2c9d4e611_events_defaults_and_processed_at.py
    │   │   │   ├── bc1a2d3e4f50_add_fcm_tokens.py
    │   │   │   ├── c8ce14e1e339_add_indexes.py
    │   │   │   ├── c51f9fca7313_add_locations.py
    │   │   │   ├── d2b1c5f9a1a0_merge_event_defaults_and_locations.py
    │   │   │   ├── ef8cae6fe367_merge_heads.py
    │   │   │   ├── f1c2d3e4b5a6_add_delta_to_movements.py
    │   │   ├── env.py
    │   │   ├── README
    │   │   ├── script.py.mako
    │   ├── app/
    │   │   ├── __pycache__/
    │   │   │   ├── main.cpython-313.pyc
    │   │   ├── api/
    │   │   │   ├── routers/
    │   │   │   │   ├── __pycache__/
    │   │   │   │   │   ├── events.cpython-313.pyc
    │   │   │   ├── routes/
    │   │   │   │   ├── alerts.py
    │   │   │   │   ├── audit.py
    │   │   │   │   ├── auth.py
    │   │   │   │   ├── categories.py
    │   │   │   │   ├── events.py
    │   │   │   │   ├── imports.py
    │   │   │   │   ├── locations.py
    │   │   │   │   ├── movements.py
    │   │   │   │   ├── products.py
    │   │   │   │   ├── reports.py
    │   │   │   │   ├── stocks.py
    │   │   │   │   ├── thresholds.py
    │   │   │   │   ├── users.py
    │   │   │   │   ├── ws_alerts.py
    │   │   │   ├── deps.py
    │   │   │   ├── security.py
    │   │   ├── cache/
    │   │   │   ├── redis_cache.py
    │   │   ├── core/
    │   │   │   ├── __init__.py
    │   │   │   ├── config.py
    │   │   │   ├── observability.py
    │   │   │   ├── security.py
    │   │   ├── db/
    │   │   │   ├── __init__.py
    │   │   │   ├── base.py
    │   │   │   ├── deps.py
    │   │   │   ├── session.py
    │   │   ├── models/
    │   │   │   ├── __init__.py
    │   │   │   ├── alert.py
    │   │   │   ├── audit_log.py
    │   │   │   ├── category.py
    │   │   │   ├── enums.py
    │   │   │   ├── event.py
    │   │   │   ├── fcm_token.py
    │   │   │   ├── import_batch.py
    │   │   │   ├── import_error.py
    │   │   │   ├── import_review.py
    │   │   │   ├── location.py
    │   │   │   ├── movement.py
    │   │   │   ├── product.py
    │   │   │   ├── stock_threshold.py
    │   │   │   ├── stock.py
    │   │   │   ├── user.py
    │   │   ├── repositories/
    │   │   │   ├── __pycache__/
    │   │   │   │   ├── memory_repo.cpython-313.pyc
    │   │   │   ├── alert_repo.py
    │   │   │   ├── audit_log_repo.py
    │   │   │   ├── category_repo.py
    │   │   │   ├── event_repo.py
    │   │   │   ├── fcm_token_repo.py
    │   │   │   ├── location_repo.py
    │   │   │   ├── memory_repo.py
    │   │   │   ├── movement_repo.py
    │   │   │   ├── product_repo.py
    │   │   │   ├── report_repo.py
    │   │   │   ├── stock_repo.py
    │   │   │   ├── threshold_repo.py
    │   │   │   ├── user_repo.py
    │   │   ├── schemas/
    │   │   │   ├── __pycache__/
    │   │   │   │   ├── event.cpython-313.pyc
    │   │   │   ├── __init__.py
    │   │   │   ├── alert.py
    │   │   │   ├── audit_log.py
    │   │   │   ├── auth.py
    │   │   │   ├── category.py
    │   │   │   ├── event.py
    │   │   │   ├── fcm.py
    │   │   │   ├── location.py
    │   │   │   ├── movement.py
    │   │   │   ├── product.py
    │   │   │   ├── report.py
    │   │   │   ├── stock.py
    │   │   │   ├── threshold.py
    │   │   │   ├── user.py
    │   │   ├── services/
    │   │   │   ├── __pycache__/
    │   │   │   │   ├── event_service.cpython-313.pyc
    │   │   │   ├── __init__.py
    │   │   │   ├── auth_service.py
    │   │   │   ├── event_service.py
    │   │   │   ├── fcm_service.py
    │   │   │   ├── inventory_service.py
    │   │   │   ├── label_service.py
    │   │   │   ├── notification_service.py
    │   │   ├── ws/
    │   │   │   ├── alerts_ws.py
    │   │   ├── __init__.py
    │   │   ├── celery_app.py
    │   │   ├── main.py
    │   │   ├── tasks.py
    │   ├── context/
    │   │   ├── import_samples/
    │   │   │   ├── events_agresivo_mixto.csv
    │   │   │   ├── events_bordes_espacios.csv
    │   │   │   ├── events_lote_grande.csv
    │   │   │   ├── events_sample_errors.csv
    │   │   │   ├── events_sample_review.csv
    │   │   │   ├── events_sample.csv
    │   │   │   ├── README_stress_pack.md
    │   │   │   ├── transfers_agresivo_mixto.csv
    │   │   │   ├── transfers_lote_grande.csv
    │   │   │   ├── transfers_sample_errors.csv
    │   │   │   ├── transfers_sample.csv
    │   │   ├── Propuestas de proyectos 2DAM.pdf
    │   │   ├── readmeSprint3.md
    │   │   ├── RECAP_GLOBAL_REQUISITOS_DEMO.md
    │   ├── credentials/
    │   │   ├── .gitignore
    │   ├── observability/
    │   │   ├── grafana/
    │   │   │   ├── dashboards/
    │   │   │   │   ├── inventory-observability.json
    │   │   │   ├── provisioning/
    │   │   │   │   ├── alerting/
    │   │   │   │   │   ├── .gitkeep
    │   │   │   │   ├── dashboards/
    │   │   │   │   │   ├── dashboard.yml
    │   │   │   │   ├── datasources/
    │   │   │   │   │   ├── datasource.yml
    │   │   │   │   └── plugins/
    │   │   │   │       └── .gitkeep
    │   │   ├── prometheus/
    │   │   │   └── prometheus.yml
    │   ├── openapi/
    │   │   ├── openapi.json
    │   │   ├── README.md
    │   ├── scripts/
    │   │   ├── __init__.py
    │   │   ├── demo_grafana_errors.ps1
    │   │   ├── demo_grafana_load.ps1
    │   │   ├── export_openapi.py
    │   │   ├── k6_grafana_load.js
    │   │   ├── seed_db.py
    │   │   ├── simulate_events.py
    │   │   ├── test_db.py
    │   ├── test-reports/
    │   │   ├── contract-latest.log
    │   │   ├── contract-latest.xml
    │   ├── tests/
    │   │   ├── conftest.py
    │   │   ├── test_alerts.py
    │   │   ├── test_auth.py
    │   │   ├── test_contract.py
    │   │   ├── test_events.py
    │   │   ├── test_health.py
    │   │   ├── test_imports_csv.py
    │   │   ├── test_inventory_service_unit.py
    │   │   ├── test_openapi_snapshot.py
    │   │   ├── test_products.py
    │   │   └── test_stock_movements.py
    │   ├── .dockerignore
    │   ├── .env.example
    │   ├── alembic.ini
    │   ├── docker-compose.yml
    │   ├── Dockerfile
    │   ├── entrypoint.sh
    │   ├── requirements-dev.txt
    │   └── requirements.txt
    ├── .gitattributes
    ├── .gitignore
    ├── README_USUARIO.md
    └── README.md

---

## 📄 Licencia

Proyecto educativo (uso académico).

---

## Guías adicionales

- Guía técnica de Sprint 3: `readmeSprint3.md`
- Guía de uso para usuarios finales: `README_USUARIO.md`
