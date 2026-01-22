# 📱 Frontend Android – Sprint 1  
Sistema de Gestión de Inventario con Sensores Simulados

## 👤 Responsable
Frontend Android – Sprint 1  
(Parte realizada por Natalia)

---

## 🎯 Objetivo del Sprint 1 (Frontend)
Implementar una aplicación Android funcional que permita:
- Autenticación básica de usuario
- Navegación principal de la app
- Visualización de productos (mock)
- Registro de movimientos de inventario (entrada / salida)
- Flujo completo de escaneo y confirmación
- Persistencia básica de sesión

El foco del Sprint 1 ha sido **que el flujo funcione correctamente**, dejando integraciones avanzadas (API real, ML Kit, offline avanzado) para sprints posteriores.

---

## 🧱 Arquitectura general
El frontend está desarrollado en **Android (Kotlin)** usando:
- Activities + XML
- ViewBinding
- Repositorios *fake* (mock) para simular backend
- Separación por capas sencilla

### Estructura de paquetes
com.example.inventoryapp
│
├── ui
│ ├── auth → Login
│ ├── home → Menú principal
│ ├── products → Listado y detalle de productos
│ ├── scan → Escaneo (manual en Sprint 1)
│ └── movements → Confirmación y resultado del movimiento
│
├── domain.model → Modelos (Product, Movement, MovementType)
│
├── data
│ ├── local → Gestión de sesión
│ └── repository
│ └── fake → Repositorios simulados


---

## 🖥️ Pantallas implementadas

### 1️⃣ Login
- Usuario y contraseña (validación básica)
- Guarda sesión local (token simulado)
- Si hay sesión activa, se omite el login

📄 `LoginActivity.kt`  
📄 `activity_login.xml`

---

### 2️⃣ Home / Menú principal
Pantalla central de navegación con:
- Botón **Escanear**
- Botón **Productos**
- Botón **Cerrar sesión**

📄 `HomeActivity.kt`  
📄 `activity_home.xml`

---

### 3️⃣ Listado de productos (mock)
- Lista de productos simulados
- Información básica: nombre, SKU, stock
- Click en producto → detalle

📄 `ProductListActivity.kt`  
📄 `ProductAdapter.kt`  
📄 `activity_product_list.xml`  
📄 `item_product.xml`

---

### 4️⃣ Detalle de producto
- Nombre
- SKU
- Categoría
- Stock actual (mock)
- Botón **Registrar movimiento**

📄 `ProductDetailActivity.kt`  
📄 `activity_product_detail.xml`

---

### 5️⃣ Escaneo (Sprint 1 – manual)
- Introducción manual de código
- Simula el escaneo de código de barras
- Enlace al flujo de movimiento

📄 `ScanActivity.kt`  
📄 `activity_scan.xml`

📌 *Nota:* El lector real con cámara (ML Kit) queda planificado para Sprint 2.

---

### 6️⃣ Registro de movimiento
Flujo completo:
- Selección de tipo: **Entrada / Salida**
- Introducción de cantidad
- Confirmación
- Resultado de éxito / error

📄 `ConfirmMovementActivity.kt`  
📄 `ResultActivity.kt`  
📄 `activity_confirm_movement.xml`  
📄 `activity_result.xml`

---

## 🔄 Flujo funcional completo
Login
↓
Home
↓
Productos → Detalle → Registrar movimiento
↓
Escanear → Confirmar → Resultado


También es posible:
Home → Escanear → Confirmar → Resultado


---

## 🧪 Datos simulados (Mock)
Durante el Sprint 1 no se consume API real.

Se usan repositorios fake para:
- Productos
- Movimientos de inventario

📄 `FakeProductRepository.kt`  
📄 `FakeMovementRepository.kt`

Esto permite:
- Probar toda la app
- Validar flujos
- Facilitar la integración con backend en sprints posteriores

---

## 🔐 Gestión de sesión
- La sesión se guarda localmente
- Si existe sesión activa, la app entra directamente al contenido
- Se incluye opción de **Cerrar sesión** desde el menú

📄 `SessionManager.kt`

---

## 🧩 Estado del backend
- El backend es responsabilidad de otro equipo
- El frontend está preparado para integrarse vía API en futuros sprints
- En Sprint 1 se prioriza funcionalidad y estructura

---

## 📌 Conclusión Sprint 1
✔ Aplicación Android funcional  
✔ Flujo completo de inventario  
✔ Navegación clara  
✔ Código organizado  
✔ Preparado para integraciones futuras  

El Sprint 1 cumple los requisitos del frontend y sienta una base sólida para los siguientes sprints.

---
