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
import android.view.View
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import android.view.inputmethod.InputMethodManager

class StockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockBinding
    private lateinit var snack: SendSnack

    private val gson = Gson()
    private var items: List<StockResponseDto> = emptyList()
    private var allItems: List<StockResponseDto> = emptyList()
    private lateinit var adapter: StockListAdapter
    private var productNameById: Map<Int, String> = emptyMap()
    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var isLoading = false
    private var filteredItems: List<StockResponseDto> = emptyList()
    private var filteredOffset = 0
    private var pendingFilterApply = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateStockAdd, R.drawable.add)
        GradientIconUtil.applyGradient(binding.ivCreateStockSearch, R.drawable.search)
        applyStockTitleGradient()
        
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        snack = SendSnack(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnCreate.setOnClickListener { createStock() }
        binding.btnRefresh.setOnClickListener { loadStocks() }
        binding.layoutCreateStockHeader.setOnClickListener { toggleCreateStockForm() }
        binding.layoutSearchStockHeader.setOnClickListener { toggleSearchForm() }
        binding.btnSearchStock.setOnClickListener {
            hideKeyboard()
            applySearchFilters()
        }
        binding.btnClearSearchStock.setOnClickListener {
            hideKeyboard()
            clearSearchFilters()
        }

        setupLocationDropdown()
        setupSearchDropdowns()
        binding.tilCreateLocation.post { applyStockDropdownIcon() }

        adapter = StockListAdapter { stock -> showEditDialog(stock) }
        binding.rvStocks.layoutManager = LinearLayoutManager(this)
        binding.rvStocks.adapter = adapter

        binding.btnPrevPage.setOnClickListener {
            if (hasActiveFilters()) {
                if (filteredOffset <= 0) return@setOnClickListener
                filteredOffset = (filteredOffset - pageSize).coerceAtLeast(0)
                applyFilteredPage()
                binding.rvStocks.scrollToPosition(0)
                return@setOnClickListener
            }
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadStocks()
            binding.rvStocks.scrollToPosition(0)
        }
        binding.btnNextPage.setOnClickListener {
            if (hasActiveFilters()) {
                val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
                if (shown >= filteredItems.size) return@setOnClickListener
                filteredOffset += pageSize
                applyFilteredPage()
                binding.rvStocks.scrollToPosition(0)
                return@setOnClickListener
            }
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
                val filtersActive = hasActiveFilters()
                val effectiveLimit = if (filtersActive) 100 else pageSize
                val effectiveOffset = if (filtersActive) 0 else currentOffset
                if (filtersActive) currentOffset = 0
                val res = NetworkModule.api.listStocks(limit = effectiveLimit, offset = effectiveOffset)
                if (res.isSuccessful && res.body() != null) {
                    val pending = buildPendingStocks()
                    val pageItems = res.body()!!.items
                    totalCount = res.body()!!.total
                    val ordered = (pending + pageItems).sortedBy { it.id }
                    productNameById = resolveProductNames(ordered)
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(pageItems.size, pending.size)
                } else {
                    val pending = buildPendingStocks()
                    val ordered = pending.sortedBy { it.id }
                    productNameById = resolveProductNames(ordered)
                    totalCount = pending.size
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, ordered.size)
                    if (res.code() == 403) {
                        UiNotifier.showBlocking(
                            this@StockActivity,
                            "Permisos insuficientes",
                            "No tienes permisos para ver stock.",
                            com.example.inventoryapp.R.drawable.ic_lock
                        )
                    } else {
                        snack.showError("? Error ${res.code()}")
                    }
                }
            } catch (e: Exception) {
                val pending = buildPendingStocks()
                val ordered = pending.sortedBy { it.id }
                productNameById = resolveProductNames(ordered)
                totalCount = pending.size
                setAllItemsAndApplyFilters(ordered)
                updatePageInfo(ordered.size, ordered.size)
                if (e is IOException) {
                    snack.showError("Sin conexi?n a Internet")
                } else {
                    snack.showError("? Error de red: ${e.message}")
                }
            } finally {
                isLoading = false
                if (pendingFilterApply) {
                    pendingFilterApply = false
                    applySearchFiltersInternal(allowReload = false)
                }
            }
        }
    }

    private fun updatePageInfo(pageSizeLoaded: Int, pendingCount: Int) {
        if (hasActiveFilters()) {
            val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
            binding.tvStockPageInfo.text = "Mostrando $shown / ${filteredItems.size}"
            val prevEnabled = filteredOffset > 0
            val nextEnabled = shown < filteredItems.size
            binding.btnPrevPage.isEnabled = prevEnabled
            binding.btnNextPage.isEnabled = nextEnabled
            applyPagerButtonStyle(binding.btnPrevPage, prevEnabled)
            applyPagerButtonStyle(binding.btnNextPage, nextEnabled)
            return
        }
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
        val productInput = binding.etProductId.text.toString().trim()
        val productId = productInput.toIntOrNull() ?: resolveProductIdByName(productInput)
        val location = normalizeLocationInput(binding.etLocation.text.toString().trim())
        val quantity = binding.etQuantity.text.toString().toIntOrNull()

        if (productId == null) { binding.etProductId.error = "Product ID o nombre válido"; return }
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
                    val values = items.sortedBy { it.id }
                        .map { "(${it.id}) ${it.code}" }
                        .distinct()
                    val allValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = ArrayAdapter(this@StockActivity, android.R.layout.simple_list_item_1, allValues)
                    binding.etLocation.setAdapter(adapter)
                    binding.etLocation.setOnClickListener { binding.etLocation.showDropDown() }
                    binding.etLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etLocation.showDropDown()
                    }
                    binding.etSearchLocation.setAdapter(adapter)
                    binding.etSearchLocation.setOnClickListener { binding.etSearchLocation.showDropDown() }
                    binding.etSearchLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etSearchLocation.showDropDown()
                    }
                }
            } catch (_: Exception) {
                // Silent fallback to manual input.
            }
        }
    }

    private fun setupSearchDropdowns() {
        // Placeholder for future search dropdowns if needed.
    }

    private fun applyStockDropdownIcon() {
        binding.tilCreateLocation.setEndIconTintList(null)
        binding.tilSearchLocation.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilCreateLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
        binding.tilSearchLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
    }

    private fun toggleCreateStockForm() {
        TransitionManager.beginDelayedTransition(binding.scrollStock, AutoTransition().setDuration(180))
        val isVisible = binding.layoutCreateStockContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutCreateStockContent.visibility = View.GONE
            binding.layoutSearchStockContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutCreateStockContent.visibility = View.VISIBLE
            binding.layoutSearchStockContent.visibility = View.GONE
            setToggleActive(binding.layoutCreateStockHeader)
        }
    }

    private fun toggleSearchForm() {
        TransitionManager.beginDelayedTransition(binding.scrollStock, AutoTransition().setDuration(180))
        val isVisible = binding.layoutSearchStockContent.visibility == View.VISIBLE
        if (isVisible) {
            hideSearchForm()
        } else {
            binding.layoutSearchStockContent.visibility = View.VISIBLE
            binding.layoutCreateStockContent.visibility = View.GONE
            setToggleActive(binding.layoutSearchStockHeader)
        }
    }

    private fun hideSearchForm() {
        binding.layoutSearchStockContent.visibility = View.GONE
        binding.layoutCreateStockContent.visibility = View.GONE
        setToggleActive(null)
    }

    private fun setToggleActive(active: View?) {
        if (active === binding.layoutCreateStockHeader) {
            binding.layoutCreateStockHeader.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.layoutSearchStockHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        } else if (active === binding.layoutSearchStockHeader) {
            binding.layoutCreateStockHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchStockHeader.setBackgroundResource(R.drawable.bg_toggle_active)
        } else {
            binding.layoutCreateStockHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchStockHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        }
    }

    private fun applyStockTitleGradient() {
        binding.tvStockTitle.post {
            val paint = binding.tvStockTitle.paint
            val width = paint.measureText(binding.tvStockTitle.text.toString())
            if (width <= 0f) return@post
            val c1 = ContextCompat.getColor(this, R.color.icon_grad_start)
            val c2 = ContextCompat.getColor(this, R.color.icon_grad_mid2)
            val c3 = ContextCompat.getColor(this, R.color.icon_grad_mid1)
            val c4 = ContextCompat.getColor(this, R.color.icon_grad_end)
            val shader = android.graphics.LinearGradient(
                0f,
                0f,
                width,
                0f,
                intArrayOf(c1, c2, c3, c4),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            binding.tvStockTitle.invalidate()
        }
    }

    private fun applySearchFilters() {
        applySearchFiltersInternal(allowReload = true)
    }

    private fun applySearchFiltersInternal(allowReload: Boolean) {
        val productRaw = binding.etSearchProduct.text.toString().trim()
        val locationRaw = normalizeLocationInput(binding.etSearchLocation.text.toString().trim())
        val qtyRaw = binding.etSearchQuantity.text.toString().trim()

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > allItems.size)) {
            pendingFilterApply = true
            currentOffset = 0
            loadStocks()
            return
        }

        var filtered = allItems
        if (productRaw.isNotBlank()) {
            val productId = productRaw.toIntOrNull()
            filtered = if (productId != null) {
                filtered.filter { it.productId == productId }
            } else {
                val needle = productRaw.lowercase()
                filtered.filter { (productNameById[it.productId] ?: "").lowercase().contains(needle) }
            }
        }
        if (locationRaw.isNotBlank()) {
            val needle = locationRaw.lowercase()
            filtered = filtered.filter { it.location.lowercase().contains(needle) }
        }
        if (qtyRaw.isNotBlank()) {
            val qty = qtyRaw.toIntOrNull()
            if (qty != null) {
                filtered = filtered.filter { it.quantity == qty }
            }
        }

        filteredItems = filtered
        filteredOffset = 0
        applyFilteredPage()
    }

    private fun clearSearchFilters() {
        binding.etSearchProduct.setText("")
        binding.etSearchLocation.setText("")
        binding.etSearchQuantity.setText("")
        filteredItems = emptyList()
        filteredOffset = 0
        items = allItems
        adapter.submit(allItems, productNameById)
        updatePageInfo(items.size, items.size)
    }

    private fun setAllItemsAndApplyFilters(ordered: List<StockResponseDto>) {
        allItems = ordered
        if (hasActiveFilters() || pendingFilterApply) {
            applySearchFiltersInternal(allowReload = false)
        } else {
            items = ordered
            adapter.submit(ordered, productNameById)
        }
    }

    private fun hasActiveFilters(): Boolean {
        return binding.etSearchProduct.text?.isNotBlank() == true ||
            binding.etSearchLocation.text?.isNotBlank() == true ||
            binding.etSearchQuantity.text?.isNotBlank() == true
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        items = page
        adapter.submit(page, productNameById)
        updatePageInfo(page.size, page.size)
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun resolveProductIdByName(input: String): Int? {
        val needle = input.trim().lowercase()
        if (needle.isBlank()) return null
        val match = productNameById.entries.firstOrNull { it.value.lowercase() == needle }
        return match?.key
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
