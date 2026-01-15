# 📦 Sistema de Gestión de Inventario con Sensores Simulados

Proyecto transversal – **2º Desarrollo de Aplicaciones Multiplataforma (2ª Evaluación)**  

Módulos implicados:
- Programación de Servicios y Procesos  
- Acceso a Datos  
- Programación Multimedia y Dispositivos Móviles  

---

## 🧩 Descripción del proyecto

Este proyecto consiste en el desarrollo de un **Sistema de Gestión de Inventario** que permite controlar productos y stock mediante **eventos simulados de sensores IoT** y **escaneo móvil desde una aplicación Android**.

El sistema sigue el patrón **Modelo–Vista–Controlador (MVC)** y está diseñado para ser escalable, seguro y multiplataforma, utilizando una **API REST** como núcleo del sistema.

---

## 🏗️ Arquitectura general

- **Vista (Frontend)**: Aplicación Android  
- **Controlador (Backend)**: API REST en Python  
- **Modelo (Datos)**: Base de datos PostgreSQL  

La comunicación entre capas se realiza mediante **JSON sobre HTTP**, siguiendo principios REST.

---

## 🛠️ Stack tecnológico

### Backend (Python)
- FastAPI (ASGI)
- SQLAlchemy 2.0 + Alembic
- PostgreSQL
- JWT (OAuth2)
- Redis + Celery (eventos y procesos en background)
- Docker + docker-compose
- pytest

### Android
- Kotlin
- Arquitectura MVVM
- Retrofit + OkHttp
- Room (base de datos local)
- ML Kit (escaneo de códigos de barras / QR)
- WorkManager (sincronización básica)
- Firebase Cloud Messaging (opcional)

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

## 🔐 Requisitos funcionales

- Autenticación con JWT y roles (User, Manager, Admin)
- CRUD de productos y stock
- Escaneo de códigos de barras desde Android
- Simulación de sensores IoT (eventos de entrada/salida)
- Procesamiento de eventos y actualización automática del stock
- Historial de movimientos
- Auditoría de cambios
- Búsqueda por SKU, nombre y categoría
- Importación de catálogo (CSV)
- Reportes de consumo y rotación
- Alertas por stock bajo (email/push)

---

## ⚙️ Requisitos transversales

- Hash de contraseñas (BCrypt / Argon2)
- JWT con expiración
- Control de roles y permisos
- Configuración CORS segura
- Paginación y filtros en listados
- Logs y auditoría
- Tests:
  - Unitarios
  - Integración
  - Contrato (OpenAPI)
- CI/CD con GitHub Actions
- Contenedores Docker
- Documentación de la API (Swagger / OpenAPI)

---

## 🗂️ Estructura del proyecto

