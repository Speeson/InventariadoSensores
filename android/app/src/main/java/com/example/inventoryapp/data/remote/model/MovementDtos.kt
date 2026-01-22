package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

enum class MovementSourceDto { SCAN, MANUAL }
enum class MovementTypeDto { IN, OUT, ADJUST }

data class MovementOperationRequest(
    @SerializedName("product_id") val productId: Int,
    val quantity: Int,
    val location: String,
    @SerializedName("movement_source") val movementSource: MovementSourceDto
)

data class MovementAdjustOperationRequest(
    @SerializedName("product_id") val productId: Int,
    val delta: Int, // puede ser negativo o positivo, pero no 0
    val location: String,
    @SerializedName("movement_source") val movementSource: MovementSourceDto
)

data class MovementResponseDto(
    val id: Int,
    @SerializedName("product_id") val productId: Int,
    val quantity: Int,
    @SerializedName("movement_type") val movementType: MovementTypeDto,
    @SerializedName("movement_source") val movementSource: MovementSourceDto,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("created_at") val createdAt: String
)

data class MovementWithStockResponseDto(
    val stock: StockResponseDto,
    val movement: MovementResponseDto
)
