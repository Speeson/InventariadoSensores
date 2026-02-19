# Futuras Mejoras

## 1) Optimizaciones de lo existente (5)

1. Importaciones en 2 fases (validar + aplicar)
   - Objetivo: que `dry_run=true` y `dry_run=false` den conteos coherentes (`ok/error/review`) sobre el mismo CSV.
   - Enfoque: primera pasada de validación con estado virtual en memoria, segunda pasada de aplicación real solo si `dry_run=false`.

2. Transacciones por lote en imports
   - Objetivo: reducir inconsistencias parciales y mejorar rendimiento.
   - Enfoque: evitar `commit` intermedios por fila (por ejemplo en creación de producto) y consolidar por lote/chunk.

3. Cache de lookups durante import
   - Objetivo: menos consultas repetidas a BD para `sku`, `barcode`, `category`, `location`.
   - Enfoque: diccionarios en memoria por batch + invalidación controlada al final.

4. Paginación y carga incremental en pantallas pesadas de Android
   - Objetivo: mejorar fluidez en listados grandes (imports, eventos, movimientos, alertas).
   - Enfoque: fetch por páginas, placeholders y actualización incremental del adapter.

5. Trazabilidad técnica end-to-end
   - Objetivo: acelerar diagnóstico en demo y producción.
   - Enfoque: añadir `correlation_id` en API, worker y logs de Celery para seguir cada operación completa.

## 2) Nuevas implementaciones (5)

6. Panel de control de importaciones
   - Funcionalidad: vista de batches con filtros, métricas por tipo, ratio de errores y tiempos medios.
   - Valor: control operativo claro para managers/admin.

7. Reintento masivo de errores de import
   - Funcionalidad: seleccionar errores fallidos y relanzar proceso (con validaciones actuales).
   - Valor: menos trabajo manual al corregir incidencias.

8. Aprobación/rechazo masivo de revisiones
   - Funcionalidad: acciones bulk por filtros (tipo, fecha, motivo de review).
   - Valor: acelera la gestión de cuarentena de datos.

9. Motor de reglas de reposición automática
   - Funcionalidad: definir reglas por producto/ubicación para generar propuestas de reabastecimiento cuando el stock previsto cae bajo umbral.
   - Valor: automatiza decisiones operativas y reduce roturas de stock.

10. Forecast básico de consumo y rotación
   - Funcionalidad: proyección simple por histórico (consumo semanal/mensual) y sugerencia de umbrales.
   - Valor: pasar de gestión reactiva a planificación proactiva.
sss