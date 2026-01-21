package com.example.inventoryapp.data.repository.fake

import com.example.inventoryapp.domain.model.Product

class FakeProductRepository {
    fun getProducts(): List<Product> = listOf(
        Product(1, "SKU-001", "Sensor Temperatura", "Sensores", 12),
        Product(2, "SKU-002", "Sensor Humedad", "Sensores", 4),
        Product(3, "SKU-003", "Placa ESP32", "Microcontroladores", 20),
        Product(4, "SKU-004", "Relé 5V", "Componentes", 7),
        Product(5, "SKU-005", "Cable Dupont", "Accesorios", 50)
    )

    fun findBySkuOrName(code: String): Product? {
        val q = code.trim().lowercase()
        return getProducts().firstOrNull {
            it.sku.lowercase() == q || it.name.lowercase().contains(q)
        }
    }
}
