package com.example.inventoryapp.ui.common

object ApiErrorFormatter {

    fun format(code: Int, detail: String? = null): String {
        val detailLower = detail?.lowercase() ?: ""
        return when (code) {
            400 -> "Solicitud inválida"
            401 -> "Sesión caducada. Inicia sesión."
            403 -> "Permisos insuficientes"
            404 -> {
                when {
                    detailLower.contains("categoria") || detailLower.contains("category") -> "Categoría no encontrada"
                    detailLower.contains("producto") || detailLower.contains("product") -> "Producto no encontrado"
                    else -> "No encontrado"
                }
            }
            409 -> "Conflicto: ya existe"
            422 -> "Datos no válidos"
            else -> if (code >= 500) "Error del servidor" else "Error ${code}"
        }
    }
}
