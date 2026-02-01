# README.md — Sprint 2 (Persona 2)

**Proyecto:** InventariadoSensores  
**Fecha:** 2026-02-01

---

## Índice

1. Alcance de mis entregas  
2. Cambios de esquema y migraciones  
3. Seeds actualizados  
4. CRUD de Thresholds  
5. Reporte Top N Productos Consumidos  
6. Reporte de Rotación (Turnover)  
7. Normalización de ubicaciones (case-insensitive)  
8. Modelos y esquemas tocados  
9. Endpoints y ejemplos de uso (Swagger/Postman)  
10. Manejo de errores y validaciones  
11. Consideraciones de negocio y métricas  
12. Pruebas sugeridas  
13. Pendientes / ideas futuras  

---

## Alcance de mis entregas

- Implementé el CRUD completo de thresholds con validaciones y soporte de `location_id`.
- Ajusté el modelo de datos para usar claves foráneas en `movements` y `stock_thresholds`.
- Añadí dos reportes: **top-consumed** y **turnover**, ambos con filtros de fechas y ubicación.
- Hice que la resolución de `locations` sea *case-insensitive* para evitar duplicados.
- Actualicé seeds para funcionar con los cambios de esquema.
- Exposición en Swagger de parámetros de fecha en formato corto (`YYYY-MM-DD`).

---

## Cambios de esquema y migraciones

- **Tabla `movements`**
  - Añadida columna `location_id` (FK a `locations`).
  - Índice: `ix_movements_location`.

- **Tabla `stock_thresholds`**
  - Reemplazo de la columna `location` (string) por `location_id` (FK a `locations`).
  - Índice: `ix_thresholds_location`.

- **Alembic**
  - Versión relevante: `3373e6b81640_add_location_id_to_movements_and_...`
  - Visible en `alembic_version`.

**Impacto:**  
Todos los accesos a `movements` y `thresholds` ahora usan `location_id`.  
Los modelos exponen también `location` como propiedad derivada.

---

## Seeds actualizados

### `backend/scripts/seed_db.py`
- Corrige creación de `locations` usando `location_repo.get_or_create` sin subíndice.
- Población de:
  - Categorías
  - Usuarios
  - Locations (5)
  - Productos (5)
  - Stocks
  - Stock thresholds
  - Events
  - Movements
  - Audit logs

### `backend/scripts/seed3_db.py`
- Misma corrección de `locations`.
- Uso de enums y campos requeridos actuales (`idempotency_key`, `event_status`, `source`).

**Efecto:**  
Los datos iniciales respetan `location_id` y no rompen con el nuevo esquema.

---

## CRUD de Thresholds

- **Router:** `backend/app/api/routes/thresholds.py`
- **Endpoints:**
  - `POST /thresholds`
  - `GET /thresholds`
  - `GET /thresholds/{id}`
  - `PATCH /thresholds/{id}`
  - `DELETE /thresholds/{id}`

- **Roles:** `MANAGER` o `ADMIN`.

### Validación de duplicados
- `get_by_product_and_location` evita duplicados `(product_id, location)`.

### Repositorio
- `backend/app/repositories/threshold_repo.py`
- Usa `location_id`.
- Crea `location` si no existe (*case-insensitive*).
- Permite `location = None` para thresholds globales.

### Esquemas
- `backend/app/schemas/threshold.py`
- Normaliza `location` (trim, `"" → None`).
- Respuesta incluye `location_id` y `location` (derivada).

### Modelo
- `backend/app/models/stock_threshold.py`
- `location_id` FK.
- Propiedad `location` expone el `code` de `Location`.

### Comportamiento
- **Crear:** requiere `product_id` y `min_quantity`; `location` opcional.
- **Actualizar:** chequea duplicados si se cambia `location`.
- **Listar:** filtros por `product_id` y `location` (opcional); paginación `limit/offset`.
- **Borrar:** `204` si existe; `404` si no.

---

## Reporte Top N Productos Consumidos

- **Endpoint:** `GET /reports/top-consumed`
- **Parámetros:**
  - `date_from`, `date_to` (`YYYY-MM-DD`)
  - `location` (opcional)
  - `limit`, `offset`

### Lógica (`report_repo.list_top_consumed`)
- Filtra movimientos `MovementType.OUT`.
- Filtra por fechas y `location` (*case-insensitive*, usa `location_id`).
- Agrupa por `product_id / sku / name`.
- Suma `quantity` como `total_out`.
- Ordena desc por `total_out`; paginación.
- Si la `location` no existe: lista vacía, total `0`.

