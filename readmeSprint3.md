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

## Etiquetas Niimbot (B1) + guardado en galería

Objetivo: guardar la etiqueta que se ve en el preview y abrir la app Niimbot para imprimir.

Android:
- Botón Niimbot en la pantalla de etiqueta: guarda en galería y abre Niimbot.
- La imagen guardada se genera **desde el preview** (WebView) y se recorta el blanco superior/inferior con margen.
- Sin reescalado forzado: se guarda tal cual para ajustar tamaño en la app Niimbot.
- Intent explícito para abrir Niimbot:
  - Paquete: `com.gengcon.android.jccloudprinter`
  - Activity: `com.gengcon.android.jccloudprinter.LaunchActivity`
  - Fallback: `com.gengcon.android.jccloudprinter.LaunchFromWebActivity`

UI pantalla etiqueta:
- Botones cuadrados con iconos: `print.png` y `niimbot.png` (misma altura, lado a lado).
- Botones y acciones con tema morado suave.
- Título "Etiqueta" visible con el estilo de otras actividades.

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
