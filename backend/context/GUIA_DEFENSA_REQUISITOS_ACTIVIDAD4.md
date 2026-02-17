# Guion de defensa por requisitos (Actividad 4)

Este guion esta pensado para exponer los requisitos del PDF de la actividad uno a uno, indicando que decir y como demostrarlo en directo.

## 0) Preparacion previa (2 minutos)

- Levantar entorno:
  - `docker compose -f backend/docker-compose.yml up -d --build`
- Abrir:
  - Swagger: `http://localhost:8000/docs`
  - Grafana: `http://localhost:3001`
  - Prometheus: `http://localhost:9090`
  - App Android en emulador/dispositivo
- Tener a mano:
  - `README.md`
  - `readmeSprint3.md`
  - `backend/openapi/openapi.json`
  - pestaña Actions (workflows CI)

## 1) Arquitectura MVC + stack

Que decir:
- "La solucion sigue MVC: Android como vista, FastAPI como controlador y PostgreSQL/SQLAlchemy como modelo."

Como demostrar:
- Mostrar seccion de arquitectura en `README.md`.
- Enseñar estructura de carpetas (backend/app + android/app).

## 2) Seguridad: JWT + roles

Que decir:
- "Autenticamos con JWT y autorizamos por rol (USER, MANAGER, ADMIN)."

Como demostrar:
1. En Swagger, ejecutar `POST /auth/login`.
2. `Authorize` con el token.
3. Probar:
   - endpoint de lectura permitido a USER (`GET /products`).
   - endpoint restringido a MANAGER/ADMIN (por ejemplo `POST /products`).
4. Enseñar que `GET /users/admin-only` solo responde para ADMIN.

## 3) CRUD de inventario (productos, stock, movimientos)

Que decir:
- "Tenemos operaciones completas de inventario con validaciones de negocio y paginacion."

Como demostrar:
1. Crear categoria.
2. Crear producto.
3. Crear/editar stock.
4. Registrar movimiento IN/OUT/ADJUST.
5. Consultar listados con filtros y paginacion.

## 4) Escaneo desde Android

Que decir:
- "La app registra operaciones desde codigo de barras usando la camara."

Como demostrar:
1. Abrir pantalla de escaneo.
2. Escanear (o simular) y confirmar movimiento.
3. Ver reflejo en stock/movimientos.

## 4.bis) Integracion API externa (Niimbot B1)

Que decir:
- "Integramos un SDK/API externo oficial (Niimbot) para imprimir etiquetas directamente por Bluetooth en la B1."

Como demostrar:
1. Mostrar en codigo las librerias oficiales en `android/app/libs` (AAR/JAR de Niimbot).
2. Mostrar `NiimbotSdkManager.kt` con imports del paquete oficial `com.gengcon.www.jcprintersdk`.
3. En la app, abrir preview de etiqueta y lanzar impresion directa Niimbot.
4. Enseñar fallback a apertura de la app oficial Niimbot.

## 5) Sensores IoT simulados + procesado asincrono

Que decir:
- "Los eventos de sensores se registran y se procesan asincronamente con Celery."

Como demostrar:
1. `POST /events` en Swagger.
2. Mostrar que queda encolado/procesado.
3. Ver efecto en stock y movimientos.

## 6) Alertas de stock y notificaciones

Que decir:
- "Gestionamos umbrales y alertas en tiempo real."

Como demostrar:
1. Configurar threshold con `POST /thresholds`.
2. Forzar stock bajo.
3. Ver alertas en `GET /alerts`.
4. Mostrar ack (`POST /alerts/{id}/ack`).
5. En Android, enseñar dialogo/aviso de alerta.

## 7) Reportes

Que decir:
- "Incluimos endpoints de analitica para consumo y rotacion."

Como demostrar:
- `GET /reports/top-consumed`
- `GET /reports/turnover`

## 8) Importacion CSV con revision

Que decir:
- "La importacion soporta validacion, cuarentena de errores y flujo de review."

Como demostrar:
1. `POST /imports/events/csv` (dry-run y real).
2. `GET /imports/reviews`.
3. Aprobar/rechazar una review.
4. Ver impacto final en movimientos/stocks.

## 9) Auditoria

Que decir:
- "Cada accion critica queda trazada en auditoria."

Como demostrar:
1. Ejecutar una accion de negocio (crear/editar/borrar).
2. Consultar `GET /audit` como ADMIN.
3. Filtrar por entidad/accion/fecha.

## 10) Observabilidad (metricas + dashboard)

Que decir:
- "Medimos trafico, errores y latencia, y lo visualizamos en Grafana."

Como demostrar:
1. `GET /metrics`.
2. Prometheus targets en `UP`.
3. Dashboard de Grafana con paneles HTTP.

## 11) Contrato OpenAPI documentado

Que decir:
- "La API no solo esta documentada: el contrato se valida automaticamente."

Como demostrar:
1. Abrir Swagger y enseñar:
   - examples en payloads.
   - responses de error (400/404/409/503, etc.).
2. Enseñar `backend/openapi/openapi.json`.

## 12) Calidad y CI/CD

Que decir:
- "En cada push/PR ejecutamos tests y validamos build de contenedores."

Como demostrar:
1. Abrir GitHub Actions.
2. Workflow `Backend Contract Tests`:
   - snapshot + contract tests.
3. Workflow `Backend CI`:
   - pytest + docker build (`api/worker/beat`).

Comandos utiles para demo local:
- Contrato:
  - `docker compose -f backend/docker-compose.yml exec -T api sh -lc "python -m pytest -q tests/test_openapi_snapshot.py tests/test_contract.py"`
- Contrato con log:
  - `New-Item -ItemType Directory -Force backend/test-reports | Out-Null`
  - `docker compose -f backend/docker-compose.yml exec -T api sh -lc "python -m pytest -q tests/test_openapi_snapshot.py tests/test_contract.py --junitxml=/tmp/contract-latest.xml" | Tee-Object -FilePath backend/test-reports/contract-latest.log`
  - `docker compose -f backend/docker-compose.yml cp api:/tmp/contract-latest.xml backend/test-reports/contract-latest.xml`
- Suite completa:
  - `docker compose -f backend/docker-compose.yml exec -T api sh -lc "python -m pytest -q tests"`

## 13) Cierre (30-45 segundos)

Mensaje final sugerido:
- "Cumplimos los requisitos funcionales y transversales: seguridad por roles, trazabilidad, importacion, observabilidad, contrato API y pipeline CI/CD. Ademas, la app Android consume este backend con soporte offline/online y manejo de errores consistente."
