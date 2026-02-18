# Guia presentacion Sprint 3 (mi parte)

## Objetivo de mi parte
En Sprint 3 mi foco ha sido:
- Importacion CSV robusta (eventos y transferencias).
- Cache hibrido (Redis en backend + Room en Android) y modo offline.
- Integracion externa con etiquetadora Niimbot B1 usando SDK oficial.

---

## 1) Importacion CSV

### Que problema resuelve
- Cargar lotes grandes de datos de forma rapida y controlada.
- Evitar errores silenciosos con validacion, resumen y revisiones.

### Como esta implementado
- Endpoints principales:
  - `POST /imports/events/csv`
  - `POST /imports/transfers/csv`
  - `GET /imports/reviews`
  - `POST /imports/reviews/{review_id}/approve`
  - `POST /imports/reviews/{review_id}/reject`
- Flujo:
  1. Subida de archivo CSV.
  2. Validacion de columnas y tipos.
  3. Procesado fila a fila.
  4. Resultado en 3 grupos: OK, errores, review manual.
  5. Resumen final con `batch_id`, totales y detalle.

### Conceptos clave que explicar
- `dry-run`: simula todo el proceso sin persistir cambios.
- `fuzzy_threshold` (0..1): umbral de similitud para detectar posibles duplicados.
- `batch_id`: identificador del lote importado para trazabilidad.
- Revisions: cuando no es seguro decidir automaticamente, se manda a `reviews`.

### Que enseño en la demo
1. Importar CSV valido y ver resumen correcto.
2. Importar CSV con errores y ver listado de errores.
3. Importar CSV con casos ambiguos y ver items en revisiones.
4. Aprobar/rechazar una revision y ver que cambia el estado.
5. Mostrar que eventos/transferencias mantienen paginacion en errores.

### Mensaje corto para defenderlo
"No solo importamos CSV; lo hacemos de forma segura y auditable: validacion fuerte, simulacion con dry-run, deteccion difusa de duplicados, y resolucion manual cuando hay ambiguedad."

---

## 2) Cache hibrido y modo offline

### Que problema resuelve
- Reducir latencia en lecturas repetidas.
- Mantener uso funcional de la app aunque no haya red.

### Arquitectura
- Backend: Redis para cache de lecturas (listas/reportes).
- Android: Room (SQLite) para cache local persistente.
- Estrategia: cache-first.

### Como funciona en runtime
1. La pantalla entra y muestra cache local de inmediato (si existe).
2. En paralelo, intenta refrescar desde API.
3. Si API responde, actualiza UI + cache local.
4. Si no hay red, mantiene modo offline con datos cacheados.

### Persistencia local (Room)
- DB local: `app_cache.db`.
- Persiste entre sesiones.
- Se borra al limpiar datos o desinstalar.
- Maximo de entradas y poda por antiguedad de actualizacion (`updatedAt`).

### Coherencia de datos
- En backend se invalida cache Redis en operaciones create/update/delete.
- En Android, al volver online se relanza sync de pendientes.
- WorkManager/cola offline para operaciones diferidas.

### Que enseño en la demo
1. Abrir una lista con red: carga instantanea + refresco.
2. Cortar red: seguir navegando con datos cacheados.
3. Crear cambios offline (si aplica en esa vista).
4. Volver online y mostrar sincronizacion de pendientes.
5. Confirmar en backend que los cambios se aplicaron.

### Mensaje corto para defenderlo
"El cache hibrido nos da dos beneficios: rendimiento en online y continuidad operativa en offline. Es una decision de arquitectura, no solo de interfaz."

---

## 3) Integracion externa Niimbot B1

### Que problema resuelve
- Cerrar el flujo extremo a extremo: no solo generar etiqueta, tambien imprimirla en hardware real.

### Como esta implementado
- Integracion en Android con SDK oficial de Niimbot.
- Flujo:
  1. Backend genera/entrega plantilla de etiqueta.
  2. Android prepara el contenido para impresion.
  3. SDK Niimbot envia el trabajo a la B1.
  4. UI muestra estado final ("Etiqueta impresa correctamente").

### Que enseño en la demo
1. Abrir preview de etiqueta.
2. Lanzar impresion con Niimbot conectada.
3. Ver confirmacion visual en app.
4. Mostrar etiqueta fisica impresa.

### Mensaje corto para defenderlo
"La integracion externa demuestra que el sistema no termina en la API: llega al dispositivo fisico y ejecuta una operacion real de negocio."

---

## 4) Orden recomendado para exponer (5-7 min)

1. Contexto rapido (20-30s): que cubre mi parte del sprint.
2. Importacion CSV (2 min):
   - dry-run, fuzzy, errores/reviews, paginacion.
3. Cache hibrido + offline (2-3 min):
   - arquitectura, flujo cache-first, demo offline/online.
4. Niimbot (1-2 min):
   - integracion SDK oficial y prueba fisica.
5. Cierre (20s):
   - impacto: robustez, rendimiento y flujo real end-to-end.

---

## 5) Preguntas tipicas y respuesta corta

### "Por que dry-run es importante?"
Porque permite validar lotes sin tocar datos reales y reduce errores en produccion.

### "Como evitais duplicados en CSV?"
Con validaciones de SKU/barcode/categoria y fuzzy matching para casos ambiguos.

### "Que pasa si no hay internet?"
Se sirve cache local, se encolan pendientes y se sincroniza al recuperar conexion.

### "Que aporta Redis si ya existe Room?"
Redis acelera backend para todos los clientes; Room acelera experiencia local por dispositivo.

### "Niimbot afecta a la logica core?"
No. Es integracion de salida: la logica de inventario sigue desacoplada del canal de impresion.

---

## 6) Mini guion literal (texto para decir)

"En mi parte del Sprint 3 he trabajado tres bloques. Primero, la importacion CSV: implementamos un flujo robusto con validacion, modo dry-run, fuzzy threshold para coincidencias ambiguas y sistema de revisiones para decisiones manuales.  
Segundo, cache hibrido y offline: en backend usamos Redis para acelerar lecturas y en Android Room para cache local persistente con estrategia cache-first. Si no hay red, la app sigue operativa y sincroniza despues.  
Tercero, integracion externa con Niimbot B1 mediante SDK oficial, para imprimir etiquetas reales desde la app.  
Con esto cubrimos robustez de datos, rendimiento y una integracion hardware real de extremo a extremo." 

