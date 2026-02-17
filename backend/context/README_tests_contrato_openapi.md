# Tests de contrato OpenAPI

Esta guía define cómo validar que la API backend cumple su contrato OpenAPI y no rompe el cliente Android.

## Qué se valida

1. Snapshot OpenAPI versionado:
   - Archivo: `backend/openapi/openapi.json`
2. Consistencia snapshot vs runtime:
   - Test: `backend/tests/test_openapi_snapshot.py`
3. Conformidad de endpoints/respuestas con contrato:
   - Test Schemathesis: `backend/tests/test_contract.py`

## Flujo de trabajo

1. Cambias rutas/schemas en backend.
2. Regeneras contrato:

```powershell
python backend/scripts/export_openapi.py
```

3. Ejecutas tests:

```powershell
pytest -q backend/tests/test_openapi_snapshot.py backend/tests/test_contract.py
```

4. Commit obligatorio de:
   - Cambios en código backend.
   - `backend/openapi/openapi.json` actualizado si cambió el contrato.

## CI

Workflow: `.github/workflows/backend-contract.yml`

El pipeline falla si:
- El snapshot no existe o no coincide con `app.openapi()`.
- Alguna operación devuelve status/content-type/schema fuera de contrato.
- Aparece un `5xx` no esperado durante validación de contrato.
