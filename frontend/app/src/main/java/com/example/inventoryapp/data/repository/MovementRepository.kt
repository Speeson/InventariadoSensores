package com.example.inventoryapp.data.repository
import com.example.inventoryapp.domain.model.Movement

    interface MovementRepository {
        suspend fun sendMovement(movement: Movement): Result<Unit>
}