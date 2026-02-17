# Guia de usuario - App IoTrack (Android)

Esta guia explica, paso a paso, como usar la aplicacion IoTrack como usuario final.

---

## 1) Que puedes hacer con la app

- Iniciar sesion con tu cuenta.
- Consultar productos, stock, movimientos y eventos.
- Escanear codigos de barras para registrar operaciones.
- Trabajar en modo offline y sincronizar al volver la conexion.
- Recibir alertas importantes.
- Imprimir etiquetas de producto (incluida integracion con Niimbot B1, segun permisos).

---

## 2) Requisitos minimos

- Android 7.0 o superior (API 24+).
- Conexion a internet para modo online.
- Permiso de camara (escaneo).
- Bluetooth activo y permisos Bluetooth (si vas a imprimir con Niimbot).

---

## 3) Primer inicio y conexion al servidor

1. Abre la app.
2. En login, introduce email y contraseña.
3. Si usas emulador Android, la URL por defecto suele ser `http://10.0.2.2:8000/`.
4. Si usas movil fisico en red local, configura la IP del servidor.

Notas:
- Existe accion de long-press en login/home para actualizar la URL del servidor.
- Si no hay conexion y ya iniciaste sesion antes, la app puede entrar en modo offline.

---

## 4) Inicio de sesion y roles

La app maneja 3 roles:

- `USER`: consulta y operaciones limitadas.
- `MANAGER`: gestion operativa (crear/editar en inventario).
- `ADMIN`: control completo y funciones avanzadas.

Segun tu rol, veras botones habilitados o bloqueados (por ejemplo, acciones de edicion o impresion).

---

## 5) Navegacion principal

Desde Home puedes entrar a:

- Productos
- Stock
- Movimientos
- Eventos
- Alertas
- Importaciones (si aplica por rol)
- Estado del sistema

Tambien dispones de menu lateral con acciones de sesion y configuracion visual.

---

## 6) Flujos de uso mas comunes

### 6.1 Consultar productos

1. Entra en `Productos`.
2. Usa filtros (nombre, SKU, barcode, categoria).
3. Abre detalle para ver informacion completa.

### 6.2 Crear/editar/eliminar inventario (manager/admin)

1. Desde `Productos` o `Stock`, usa el bloque de creacion/edicion.
2. Completa los campos obligatorios.
3. Confirma y revisa el resultado en listado.

Si un elemento no puede eliminarse por historial asociado (movimientos/eventos), la app mostrara error funcional.

### 6.3 Registrar movimientos con escaneo

1. Abre `Escanear`.
2. Escanea o introduce codigo.
3. Indica tipo de movimiento (IN/OUT/ADJUST segun pantalla) y cantidad.
4. Confirma.

### 6.4 Revisar eventos

1. Entra en `Eventos`.
2. Filtra por tipo, producto o estado.
3. Verifica que los eventos procesados impactan en stock/movimientos.

---

## 7) Etiquetas e impresion (Niimbot B1)

Si tu rol lo permite:

1. Abre un producto y entra en la pantalla de etiqueta.
2. Puedes:
   - Descargar SVG
   - Descargar PDF
   - Regenerar etiqueta
   - Imprimir
3. En impresion Niimbot:
   - Opcion 1: impresion directa por Bluetooth (SDK oficial integrado).
   - Opcion 2: abrir app oficial Niimbot (fallback).

Requisitos:
- Bluetooth activado.
- Permisos Bluetooth aceptados.
- Impresora Niimbot disponible/emparejada.

---

## 8) Modo offline y sincronizacion

La app permite trabajar sin red en operaciones soportadas.

Comportamiento esperado:
- Veras indicadores visuales de offline.
- Las acciones se guardan en pendientes offline.
- Al recuperar conexion, la app reintenta sincronizar automaticamente.
- Se mostraran dialogos de exito/error de sincronizacion.

Recomendacion:
- Revisa la seccion de pendientes offline si alguna accion no se envio correctamente.

---

## 9) Alertas y notificaciones

- Las alertas pueden llegar en tiempo real y mostrarse con dialogos.
- Puedes marcar alertas como vistas (segun rol y seccion).
- Si no hay alertas pendientes, la app lo indicara con mensaje correspondiente.

---

## 10) Problemas frecuentes

### No conecta con el servidor

- Verifica URL/IP configurada.
- Comprueba que backend esta levantado y accesible.
- Si estas en emulador, usa `10.0.2.2` (no `localhost`).

### No puedo imprimir con Niimbot

- Activa Bluetooth.
- Acepta permisos Bluetooth.
- Comprueba que la impresora esta encendida y cerca.
- Intenta primero con la opcion de abrir app oficial Niimbot.

### No aparece un cambio hecho en offline

- Espera la sincronizacion al recuperar red.
- Revisa la lista de pendientes offline y reintenta.
- Comprueba si hubo error de validacion en backend.

---

## 11) Buenas practicas de uso

- Mantener red estable para operaciones masivas.
- Revisar alertas importantes al inicio de jornada.
- Evitar duplicar acciones si ya se mostro confirmacion.
- Usar la impresion de etiquetas desde el detalle de producto para asegurar datos actualizados.

---

## 12) Cierre de sesion

Desde el menu lateral selecciona `Cerrar sesion`.

Si compartes dispositivo, es recomendable cerrar sesion al finalizar.
