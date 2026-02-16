package com.example.inventoryapp.data.local

import android.content.Context
import android.util.Log
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.*
import com.example.inventoryapp.domain.model.MovementType
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException

object OfflineSyncer {

    private const val TAG = "OfflineSyncer"
    private val gson = Gson()

    data class FlushReport(
        val sent: Int,
        val movedToFailed: Int,
        val stoppedReason: String? = null,
        val newlyFailed: List<FailedRequest> = emptyList()
    )

    suspend fun flush(context: Context): FlushReport {
        val queue = OfflineQueue(context)
        val token = SessionManager(context).getToken()
        val items = queue.getAll().toMutableList()

        if (items.isEmpty()) return FlushReport(sent = 0, movedToFailed = 0)

        if (token.isNullOrBlank()) {
            return FlushReport(sent = 0, movedToFailed = 0, stoppedReason = "NO_TOKEN")
        }

        // Health check simple (si no hay backend, no hagas nada)
        val healthOk = try {
            NetworkModule.api.health().isSuccessful
        } catch (_: Exception) { false }

        if (!healthOk) {
            return FlushReport(sent = 0, movedToFailed = 0, stoppedReason = "NO_BACKEND")
        }

        var sent = 0
        var movedToFailed = 0
        val newlyFailed = mutableListOf<FailedRequest>()

        var i = 0
        while (i < items.size) {
            val req = items[i]

            val result = try {
                sendOneDetailed(req)
            } catch (e: IOException) {
                return FlushReport(
                    sent = sent,
                    movedToFailed = movedToFailed,
                    stoppedReason = "IOEXCEPTION:${e.message}",
                    newlyFailed = newlyFailed
                )
            } catch (e: Exception) {
                return FlushReport(
                    sent = sent,
                    movedToFailed = movedToFailed,
                    stoppedReason = "EXCEPTION:${e.message}",
                    newlyFailed = newlyFailed
                )
            }

            when (result) {
                is SendResult.Success -> {
                    sent++
                    items.removeAt(i) // quitar de pendientes y seguir (NO incrementes i)
                }

                is SendResult.PermanentFailure -> {
                    val failed = FailedRequest(
                        original = req,
                        httpCode = result.httpCode,
                        errorMessage = result.error
                    )
                    queue.addFailed(failed)
                    newlyFailed.add(failed)

                    movedToFailed++
                    items.removeAt(i) // quitar de pendientes y seguir (NO incrementes i)
                }

                is SendResult.TransientFailure -> {
                    // Deja el resto en pending, para reintentar luego
                    Log.w(TAG, "Transient failure en ${req.type}: code=${result.httpCode} err=${result.error}")
                    break
                }
            }
        }

        // Guardar “pending” restante
        queue.saveAll(items)

        val stoppedReason =
            if (items.isEmpty()) null else "STOPPED_WITH_${items.size}_PENDING"

        return FlushReport(
            sent = sent,
            movedToFailed = movedToFailed,
            stoppedReason = stoppedReason,
            newlyFailed = newlyFailed
        )
    }

    // --- Resultado detallado por request ---
    private sealed class SendResult {
        object Success : SendResult()
        data class PermanentFailure(val httpCode: Int?, val error: String) : SendResult()
        data class TransientFailure(val httpCode: Int?, val error: String) : SendResult()
    }

