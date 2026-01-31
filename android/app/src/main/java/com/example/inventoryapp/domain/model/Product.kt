package com.example.inventoryapp.domain.model

data class Product(
    val id: Int,
    val sku: String,
    val name: String,
    val category: String,
    val stock: Int
)
