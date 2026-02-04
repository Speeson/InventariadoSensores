package com.example.inventoryapp.ui.events

import com.example.inventoryapp.data.remote.model.EventTypeDto

data class EventRowUi(
    val id: Int,
    val eventType: EventTypeDto,
    val productId: Int,
    val productName: String?,
    val delta: Int,
    val source: String,
    val createdAt: String,
    val status: String,
    val isPending: Boolean,
    val pendingMessage: String? = null
)
