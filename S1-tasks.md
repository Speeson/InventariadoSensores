# 🔐 Tareas – Backend Auth & Seguridad (Sprint 1)

Este documento recoge las **tareas del Sprint 1** correspondientes a la **autenticación, autorización y seguridad** del backend.

Responsable: **Esteban**  
Módulo: Backend (FastAPI)

---

## 🎯 Objetivo del Sprint 1

Disponer de un sistema de autenticación **seguro y funcional** que permita:
- Identificar usuarios
- Autorizar acciones según su rol
- Proteger los endpoints de la API
- Facilitar la integración con la app Android

---

## 📌 Historia principal

**Historia:** Autenticación con JWT y roles  
**Sprint:** Sprint 1  
**Story Points:** 5  

> Como usuario quiero iniciar sesión de forma segura para acceder al sistema según mi rol.

---

## 🧩 Subtareas detalladas

### 1️⃣ Hash de contraseñas
**Descripción:**  
Implementar el cifrado de contraseñas utilizando un algoritmo seguro.

**Qué incluye:**
- Uso de BCrypt o Argon2
- Funciones para:
  - Hashear contraseñas
  - Verificar contraseñas

**Resultado esperado:**  
Las contraseñas nunca se almacenan en texto plano.

---

### 2️⃣ Endpoint de registro (`POST /auth/register`)
**Descripción:**  
Permitir la creación de nuevos usuarios en el sistema.

**Qué incluye:**
- Validación de datos (email único, password mínimo)
- Asignación de rol por defecto (`User`)
- Almacenamiento seguro en base de datos

**Resultado esperado:**  
Un usuario puede registrarse correctamente sin exponer datos sensibles.

---

### 3️⃣ Endpoint de login (`POST /auth/login`)
**Descripción:**  
Autenticar usuarios y emitir un token JWT.

**Qué incluye:**
- Verificación de credenciales
- Generación de JWT firmado
- Respuesta con token y tipo `Bearer`

**Resultado esperado:**  
El usuario obtiene un token válido para acceder a la API.

---

### 4️⃣ Generación y validación de JWT
**Descripción:**  
Configurar la lógica de creación y validación de tokens JWT.

**Qué incluye:**
- Clave secreta y algoritmo (HS256)
- Expiración del token
- Manejo de tokens inválidos o expirados

**Resultado esperado:**  
Los tokens expiran y no pueden ser reutilizados indefinidamente.

---

### 5️⃣ Autenticación del usuario actual
**Endpoint:** `GET /auth/me`

**Descripción:**  
Permitir al cliente conocer el usuario autenticado.

**Qué incluye:**
- Decodificación del token
- Recuperación del usuario desde la BD
- Respuesta con datos básicos (id, email, rol)

**Resultado esperado:**  
Android puede obtener la información del usuario autenticado fácilmente.

---

### 6️⃣ Autorización por roles
**Descripción:**  
Restringir el acceso a endpoints según el rol del usuario.

**Qué incluye:**
- Dependencia `get_current_user`
- Dependencia `require_roles`
- Respuestas correctas:
  - 401 (no autenticado)
  - 403 (sin permisos)

**Resultado esperado:**  
Cada rol solo puede acceder a las acciones permitidas.

---

### 7️⃣ Configuración CORS
**Descripción:**  
Permitir el acceso controlado desde la aplicación Android.

**Qué incluye:**
- Configuración de orígenes permitidos
- Métodos y headers autorizados
- Documentación de la configuración

**Resultado esperado:**  
La API acepta peticiones desde el cliente autorizado.

---

### 8️⃣ Documentación en Swagger (OpenAPI)
**Descripción:**  
Documentar correctamente los endpoints de autenticación.

**Qué incluye:**
- Modelos de request y response
- Esquema de seguridad Bearer
- Ejemplos de uso y errores comunes

**Resultado esperado:**  
La autenticación es entendible y usable desde Swagger.

---

## ✅ Definition of Done (Auth & Seguridad)

Una tarea de autenticación se considera terminada cuando:
- Funciona correctamente
- Cumple los requisitos de seguridad
- No expone información sensible
- Está documentada en Swagger
- Se puede demostrar en ejecución

---

## 📝 Notas finales

- Redis, refresh tokens y mejoras avanzadas se abordarán en sprints posteriores.
- Este módulo es la base de seguridad para el resto del sistema.
