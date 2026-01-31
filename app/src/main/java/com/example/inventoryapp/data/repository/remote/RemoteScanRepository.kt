package com.example.inventoryapp.data.repository.remote

import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventTypeDto
import com.example.inventoryapp.domain.model.EventMovementResult
import com.example.inventoryapp.domain.model.EventMovementStatus
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType

class RemoteScanRepository {

    /**
     * ✅ TC-89
     * Flujo por escaneo: llamar SOLO a 1 endpoint.
     *
     * Nuevo flujo:
     * 1) Buscar producto por barcode
     * 2) POST /events (SENSOR_IN / SENSOR_OUT)
     * 3) Devolver EventMovementResult con estado:
     *    - PROCESSED si processed=true (backend ya aplicó stock)
     *    - PENDING   si processed=false (queda pendiente / worker)
     *
     * ❌ Ya NO llamamos a /movements/in ni /movements/out
     *    porque ahora POST /events ya actualiza stock directamente (Source.MANUAL / SCAN según backend).
     */
    suspend fun sendFromBarcode(movement: Movement): Result<EventMovementResult> {
        return try {
            // --------------------------
            // 1) Resolver producto por barcode
            // --------------------------
            val prodRes = NetworkModule.api.listProducts(
                barcode = movement.barcode,
                limit = 1,
                offset = 0
            )

            if (!prodRes.isSuccessful || prodRes.body() == null) {
                return Result.failure(
                    Exception("No se pudo buscar el producto (HTTP ${prodRes.code()})")
                )
            }

            val product = prodRes.body()!!.items.firstOrNull()
                ?: return Result.failure(
                    Exception("No existe producto con barcode=${movement.barcode}")
                )

            val productId = product.id
            val location = movement.location?.ifBlank { "default" } ?: "default"

            // --------------------------
            // 2) Crear evento (ÚNICA llamada al backend)  ✅ TC-89
            // --------------------------
            val eventType = if (movement.type == MovementType.IN) {
                EventTypeDto.SENSOR_IN
            } else {
                EventTypeDto.SENSOR_OUT
            }

            val eventRes = NetworkModule.api.createEvent(
                EventCreateDto(
                    eventType = eventType,
                    productId = productId,
                    delta = movement.quantity,   // siempre positivo
                    source = "SCAN",             // tu fuente de app
                    location = location
                )
            )

            // Si falla creación del evento -> devolvemos error
            if (!eventRes.isSuccessful) {
                return Result.failure(
                    Exception(
                        "No se pudo crear evento (HTTP ${eventRes.code()}): ${eventRes.errorBody()?.string()}"
                    )
                )
            }

            // --------------------------
            // 3) Interpretar respuesta del evento para mostrar estado en UI  ✅ TC-79/TC-89
            // --------------------------
            val eventBody = eventRes.body()

            // Por tu DTO, el backend devuelve "processed"
            val status = if (eventBody?.processed == true) {
                EventMovementStatus.PROCESSED
            } else {
                EventMovementStatus.PENDING
            }

            // Mensaje para la pantalla de resultado
            val msg = buildString {
                append("Evento creado: ${product.name} (id=$productId) | ")
                append("tipo=${eventType.name} | ")
                append("qty=${movement.quantity} | ")
                append("loc=$location | ")
                append("estado=${status.name}")
            }

            // ✅ devolvemos resultado completo (status + msg)
            Result.success(
                EventMovementResult(
                    status = status,
                    message = msg
                )
            )

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
