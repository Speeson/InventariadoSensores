package com.example.inventoryapp.data.repository.remote

import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.AlertDto

class AlertRepository {

    suspend fun listAlerts(): Result<List<AlertDto>> {
        return try {
            val res = NetworkModule.api.listAlerts()
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!.items)
            } else {
                Result.failure(Exception("Error ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ackAlert(alertId: Int): Result<Unit> {
        return try {
            val res = NetworkModule.api.ackAlert(alertId)
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