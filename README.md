# 📦 IoTrack - Sistema de Gestión de Inventario con Sensores IoT (simulados)

Proyecto transversal — **2º Desarrollo de Aplicaciones Multiplataforma (2ª Evaluación)**  
**Actividad 4:** *Inventario con sensores IoT simulados*.

---

## 🧩 Descripción

Sistema para **gestionar inventario y stock** de productos, registrando movimientos por:
- **Escaneo de códigos de barras** desde la app Android (cámara).
- **Eventos de sensores IoT simulados** (entradas/salidas automáticas).
- **Movimientos manuales** (entrada/salida/ajuste) desde la API.

El objetivo es entregar una solución **segura, documentada y desplegable en contenedores**, con una base preparada para extender:
- consumidor de eventos en background,
- alertas de stock bajo,
- reportes,
- importación CSV,
- auditoría de cambios.

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
- Sesión persistente (token)

**SDK (según Gradle detectado):**
- compileSdk: 34
- minSdk: 24
- targetSdk: 34
- applicationId: com.example.inventoryapp

---

## 👥 Reparto de tareas

| Persona   | Responsabilidad |
|----------|-----------------|
| **Esteban** | Backend – Autenticación, Seguridad, Calidad, Tests y CI |
| Carolina | Backend – CRUD de Inventario (productos, stock, movimientos) |
| Christian | Backend – Eventos de sensores simulados y apoyo |
| Jorge | Android – App base, autenticación y listados |
| Natalia | Android – Escaneo, movimientos y sincronización |
| Gonzalo | Base de datos, migraciones y entorno |

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

---

## 📌 Requisitos del enunciado (Actividad 4) — estado

| Requisito | Estado | Comentario |
|---|---:|---|
| Auth con JWT + roles | ✅ | Implementado en backend |
| CRUD productos/stocks | ✅ | Incluye filtros + paginación |
| Escaneo móvil | ✅ | Android con ML Kit |
| Simulación de sensores | ✅ | Endpoints de eventos |
| Procesamiento de eventos | ✅ | Asíncrono con Redis + Celery (cola + worker) |
| Historial de movimientos | ✅ | Endpoint + filtros |
| Auditoría de cambios | ⏳ | Planificado (S3) |
| Alertas stock bajo | ✅ | Celery Beat + notificación por email |
| Importación CSV | ⏳ | Planificado (S3) |
| Reportes | ✅ | Top-consumed y turnover |
| Tests/CI | ⏳ | Planificado / base preparada |

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

**Demo rÃ¡pida (PowerShell):**
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

**Cambiar IP desde el movil (sin recompilar):**
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
- **Sprint 3:** importación CSV, auditoría, optimizaciones.

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
  limpieza rapida por seccion y eventos fallidos.
- Alertas del sistema con dialogo central ante caidas de servicios y guardado en historial.

---

## 👥 Equipo (2º DAM)

- Christian Ballesteros  
- Gonzalo Bravo  
- Natalia Chuquillanqui  
- Carolina de la Losa  
- Esteban Garcés  
- Jorge Llanes  

---

## 🗂️ Estructura completa del proyecto (todas las carpetas y archivos)

