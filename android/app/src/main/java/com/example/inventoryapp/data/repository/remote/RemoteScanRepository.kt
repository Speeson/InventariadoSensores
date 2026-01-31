package com.example.inventoryapp.data.repository.remote

import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.domain.model.Movement
import com.example.inventoryapp.domain.model.MovementType

class RemoteScanRepository {

    /**
     */
        return try {
            // 1) Resolver producto por barcode
            if (!prodRes.isSuccessful || prodRes.body() == null) {
            }

            val product = prodRes.body()!!.items.firstOrNull()

            val productId = product.id
            val location = movement.location?.ifBlank { "default" } ?: "default"

            val eventRes = NetworkModule.api.createEvent(
                EventCreateDto(
                    eventType = eventType,
                    productId = productId,
                    delta = movement.quantity,       // siempre positivo
                    location = location
                )
            )
            if (!eventRes.isSuccessful) {
            }


            } else {
            }

            }


        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
