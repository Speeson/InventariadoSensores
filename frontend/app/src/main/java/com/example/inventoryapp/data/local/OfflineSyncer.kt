package com.example.inventoryapp.data.local

import android.content.Context
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.*
import com.google.gson.Gson
import java.io.IOException

object OfflineSyncer {

    private val gson = Gson()

    /**
     * Devuelve cuántos pendientes se han reenviado correctamente.
     * - Solo reintenta si /health responde OK.
     * - Para en el primer fallo (para no “ciclar”).
     */
    suspend fun flush(context: Context): Int {
        val queue = OfflineQueue(context)

        if (queue.size() == 0) return 0

        // Si backend no está OK, no intentamos reenviar
        val healthOk = try {
            val h = NetworkModule.api.health()
            h.isSuccessful
        } catch (_: Exception) {
            false
        }
        if (!healthOk) return 0

        val items = queue.getAll()
        var sent = 0

        for (req in items) {
            val ok = try {
                sendOne(req)
            } catch (_: IOException) {
                false // sin red o backend caído
            } catch (_: Exception) {
                false
            }

            if (ok) {
                sent++
            } else {
                break
            }
        }

        if (sent > 0) queue.removeFirst(sent)
        return sent
    }

    private suspend fun sendOne(req: PendingRequest): Boolean {
        return when (req.type) {

            PendingType.EVENT_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, EventCreateDto::class.java)
                val res = NetworkModule.api.createEvent(dto)
                res.isSuccessful
            }

            PendingType.MOVEMENT_IN -> {
                val dto = gson.fromJson(req.payloadJson, MovementOperationRequest::class.java)
                val res = NetworkModule.api.movementIn(dto)
                res.isSuccessful
            }

            PendingType.MOVEMENT_OUT -> {
                val dto = gson.fromJson(req.payloadJson, MovementOperationRequest::class.java)
                val res = NetworkModule.api.movementOut(dto)
                res.isSuccessful
            }

            PendingType.MOVEMENT_ADJUST -> {
                val dto = gson.fromJson(req.payloadJson, MovementAdjustOperationRequest::class.java)
                val res = NetworkModule.api.movementAdjust(dto)
                res.isSuccessful
            }

            PendingType.PRODUCT_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, ProductCreateDto::class.java)
                val res = NetworkModule.api.createProduct(dto)
                res.isSuccessful
            }

            PendingType.PRODUCT_UPDATE -> {
                val payload = gson.fromJson(req.payloadJson, ProductUpdatePayload::class.java)
                val res = NetworkModule.api.updateProduct(payload.productId, payload.body)
                res.isSuccessful
            }

            PendingType.PRODUCT_DELETE -> {
                val payload = gson.fromJson(req.payloadJson, ProductDeletePayload::class.java)
                val res = NetworkModule.api.deleteProduct(payload.productId)
                res.isSuccessful
            }

            PendingType.STOCK_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, StockCreateDto::class.java)
                val res = NetworkModule.api.createStock(dto)
                res.isSuccessful
            }

            PendingType.STOCK_UPDATE -> {
                val payload = gson.fromJson(req.payloadJson, StockUpdatePayload::class.java)
                val res = NetworkModule.api.updateStock(payload.stockId, payload.body)
                res.isSuccessful
            }
        }
    }

    // Payloads auxiliares para operaciones que necesitan ID + body
    data class ProductUpdatePayload(val productId: Int, val body: ProductUpdateDto)
    data class ProductDeletePayload(val productId: Int)
    data class StockUpdatePayload(val stockId: Int, val body: StockUpdateDto)
}
