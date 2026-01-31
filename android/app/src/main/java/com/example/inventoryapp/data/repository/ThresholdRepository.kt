package com.example.inventoryapp.data.repository

import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.LowStockDto
import com.example.inventoryapp.data.remote.model.StockUpdateDto

class ThresholdRepository {

    suspend fun listLowStocks(): Result<List<LowStockDto>> {
        return try {
            val res = NetworkModule.api.listLowStocks()
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Error ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStock(stockId: Int, quantity: Int): Result<Unit> {
        return try {
            val res = NetworkModule.api.updateStock(
                stockId,
                StockUpdateDto(quantity = quantity)
            )
            if (res.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}