# Sistema de Gestion de Inventario con Sensores Simulados

Proyecto transversal 2o Desarrollo de Aplicaciones Multiplataforma.

---

## Descripcion

Sistema de gestion de inventario con eventos simulados de sensores IoT y app Android.
Backend con API REST en FastAPI y base de datos PostgreSQL.

---

## Estado actual (lo implementado)

- Backend API con FastAPI.
- Autenticacion JWT y roles (USER, MANAGER, ADMIN).
- Registro y login con hashing de contrasenas (BCrypt).
- Endpoints protegidos y control de permisos.
- Base de datos PostgreSQL con Alembic.
- Docker Compose para levantar API + DB.
- Entry point que aplica migraciones y ejecuta seed.

---

## Estructura de carpetas (backend)

- `backend/app/main.py` arranque de FastAPI y registro de routers.
- `backend/app/api/routes/` endpoints (auth, users).
- `backend/app/api/deps.py` dependencias de seguridad (`get_current_user`, `require_roles`).
- `backend/app/api/security.py` esquema HTTPBearer para Swagger.
- `backend/app/core/config.py` carga de variables de entorno (Settings).
- `backend/app/core/security.py` hashing y JWT.
- `backend/app/db/session.py` engine y SessionLocal.
- `backend/app/db/deps.py` dependencia `get_db`.
- `backend/app/models/` modelos SQLAlchemy (incluye `user.py`).
- `backend/app/repositories/` acceso a datos (UserRepo).
- `backend/app/schemas/` validacion y respuesta (Pydantic).
- `backend/app/services/` logica de negocio (auth_service).
- `backend/alembic/` migraciones y `env.py`.
- `backend/entrypoint.sh` espera DB, migra y arranca API.
- `backend/scripts/seed_db.py` seed inicial de datos.
- `backend/Dockerfile` imagen del backend (dev).
- `backend/docker-compose.yml` servicios API + DB.

---

## Variables de entorno (backend/.env)

```
DATABASE_URL=postgresql+psycopg://inventory_user:inventory_pass@localhost:5432/inventory_db
JWT_SECRET=change_me_super_secret
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30
```

Opcional para el seed:
```
SEED_ADMIN_EMAIL=admin@example.com
SEED_ADMIN_PASSWORD=admin1234
SEED_ADMIN_USERNAME=admin@example.com
```

---

## Docker (dev) - levantar la aplicacion

Desde `backend/`:

```bash
cd backend
docker compose up --build -d
```

Servicios:
- API: http://localhost:8000
- Swagger: http://localhost:8000/docs

Logs:
```bash
docker compose logs -f api
```

Parar:
```bash
docker compose down
```

Reset completo (incluye volumen DB):
```bash
docker compose down -v
```

---

## Autenticacion y Seguridad

### Endpoints principales
- `POST /auth/register` crea usuario y devuelve `access_token`.
- `POST /auth/login` autentica y devuelve `access_token`.
- `GET /users/me` devuelve el usuario autenticado.
- `GET /users/admin-only` acceso restringido a `ADMIN`.

### Flujo en Swagger
1) Registrar o hacer login para obtener `access_token`.
2) En Swagger, boton **Authorize** y pegar solo el token (sin "Bearer ").
3) Probar endpoints protegidos.

### Archivos clave
- `backend/app/api/routes/auth.py` endpoints de registro y login.
- `backend/app/services/auth_service.py` validacion de credenciales y creacion de token.
- `backend/app/core/security.py` hashing y `create_access_token`.
- `backend/app/api/deps.py` validacion de token y roles.
- `backend/app/api/security.py` HTTPBearer para Swagger.
- `backend/app/models/user.py` modelo User con roles y timestamps.

---

## Base de datos y migraciones

Credenciales por defecto en `backend/docker-compose.yml`:
- DB: `inventory_db`
- User: `inventory_user`
- Password: `inventory_pass`

Migraciones Alembic:
- `entrypoint.sh` ejecuta `alembic upgrade head` al arrancar.
- Migraciones en `backend/alembic/versions/`.

Ejecucion manual:
```bash
cd backend
alembic upgrade head
```

---

## Seed de datos

El `entrypoint.sh` ejecuta:
```
python -m scripts.seed_db
```

Por defecto crea un admin si no existe. Configurable con variables `SEED_ADMIN_*`.

---

## Ejecucion local sin Docker (opcional)

```bash
cd backend
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --reload
```

---

## Notas de seguridad

- No guardar contrasenas en texto plano.
- No exponer `JWT_SECRET` en repositorios publicos.
- Cambiar credenciales por defecto en entornos reales.
