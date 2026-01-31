package com.example.inventoryapp.data.remote.model

data class LowStockDto(
    val stockId: Int,
    val productId: Int,
    val productName: String,
    val location: String,
    val quantity: Int,
    val threshold: Int
)