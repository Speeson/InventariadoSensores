# Sprint 3 - Cambios y decisiones

Fecha: 2026-02-05

## Movimientos: añadir `delta` para auditoría

Objetivo: conservar el signo del cambio de stock en todos los movimientos.

Reglas:
- `quantity` es magnitud positiva en IN/OUT/TRANSFER.
- `delta` es el cambio con signo:
  - IN: `delta = +quantity`
  - OUT: `delta = -quantity`
  - ADJUST: `delta = input` (puede ser + o -)
- En ADJUST, `quantity` también se guarda con signo (igual a `delta`).

Frontend:
- En Movimientos, el campo `cantidad` se usa como **delta** cuando el tipo es ADJUST.
- Hint ADJUST: `(-)Negativo para retirar | (+)Positivo para introducir`.

Backend:
- Nueva columna `movements.delta` (Integer).
- Migración backfill:
  - OUT: `delta = -quantity`
  - IN/ADJUST: `delta = quantity`
  - Nota: los ADJUST históricos no preservaban signo, por lo que su `delta` queda con el valor previo de `quantity`.

## Etiquetas de producto (SVG Code 128)

Objetivo: generar y almacenar una etiqueta SVG al crear/actualizar un producto, con layout:
- Título empresa: `IoTrack`
- SKU a la derecha
- Código de barras (Code 128)
- Texto del barcode debajo

Backend:
- Servicio `label_service` genera SVG y lo guarda en `backend/storage/labels/`.
- Endpoint protegido `GET /products/{id}/label.svg` devuelve la etiqueta (genera si no existe).
- Endpoint `POST /products/{id}/label/regenerate` para regenerar una etiqueta puntual.
- Regenera etiqueta cuando cambia el barcode.

Frontend (Android):
- Botón de etiqueta (icono impresora) en la lista de productos.
- Nueva pantalla con preview (WebView).
- Acciones: Descargar SVG, Descargar PDF (export local desde WebView), Imprimir/Guardar PDF.
- Preview centrado y escalado para ocupar la pantalla.
- WebView sin caché para evitar SVG obsoletos.
- Iconos de copiar/imprimir en lista más grandes.
- Botón "Regenerar etiqueta" en la pantalla de preview.
- Botón "Regenerar etiqueta" bloqueado por rol (candado) cuando no hay permisos.
- Gradiente de iconos aplicado a las tarjetas del Home, campana de alertas y menú lateral.
- Título "IoTrack" con el mismo gradiente de iconos.

Seed:
- Para generar etiquetas en seed: `SEED_LABELS=1` al ejecutar `backend/scripts/seed_db.py`.

## Listados: ordenación por API

Objetivo: permitir ordenar resultados en backend para listas y reportes.

Endpoints con orden:
- Productos: `order_by=id|created_at`, `order_dir=asc|desc` (por defecto `id asc`).
- Categorías: `order_by=id`, `order_dir=asc|desc` (por defecto `id asc`).
- Stock: `order_by=id`, `order_dir=asc|desc` (por defecto `id asc`).
- Movimientos: `order_by=created_at`, `order_dir=asc|desc` (por defecto `created_at desc`).
- Eventos: `order_by=created_at`, `order_dir=asc|desc` (por defecto `created_at desc`).
- Reportes:
  - Top consumidos: `order_dir=asc|desc` (por defecto `desc`).
  - Rotación: `order_dir=asc|desc` (por defecto `desc`).

## Plantilla UI: Eventos, Stock y Productos

Objetivo: unificar estructura visual con desplegables Crear/Buscar, paginación compacta y botones con estilo morado suave.

**Eventos**
- Header simple: back + título con gradiente + alertas.
- Crear/Buscar en dos fracciones desplegables (exclusivos).
- Búsqueda por tipo (IN/OUT o SENSOR_IN/SENSOR_OUT), producto (ID o nombre) y source (SCAN/MANUAL).
- Paginación a 5 registros por página; cuando hay filtros, paginación local del resultado filtrado.
- Botones de acción (Crear/Buscar/Limpiar/Recargar) con `bg_button_soft_purple`.
- Dropdowns con icono de flecha hacia abajo con gradiente.

