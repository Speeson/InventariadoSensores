package com.example.inventoryapp.data.remote.model

data class EventCreateRequest(
    val event_type: String,   // "SENSOR_IN" | "SENSOR_OUT"
    val product_id: Int,
    val delta: Int = 1,
    val source: String = "SCAN",
    val location: String = "default"
)