```
sistemaInventariadoSensores/
├─ README.md
├─ .gitignore
├─ .env.example
├─ docker-compose.yml
├─ docs/
│  ├─ api/
│  │  ├─ openapi_notes.md
│  │  └─ postman_collection.json (opcional)
│  ├─ arquitectura/
│  │  ├─ decisiones-adr.md
│  │  └─ diagrama-mvc.md
│  └─ entregas/
│     ├─ sprint1.md
│     ├─ sprint2.md
│     └─ sprint3.md
├─ infra/
│  ├─ nginx/ (opcional si hacéis reverse proxy)
│  ├─ postgres/
│  │  └─ init.sql (opcional)
│  └─ scripts/
│     ├─ seed_db.sh
│     └─ reset_env.sh
├─ backend/
│  ├─ pyproject.toml (o requirements.txt)
│  ├─ Dockerfile
│  ├─ alembic.ini
│  ├─ alembic/
│  │  ├─ env.py
│  │  └─ versions/
│  ├─ app/
│  │  ├─ main.py
│  │  ├─ core/
│  │  │  ├─ config.py          # env vars, settings
│  │  │  ├─ logging.py         # configuración logs
│  │  │  └─ security.py        # JWT, password hashing helpers
│  │  ├─ api/
│  │  │  ├─ deps.py            # get_current_user, require_roles
│  │  │  └─ routers/
│  │  │     ├─ auth.py
│  │  │     ├─ products.py
│  │  │     ├─ stocks.py
│  │  │     ├─ movements.py
│  │  │     ├─ events.py
│  │  │     ├─ reports.py
│  │  │     └─ health.py
│  │  ├─ models/               # SQLAlchemy models
│  │  │  ├─ user.py
│  │  │  ├─ product.py
│  │  │  ├─ stock.py
│  │  │  ├─ movement.py
│  │  │  └─ event.py
│  │  ├─ schemas/              # Pydantic schemas
│  │  │  ├─ auth.py
│  │  │  ├─ user.py
│  │  │  ├─ product.py
│  │  │  ├─ stock.py
│  │  │  ├─ movement.py
│  │  │  └─ event.py
│  │  ├─ services/             # lógica de negocio
│  │  │  ├─ auth_service.py
│  │  │  ├─ inventory_service.py
│  │  │  ├─ event_service.py
│  │  │  ├─ report_service.py
│  │  │  └─ notification_service.py (S2)
│  │  ├─ repositories/         # acceso a datos (CRUD DB)
│  │  │  ├─ user_repo.py
│  │  │  ├─ product_repo.py
│  │  │  ├─ stock_repo.py
│  │  │  ├─ movement_repo.py
│  │  │  └─ event_repo.py
│  │  ├─ db/
│  │  │  ├─ session.py          # engine/sessionmaker
│  │  │  └─ base.py             # Base declarative
│  │  ├─ workers/
│  │  │  ├─ celery_app.py
│  │  │  ├─ tasks_events.py     # consumidor eventos (S2)
│  │  │  ├─ tasks_alerts.py     # alertas (S2)
│  │  │  └─ tasks_reports.py    # reportes programados (S2/S3)
│  │  ├─ integrations/
│  │  │  └─ redis_client.py
│  │  └─ utils/
│  │     ├─ pagination.py
│  │     └─ errors.py
│  ├─ tests/
│  │  ├─ unit/
│  │  ├─ integration/
│  │  └─ conftest.py
│  └─ scripts/
│     ├─ sensor_simulator.py    # generador de eventos (S1 simple, S2 Redis)
│     └─ seed_data.py
├─ android/
│  ├─ build.gradle
│  ├─ settings.gradle
│  └─ app/
│     ├─ build.gradle
│     └─ src/main/
│        ├─ AndroidManifest.xml
│        ├─ java/.../ui/
│        ├─ java/.../data/
│        ├─ java/.../domain/
│        └─ res/
└─ .github/
   └─ workflows/
      ├─ backend_ci.yml
      ├─ android_ci.yml (opcional)
      └─ docker_build.yml

```

---

## 🧪 Metodología de trabajo

Se utiliza la metodología **Scrum**, gestionando el proyecto con **Jira**:
- Epics
- Historias de usuario
- Subtareas
- Story Points
- Sprints

Cada sprint cuenta con un **Definition of Done** común para todo el equipo.

---

## 🏃‍♂️ Planificación por sprints

### 🟢 Sprint 1 – Base funcional
- Autenticación y roles
- CRUD de productos y stock
- Escaneo móvil
- Eventos básicos simulados
- Sincronización básica
- Entorno Docker
- CI inicial

### 🟡 Sprint 2 – Procesamiento y análisis
- Consumidor de eventos (Redis + Celery)
- Alertas por stock bajo
- Reportes y estadísticas
- Adjuntos (opcional)

### 🔵 Sprint 3 – Calidad y cierre
- Importación / exportación (CSV)
- Auditoría avanzada
- Accesibilidad Android
- Pruebas finales
- Hardening de seguridad

---

## ✅ Definition of Done (resumen)

Una historia se considera terminada cuando:
- La funcionalidad está implementada y demostrable
- El código compila y funciona correctamente
- Cumple los requisitos de seguridad
- Está documentada
- Pasa las pruebas básicas
- Está integrada con el resto del sistema

---

## 🚀 Puesta en marcha (backend)

```bash
docker-compose up --build
```

La API estará disponible en:
- http://localhost:8000
- Documentación Swagger: http://localhost:8000/docs

---

## 📄 Licencia

Proyecto educativo desarrollado para el ciclo formativo de **Desarrollo de Aplicaciones Multiplataforma (DAM)**.
