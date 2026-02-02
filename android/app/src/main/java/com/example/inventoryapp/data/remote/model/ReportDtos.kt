package com.example.inventoryapp.data.remote.model

import com.google.gson.annotations.SerializedName

data class TurnoverItemDto(
    @SerializedName("product_id") val productId: Int,
    val sku: String,
    val name: String,
    val turnover: Double?,
    val outs: Int,
    @SerializedName("stock_initial") val stockInitial: Double,
    @SerializedName("stock_final") val stockFinal: Double,
    @SerializedName("stock_average") val stockAverage: Double,
    val location: String?
)

data class TurnoverResponseDto(
    val items: List<TurnoverItemDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class TopConsumedItemDto(
    @SerializedName("product_id") val productId: Int,
    val sku: String,
    val name: String,
    @SerializedName("total_out") val totalOut: Int
)

data class TopConsumedResponseDto(
    val items: List<TopConsumedItemDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)
