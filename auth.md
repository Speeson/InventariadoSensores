# Auth y seguridad (InventariadoSensores)

Este documento resume de forma exhaustiva la parte de autenticacion y seguridad del backend (FastAPI). La idea es que puedas explicarlo en una exposicion y entender como encaja cada pieza.

---

## 1) Objetivo de la capa de auth

- Asegurar que solo usuarios registrados pueden acceder a endpoints protegidos.
- Emitir tokens JWT con expiracion para autenticar peticiones sin mantener sesiones en servidor.
- Aplicar roles (USER, MANAGER, ADMIN) para restringir acciones segun permisos.
- Proteger contrasenas con hashing (BCrypt) y nunca guardarlas en texto plano.

---

## 2) Flujo general de autenticacion

1. El cliente llama a POST `/auth/register` con email, password (y role opcional en este proyecto).
2. El backend valida los datos y guarda el usuario con password hash.
3. El backend genera un JWT con `sub` = email, `role`, y `exp` (expiracion).
4. El cliente guarda el token y lo envia en cada request protegida:
   - Header: `Authorization: Bearer <token>`
5. El backend decodifica el token, valida su firma y expiracion, carga el usuario y verifica permisos.

---

## 3) Archivos y clases clave

### 3.1 API de autenticacion

**`backend/app/api/routes/auth.py`**
- Define los endpoints `/auth/register` y `/auth/login`.
- Usa `OAuth2PasswordRequestForm` para login (espera `username` + `password`).
- Delegacion total a `auth_service` (logica de negocio).
- Convierte `AuthError` en errores HTTP 400/401.

Endpoints:
- `POST /auth/register` -> crea usuario + devuelve token
- `POST /auth/login` -> valida credenciales + devuelve token

### 3.2 Dependencias de seguridad

**`backend/app/api/deps.py`**
- `get_current_user`: decodifica JWT, valida `sub`, busca el usuario en la DB. Si falla, lanza 401.
- `require_roles(*allowed)`: wrapper para comprobar si el rol del usuario esta permitido. Si no, lanza 403.

Esta capa se inyecta en endpoints con `Depends` para proteger rutas.

### 3.3 Seguridad del header Authorization

**`backend/app/api/security.py`**
- Define `HTTPBearer` y la funcion `get_bearer_token`.
- Valida que el esquema sea exactamente `Bearer`.
- Extrae el token del header para que `deps.py` lo procese.

### 3.4 Logica de auth

**`backend/app/services/auth_service.py`**
- `register`: comprueba email existente, hashea password, crea usuario y genera JWT.
- `login`: valida credenciales (password vs hash) y genera JWT.
- `AuthError`: excepcion de dominio para separar errores de auth de errores HTTP.

### 3.5 Hashing y JWT

**`backend/app/core/security.py`**
- `hash_password`: usa BCrypt para hashear contrasenas.
- `verify_password`: compara password plano contra hash.
- `create_access_token`: crea JWT con `sub`, `role`, `exp`.

Parametros:
- `JWT_SECRET`: clave secreta (debe mantenerse privada).
- `JWT_ALGORITHM`: por defecto `HS256`.
- `ACCESS_TOKEN_EXPIRE_MINUTES`: expiracion de tokens.

### 3.6 Modelo de usuario

**`backend/app/models/user.py`**
- Define la tabla `users` con SQLAlchemy.
- Campos clave: `email`, `username`, `password_hash`, `role`.
- Timestamps `created_at` y `updated_at`.

### 3.7 Roles del sistema

**`backend/app/models/enums.py`**
- `UserRole`: enum con `USER`, `MANAGER`, `ADMIN`.
- Evita roles arbitrarios y facilita validacion.

### 3.8 Repositorio de usuarios

**`backend/app/repositories/user_repo.py`**
- `get_by_email`: busca usuario por email normalizado.
- `create_user`: crea usuario, valida rol con `UserRole` y persiste en DB.

### 3.9 Esquemas (DTOs)

**`backend/app/schemas/auth.py`**
- `RegisterRequest`: valida email y password (min 8).
- `TokenResponse`: estructura estandar del token.

**`backend/app/schemas/user.py`**
- `UserMeResponse`: respuesta de `/users/me` sin datos sensibles.

---

## 4) Proteccion de endpoints con roles

Ejemplo en `backend/app/api/routes/users.py`:
- `/users/me` requiere usuario autenticado (`get_current_user`).
- `/users/admin-only` requiere rol `ADMIN` (`require_roles("ADMIN")`).

Si el rol no coincide, se devuelve 403.

---

## 5) CORS (Cross-Origin Resource Sharing)

**Archivo: `backend/app/main.py`**
- Se configura `CORSMiddleware` para permitir que el frontend (web o Android en emulador) acceda a la API.
- Origenes permitidos:
  - `http://localhost:3000`
  - `http://127.0.0.1:3000`
  - `http://10.0.2.2:3000` (emulador Android)
- Se puede modificar con variable `CORS_ORIGINS` (lista separada por comas).
- Se permiten metodos comunes y header `Authorization` para enviar el Bearer token.

Si CORS no esta bien configurado, el navegador bloquea peticiones aunque el backend funcione.

---

## 6) Variables de entorno clave

En `.env` (o docker-compose):
- `DATABASE_URL`
- `JWT_SECRET`
- `JWT_ALGORITHM`
- `ACCESS_TOKEN_EXPIRE_MINUTES`

Importante: **nunca** subir `JWT_SECRET` a repositorios publicos.

---

## 7) Resumen del flujo en Swagger

1. Registrar o hacer login para obtener `access_token`.
2. En Swagger: boton **Authorize** y pegar solo el token (sin "Bearer ").
3. Probar endpoints protegidos.

---

## 8) Buenas practicas destacables

- Passwords siempre hasheadas (BCrypt).
- JWT con expiracion.
- Separacion clara por capas: rutas -> servicios -> repositorios.
- Roles centralizados con enum y dependencias reutilizables.
- CORS controlado para clientes autorizados.

---

## 9) Ideas para mencionar en la exposicion

- Por que JWT y no sesiones: escalabilidad y simplicidad en APIs REST.
- Diferencia entre autenticacion (quien eres) y autorizacion (que puedes hacer).
- Importancia de `Authorization: Bearer` en cada request.
- Por que el rol no deberia poder elegirse en registro en produccion.
- CORS es imprescindible para clientes web y emulador Android.

---

## 10) Mapa rapido de archivos

- `backend/app/api/routes/auth.py`
- `backend/app/api/deps.py`
- `backend/app/api/security.py`
- `backend/app/services/auth_service.py`
- `backend/app/core/security.py`
- `backend/app/models/user.py`
- `backend/app/models/enums.py`
- `backend/app/repositories/user_repo.py`
- `backend/app/schemas/auth.py`
- `backend/app/schemas/user.py`
- `backend/app/main.py` (CORS)

Fin.
