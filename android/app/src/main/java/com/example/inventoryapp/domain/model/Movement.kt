package com.example.inventoryapp.domain.model

import java.util.UUID

data class Movement(
    val clientMovementId: String = UUID.randomUUID().toString(),
    val barcode: String,
    val type: MovementType,
    val quantity: Int,
    val location: String? = null
)
