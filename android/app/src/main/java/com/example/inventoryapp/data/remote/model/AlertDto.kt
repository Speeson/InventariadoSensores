package com.example.inventoryapp.data.remote.model

data class AlertDto(
    val id: Int,
    val productId: Int,
    val message: String,
    val status: String, // "pending" | "processed"
    val createdAt: String
)