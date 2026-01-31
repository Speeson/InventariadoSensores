package com.example.inventoryapp.data.local

import android.content.Context
import android.util.Log
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.*
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
            PendingType.PRODUCT_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, ProductCreateDto::class.java)
                NetworkModule.api.createProduct(dto)
            }
            PendingType.PRODUCT_UPDATE -> {
                val payload = gson.fromJson(req.payloadJson, ProductUpdatePayload::class.java)
                NetworkModule.api.updateProduct(payload.productId, payload.body)
            }
            PendingType.PRODUCT_DELETE -> {
                val payload = gson.fromJson(req.payloadJson, ProductDeletePayload::class.java)
                NetworkModule.api.deleteProduct(payload.productId)
            }
            PendingType.STOCK_CREATE -> {
                val dto = gson.fromJson(req.payloadJson, StockCreateDto::class.java)
                NetworkModule.api.createStock(dto)
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
    data class StockUpdatePayload(val stockId: Int, val body: StockUpdateDto)
}
