package com.example.inventoryapp.ui.common

import java.io.IOException

object ApiResultMapper {

    fun <T> fromResponse(res: retrofit2.Response<T>): UiResult {
        return when {
            res.code() == 401 -> UiResult.SessionExpired
            res.code() == 403 -> UiResult.Error("No tienes permisos para esta acción")
            res.code() == 404 -> UiResult.Error("Recurso no encontrado")
            res.code() == 422 -> UiResult.Error("Datos inválidos")
            res.isSuccessful -> UiResult.Success("Operación realizada correctamente")
            else -> UiResult.Error("Error del servidor (${res.code()})")
        }
    }

    fun fromException(e: Throwable): UiResult {
        return when (e) {
            is IOException -> UiResult.Error("Sin conexión. Inténtalo más tarde")
            else -> UiResult.Error(e.message ?: "Error inesperado")
        }
    }
}