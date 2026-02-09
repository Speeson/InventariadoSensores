package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

enum class AlertStatusDto {
    PENDING,
    ACK,
    RESOLVED
}

enum class AlertTypeDto {
    LOW_STOCK,
    OUT_OF_STOCK,
    LARGE_MOVEMENT,
    TRANSFER_COMPLETE,
    IMPORT_ISSUES
}

data class AlertResponseDto(
    val id: Int,
    @SerializedName("stock_id") val stockId: Int?,
    val quantity: Int,
    @SerializedName("min_quantity") val minQuantity: Int,
    @SerializedName("alert_status") val alertStatus: AlertStatusDto,
    @SerializedName("alert_type") val alertType: AlertTypeDto,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("ack_at") val ackAt: String? = null,
    @SerializedName("ack_user_id") val ackUserId: Int? = null
)

data class AlertListResponseDto(
    val items: List<AlertResponseDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)
