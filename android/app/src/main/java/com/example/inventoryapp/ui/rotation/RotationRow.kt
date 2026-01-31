package com.example.inventoryapp.ui.rotation

data class RotationRow(
    val productId: Int,
    val productName: String,
    val inQty: Int,
    val outQty: Int,
    val net: Int,
    val eventsCount: Int,
    val lastDate: String
)
