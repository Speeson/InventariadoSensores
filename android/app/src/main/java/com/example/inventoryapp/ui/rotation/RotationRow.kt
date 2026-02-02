package com.example.inventoryapp.ui.rotation

data class RotationRow(
    val productId: Int,
    val sku: String,
    val name: String,
    val totalIn: Int,
    val totalOut: Int,
    val stock: Int
)
