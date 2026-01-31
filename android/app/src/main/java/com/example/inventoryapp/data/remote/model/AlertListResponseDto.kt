package com.example.inventoryapp.data.remote.model

data class AlertListResponseDto(
    val items: List<AlertDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)