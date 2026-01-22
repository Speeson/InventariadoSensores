# 📱 Android – Escaneo de productos y movimientos de stock (P5)

## 📌 Descripción general
Esta parte del proyecto corresponde al **flujo de registro de movimientos de stock desde la aplicación Android**, permitiendo identificar un producto mediante un código y registrar **entradas y salidas de inventario**.

En el **Sprint 1**, el objetivo principal ha sido **implementar un flujo funcional completo**, priorizando que la aplicación funcione correctamente antes de añadir funcionalidades avanzadas.

---

## 🎯 Funcionalidad implementada (Sprint 1)

El usuario puede:

- Introducir un **código de producto** (escaneo manual).
- Seleccionar el **tipo de movimiento**:
  - Entrada (IN)
  - Salida (OUT)
- Indicar la **cantidad** del movimiento.
- Confirmar la operación.
- Recibir una **respuesta visual** de éxito o error.

El flujo está completamente operativo dentro de la aplicación.

---

## 🧭 Flujo de pantallas

### 1️⃣ Escaneo / Introducción de código
- Pantalla inicial para introducir el código del producto.
- Validación para evitar códigos vacíos.
- Preparada para sustituir la entrada manual por escaneo con cámara en futuros sprints.

### 2️⃣ Confirmación de movimiento
- Selección del tipo de movimiento (entrada o salida).
- Introducción de la cantidad.
- Validaciones básicas (cantidad mayor que 0).

### 3️⃣ Resultado
- Mensaje de confirmación si el movimiento es válido.
- Mensaje de error si los datos introducidos no son correctos.

---

## 🔍 Validaciones implementadas
Antes de enviar un movimiento, la aplicación comprueba:

- Que el código del producto no esté vacío.
- Que la cantidad sea mayor que cero.

Esto evita el envío de datos inválidos al sistema.

---

## 🔄 Envío de movimientos (simulado)

En este sprint, el envío de movimientos se realiza mediante un **repositorio simulado (Fake Repository)**.

### ¿Por qué se utiliza una simulación?
- El backend aún está en desarrollo.
- Permite validar el flujo completo sin depender de otros módulos.
- Facilita el desarrollo incremental por sprints.

La estructura está preparada para sustituir este repositorio por una implementación real conectada a la API.

---

## 📦 Sincronización / Offline (Sprint 1)
- El diseño desacopla la lógica de envío del resto de la aplicación.
- En Sprint 1 se implementa una **versión mínima**, suficiente para demostrar el funcionamiento.
- La sincronización offline completa queda planificada para el siguiente sprint.

---

## 🧱 Arquitectura (resumen)
La implementación sigue una separación clara de responsabilidades:

- **UI**: pantallas y navegación.
- **Domain**: modelos de negocio (`Movement`, `MovementType`).
- **Data**: repositorios (simulados en Sprint 1).

Esta estructura facilita la escalabilidad y el mantenimiento del proyecto.

---

## 🚀 Estado actual
- ✅ Flujo completo funcional
- ✅ Validaciones implementadas
- ✅ Envío de datos simulado
- ✅ Preparado para integración con backend real
- ✅ Listo para la demo del Sprint 1

---

## 🔜 Próximos pasos (Sprint 2)
- Integración de escáner real con cámara (ML Kit).
- Implementación de cola local para funcionamiento offline.
- Conexión con la API real.
- Mejoras visuales y de experiencia de usuario.

---

## 👤 Autor
**P5 – Android Escaneo y Movimientos**  
Sprint 1 – Proyecto *Sistema de Gestión de Inventario con Sensores Simulados*
