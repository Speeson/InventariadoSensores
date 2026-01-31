# 📱 Frontend – Sprint 2  
## InventoryApp (Android)

Este documento resume el trabajo realizado en el **frontend durante el Sprint 2**, centrado principalmente en **la lógica del frontend y la integración con el backend**, dejando preparada la base para que otros compañeros puedan mejorar la parte visual (UI/UX).

---

## 🎯 Objetivo del Sprint 2 (Frontend)

- Gestionar **eventos y movimientos** desde el frontend  
- Manejar **estados de eventos** (pendiente / procesado / error)  
- Unificar el **flujo de escaneo**  
- Implementar una **pantalla de rotación**  
- Preparar el frontend para trabajar correctamente **online y offline**

---

## ✅ Tareas realizadas

### 🟢 TC-79 – Flujo por eventos
- Implementación del flujo de eventos en frontend.
- Visualización del estado de cada evento:
  - pendiente  
  - procesado  
  - error  
- Integración con el backend para obtener y mostrar eventos reales.
- Uso de listas para representar eventos individuales.

**Archivos relacionados:**
- `EventsActivity.kt`
- `item_event_row.xml`
- lógica asociada a eventos y estados

---

### 🟢 TC-89 – Flujo de escaneo con un solo endpoint
- Unificación del flujo de escaneo para que el frontend:
  - llame a **un único endpoint**
  - delegue la lógica de decisión al backend
- Simplificación del código y mejora de mantenimiento.

**Archivos relacionados:**
- `ScanActivity.kt`
- `RemoteScanRepository`

---

### 🟢 TC-104 – Pantalla de Rotación (tabla simple)
- Creación de una **pantalla específica de rotación**.
- La rotación muestra un **resumen por producto**, no eventos individuales:
  - entradas (IN)
  - salidas (OUT)
  - stock neto
  - número de eventos
  - última fecha de movimiento
- Acceso a la pantalla desde el Home.

**Archivos creados:**
- `RotationActivity.kt`
- `RotationAdapter.kt`
- `RotationRow.kt`
- `activity_rotation.xml`
- `item_rotation_row.xml`

---

## 🧠 Enfoque del trabajo realizado

El trabajo se ha centrado principalmente en:

- La **lógica del frontend**
- La **conexión con el backend**
- El manejo de datos reales (productos, eventos, movimientos)
- La preparación de las pantallas para que puedan ser mejoradas visualmente

La parte visual (colores, estilos, diseño) se ha dejado **intencionalmente simple**, para permitir que otros compañeros se encarguen de:
- mejorar la experiencia de usuario
- ajustar mensajes de éxito y error
- embellecer las pantallas y listas

---

## 🎨 XML preparados para mejoras visuales

Se han creado o adaptado varios layouts que ya funcionan y están listos para ser mejorados visualmente sin tocar la lógica:

- `item_product.xml`
- `item_event_row.xml`
- `item_rotation_row.xml`
- `activity_home.xml`
- `activity_events.xml`
- `activity_rotation.xml`

> ⚠️ Importante: los `id` de las vistas **no deben cambiarse**, ya que están vinculados a la lógica en Kotlin mediante ViewBinding.

---

## 🔄 Trabajo en equipo

- La lógica principal del frontend y la integración con backend se ha desarrollado en esta rama.
- Otros compañeros pueden traer estos cambios a sus ramas y centrarse en:
  - UI
  - mensajes
  - alertas
  - experiencia de usuario

Esta división permite separar claramente:
- **Lógica y datos** → frontend funcional  
- **Diseño y UX** → frontend visual

---

## ✅ Estado final

- Sprint 2 completado en frontend  
- Funcionalidad estable  
- Código preparado para mejoras visuales  
- Integración correcta con backend  
