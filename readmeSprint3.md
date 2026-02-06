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
- Botón "Regenerar etiqueta" con lock y texto "Solo admin/manager" si el usuario no tiene permisos y está habilitado el toggle de ver restringidos.
- Gradiente de iconos aplicado a las tarjetas del Home, campana de alertas y menú lateral.
- Título "IoTrack" con el mismo gradiente de iconos.

Seed:
- Para generar etiquetas en seed: `SEED_LABELS=1` al ejecutar `backend/scripts/seed_db.py`.