- **Esquema:** `TopConsumedResponse` (`items`, `total`, `limit`, `offset`).

---

## Reporte de Rotación (Turnover)

- **Endpoint:** `GET /reports/turnover`
- **Parámetros:**
  - `date_from`, `date_to` (`YYYY-MM-DD`)
  - `location` (opcional)
  - `limit`, `offset`

### Fórmula (por producto)
- `outs` = SUM(quantity) de movimientos `OUT`.
- `net` = Σ(+IN, –OUT, ±ADJUST).
- `stock_final` = `stock.quantity`.
- `stock_inicial_estimado` = `stock_final – net`.
- `stock_promedio` = (`stock_inicial_estimado + stock_final`) / 2.
- `turnover` = `outs / stock_promedio`  
  - Si `stock_promedio ≤ 0` → `None`.

### Lógica (`report_repo.list_turnover`)
- Agrega movimientos en CTE.
- Agrega stock por producto (y `location`).
- Excluye productos sin movimientos ni stock cuando se filtra por `location`.
- Devuelve:
  - `outs`
  - `stock_initial`
  - `stock_final`
  - `stock_average`
  - `turnover`

- **Esquema:** `TurnoverResponse` con lista de `TurnoverItem`.

---

## Normalización de ubicaciones (case-insensitive)

- **Repositorio:** `backend/app/repositories/location_repo.py`
- `get_by_code` y `get_or_create` comparan con `func.lower`.
- Previene duplicados como:
  - “Oficina Central”
  - “oficina central”

**Impacto:**  
CRUD de thresholds, reportes y seeds no generan duplicados.

---

## Modelos y esquemas tocados

- **Movement** (`backend/app/models/movement.py`)
  - Añadido `location_id` FK.
  - Propiedad `location` derivada.

- **StockThreshold** (`backend/app/models/stock_threshold.py`)
  - `location_id` FK.
  - Propiedad `location`.

- **Schemas**
  - `report.py`: `TopConsumedItem/Response`, `TurnoverItem/Response`.
  - `threshold.py`: normalización de `location`, `location_id` en respuesta.

- **Router**
  - `reports.py`: parámetros de fecha como `date` (`YYYY-MM-DD`) → combinados a `datetime` min/max.

---

## Endpoints y ejemplos de uso (Swagger / Postman)

### Autorización
- Roles `MANAGER` / `ADMIN` para thresholds y reportes.

### Thresholds

```json
POST /thresholds
{
  "product_id": 1,
  "location": "Oficina Central",
  "min_quantity": 10
}
```

```
GET /thresholds?product_id=1&location=Oficina%20Central
```

```json
PATCH /thresholds/3
{
  "location": null,
  "min_quantity": 5
}
```

```
DELETE /thresholds/3
```

### Reportes

```
GET /reports/top-consumed?date_from=2026-01-01&date_to=2026-02-01&location=Planta%20Norte&limit=5
```

```
GET /reports/turnover?date_from=2026-01-01&date_to=2026-02-01&location=Oficina%20Central&limit=10
```

**Location inexistente:** `items = []`, `total = 0`.

---

## Manejo de errores y validaciones

### Thresholds
- `400` si el producto no existe.
- `400` si ya existe threshold para `(product_id, location)`.
- `404` si el `id` no existe.
- `location` vacía → normalizada a `None`.

### Reportes
- `400` si `date_from > date_to`.
- `location` inexistente → lista vacía.
- División por cero en turnover → `None`.

---

## Consideraciones de negocio y métricas

- **Top-consumed:** mide salidas (`OUT`), adecuado para consumo real.
- **Turnover:** relaciona salidas con stock medio.
- **Threshold global:** (`location = None`) aplica si no hay uno específico.
- **Locations case-insensitive:** evita datos inconsistentes.

---

## Pruebas sugeridas

### Swagger (Smoke)
- Crear threshold con y sin location.
- Listar y borrar.
- Reportes con y sin location.
- Rango de fechas válido e inválido.

### Turnover
- `outs > 0` y `stock > 0` → turnover > 0.
- Sin outs → turnover = 0.
- `stock_promedio = 0` → turnover = None.
- Filtro por location correcto.

### Seeds
- 5 locations creadas.
- Stocks, thresholds y movements con `location_id`.

### Base de datos
- Columnas `location_id` en `movements` y `stock_thresholds`.
- Índices creados.
- `alembic_version` = `3373e6b81640`.



