# Guía de usuario - App IoTrack (Android)

Esta guía explica, paso a paso, cómo usar la aplicación IoTrack como usuario final.

---

## 1) Qué puedes hacer con la app

- Iniciar sesión con tu cuenta.
- Consultar productos, stock, movimientos y eventos.
- Escanear códigos de barras para registrar operaciones.
- Trabajar en modo offline y sincronizar al volver la conexión.
- Recibir alertas importantes.
- Imprimir etiquetas de producto (incluida integración con Niimbot B1, según permisos).

---

## 2) Requisitos mínimos

- Android 7.0 o superior (API 24+).
- Conexión a internet para modo online.
- Permiso de cámara (escaneo).
- Bluetooth activo y permisos Bluetooth (si vas a imprimir con Niimbot).

---

## 3) Primer inicio y conexión al servidor

1. Abre la app.
2. En login, introduce email y contraseña.
3. Si usas emulador Android, la URL por defecto suele ser `http://10.0.2.2:8000/`.
4. Si usas móvil físico en red local, configura la IP del servidor.

Notas:
- Existe acción de long-press en login/home para actualizar la URL del servidor.
- Si no hay conexión y ya iniciaste sesión antes, la app puede entrar en modo offline.

---

## 4) Inicio de sesión y roles

La app maneja 3 roles:

- `USER`: consulta y operaciones limitadas.
- `MANAGER`: gestión operativa (crear/editar en inventario).
- `ADMIN`: control completo y funciones avanzadas.

Según tu rol, verás botones habilitados o bloqueados (por ejemplo, acciones de edición o impresión).

---

## 5) Navegación principal

Desde Home puedes entrar a:

- Productos
- Stock
- Movimientos
- Eventos
- Alertas
- Importaciones (si aplica por rol)
- Estado del sistema

También dispones de menú lateral con acciones de sesión y configuración visual.

---

## 6) Flujos de uso más comunes

### 6.1 Consultar productos

1. Entra en `Productos`.
2. Usa filtros (nombre, SKU, barcode, categoría).
3. Abre detalle para ver información completa.

### 6.2 Crear/editar/eliminar inventario (manager/admin)

1. Desde `Productos` o `Stock`, usa el bloque de creación/edición.
2. Completa los campos obligatorios.
3. Confirma y revisa el resultado en listado.

Si un elemento no puede eliminarse por historial asociado (movimientos/eventos), la app mostrará error funcional.

### 6.3 Registrar movimientos con escaneo

1. Abre `Escanear`.
2. Escanea o introduce código.
3. Indica tipo de movimiento (IN/OUT/ADJUST según pantalla) y cantidad.
4. Confirma.

### 6.4 Revisar eventos

1. Entra en `Eventos`.
2. Filtra por tipo, producto o estado.
3. Verifica que los eventos procesados impactan en stock/movimientos.

---

## 7) Etiquetas e impresión (Niimbot B1)

Si tu rol lo permite:

1. Abre un producto y entra en la pantalla de etiqueta.
2. Puedes:
   - Descargar SVG
   - Descargar PDF
   - Regenerar etiqueta
   - Imprimir
3. En impresión Niimbot:
   - Opción 1: impresión directa por Bluetooth (SDK oficial integrado).
   - Opción 2: abrir app oficial Niimbot (fallback).

Requisitos:
- Bluetooth activado.
- Permisos Bluetooth aceptados.
- Impresora Niimbot disponible/emparejada.

---

## 8) Modo offline y sincronización

La app permite trabajar sin red en operaciones soportadas.

Comportamiento esperado:
- Verás indicadores visuales de offline.
- Las acciones se guardan en pendientes offline.
- Al recuperar conexión, la app reintenta sincronizar automáticamente.
- Se mostrarán diálogos de éxito/error de sincronización.

Recomendación:
- Revisa la sección de pendientes offline si alguna acción no se envió correctamente.

---

## 9) Alertas y notificaciones

- Las alertas pueden llegar en tiempo real y mostrarse con diálogos.
- Puedes marcar alertas como vistas (según rol y sección).
- Si no hay alertas pendientes, la app lo indicará con mensaje correspondiente.

---

## 10) Problemas frecuentes

### No conecta con el servidor

- Verifica URL/IP configurada.
- Comprueba que backend está levantado y accesible.
- Si estás en emulador, usa `10.0.2.2` (no `localhost`).

### No puedo imprimir con Niimbot

- Activa Bluetooth.
- Acepta permisos Bluetooth.
- Comprueba que la impresora está encendida y cerca.
- Intenta primero con la opción de abrir app oficial Niimbot.

### No aparece un cambio hecho en offline

- Espera la sincronización al recuperar red.
- Revisa la lista de pendientes offline y reintenta.
- Comprueba si hubo error de validación en backend.

---

## 11) Buenas prácticas de uso

- Mantener red estable para operaciones masivas.
- Revisar alertas importantes al inicio de jornada.
- Evitar duplicar acciones si ya se mostró confirmación.
- Usar la impresión de etiquetas desde el detalle de producto para asegurar datos actualizados.

---

## 12) Cierre de sesión

Desde el menú lateral selecciona `Cerrar sesión`.

Si compartes dispositivo, es recomendable cerrar sesión al finalizar.