    private suspend fun sendOneDetailed(req: PendingRequest): SendResult {
        val res: Response<*> = when (req.type) {
            PendingType.EVENT_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, EventCreateDto::class.java)
                NetworkModule.api.createEvent(dto)
            }
            PendingType.SCAN_EVENT -> {
                val payload = gson.fromJson(req.payloadJson, ScanEventPayload::class.java)
                val prodRes = NetworkModule.api.listProducts(barcode = payload.barcode, limit = 1, offset = 0)
                if (!prodRes.isSuccessful || prodRes.body() == null) {
                    return SendResult.TransientFailure(prodRes.code(), prodRes.safeError())
                }

                val product = prodRes.body()!!.items.firstOrNull()
                    ?: return SendResult.PermanentFailure(404, "Producto no existe: ${payload.barcode}")

                val eventType = if (payload.type == MovementType.IN) EventTypeDto.SENSOR_IN else EventTypeDto.SENSOR_OUT
                val eventRes = NetworkModule.api.createEvent(
                    EventCreateDto(
                        eventType = eventType,
                        productId = product.id,
                        delta = payload.quantity,
                        source = payload.source,
                        location = payload.location,
                        idempotencyKey = payload.idempotencyKey
                    )
                )
                eventRes
            }
            PendingType.MOVEMENT_IN -> {
                val dto = gson.fromJson(req.payloadJson, MovementOperationRequest::class.java)
                NetworkModule.api.movementIn(dto)
            }
            PendingType.MOVEMENT_OUT -> {
                val dto = gson.fromJson(req.payloadJson, MovementOperationRequest::class.java)
                NetworkModule.api.movementOut(dto)
            }
            PendingType.MOVEMENT_ADJUST -> {
                val dto = gson.fromJson(req.payloadJson, MovementAdjustOperationRequest::class.java)
                NetworkModule.api.movementAdjust(dto)
            }
            PendingType.MOVEMENT_TRANSFER -> {
                val dto = gson.fromJson(req.payloadJson, MovementTransferOperationRequest::class.java)
                NetworkModule.api.movementTransfer(dto)
            }
            PendingType.PRODUCT_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, ProductCreateDto::class.java)
                // Evitar 409 si el producto ya fue creado en un intento anterior
                val existing = try {
                    val bySku = if (!dto.sku.isNullOrBlank()) {
                        NetworkModule.api.listProducts(sku = dto.sku, limit = 1, offset = 0)
                    } else null
                    val byBarcode = if (!dto.barcode.isNullOrBlank()) {
                        NetworkModule.api.listProducts(barcode = dto.barcode, limit = 1, offset = 0)
                    } else null
                    val skuHit = bySku?.isSuccessful == true && (bySku.body()?.items?.isNotEmpty() == true)
                    val barHit = byBarcode?.isSuccessful == true && (byBarcode.body()?.items?.isNotEmpty() == true)
                    skuHit || barHit
                } catch (_: Exception) {
                    false
                }
                if (existing) {
                    return SendResult.Success
                }
                val created = NetworkModule.api.createProduct(dto)
                if (created.code() == 409) {
                    val existsNow = try {
                        val checkSku = if (!dto.sku.isNullOrBlank()) {
                            NetworkModule.api.listProducts(sku = dto.sku, limit = 1, offset = 0)
                        } else null
                        val checkBarcode = if (!dto.barcode.isNullOrBlank()) {
                            NetworkModule.api.listProducts(barcode = dto.barcode, limit = 1, offset = 0)
                        } else null
                        val skuHit = checkSku?.isSuccessful == true && (checkSku.body()?.items?.isNotEmpty() == true)
                        val barHit = checkBarcode?.isSuccessful == true && (checkBarcode.body()?.items?.isNotEmpty() == true)
                        skuHit || barHit
                    } catch (_: Exception) {
                        false
                    }
                    return if (existsNow) {
                        SendResult.Success
                    } else {
                        SendResult.TransientFailure(409, "409 pero no visible en listado")
                    }
                }
                created
            }
            PendingType.PRODUCT_UPDATE -> {
                val payload = gson.fromJson(req.payloadJson, ProductUpdatePayload::class.java)
                NetworkModule.api.updateProduct(payload.productId, payload.body)
            }
            PendingType.PRODUCT_DELETE -> {
                val payload = gson.fromJson(req.payloadJson, ProductDeletePayload::class.java)
                NetworkModule.api.deleteProduct(payload.productId)
            }
            PendingType.CATEGORY_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, CategoryCreateDto::class.java)
                NetworkModule.api.createCategory(dto)
            }
            PendingType.CATEGORY_DELETE -> {
                val payload = gson.fromJson(req.payloadJson, CategoryDeletePayload::class.java)
                NetworkModule.api.deleteCategory(payload.categoryId)
            }
            PendingType.THRESHOLD_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, ThresholdCreateDto::class.java)
                NetworkModule.api.createThreshold(dto)
            }
            PendingType.THRESHOLD_DELETE -> {
                val payload = gson.fromJson(req.payloadJson, ThresholdDeletePayload::class.java)
                NetworkModule.api.deleteThreshold(payload.thresholdId)
            }
            PendingType.STOCK_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, StockCreateDto::class.java)
                val existing = try {
                    val res = NetworkModule.api.listStocks(
                        productId = dto.productId,
                        location = dto.location,
                        limit = 1,
                        offset = 0
                    )
                    res.isSuccessful && (res.body()?.items?.isNotEmpty() == true)
                } catch (_: Exception) {
                    false
                }
                if (existing) {
                    return SendResult.Success
                }
                val created = NetworkModule.api.createStock(dto)
                if (created.code() == 400) {
                    // "Ya existe stock para esta ubicación" -> considerar éxito
                    return SendResult.Success
                }
                created
            }
            PendingType.STOCK_UPDATE -> {
                val payload = gson.fromJson(req.payloadJson, StockUpdatePayload::class.java)
                NetworkModule.api.updateStock(payload.stockId, payload.body)
            }
        }

        if (res.isSuccessful) return SendResult.Success

        val code = res.code()
        val err = res.safeError()

        // Clasificación: ajusta si quieres
        return when (code) {
            400, 404, 409, 422 -> SendResult.PermanentFailure(code, err) // “no va a funcionar reintentando”
            401, 403 -> SendResult.TransientFailure(code, err) // requiere re-login/permisos -> no descartes
            408, 429 -> SendResult.TransientFailure(code, err) // timeout/rate limit
            in 500..599 -> SendResult.TransientFailure(code, err) // backend roto temporalmente
            else -> SendResult.TransientFailure(code, err)
        }
    }

    private fun Response<*>.safeError(): String {
        return try {
            errorBody()?.string()?.take(2000) ?: "(sin errorBody)"
        } catch (_: Exception) {
            "(error leyendo errorBody)"
        }
    }

    // Payloads auxiliares
    data class ProductUpdatePayload(val productId: Int, val body: ProductUpdateDto)
    data class ProductDeletePayload(val productId: Int)
    data class CategoryDeletePayload(val categoryId: Int)
    data class ThresholdDeletePayload(val thresholdId: Int)
    data class StockUpdatePayload(val stockId: Int, val body: StockUpdateDto)
    data class ScanEventPayload(
        val barcode: String,
        val type: MovementType,
        val quantity: Int,
        val location: String,
        val source: String = "SCAN",
        val idempotencyKey: String = java.util.UUID.randomUUID().toString()
    )
}
