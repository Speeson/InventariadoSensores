# OpenAPI Contract Snapshot

Este directorio guarda el contrato OpenAPI versionado del backend.

## Generar / actualizar snapshot

Desde `backend/`:

```powershell
python scripts/export_openapi.py
```

El comando actualiza `backend/openapi/openapi.json`.

## Uso

- Los tests de contrato deben validar la API contra este snapshot.
- Cualquier cambio intencional del contrato debe actualizar este archivo en el mismo PR.
