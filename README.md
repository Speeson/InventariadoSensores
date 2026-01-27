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

### Android
- Login/registro contra la API
- Listado y detalle de productos
- Escaneo con cámara (ML Kit)
- Registro de movimiento desde barcode y ubicación
- Pantallas de stocks/eventos (según implementación)

---

## 📌 Requisitos del enunciado (Actividad 4) — estado

| Requisito | Estado | Comentario |
|---|---:|---|
| Auth con JWT + roles | ✅ | Implementado en backend |
| CRUD productos/stocks | ✅ | Incluye filtros + paginación |
| Escaneo móvil | ✅ | Android con ML Kit |
| Simulación de sensores | ✅ | Endpoints de eventos |
| Procesamiento de eventos | ⚠️ | En este sprint el evento impacta stock al instante (sin cola) |
| Historial de movimientos | ✅ | Endpoint + filtros |
| Auditoría de cambios | ⏳ | Planificado (S3) |
| Alertas stock bajo | ⏳ | Planificado (S2) |
| Importación CSV | ⏳ | Planificado (S3) |
| Reportes | ⏳ | Planificado (S2–S3) |
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
  -d '{"email":"admin@demo.com","password":"admin123","role":"ADMIN"}'
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
│   │   │   └── dcc886ba14d3_initial_schema.py
│   │   ├── env.py
│   │   ├── README
│   │   └── script.py.mako
│   ├── app/
│   │   ├── __pycache__/
│   │   │   └── main.cpython-313.pyc
│   │   ├── api/
│   │   │   ├── routers/
│   │   │   │   └── __pycache__/
│   │   │   │       └── events.cpython-313.pyc
│   │   │   ├── routes/
│   │   │   │   ├── auth.py
│   │   │   │   ├── events.py
│   │   │   │   ├── movements.py
│   │   │   │   ├── products.py
│   │   │   │   ├── stocks.py
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
│   │   │   ├── audit_log.py
│   │   │   ├── category.py
│   │   │   ├── entity.py
│   │   │   ├── enums.py
│   │   │   ├── event.py
│   │   │   ├── movement.py
│   │   │   ├── product.py
│   │   │   ├── stock.py
│   │   │   └── user.py
│   │   ├── repositories/
│   │   │   ├── __pycache__/
│   │   │   │   └── memory_repo.cpython-313.pyc
│   │   │   ├── event_repo.py
│   │   │   ├── memory_repo.py
│   │   │   ├── movement_repo.py
│   │   │   ├── product_repo.py
│   │   │   ├── stock_repo.py
│   │   │   └── user_repo.py
│   │   ├── schemas/
│   │   │   ├── __pycache__/
│   │   │   │   └── event.cpython-313.pyc
│   │   │   ├── __init__.py
│   │   │   ├── auth.py
│   │   │   ├── event.py
│   │   │   ├── movement.py
│   │   │   ├── product.py
│   │   │   ├── stock.py
│   │   │   └── user.py
│   │   ├── services/
│   │   │   ├── __pycache__/
│   │   │   │   └── event_service.cpython-313.pyc
│   │   │   ├── auth_service.py
│   │   │   ├── event_service.py
│   │   │   └── inventory_service.py
│   │   ├── __init__.py
│   │   └── main.py
│   ├── scripts/
│   │   ├── __init__.py
│   │   ├── seed3_db.py
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
└── README.md
```

---

## 📄 Licencia

Proyecto educativo (uso académico).
