package com.example.inventoryapp.domain.model

data class EventMovementResult(
    val ok: Boolean,
    val status: EventMovementStatus,
    val message: String
)
