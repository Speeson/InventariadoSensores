package com.example.inventoryapp.data.remote.model

data class EventResponse(
    val id: Int,
    val event_type: String,
    val product_id: Int,
    val delta: Int,
    val source: String,
    val processed: Boolean,
    val created_at: String
)