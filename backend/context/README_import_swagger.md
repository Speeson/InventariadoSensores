# Pruebas de Importación CSV (Swagger) — Paso a Paso

Este documento te guía para probar la importación CSV (eventos y transferencias) desde Swagger.

## Requisitos previos
- Contenedores levantados desde `backend/`:
  - `docker compose up -d --build`
- Seed base cargada (se ejecuta sola con `RUN_SEED=1`).
- Opcional: seed extra para reviews:
  - Ejecutar **desde** `backend/`:
    - `docker compose exec -w /app api python -m scripts.seed2_db`

## 1) Abrir Swagger
Visita:
- `http://localhost:8000/docs`

## 2) Obtener token (Auth)
En Swagger:
1. Abre `POST /auth/login`
2. En `email`, pon `admin@example.com`
3. En `password`, pon `Pass123!`
4. Ejecuta y copia el `access_token`
5. Pulsa el botón **Authorize** (arriba a la derecha)
6. Escribe:
   - `Bearer <TOKEN>`

## 3) Ver reviews (si ejecutaste seed2)
Endpoint:
- `GET /imports/reviews`

Deberías ver 1 review (row_number=4).

## 4) Probar dry‑run de eventos
Endpoint:
- `POST /imports/events/csv?dry_run=true`

En Swagger, sube el archivo:
- `backend/context/import_samples/events_sample.csv`

Nota:
- Si el seed ya tiene productos, puede salir `review` o `sku_barcode_mismatch`.
- Para evitar `review`, puedes usar:
  - `fuzzy_threshold=1.0`

## 5) Probar dry‑run de transferencias
Endpoint:
- `POST /imports/transfers/csv?dry_run=true`

Archivo:
- `backend/context/import_samples/transfers_sample.csv`
- `backend/context/import_samples/transfers_sample_errors.csv`

## 6) Aprobar o rechazar una review
1. `GET /imports/reviews`
2. Elige un `id`
3. Ejecuta:
   - `POST /imports/reviews/{id}/approve`
   - o `POST /imports/reviews/{id}/reject`

## 7) Ver resultados
Después de aprobar:
- `GET /movements`
- `GET /stocks`

## Archivos de ejemplo
- `backend/context/import_samples/events_sample.csv`
- `backend/context/import_samples/events_sample_review.csv`
- `backend/context/import_samples/events_sample_errors.csv`
- `backend/context/import_samples/transfers_sample.csv`

## CSV con errores (validaciones críticas)
Para probar validaciones, usa:
- `POST /imports/events/csv?dry_run=true`
- Archivo: `backend/context/import_samples/events_sample_errors.csv`

Errores cubiertos:
- SKU vacío
- Barcode vacío
- `category_id` inexistente
- `location_id` inexistente
- Cantidad negativa en IN
- ADJUST con 0
- OUT con stock insuficiente

## CSV de transferencias con errores
Para probar validaciones en transferencias:
- `POST /imports/transfers/csv?dry_run=true`
- Archivo: `backend/context/import_samples/transfers_sample_errors.csv`

Errores cubiertos:
- Origen y destino iguales
- `from_location_id` inexistente
- `to_location_id` inexistente
- Cantidad 0
- Cantidad negativa
