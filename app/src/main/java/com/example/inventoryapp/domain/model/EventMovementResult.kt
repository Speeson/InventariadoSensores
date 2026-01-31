package com.example.inventoryapp.domain.model

/**
 * Resultado que pasamos a la UI cuando el movimiento viene por evento.
 * status -> para mostrar Pendiente/Procesado/Error
 * message -> detalle para el usuario (y para debug rápido)
 */
data class EventMovementResult(
    val status: EventMovementStatus,
    val message: String
)