```text

InventariadoSensores/
├── backend/
│   ├── alembic/
│   │   ├── versions/
│   │   │   ├── 2f4c1b7f1b0d_remove_alert_ack_at_default.py
│   │   │   ├── 3373e6b81640_add_location_id_to_movements_and_.py
│   │   │   ├── 4f3c2a9b7e2c_add_transfer_id_to_movements.py
│   │   │   ├── 6f8657d8911f_merge_heads.py
│   │   │   ├── 8ec94a38e7f4_add_alerts_notifications.py
│   │   │   ├── 9e75fa04121a_add_stock_thresholds_and_alerts_add_.py
│   │   │   ├── b7a2c9d4e611_events_defaults_and_processed_at.py
│   │   │   ├── c51f9fca7313_add_locations.py
│   │   │   ├── c8ce14e1e339_add_indexes.py
│   │   │   ├── d2b1c5f9a1a0_merge_event_defaults_and_locations.py
│   │   │   └── dcc886ba14d3_initial_schema.py
│   │   ├── env.py
│   │   ├── README
│   │   └── script.py.mako
│   ├── app/
│   │   ├── celery_app.py
│   │   ├── __pycache__/
│   │   │   └── main.cpython-313.pyc
│   │   ├── api/
│   │   │   ├── routers/
│   │   │   │   └── __pycache__/
│   │   │   │       └── events.cpython-313.pyc
│   │   │   ├── routes/
│   │   │   │   ├── alerts.py
│   │   │   │   ├── auth.py
│   │   │   │   ├── categories.py
│   │   │   │   ├── events.py
│   │   │   │   ├── locations.py
│   │   │   │   ├── movements.py
│   │   │   │   ├── products.py
│   │   │   │   ├── reports.py
│   │   │   │   ├── stocks.py
│   │   │   │   ├── thresholds.py
│   │   │   │   └── users.py
│   │   │   ├── deps.py
│   │   │   └── security.py
│   │   ├── core/
│   │   │   ├── __init__.py
│   │   │   ├── config.py
│   │   │   └── security.py
│   │   ├── db/
│   │   │   ├── __init__.py
│   │   │   ├── base.py
│   │   │   ├── deps.py
│   │   │   └── session.py
│   │   ├── models/
│   │   │   ├── __init__.py
│   │   │   ├── alert.py
│   │   │   ├── audit_log.py
│   │   │   ├── category.py
│   │   │   ├── entity.py
│   │   │   ├── enums.py
│   │   │   ├── event.py
│   │   │   ├── location.py
│   │   │   ├── movement.py
│   │   │   ├── product.py
│   │   │   ├── stock.py
│   │   │   ├── stock_threshold.py
│   │   │   └── user.py
│   │   ├── repositories/
│   │   │   ├── __pycache__/
│   │   │   │   └── memory_repo.cpython-313.pyc
│   │   │   ├── alert_repo.py
│   │   │   ├── category_repo.py
│   │   │   ├── event_repo.py
│   │   │   ├── location_repo.py
│   │   │   ├── memory_repo.py
│   │   │   ├── movement_repo.py
│   │   │   ├── product_repo.py
│   │   │   ├── report_repo.py
│   │   │   ├── stock_repo.py
│   │   │   ├── threshold_repo.py
│   │   │   └── user_repo.py
│   │   ├── schemas/
│   │   │   ├── __pycache__/
│   │   │   │   └── event.cpython-313.pyc
│   │   │   ├── __init__.py
│   │   │   ├── alert.py
│   │   │   ├── auth.py
│   │   │   ├── category.py
│   │   │   ├── event.py
│   │   │   ├── location.py
│   │   │   ├── movement.py
│   │   │   ├── product.py
│   │   │   ├── report.py
│   │   │   ├── stock.py
│   │   │   ├── threshold.py
│   │   │   └── user.py
│   │   ├── services/
│   │   │   ├── __pycache__/
│   │   │   │   └── event_service.cpython-313.pyc
│   │   │   ├── auth_service.py
│   │   │   ├── event_service.py
│   │   │   ├── inventory_service.py
│   │   │   └── notification_service.py
│   │   ├── __init__.py
│   │   ├── tasks.py
│   │   └── main.py
│   ├── context/
│   │   ├── backend_demo_guide.md
│   │   ├── generate_barcodes_pdf.py
│   │   ├── guion.md
│   │   ├── productos_barcodes.pdf
│   │   ├── Propuestas de proyectos 2DAM.pdf
│   │   ├── roles_validations.txt
│   │   └── sprint2.txt
│   ├── scripts/
│   │   ├── __init__.py
│   │   ├── seed_db.py
│   │   ├── simulate_events.py
│   │   └── test_db.py
│   ├── tests/
│   │   ├── conftest.py
│   │   ├── test_auth.py
│   │   ├── test_events.py
│   │   ├── test_health.py
│   │   ├── test_products.py
│   │   └── test_stock_movements.py
│   ├── .dockerignore
│   ├── .env
│   ├── .env.example
│   ├── alembic.ini
│   ├── docker-compose.yml
│   ├── Dockerfile
│   ├── entrypoint.sh
│   ├── requirements-dev.txt
│   └── requirements.txt
├── android/
│   ├── .idea/
│   │   ├── codeStyles/
│   │   │   ├── codeStyleConfig.xml
│   │   │   └── Project.xml
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
│   │   └── vcs.xml
│   ├── app/
│   │   ├── src/
│   │   │   ├── androidTest/
│   │   │   │   └── java/
│   │   │   │       └── com/
│   │   │   │           └── example/
│   │   │   │               └── inventoryapp/
│   │   │   │                   └── ExampleInstrumentedTest.kt
│   │   │   ├── main/
│   │   │   │   ├── java/
│   │   │   │   │   └── com/
│   │   │   │   │       └── example/
│   │   │   │   │           └── inventoryapp/
│   │   │   │   │               ├── data/
│   │   │   │   │               │   ├── local/
│   │   │   │   │               │   │   ├── OfflineQueue.kt
│   │   │   │   │               │   │   ├── OfflineSyncer.kt
│   │   │   │   │               │   │   └── SessionManager.kt
│   │   │   │   │               │   ├── remote/
│   │   │   │   │               │   │   ├── model/
│   │   │   │   │               │   │   │   ├── EventDtos.kt
│   │   │   │   │               │   │   │   ├── MovementDtos.kt
│   │   │   │   │               │   │   │   ├── ProductDtos.kt
│   │   │   │   │               │   │   │   ├── StockDtos.kt
│   │   │   │   │               │   │   │   └── TokenResponse.kt
│   │   │   │   │               │   │   ├── AuthInterceptor.kt
│   │   │   │   │               │   │   ├── InventoryApi.kt
│   │   │   │   │               │   │   └── NetworkModule.kt
│   │   │   │   │               │   └── repository/
│   │   │   │   │               │       ├── remote/
│   │   │   │   │               │       │   └── RemoteScanRepository.kt
│   │   │   │   │               │       └── MovementRepository.kt
│   │   │   │   │               ├── domain/
│   │   │   │   │               │   └── model/
│   │   │   │   │               │       ├── Movement.kt
│   │   │   │   │               │       ├── MovementType.kt
│   │   │   │   │               │       └── Product.kt
│   │   │   │   │               ├── ui/
│   │   │   │   │               │   ├── auth/
│   │   │   │   │               │   │   └── LoginActivity.kt
│   │   │   │   │               │   ├── events/
│   │   │   │   │               │   │   └── EventsActivity.kt
│   │   │   │   │               │   ├── home/
│   │   │   │   │               │   │   └── HomeActivity.kt
│   │   │   │   │               │   ├── movements/
│   │   │   │   │               │   │   ├── ConfirmMovementActivity.kt
│   │   │   │   │               │   │   ├── MovimientosActivity.kt
│   │   │   │   │               │   │   └── ResultActivity.kt
│   │   │   │   │               │   ├── products/
│   │   │   │   │               │   │   ├── ProductAdapter.kt
│   │   │   │   │               │   │   ├── ProductDetailActivity.kt
│   │   │   │   │               │   │   └── ProductListActivity.kt
│   │   │   │   │               │   ├── stock/
│   │   │   │   │               │   │   └── StockActivity.kt
│   │   │   │   │               │   └── ScanActivity.kt
│   │   │   │   │               ├── InventoryApp.kt
│   │   │   │   │               └── MainActivity.kt
│   │   │   │   ├── res/
│   │   │   │   │   ├── drawable/
│   │   │   │   │   │   ├── baseline_account_circle_24.xml
│   │   │   │   │   │   ├── ic_company_logo.png
│   │   │   │   │   │   ├── ic_launcher_background.xml
│   │   │   │   │   │   ├── ic_launcher_foreground.xml
│   │   │   │   │   │   ├── ic_profile.xml
│   │   │   │   │   │   └── ic_status.xml
│   │   │   │   │   ├── layout/
│   │   │   │   │   │   ├── activity_confirm_movement.xml
│   │   │   │   │   │   ├── activity_events.xml
│   │   │   │   │   │   ├── activity_home.xml
│   │   │   │   │   │   ├── activity_login.xml
│   │   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   │   ├── activity_movimientos.xml
│   │   │   │   │   │   ├── activity_product_detail.xml
│   │   │   │   │   │   ├── activity_product_list.xml
│   │   │   │   │   │   ├── activity_result.xml
│   │   │   │   │   │   ├── activity_scan.xml
│   │   │   │   │   │   ├── activity_stock.xml
│   │   │   │   │   │   ├── dialog_register.xml
│   │   │   │   │   │   └── item_product.xml
│   │   │   │   │   ├── menu/
│   │   │   │   │   │   └── home_menu.xml
│   │   │   │   │   ├── mipmap-anydpi-v26/
│   │   │   │   │   │   ├── ic_launcher.xml
│   │   │   │   │   │   └── ic_launcher_round.xml
│   │   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   │   ├── ic_launcher_foreground.webp
│   │   │   │   │   │   └── ic_launcher_round.webp
│   │   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   │   ├── ic_launcher_foreground.webp
│   │   │   │   │   │   └── ic_launcher_round.webp
│   │   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   │   ├── ic_launcher_foreground.webp
│   │   │   │   │   │   └── ic_launcher_round.webp
│   │   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   │   ├── ic_launcher_foreground.webp
│   │   │   │   │   │   └── ic_launcher_round.webp
│   │   │   │   │   ├── mipmap-xxxhdpi/
│   │   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   │   ├── ic_launcher_foreground.webp
│   │   │   │   │   │   └── ic_launcher_round.webp
│   │   │   │   │   ├── values/
│   │   │   │   │   │   ├── colors.xml
│   │   │   │   │   │   ├── strings.xml
│   │   │   │   │   │   └── themes.xml
│   │   │   │   │   ├── values-night/
│   │   │   │   │   │   └── themes.xml
│   │   │   │   │   └── xml/
│   │   │   │   │       ├── backup_rules.xml
│   │   │   │   │       └── data_extraction_rules.xml
│   │   │   │   ├── AndroidManifest.xml
│   │   │   │   └── ic_launcher-playstore.png
│   │   │   └── test/
│   │   │       └── java/
│   │   │           └── com/
│   │   │               └── example/
│   │   │                   └── inventoryapp/
│   │   │                       └── ExampleUnitTest.kt
│   │   ├── .gitignore
│   │   ├── build.gradle.kts
│   │   └── proguard-rules.pro
│   ├── gradle/
│   │   ├── wrapper/
│   │   │   ├── gradle-wrapper.jar
│   │   │   └── gradle-wrapper.properties
│   │   └── libs.versions.toml
│   ├── .gitignore
│   ├── build.gradle.kts
│   ├── Documentacion.md
│   ├── DocumentacionFront.md
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   └── settings.gradle.kts
├── .gitattributes
├── .gitignore
├── MER.png
├── ReadmeCRUDsprint2.md
└── README.md
```

---

## 📄 Licencia

Proyecto educativo (uso académico).