**Stock**
- Misma plantilla de eventos (crear/buscar + listado).
- Crear stock: Product ID o nombre, Location (dropdown), Cantidad. Botón en morado suave.
- Buscar stock: producto (ID o nombre), location (dropdown), cantidad.
- Paginación a 5 registros; filtros con paginación local.
- Tarjeta de stock: el ID se muestra debajo del icono (ya no “Stock #X” en el título).
- Dropdown de locations ordenado por ID numérico y con opción vacía.

**Productos**
- Misma plantilla de stock.
- Crear producto: SKU, nombre, barcode (13 dígitos), categoría (dropdown ordenado por ID y con opción vacía).
- Buscar producto: ID/nombre, SKU, barcode, categoría (dropdown).
- Paginación a 5 registros; filtros con paginación local.
- `ProductDetail` queda solo para edición (crear se hace desde listado).

## Etiquetas Niimbot (B1) + integración SDK oficial

Objetivo: imprimir etiquetas 50x30 mm directamente en Niimbot B1 desde la app, manteniendo fallback a apertura de app Niimbot.

Android:
- Integración del SDK oficial Niimbot (AAR/JAR) en `android/app/libs`.
- Nuevo flujo de impresión directa por Bluetooth:
  - Escaneo de impresoras compatibles.
  - Priorización de dispositivos emparejados en la lista.
  - Recordatorio de última impresora usada (MAC) para impresión directa en siguientes intentos.
  - Si la impresora recordada no está disponible, fallback automático al diálogo de selección.
- Preparación de bitmap para etiqueta 50x30 con margen configurable.
- Conservado el flujo anterior de "Abrir app Niimbot" como alternativa.

UI/UX en `LabelPreview`:
- Reorganización de botones:
  - Descargar SVG / Descargar PDF
  - Regenerar etiqueta
  - Botón impresión sistema + botón Niimbot
- Botón Niimbot abre diálogo con dos acciones:
  - Abrir app Niimbot
  - Impresión directa SDK
- Diálogos personalizados:
  - Búsqueda Bluetooth (animación)
  - Conectando con impresora (animación)
  - Imprimiendo etiqueta (animación)
  - Error de impresión/conexión (popup error con animación dedicada)
- Fondos de diálogo ajustados (sin recuadro exterior no deseado) y layout más compacto para listas/dispositivos.

Permisos:
- Solicitud y validación de permisos Bluetooth (Android S+ y compatibilidad versiones anteriores).

Compatibilidad por rol:
- En rol `USER`, acceso a `LabelPreview` bloqueado desde listado de productos (icono impresora con candado).

## Cache híbrido (Redis + Room) y modo offline

Objetivo: acelerar lecturas en online y permitir uso en offline con datos cacheados.

Backend (Redis):
- Cache de listados: productos, stock, eventos, movimientos, categorías, umbrales, ubicaciones, reportes.
- TTLs:
  - Listados: 300s
  - Catálogos estáticos (categorías/ubicaciones): 3600s
- Invalida cache en create/update/delete (y movimientos invalida reportes).
- Nota seed: los movimientos del seed solo se insertan si la tabla está vacía (evita duplicados al reiniciar contenedor).

Android (Room + cache-first):
- Cache local para listados (Room).
- Estrategia cache-first: mostrar cache al entrar y refrescar en background.
- Offline: si no hay red, se usa cache local sin bloqueo.
- Dropdowns (categorías/ubicaciones) leen de cache si la API falla.
- Resolución de nombres de producto en offline para eventos/stock/movimientos.
- Indicador “offline” en tarjetas: icono + color de texto.

Comportamiento de la caché local (Room / SQLite):
- Persistencia: la caché local se guarda en SQLite usando Room en el fichero `app_cache.db` (almacenamiento interno de la app). Los datos **persisten entre sesiones** (reinicio de la app o del dispositivo) hasta que se borren explícitamente, el usuario limpie los datos de la app o desinstale la aplicación.
- Límite de entradas (`maxEntries`): `CacheStore.put` llama a `dao.prune(maxEntries)` después de insertar. Por defecto `maxEntries = 500` (ver `CacheStore.put`) — cuando se supera ese número se eliminan las entradas más antiguas (por `updatedAt`) para mantener el límite.
- Invalidación: se puede invalidar por prefijo con `CacheStore.invalidatePrefix(prefix)` que ejecuta `deleteByPrefix` en la tabla `cache_entries`.
- Comportamiento al insertar: cada `put` actualiza `updatedAt` a `System.currentTimeMillis()` y reemplaza entradas por `key` (estrategia REPLACE). Inmediatamente después se aplica `prune` para recortar el tamaño.
- Opciones de cambio: si se desea que la caché no persista entre sesiones, se podría usar `Room.inMemoryDatabaseBuilder(...)` en lugar de `databaseBuilder`. No hay expiración temporal automática por antigüedad (TTL) implementada actualmente; podría añadirse si se quiere borrar entradas más antiguas que X días.


## Indicador visual Online/Offline

Objetivo: indicar estado de conexión de forma clara y no intrusiva.

- Barra superior fina (4dp) en todas las pantallas principales.
- Verde cuando hay conexión, roja cuando está offline.
- Avisos emergentes centrados cuando se pierde/restaura la conexión (sin spam).

## Login: autocompletar y recordar email + validación de sesión

Objetivo: mejorar UX en login y reforzar seguridad.

- Autocompletado de emails usados recientemente (hasta 8).
- Opción “Recordar email” para precargarlo en el login.
- Validación de sesión al abrir: si hay token, se valida con `/users/me` antes de entrar.
- Si no hay red y existe sesión válida previa (cache de usuario/rol), se permite entrar en modo offline.
- Si no hay sesión válida previa, se mantiene en login.

## Importación CSV (Eventos + Transferencias) con auditoría y review

Objetivo: importar lotes CSV como **eventos** (IN/OUT/ADJUST) y **transferencias** (dos movimientos con mismo `transfer_id`), con trazabilidad completa, cuarentena y revisión.

Reglas clave:
- CSV de eventos: `type` ∈ `IN|OUT|ADJUST`.
- SKU y barcode **siempre obligatorios**.
- `category_id` y `location_id` **numéricos y deben existir** (no se crean).
- Si no existe el producto (sku/barcode), se **crea** (solo si no hay conflictos).
- Si hay conflicto o duda razonable → **review** (suggest & review).
- Errores no bloquean el lote (cuarentena).

Tablas nuevas:
- `import_batches`: auditoría por lote (usuario, fecha, totales).
- `import_errors`: filas con error (cuarentena).
- `import_reviews`: filas con duda/sugerencias.

Endpoints:
- `POST /imports/events/csv`
- `POST /imports/transfers/csv`
- `GET /imports/reviews`
- `POST /imports/reviews/{id}/approve`
- `POST /imports/reviews/{id}/reject`

CSV ejemplos (carpeta):
- `backend/context/import_samples/events_sample.csv`
- `backend/context/import_samples/events_sample_review.csv`
- `backend/context/import_samples/transfers_sample.csv`

Flujo de review:
- `approve` aplica la fila y genera movimientos/transferencias.
- `reject` manda la fila a `import_errors`.

Seed extra para pruebas:
- `backend/scripts/seed2_db.py` crea un batch de prueba con 1 error + 1 review.
  - Ejecutar **desde** `backend/`:
    - `docker compose exec -w /app api python -m scripts.seed2_db`

## Notificaciones de errores 500 (usuario vs admin)

Objetivo: evitar mensajes técnicos para usuarios y managers, pero dar detalle al admin.

Android:
- Si la API responde 500:
  - `USER` / `MANAGER`: mensaje amigable según endpoint.
  - `ADMIN`: mensaje técnico con código y ruta (ej: `Error 500 en /stocks/10`).
  
Implementación:
- `android/app/src/main/java/com/example/inventoryapp/data/remote/NetworkModule.kt`

## Frontend Importaciones (Android)

Objetivo: pantalla de importación con tabs, subida de CSV y revisión con bottom sheet.

UI:
- Nueva tarjeta en Home: **Importar CSV** (grid consistente).
- Pantalla `Importar CSV` con tabs:
  - Eventos
  - Transferencias
  - Revisiones
- Formulario de import:
  - Selector CSV
  - `dry-run`
  - `fuzzy threshold`
  - Resumen + lista de errores
- Reviews:
  - Lista compacta
  - Bottom sheet con payload legible, sugerencias y botón “Ver JSON”
  - Acciones: Aprobar / Rechazar

Archivos clave:
- `android/app/src/main/res/layout/activity_imports.xml`
- `android/app/src/main/res/layout/fragment_import_form.xml`
- `android/app/src/main/res/layout/fragment_import_reviews.xml`
- `android/app/src/main/res/layout/dialog_import_review_bottom_sheet.xml`
- `android/app/src/main/java/com/example/inventoryapp/ui/imports/ImportsActivity.kt`
- `android/app/src/main/java/com/example/inventoryapp/ui/imports/ImportFormFragment.kt`
- `android/app/src/main/java/com/example/inventoryapp/ui/imports/ImportEventsFragment.kt`
- `android/app/src/main/java/com/example/inventoryapp/ui/imports/ImportTransfersFragment.kt`
- `android/app/src/main/java/com/example/inventoryapp/ui/imports/ImportReviewsFragment.kt`
- `android/app/src/main/java/com/example/inventoryapp/ui/imports/ImportsPagerAdapter.kt`

Notas:
- El import usa `fuzzy_threshold` (default `0.9`) para sugerir duplicados.
- Las transferencias se aplican como **dos movimientos** con el mismo `transfer_id`.

## Alertas en tiempo real (WebSocket)

Objetivo: notificar alertas en vivo sin cambiar de pantalla.

Backend:
- WebSocket: `ws://<host>:8000/ws/alerts?token=JWT`
- Las alertas se emiten en tiempo real desde `alert_repo.create_alert` (stock bajo/agotado, movimiento grande, transferencia completa, import con errores).
- Para Docker: **no usar `--reload`** en Uvicorn (rompe WS). Se controla con `UVICORN_RELOAD=0/1` en `backend/entrypoint.sh`.

Frontend (Android):
- Cliente WS activo y reconexiÃ³n automÃ¡tica al volver al foreground.
- Popup centrado con tÃ­tulo, detalles e icono segÃºn tipo:
  - Stock bajo (amarillo), Stock agotado (rojo), Transferencia completa (verde), Movimiento grande (violeta), ImportaciÃ³n con errores (azul).
- Auto-cierre a los 10s si no se cierra manualmente.
- Tarjeta flotante con sombreado, fondo atenuado (dim) y animaciÃ³n de entrada/salida.
- Color de tarjeta segÃºn tipo de alerta + animaciÃ³n pulse en el icono.
- Colas de alertas: si llegan varias, se muestran una a una con contador "1 de N".
- Badge de notificaciones se refresca en tiempo real al recibir alertas WS.

## Firebase Cloud Messaging (FCM)

Objetivo: push notifications cuando la app estÃ¡ en background o cerrada.

Backend:
- Tabla `fcm_tokens` (user_id, token, device_id, platform).
- Endpoint `POST /users/fcm-token` para registrar/actualizar token.
- Envio FCM al crear alertas:
  - Stock bajo, Stock agotado, Movimiento grande, ImportaciÃ³n con errores.
- ImportaciÃ³n completada: push si `total_rows` >= `IMPORT_COMPLETED_PUSH_MIN_ROWS` (default 50).
- Requiere credenciales Firebase: `FCM_CREDENTIALS_JSON=/path/service-account.json`.

Android:
- Firebase Messaging integrado (`firebase-messaging`).
- Registro automÃ¡tico de token al login y en refresh (`onNewToken`).
- Servicio `FcmService` muestra notificaciÃ³n del sistema.
- Requiere `google-services.json` en `android/app/`.

Infra:
- Credenciales backend en `backend/credentials/firebase-service-account.json`.
- Docker: monta credenciales y exporta `FCM_CREDENTIALS_JSON=/app/credentials/firebase-service-account.json`.
- `.gitignore` en `backend/credentials/` para no subir JSON a git.

## WorkManager (sync offline en background)

Objetivo: enviar la cola offline aunque la app estÃ© cerrada, con reintentos y restricciones de red.

Android:
- Dependencia: `androidx.work:work-runtime-ktx`.
- Worker `OfflineSyncWorker` llama a `OfflineSyncer.flush()` y registra `SystemAlert` cuando hay envÃ­os o motivo de parada.
- Periodic work cada 15 minutos (mÃ­nimo permitido) con `NetworkType.CONNECTED`.
- One‑time work al iniciar la app para vaciar la cola cuanto antes.
- Al encolar un pendiente offline, se programa un one‑time adicional (si hay red) para intentar vaciar la cola pronto.
- Backoff exponencial en reintentos (`10s` base).
- En mÃ³vil (no emulador) el worker se eleva a foreground si hay permiso de notificaciones, para aumentar fiabilidad.
- NotificaciÃ³n del worker: canal `offline_sync` con texto “Sincronizando pendientes”.

## Unificacion de mensajes y permisos (UI)

Objetivo: eliminar duplicados, mejorar claridad y centralizar respuestas visuales por tipo de evento.

- Reemplazo progresivo de mensajes planos por dialogos unificados:
  - Cargando lista (`loading_list.json`)
  - Lista cargada (icono exito)
  - Creacion en curso / creacion completada
  - Errores de creacion (`dialog_create_failure`)
  - Permisos insuficientes (`dialog_permission_denied` con `locked.json`)
- Politica de aviso cache/offline:
  - "Mostrando X en cache y pendientes offline" solo una vez por sesion offline.
  - Se resetea al recuperar conexion.
- En varios flujos de edicion/accion se eliminaron mensajes redundantes de tipo "sending..." cuando no aportan valor.
- En pantallas restringidas para `USER`, acceso bloqueado en cabecera/boton con dialogo de permisos en lugar de mensaje plano.
- Ajuste de animacion de permisos:
  - `locked.json` configurada para iniciar en frame 45 en el dialogo de permisos.

## Estado del sistema y health-check

Objetivo: mejorar diagnostico visual y evitar falsos offline en cliente.

Frontend:
- "Estado del sistema" redisenado como dialogo con componentes:
  - API
  - Base de datos
  - Redis
  - Celery
- Iconografia dedicada por componente y estado OK/KO.

Backend:
- Ajuste de `/health` para no forzar 503 global por estado de Celery en escenarios de arranque parcial.
- Correccion de variables de entorno en contenedores `worker` y `beat` (`JWT_SECRET`, `JWT_ALGORITHM`, `ACCESS_TOKEN_EXPIRE_MINUTES`) para evitar caida de Celery por configuracion faltante.

## Optimizaciones aplicadas (requisito Sprint 3)

Objetivo: mejorar rendimiento, estabilidad y UX en escenarios con paginación + modo offline.

- Paginación offline consistente en listados con creación local:
  - Aplicado en: Eventos, Stock, Productos, Categorías, Thresholds y Movimientos.
  - Los pendientes offline se tratan como registros al final del total remoto (no se apilan en la página actual).
  - Se pagina por `offset/limit` también en offline (incluida última página).
  - Si falta cache de una página, se usa `remoteTotal` cacheado de `offset=0` para evitar saltos de contador y páginas vacías.

- Reducción de llamadas repetidas a productos (resolución de nombres):
  - Ajuste de query para evitar 422: `order_by=id`, `order_dir=asc`, `limit=100`.
  - Cache en memoria con TTL corto (30s) para mapa `productId -> name` en:
    - Stock
    - Movimientos
    - Eventos
    - Thresholds
  - Invalidación explícita al pulsar “Recargar”.
  - Resultado: menos `GET /products/...` repetidos y menor riesgo de bloqueos “No responde”.

- Robustez del modo debug offline:
  - Failsafe en Login: al entrar en `LoginActivity`, se desactiva `manual offline` para evitar bloqueo de acceso.
  - En Home, no se fuerza logout por expiración cuando `manual offline` está activo.

## Logs y auditoría de acciones clave

Objetivo: registrar trazabilidad de acciones críticas indicando **quién hizo qué, sobre qué entidad y cuándo**.

Backend:
- Repositorio de auditoría:
  - `backend/app/repositories/audit_log_repo.py`
  - API interna:
    - `create_log(...)`
    - `list_logs(...)` con filtros y paginación
- Schemas de respuesta:
  - `backend/app/schemas/audit_log.py`

Entidad y acciones auditadas:
- `Entity.PRODUCT`: create/update/delete de productos.
- `Entity.STOCK`: create/update de stock.
- `Entity.MOVEMENT`: operaciones IN/OUT/ADJUST/TRANSFER (incluye ambos movimientos en transfer).
- `Entity.IMPORT`: creación de batch de import y acciones de review (approve/reject).

Endpoint de consulta (solo admin):
- `GET /audit`
- Filtros soportados:
  - `entity`
  - `action`
  - `user_id`
  - `date_from`
  - `date_to`
  - `order_dir=asc|desc`
  - `limit`, `offset`
- Seguridad:
  - protegido con rol `ADMIN` (`require_roles(UserRole.ADMIN.value)`).

Integración:
- Router registrado en:
  - `backend/app/main.py`
- Implementación de ruta:
  - `backend/app/api/routes/audit.py`

Migraciones:
- Se añade `IMPORT` al enum `entity` en PostgreSQL:
  - `backend/alembic/versions/3f5d8b2a1e47_add_import_entity_to_audit_log.py`
- Nota de Alembic:
  - si aparecen múltiples heads, crear merge revision y actualizar a `head` para dejar historial lineal.

## Observabilidad: métricas y trazas básicas

Objetivo: medir rendimiento y errores de la API con trazabilidad por request, sin dependencia obligatoria de Prometheus/Grafana en esta fase.

Backend:
- Middleware de observabilidad:
  - `backend/app/core/observability.py`
  - Genera o propaga `X-Request-ID`.
  - Añade `X-Request-ID` a la respuesta.
  - Registra tiempo por request (`duration_ms`), método, path, status y cliente.
  - Log estructurado JSON por request (`event=http_request`).
- Registro de métricas en memoria:
  - Requests totales.
  - Errores 5xx.
  - Suma y conteo de latencias (ms).
  - Métricas desglosadas por ruta/método/status.
- Endpoint de métricas:
  - `GET /metrics`
  - Formato texto estilo Prometheus (`text/plain; version=0.0.4`).

Métricas principales:
- `http_requests_total`
- `http_request_errors_5xx_total`
- `http_request_duration_ms_sum`
- `http_request_duration_ms_count`
- `http_requests_by_route_method_status{path,method,status}`
- `http_request_duration_ms_by_route_method_sum{path,method}`
- `http_request_duration_ms_by_route_method_count{path,method}`

Integración:
- `backend/app/main.py`
  - Se registra el middleware.
  - Se añade la ruta `/metrics`.

Filtro de agregados globales:
- Para evitar ruido en KPIs globales, se excluyen del agregado:
  - `/metrics`
  - `/health`
- Aun así, estas rutas se mantienen en métricas por ruta para diagnóstico.

Validación realizada:
- Forzado de 5xx real (`POST /auth/login` con DB caída) y aumento de `http_request_errors_5xx_total`.
- Verificación de `X-Request-ID`:
  - con cabecera entrante (se conserva),
  - sin cabecera (se genera UUID automáticamente).

## Observabilidad avanzada: Prometheus + Grafana

Objetivo: persistir y visualizar métricas de la API en dashboards operativos.

Infra Docker:
- `backend/docker-compose.yml` incluye:
  - `prometheus` (puerto `9090`)
  - `grafana` (puerto `3001`)
- Volúmenes persistentes:
  - `prometheus_data`
  - `grafana_data`

Prometheus:
- Configuración:
  - `backend/observability/prometheus/prometheus.yml`
- Scrape:
  - job `inventory-api`
  - target `api:8000`
  - `metrics_path: /metrics`
  - intervalo `10s`

Grafana (provisioning automático):
- Datasource:
  - `backend/observability/grafana/provisioning/datasources/datasource.yml`
  - URL interna: `http://prometheus:9090`
- Dashboard provider:
  - `backend/observability/grafana/provisioning/dashboards/dashboard.yml`
- Dashboard inicial:
  - `backend/observability/grafana/dashboards/inventory-observability.json`
  - Paneles incluidos:
    - Requests/s
    - 5xx/s
    - Latencia media global (ms)
    - Total requests
    - Request rate por ruta/método
    - Latencia media por ruta/método
    - Tabla de status por ruta/método

Credenciales por defecto Grafana:
- URL: `http://localhost:3001`
- User: `admin`
- Password: `admin`

## Pruebas end-to-end de observabilidad

### 1) Levantar stack
- `docker compose -f backend/docker-compose.yml up -d --build`

### 2) Verificar API y métricas base
- `curl.exe -i http://localhost:8000/health`
- `curl.exe -s http://localhost:8000/metrics`

Esperado:
- `/health` responde `200` (o `503` si dependencia caída).
- `/metrics` devuelve texto con métricas `http_*`.

### 3) Verificar Prometheus
- Abrir `http://localhost:9090/targets`

Esperado:
- Target `inventory-api` en estado `UP`.

### 4) Verificar Grafana
- Abrir `http://localhost:3001`
- Login `admin/admin`
- Ir a carpeta **Inventory** y abrir dashboard **Inventory API Observability**.

Esperado:
- Paneles con datos tras generar tráfico.

### 5) Generar tráfico de negocio
- `curl.exe -s -H "Authorization: Bearer <TOKEN>" http://localhost:8000/users/me > NUL`
- `curl.exe -s -H "Authorization: Bearer <TOKEN>" "http://localhost:8000/alerts/?status=PENDING&limit=1&offset=0" > NUL`
- `curl.exe -s -H "Authorization: Bearer <TOKEN>" "http://localhost:8000/products/?limit=5&offset=0" > NUL`

Esperado:
- Suben métricas de requests y latencia.

### 6) Verificar exclusión de `/health` y `/metrics` en agregados globales
- Consultar `/metrics` y revisar:
  - `http_requests_total`
  - `http_request_duration_ms_*`
  - series por ruta `path="/health"` y `path="/metrics"`

Esperado:
- `/health` y `/metrics` aparecen en métricas por ruta.
- No inflan agregados globales.

### 7) Forzar 5xx real y validar contador
- Parar DB temporalmente:
  - `docker compose -f backend/docker-compose.yml stop db`
- Forzar request que dependa de DB:
  - `curl.exe -i -X POST http://localhost:8000/auth/login -H "Content-Type: application/x-www-form-urlencoded" --data-raw "email=user@example.com&password=12345678"`
- Revisar métricas:
  - `curl.exe -s http://localhost:8000/metrics | findstr /i "http_request_errors_5xx_total"`
- Restaurar DB:
  - `docker compose -f backend/docker-compose.yml start db`

Esperado:
- Incrementa `http_request_errors_5xx_total`.

### 8) Verificar trazabilidad por `X-Request-ID`
- Con cabecera:
  - `curl.exe -i -H "X-Request-ID: prueba-123" http://localhost:8000/health`
- Sin cabecera:
  - `curl.exe -i http://localhost:8000/health`

Esperado:
- Con cabecera: respuesta devuelve `x-request-id: prueba-123`.
- Sin cabecera: respuesta devuelve UUID generado.

## Ajustes de estabilidad y errores (API + Android)

Objetivo: evitar loops de reconexión y mostrar errores funcionales claros cuando hay conflictos de datos.

Backend:
- Borrado de producto con historial:
  - `DELETE /products/{id}` ahora captura `IntegrityError` por FK `movements_product_id_fkey`.
  - En vez de `500`, devuelve `409 Conflict` con detalle funcional:
    - `No se puede eliminar el producto porque tiene movimientos historicos asociados.`
  - Archivo:
    - `backend/app/api/routes/products.py`

Android:
- Tratamiento de backend no disponible:
  - Respuestas `5xx` se tratan como backend temporalmente no disponible para activar comportamiento tipo offline y evitar spam.
  - Se añade cooldown para alertas de error de servidor.
  - Archivos:
    - `android/app/src/main/java/com/example/inventoryapp/data/remote/NetworkModule.kt`

- WebSocket de alertas:
  - Se evita reconexión agresiva cuando `offlineState` está activo.
  - Se añade cooldown para mensajes de estado WS (conectado/desconectado).
  - Archivo:
    - `android/app/src/main/java/com/example/inventoryapp/data/remote/AlertsWebSocketManager.kt`

- Diálogos de error en creación de eventos:
  - Si producto no existe: diálogo `failure` con animación `notfound.json`.
  - Si ubicación no válida: diálogo `failure` con mensaje limpio.
  - Archivo:
    - `android/app/src/main/java/com/example/inventoryapp/ui/events/EventsActivity.kt`

- Mensajes de `notfound` y ubicación:
  - Correcciones de texto en `Stock` y `Thresholds` para evitar cadenas corruptas (`Ã`) y mantener formato consistente (`ubicacion`).
  - Archivos:
    - `android/app/src/main/java/com/example/inventoryapp/ui/stock/StockActivity.kt`
    - `android/app/src/main/java/com/example/inventoryapp/ui/thresholds/ThresholdsActivity.kt`
