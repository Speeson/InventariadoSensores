# Guion de presentacion - Sprint 2 (IoTrack)

## 0) Preparacion previa (2 min, sin publico)
- Backend arriba: `docker compose up`.
- Worker + beat activos.
- App instalada en movil/tablet.
- Usuario `MANAGER` o `ADMIN` listo.
- Al menos 1 producto con threshold bajo.
- 1 evento fallido preparado o simulado.
- Desactivar Wi-Fi para simular offline.

---

## 1) Introduccion (1-2 min)
**Objetivo:** explicar que es IoTrack y que se ha conseguido en Sprint 2.
**Quien:** presentador principal.
**Guion:**
- "IoTrack es una app de inventario con Android + API REST."
- "Sprint 2 se centro en alertas, eventos asincronos, UI/UX y modo offline."

---

## 2) Arquitectura y Backend Sprint 2 (3-4 min)
**Quien:** responsable backend.
**Mostrar:**
- Arquitectura: API + Celery + Redis + DB.
- Eventos async (PENDING/PROCESSED/FAILED).
- Endpoints nuevos: `/alerts`, `/thresholds`, `/reports`, `/locations`.
- Job de alertas: `scan_low_stock`.

**Guion:**
- "El evento se encola y el worker lo procesa."
- "Las alertas se generan por thresholds de stock."

---

## 3) Android - Login y Home redisenado (3-4 min)
**Quien:** responsable frontend.
**Mostrar:**
- Pantalla login redisenada (degradado + inputs con iconos + registro).
- Home con tarjetas e iconos.
- Menu lateral con perfil, opciones y logout.

**Guion:**
- "Mejoramos la UX con un diseno consistente y moderno."

---

## 4) Gestion de Productos + Offline (4-5 min)
**Quien:** responsable app.
**Demostrar:**
1) Crear producto online.
2) Desconectar Wi-Fi -> crear producto offline.
3) Volver al listado -> aparece con icono rojo (offline).
4) Reconectar Wi-Fi -> se sincroniza.

**Guion:**
- "Si no hay red, se guarda en cola offline."
- "Al volver la conexion, se sincroniza automaticamente."

---

## 5) Escaneo + Confirmacion + Movimientos (3-4 min)
**Quien:** responsable escaneo.
**Mostrar:**
- Escaneo de barcode -> confirmacion de movimiento.
- Producto inexistente -> mensaje controlado (sin spam).

**Guion:**
- "El escaneo ahora valida y guia la confirmacion."

---

## 6) Alertas de Stock (3-4 min)
**Quien:** backend / Android.
**Mostrar:**
1) Forzar alerta: bajar stock o ejecutar `scan_low_stock`.
2) Menu -> Alertas -> pestaña Alertas.
3) Marcar "vista" (ACK).

**Guion:**
- "Alertas de stock bajo generadas por backend."

---

## 7) Alertas del sistema + eventos fallidos (3-4 min)
**Quien:** Android.
**Mostrar:**
- Simular caida API -> aparece dialogo central.
- Se guarda como alerta del sistema.
- Pestañas: Alertas / Pendientes offline.
- Limpiar alertas con boton X.

**Guion:**
- "Avisos criticos no se pierden."
- "Offline y errores visibles en una unica pantalla."

---

## 8) Pendientes Offline + reintentos (2-3 min)
**Quien:** Android.
**Mostrar:**
- Alertas -> Pendientes offline.
- Reintentar un fallo.
- Mensaje de "pendientes procesados" con tick verde.

**Guion:**
- "El usuario puede resolver errores offline sin perder datos."

---

## 9) Reportes / Rotacion (2-3 min)
**Quien:** Android o backend.
**Mostrar:**
- Entrar a Reportes.
- Mostrar rotacion.

**Guion:**
- "Se anadieron metricas y reportes para analisis."

---

## 10) Cierre (1 min)
**Quien:** presentador principal.
**Guion:**
- "Sprint 2 entrega alertas, offline robusto, UX mejorada y procesos asincronos."
- "Sprint 3: CSV, auditoria y optimizaciones."
