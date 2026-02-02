package com.example.inventoryapp.ui.reports

data class TurnoverRow(
    val productId: Int,
    val sku: String,
    val name: String,
    val outs: Int,
    val stockInitial: Double,
    val stockFinal: Double,
    val stockAverage: Double,
    val turnover: Double?
)
