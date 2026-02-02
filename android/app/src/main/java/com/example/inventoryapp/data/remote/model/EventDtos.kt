package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

enum class EventTypeDto { SENSOR_IN, SENSOR_OUT }

data class EventCreateDto(
    @SerializedName("event_type") val eventType: EventTypeDto,
    @SerializedName("product_id") val productId: Int,
    val delta: Int, // > 0
    val source: String = "sensor_simulado",
    val location: String = "default",
    @SerializedName("idempotency_key") val idempotencyKey: String
)

data class EventResponseDto(
    val id: Int,
    @SerializedName("event_type") val eventType: EventTypeDto,
    @SerializedName("product_id") val productId: Int,
    val delta: Int,
    val source: String,
    val processed: Boolean,
    @SerializedName("event_status") val eventStatus: String? = null,
    @SerializedName("created_at") val createdAt: String
)

data class EventListResponseDto(
    val items: List<EventResponseDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)
