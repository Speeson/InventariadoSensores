package com.example.inventoryapp.data.repository.remote

import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.*
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType

class RemoteScanRepository {

    /**
     * Envía:
     * 1) Crea evento (SENSOR_IN / SENSOR_OUT)
     * 2) Registra movimiento (IN/OUT) para impactar stock
     */
    suspend fun sendFromBarcode(movement: Movement): Result<String> {
        return try {
            // 1) Resolver producto por barcode
            val prodRes = NetworkModule.api.listProducts(barcode = movement.barcode, limit = 1, offset = 0)
            if (!prodRes.isSuccessful || prodRes.body() == null) {
                return Result.failure(Exception("No se pudo buscar el producto (HTTP ${prodRes.code()})"))
            }

            val product = prodRes.body()!!.items.firstOrNull()
                ?: return Result.failure(Exception("No existe producto con barcode=${movement.barcode}"))

            val productId = product.id
            val location = movement.location?.ifBlank { "default" } ?: "default"

            // 2) Crear evento (lo que tú querías)
            val eventType = if (movement.type == MovementType.IN) EventTypeDto.SENSOR_IN else EventTypeDto.SENSOR_OUT
            val eventRes = NetworkModule.api.createEvent(
                EventCreateDto(
                    eventType = eventType,
                    productId = productId,
                    delta = movement.quantity,       // siempre positivo
                    source = "SCAN",
                    location = location
                )
            )
            if (!eventRes.isSuccessful) {
                return Result.failure(Exception("No se pudo crear evento (HTTP ${eventRes.code()}): ${eventRes.errorBody()?.string()}"))
            }

            // 3) Registrar movimiento para que actualice stock (opcional pero recomendado)
            val op = MovementOperationRequest(
                productId = productId,
                quantity = movement.quantity,
                location = location,
                movementSource = MovementSourceDto.SCAN
            )

            val moveRes = if (movement.type == MovementType.IN) {
                NetworkModule.api.movementIn(op)
            } else {
                NetworkModule.api.movementOut(op)
            }

            if (!moveRes.isSuccessful || moveRes.body() == null) {
                return Result.failure(Exception("No se pudo registrar movimiento (HTTP ${moveRes.code()}): ${moveRes.errorBody()?.string()}"))
            }

            val body = moveRes.body()!!
            val msg = "OK: ${product.name} (id=${productId}) | stock(${body.stock.location})=${body.stock.quantity}"
            Result.success(msg)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
