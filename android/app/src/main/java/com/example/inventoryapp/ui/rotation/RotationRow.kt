package com.example.inventoryapp.ui.rotation

data class RotationRow(
    val movementId: Int,
    val productId: Int,
    val sku: String,
    val name: String,
    val quantity: Int,
    val fromLocation: String,
    val toLocation: String,
    val createdAt: String
)
