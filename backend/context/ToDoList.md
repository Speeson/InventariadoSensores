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
- [ ] Panel de alertas: anadir pestana "Actividad" (timeline operativo unificado).
- [ ] Pestana Actividad: registrar eventos de alertas, offline/online, ultimos escaneos (manual/camara), sincronizaciones y reintentos.
- [ ] Pestana Actividad: mostrar estado de cola offline (pendientes, enviados, fallidos) y transiciones relevantes.
- [ ] Pestana Actividad: anadir filtros rapidos (Todo | Alertas | Escaneos | Sync | Offline | Sistema).
- [ ] Pestana Actividad: paginacion/carga incremental y orden por fecha descendente.
- [ ] Pestana Actividad: acciones rapidas por evento (abrir detalle, reintentar sync, navegar al modulo origen).
- [ ] Campana/alertas: badge solo con alertas no leidas; la actividad no incrementa contador de notificaciones.
- [ ] Definir modelo de evento de actividad (tipo, severidad, origen, usuario, timestamp, payload minimo).
- [ ] Definir retencion/limpieza de actividad (limite por usuario/dispositivo y politica de purga).
- [ ] Asistente IA (nivel intermedio): chat de ayuda dentro de la app con respuestas de solo lectura.
- [ ] Asistente IA: integrar consultas a datos reales via API (estado sistema, alertas, sincronizacion, metricas operativas).
- [ ] Asistente IA: restringir alcance a "consulta", sin ejecucion de acciones ni cambios de estado.
- [ ] Asistente IA: aplicar permisos por rol en las respuestas (no exponer datos no autorizados).
- [ ] Asistente IA: registrar trazabilidad (pregunta, respuesta, fuentes/API consultadas, timestamp, usuario).
- [ ] Asistente IA: incluir mensaje de limite/capacidad (puede consultar datos, no operar acciones).
- [ ] Atajos personalizables: definir slots configurables (`top_slot_left`, `top_slot_right`, `bottom_slot_secondary`).
- [ ] Atajos personalizables: definir catalogo cerrado de acciones por slot (sin opciones libres) y defaults globales.
- [ ] Atajos personalizables: aplicar reglas por rol para filtrar opciones permitidas por usuario.
- [ ] Backend atajos: crear tabla `user_ui_preferences` (1:1 con `users`) con valores de slots y timestamps.
- [ ] Backend atajos: exponer `GET /users/me/ui-preferences` para lectura de configuracion actual.
- [ ] Backend atajos: exponer `PATCH /users/me/ui-preferences` con validacion server-side (slot + opcion + rol).
- [ ] Backend atajos: implementar fallback automatico a defaults si falta o es invalida una preferencia.
- [ ] Backend atajos: anadir migracion alembic y tests de contrato para lectura/actualizacion.
- [ ] Android atajos: anadir DTOs y endpoints Retrofit de preferencias UI.
- [ ] Android atajos: crear repositorio de preferencias (API + cache local en `SharedPreferences`).
- [ ] Android atajos: renderizar botones dinamicos en menu superior e inferior segun preferencias del usuario.
- [ ] Android atajos: crear pantalla/modal "Personalizar atajos" con preview y boton "Restaurar defaults".
- [ ] Android atajos: mostrar solo opciones permitidas por rol y manejar estado offline con cache local.
- [ ] Atajos personalizables: instrumentar telemetria basica (atajo mas usado, cambios por usuario, errores de validacion).
- [ ] Atajos personalizables: plan de entrega por fases (Fase 1 local, Fase 2 sincronizado backend, Fase 3 hardening y tests).
