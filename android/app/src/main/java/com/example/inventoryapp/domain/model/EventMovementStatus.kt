package com.example.inventoryapp.domain.model

/**
 * Estado visual del flujo que viene por EVENTO.
 * - PENDING: evento creado pero aún no marcado como procesado
 * - PROCESSED: evento procesado
 * - ERROR: fallo en llamada/s o lógica
 */
enum class EventMovementStatus {
    PENDING,
    PROCESSED,
    ERROR
}