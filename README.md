Propuesta estructura general del proyecto



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
