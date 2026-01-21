package com.example.inventoryapp.data.repository.fake
import com.example.inventoryapp.data.repository.MovementRepository
import com.example.inventoryapp.domain.model.Movement
import kotlinx.coroutines.delay

class FakeMovementRepository : MovementRepository {
    override suspend fun sendMovement(movement: Movement): Result<Unit> {
        delay(600) // simula red
        return if (movement.barcode.isNotBlank() && movement.quantity > 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("Datos inválidos"))
        }
    }
}