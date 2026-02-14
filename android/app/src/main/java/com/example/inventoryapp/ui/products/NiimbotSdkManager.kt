package com.example.inventoryapp.ui.products

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import com.gengcon.www.jcprintersdk.JCPrintApi
import com.gengcon.www.jcprintersdk.callback.Callback
import com.gengcon.www.jcprintersdk.callback.PrintCallback
import java.util.HashMap
import java.util.concurrent.atomic.AtomicBoolean

object NiimbotSdkManager {
    private const val PRINT_MULTIPLE = 8f // 200dpi printers (B1/B21/B3S)
    private const val LABEL_WIDTH_MM = 50f
    private const val LABEL_HEIGHT_MM = 30f
    private const val PRINT_DENSITY = 3
    private const val LABEL_TYPE = 1
    private const val PRINT_MODE = 1 // thermal

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var api: JCPrintApi? = null

    private val callback = object : Callback {
        override fun onConnectSuccess(address: String?, type: Int) {}
        override fun onDisConnect() {}
        override fun onElectricityChange(powerLevel: Int) {}
        override fun onCoverStatus(coverStatus: Int) {}
        override fun onPaperStatus(paperStatus: Int) {}
        override fun onRfidReadStatus(rfidReadStatus: Int) {}
        override fun onRibbonRfidReadStatus(ribbonRfidReadStatus: Int) {}
        override fun onRibbonStatus(ribbonStatus: Int) {}
        override fun onFirmErrors() {}
    }

    private fun getApi(context: Context): JCPrintApi {
        val cached = api
        if (cached != null) return cached
        synchronized(this) {
            val second = api
            if (second != null) return second
            val created = JCPrintApi.getInstance(callback)
            created.initSdk(context.applicationContext as Application)
            api = created
            return created
        }
    }

    fun connectBluetooth(context: Context, address: String): Int {
        return getApi(context).connectBluetoothPrinter(address)
    }

    fun isConnected(context: Context): Boolean {
        return getApi(context).isConnection() == 0
    }

    fun close(context: Context) {
        getApi(context).close()
    }

    fun prepareBitmapFor50x30(source: Bitmap, marginMm: Float = 2f): Bitmap {
        val targetWidthPx = (LABEL_WIDTH_MM * PRINT_MULTIPLE).toInt().coerceAtLeast(1)
        val targetHeightPx = (LABEL_HEIGHT_MM * PRINT_MULTIPLE).toInt().coerceAtLeast(1)
        val marginPx = (marginMm * PRINT_MULTIPLE).toInt().coerceAtLeast(0)

        val contentWidth = (targetWidthPx - marginPx * 2).coerceAtLeast(1)
        val contentHeight = (targetHeightPx - marginPx * 2).coerceAtLeast(1)

        val ratio = minOf(
            contentWidth.toFloat() / source.width.toFloat(),
            contentHeight.toFloat() / source.height.toFloat()
        )
        val drawW = (source.width * ratio).toInt().coerceAtLeast(1)
        val drawH = (source.height * ratio).toInt().coerceAtLeast(1)
        val left = ((targetWidthPx - drawW) / 2f)
        val top = ((targetHeightPx - drawH) / 2f)

        val output = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            source,
            null,
            android.graphics.RectF(left, top, left + drawW, top + drawH),
            paint
        )
        return output
    }

    fun printBitmap(
        context: Context,
        bitmap: Bitmap,
        onProgress: (String) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val localApi = getApi(context)
        if (localApi.isConnection() != 0) {
            onError("La impresora no está conectada")
            return
        }

        val widthMm = (bitmap.width / PRINT_MULTIPLE).coerceAtLeast(1f)
        val heightMm = (bitmap.height / PRINT_MULTIPLE).coerceAtLeast(1f)
        val sent = AtomicBoolean(false)
        val done = AtomicBoolean(false)

        localApi.setTotalPrintQuantity(1)
        localApi.startPrintJob(PRINT_DENSITY, LABEL_TYPE, PRINT_MODE, object : PrintCallback {
            override fun onProgress(
                pageIndex: Int,
                quantityIndex: Int,
                hashMap: HashMap<String, Any>?
            ) {
                mainHandler.post { onProgress("Imprimiendo etiqueta") }
                if (pageIndex >= 1 && quantityIndex >= 1 && done.compareAndSet(false, true)) {
                    localApi.endPrintJob()
                    mainHandler.post(onSuccess)
                }
            }

            override fun onError(i: Int) {}

            override fun onError(errorCode: Int, printState: Int) {
                if (done.compareAndSet(false, true)) {
                    mainHandler.post { onError("Error de impresión (código $errorCode)") }
                }
            }

            override fun onCancelJob(success: Boolean) {
                if (done.compareAndSet(false, true)) {
                    mainHandler.post { onError(if (success) "Impresión cancelada" else "No se pudo cancelar") }
                }
            }

            override fun onBufferFree(pageIndex: Int, bufferSize: Int) {
                if (sent.compareAndSet(false, true)) {
                    localApi.commitImageData(
                        0,
                        bitmap,
                        widthMm,
                        heightMm,
                        1,
                        0,
                        0,
                        0,
                        0,
                        ""
                    )
                }
            }
        })
    }
}
