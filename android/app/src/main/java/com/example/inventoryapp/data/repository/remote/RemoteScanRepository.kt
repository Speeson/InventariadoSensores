package com.example.inventoryapp.data.repository.remote

import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.EventCreateDto
import com.example.inventoryapp.data.remote.model.EventTypeDto
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType

class RemoteScanRepository {

    // Envia un solo evento; el backend procesa el stock
    suspend fun sendFromBarcode(movement: Movement): Result<String> {
        return try {
            val prodRes = NetworkModule.api.listProducts(barcode = movement.barcode, limit = 1, offset = 0)
            if (!prodRes.isSuccessful || prodRes.body() == null) {
                return Result.failure(Exception("No se pudo buscar el producto (HTTP ${prodRes.code()})"))
            }

            val product = prodRes.body()!!.items.firstOrNull()
                ?: return Result.failure(Exception("No existe producto con barcode=${movement.barcode}"))

            val productId = product.id
            val location = movement.location?.ifBlank { "default" } ?: "default"

            val eventType = if (movement.type == MovementType.IN) EventTypeDto.SENSOR_IN else EventTypeDto.SENSOR_OUT
            val eventRes = NetworkModule.api.createEvent(
                EventCreateDto(
                    eventType = eventType,
                    productId = productId,
                    delta = movement.quantity,
                    source = "SCAN",
                    location = location,
                    idempotencyKey = java.util.UUID.randomUUID().toString()
                )
            )
            if (!eventRes.isSuccessful) {
                return Result.failure(Exception("No se pudo crear evento (HTTP ${eventRes.code()}): ${eventRes.errorBody()?.string()}"))
            }

            val body = eventRes.body()
            val status = body?.eventStatus ?: if (body?.processed == true) "PROCESSED" else "PENDING"
            val msg = "OK: ${product.name} (id=${productId}) | evento ${body?.id ?: "?"} | ${status}"
            Result.success(msg)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
