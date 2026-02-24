# ToDoList

## Futuro

- [ ] Añadir proxy inverso (Nginx o Traefik) para exponer solo un punto de entrada (80/443), gestionar HTTPS/TLS y enrutar servicios (`/api`, `/grafana`) en despliegues de staging/produccion.

## Optimizaciones

- [ ] Importaciones en 2 fases (validar + aplicar).
- [ ] Transacciones por lote (imports/movimientos).
- [ ] Interfaz separada (recepcion | despacho).
- [ ] Paginacion y carga incremental (mejora fluidez).
- [ ] Trazabilidad tecnica end-to-end (acelerar diagnostico en prod. <celery>).

## Nuevas implementaciones

- [ ] Panel de control de importaciones (filtros, metricas, ratios, tiempos).
- [ ] Reintento masivo de errores de import (relanzar procesos).
- [ ] Aprobacion masiva de revisiones.
- [ ] Motor de reglas de reposicion automatica (reabastecimiento).
- [ ] Forecast basico de consumo y rotacion (consumo semanal/mensual y sugerencia de umbrales).
- [ ] Soportar generacion de distintos tipos de codigo de barras y desacoplar el dato almacenado en BD del formato especifico Code 128.
