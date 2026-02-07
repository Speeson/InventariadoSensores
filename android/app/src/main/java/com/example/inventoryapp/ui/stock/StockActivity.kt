package com.example.inventoryapp.ui.stock
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.StockCreateDto
import com.example.inventoryapp.data.remote.model.StockResponseDto
import com.example.inventoryapp.data.remote.model.StockUpdateDto
import com.example.inventoryapp.databinding.ActivityStockBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException
import com.example.inventoryapp.ui.common.GradientIconUtil
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat

class StockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockBinding
    private lateinit var snack: SendSnack

    private val gson = Gson()
    private var items: List<StockResponseDto> = emptyList()
    private lateinit var adapter: StockListAdapter
    private var productNameById: Map<Int, String> = emptyMap()
    private var currentOffset = 0
    private val pageSize = 10
    private var totalCount = 0
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        snack = SendSnack(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnCreate.setOnClickListener { createStock() }

        setupLocationDropdown()

        adapter = StockListAdapter { stock -> showEditDialog(stock) }
        binding.rvStocks.layoutManager = LinearLayoutManager(this)
        binding.rvStocks.adapter = adapter

        binding.btnPrevPage.setOnClickListener {
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadStocks()
            binding.rvStocks.scrollToPosition(0)
        }
        binding.btnNextPage.setOnClickListener {
            val shown = (currentOffset + items.size).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadStocks()
            binding.rvStocks.scrollToPosition(0)
        }

        // Initial button styling
        applyPagerButtonStyle(binding.btnPrevPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextPage, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        currentOffset = 0
        loadStocks()
    }

    private fun loadStocks() {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listStocks(limit = pageSize, offset = currentOffset)
                if (res.isSuccessful && res.body() != null) {
                    val pending = buildPendingStocks()
                    val pageItems = res.body()!!.items
                    totalCount = res.body()!!.total
                    items = (pending + pageItems).sortedBy { it.id }
                    productNameById = resolveProductNames(items)
                    adapter.submit(items, productNameById)
                    updatePageInfo(pageItems.size, pending.size)
                } else {
                    val pending = buildPendingStocks()
                    val ordered = pending.sortedBy { it.id }
                    productNameById = resolveProductNames(ordered)
                    adapter.submit(ordered, productNameById)
                    totalCount = pending.size
                    updatePageInfo(ordered.size, ordered.size)
                    if (res.code() == 403) {
                        UiNotifier.showBlocking(
                            this@StockActivity,
                            "Permisos insuficientes",
                            "No tienes permisos para ver stock.",
                            com.example.inventoryapp.R.drawable.ic_lock
                        )
                    } else {
                        snack.showError("❌ Error ${res.code()}")
                    }
                }
            } catch (e: Exception) {
                val pending = buildPendingStocks()
                val ordered = pending.sortedBy { it.id }
                productNameById = resolveProductNames(ordered)
                adapter.submit(ordered, productNameById)
                totalCount = pending.size
                updatePageInfo(ordered.size, ordered.size)
                if (e is IOException) {
                    snack.showError("Sin conexión a Internet")
                } else {
                    snack.showError("❌ Error de red: ${e.message}")
                }
            } finally {
                isLoading = false
            }
        }
    }

    private fun updatePageInfo(pageSizeLoaded: Int, pendingCount: Int) {
        val shownOnline = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        val label = if (totalCount > 0) {
            "Mostrando $shownOnline / $totalCount"
        } else {
            "Mostrando $pendingCount / $pendingCount"
        }
        binding.tvStockPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = shownOnline < totalCount
        binding.btnPrevPage.isEnabled = prevEnabled
        binding.btnNextPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextPage, nextEnabled)
    }

    private fun applyPagerButtonStyle(button: Button, enabled: Boolean) {
        button.backgroundTintList = null
        if (!enabled) {
            val colors = intArrayOf(
                ContextCompat.getColor(this, android.R.color.darker_gray),
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
            val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
                cornerRadius = resources.displayMetrics.density * 18f
                setStroke((resources.displayMetrics.density * 1f).toInt(), 0xFFB0B0B0.toInt())
            }
            button.background = drawable
            button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            return
        }
        val colors = intArrayOf(
            ContextCompat.getColor(this, R.color.icon_grad_start),
            ContextCompat.getColor(this, R.color.icon_grad_mid2),
            ContextCompat.getColor(this, R.color.icon_grad_mid1),
            ContextCompat.getColor(this, R.color.icon_grad_end)
        )
        val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = resources.displayMetrics.density * 18f
            setStroke((resources.displayMetrics.density * 1f).toInt(), 0x33000000)
        }
        button.background = drawable
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }

    private suspend fun resolveProductNames(stocks: List<StockResponseDto>): Map<Int, String> {
        val ids = stocks.map { it.productId }.distinct()
        if (ids.isEmpty()) return emptyMap()

        val resolved = mutableMapOf<Int, String>()

        val productRes = runCatching { NetworkModule.api.listProducts(limit = 200, offset = 0) }.getOrNull()
        if (productRes?.isSuccessful == true && productRes.body() != null) {
            resolved.putAll(productRes.body()!!.items.associate { it.id to it.name })
        }

        for (id in ids) {
            if (resolved.containsKey(id)) continue
            val singleRes = runCatching { NetworkModule.api.getProduct(id) }.getOrNull()
            if (singleRes?.isSuccessful == true && singleRes.body() != null) {
                resolved[id] = singleRes.body()!!.name
            }
        }

        return resolved
    }

    private fun createStock() {
        val productId = binding.etProductId.text.toString().toIntOrNull()
        val location = normalizeLocationInput(binding.etLocation.text.toString().trim())
        val quantity = binding.etQuantity.text.toString().toIntOrNull()

        if (productId == null) { binding.etProductId.error = "Product ID requerido"; return }
        if (location.isBlank()) { binding.etLocation.error = "Location requerida"; return }
        if (quantity == null || quantity < 0) { binding.etQuantity.error = "Quantity >= 0"; return }

        val dto = StockCreateDto(productId = productId, location = location, quantity = quantity)

        binding.btnCreate.isEnabled = false
        snack.showSending("Enviando stock...")

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.createStock(dto)
                if (res.isSuccessful) {
                    snack.showSuccess("✅ Stock creado")
                    binding.etQuantity.setText("")
                    loadStocks()
                } else {
                    if (res.code() == 403) {
                        UiNotifier.showBlocking(
                            this@StockActivity,
                            "Permisos insuficientes",
                            "No tienes permisos para crear stock.",
                            com.example.inventoryapp.R.drawable.ic_lock
                        )
                    } else {
                        if (res.code() == 403) {
                        UiNotifier.showBlocking(
                            this@StockActivity,
                            "Permisos insuficientes",
                            "No tienes permisos para actualizar stock.",
                            com.example.inventoryapp.R.drawable.ic_lock
                        )
                    } else {
                        snack.showError("❌ Error ${res.code()}: ${res.errorBody()?.string()}")
                    }
                    }
                }

            } catch (e: IOException) {
                OfflineQueue(this@StockActivity).enqueue(PendingType.STOCK_CREATE, gson.toJson(dto))
                snack.showQueuedOffline("Sin conexión. Stock guardado offline")
                loadStocks()

            } catch (e: Exception) {
                snack.showError("❌ Error: ${e.message}")
            } finally {
                binding.btnCreate.isEnabled = true
            }
        }
    }

    private fun showEditDialog(stock: StockResponseDto) {
        val inputQty = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(stock.quantity.toString())
            hint = "Nueva quantity"
        }

        AlertDialog.Builder(this)
            .setTitle("Editar stock #${stock.id}")
            .setMessage("prod=${stock.productId} | loc=${stock.location}\nCambia quantity:")
            .setView(inputQty)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val newQty = inputQty.text.toString().toIntOrNull()
                if (newQty == null || newQty < 0) {
                    snack.showError("❌ Quantity inválida")
                    return@setPositiveButton
                }
                updateStock(stock.id, StockUpdateDto(quantity = newQty))
            }
            .show()
    }

    private fun updateStock(stockId: Int, body: StockUpdateDto) {
        snack.showSending("Enviando actualización de stock...")

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateStock(stockId, body)
                if (res.isSuccessful) {
                    snack.showSuccess("✅ Stock actualizado")
                    loadStocks()
                } else {                    if (res.code() == 403) {
                        UiNotifier.showBlocking(
                            this@StockActivity,
                            "Permisos insuficientes",
                            "No tienes permisos para actualizar stock.",
                            com.example.inventoryapp.R.drawable.ic_lock
                        )
                    } else {
                        snack.showError("? Error ${res.code()}: ${res.errorBody()?.string()}")
                    }
                }

            } catch (e: IOException) {
                val payload = OfflineSyncer.StockUpdatePayload(stockId, body)
                OfflineQueue(this@StockActivity).enqueue(PendingType.STOCK_UPDATE, gson.toJson(payload))
                snack.showQueuedOffline("Sin conexión. Update guardado offline")

            } catch (e: Exception) {
                snack.showError("❌ Error red: ${e.message}")
            }
        }
    }

    private fun buildPendingStocks(): List<StockResponseDto> {
        val pending = OfflineQueue(this).getAll().filter { it.type == PendingType.STOCK_CREATE }
        return pending.mapIndexed { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, StockCreateDto::class.java) }.getOrNull()
            if (dto == null) {
                StockResponseDto(
                    productId = 0,
                    location = "offline",
                    quantity = 0,
                    id = -1 - index,
                    createdAt = "offline",
                    updatedAt = "offline"
                )
            } else {
                StockResponseDto(
                    productId = dto.productId,
                    location = dto.location,
                    quantity = dto.quantity,
                    id = -1 - index,
                    createdAt = "offline",
                    updatedAt = "offline"
                )
            }
        }
    }

    private fun setupLocationDropdown() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    val values = items.map { "(${it.id}) ${it.code}" }.distinct().sorted()
                    val allValues = if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = ArrayAdapter(this@StockActivity, android.R.layout.simple_list_item_1, allValues)
                    binding.etLocation.setAdapter(adapter)
                    binding.etLocation.setOnClickListener { binding.etLocation.showDropDown() }
                    binding.etLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etLocation.showDropDown()
                    }
                }
            } catch (_: Exception) {
                // Silent fallback to manual input.
            }
        }
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }
}
