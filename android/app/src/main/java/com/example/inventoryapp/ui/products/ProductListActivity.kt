package com.example.inventoryapp.ui.products

import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.CategoryResponseDto
import com.example.inventoryapp.data.remote.model.ProductCreateDto
import com.example.inventoryapp.data.remote.model.ProductResponseDto
import com.example.inventoryapp.databinding.ActivityProductListBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException
import android.graphics.drawable.GradientDrawable

class ProductListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductListBinding
    private lateinit var session: SessionManager
    private lateinit var offlineQueue: OfflineQueue
    private val gson = Gson()

    private val products = mutableListOf<ProductResponseDto>()
    private var allItems: List<ProductResponseDto> = emptyList()
    private var filteredItems: List<ProductResponseDto> = emptyList()
    private var filteredOffset = 0
    private var pendingFilterApply = false

    private lateinit var adapter: ProductListAdapter
    private var isLoading = false
    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var sortAsc = true

    private var categoryCache: Map<Int, String> = emptyMap()
    private var categoryIdByName: Map<String, Int> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateProductAdd, R.drawable.add)
        GradientIconUtil.applyGradient(binding.ivCreateProductSearch, R.drawable.search)
        applyProductsTitleGradient()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        session = SessionManager(this)
        offlineQueue = OfflineQueue(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = ProductListAdapter(
            onClick = { p ->
                val i = Intent(this, ProductDetailActivity::class.java)
                i.putExtra("product_id", p.id)
                startActivity(i)
            },
            onLabelClick = { p ->
                val i = Intent(this, LabelPreviewActivity::class.java)
                i.putExtra("product_id", p.id)
                i.putExtra("product_sku", p.sku)
                i.putExtra("product_barcode", p.barcode ?: "")
                startActivity(i)
            }
        )
        binding.rvProducts.layoutManager = LinearLayoutManager(this)
        binding.rvProducts.adapter = adapter

        binding.layoutCreateProductHeader.setOnClickListener { toggleCreateProductForm() }
        binding.layoutSearchProductHeader.setOnClickListener { toggleSearchForm() }

        binding.btnCreateProduct.setOnClickListener { createProduct() }
        binding.btnSearchProducts.setOnClickListener { hideKeyboard(); applySearchFilters() }
        binding.btnClearSearchProducts.setOnClickListener { hideKeyboard(); clearSearchFilters() }
        binding.btnRefresh.setOnClickListener { loadProducts(withSnack = true) }

        binding.btnPrevPage.setOnClickListener {
            if (hasActiveFilters()) {
                if (filteredOffset <= 0) return@setOnClickListener
                filteredOffset = (filteredOffset - pageSize).coerceAtLeast(0)
                applyFilteredPage()
                binding.rvProducts.scrollToPosition(0)
                return@setOnClickListener
            }
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadProducts(withSnack = false)
            binding.rvProducts.scrollToPosition(0)
        }
        binding.btnNextPage.setOnClickListener {
            if (hasActiveFilters()) {
                val shown = (filteredOffset + products.size).coerceAtMost(filteredItems.size)
                if (shown >= filteredItems.size) return@setOnClickListener
                filteredOffset += pageSize
                applyFilteredPage()
                binding.rvProducts.scrollToPosition(0)
                return@setOnClickListener
            }
            val nextOffset = currentOffset + pageSize
            if (nextOffset >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadProducts(withSnack = false)
            binding.rvProducts.scrollToPosition(0)
        }
        binding.btnSortUp.setOnClickListener {
            sortAsc = true
            resetAndLoad()
        }
        binding.btnSortDown.setOnClickListener {
            sortAsc = false
            resetAndLoad()
        }

        applyPagerButtonStyle(binding.btnPrevPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextPage, enabled = false)

        setupCategoryDropdowns()
        binding.tilCreateCategory.post { applyCategoryDropdownIcon() }
    }

    override fun onResume() {
        super.onResume()
        resetAndLoad()
    }

    private fun resetAndLoad() {
        currentOffset = 0
        isLoading = false
        products.clear()
        allItems = emptyList()
        filteredItems = emptyList()
        filteredOffset = 0
        adapter.submit(emptyList())
        loadProducts(withSnack = false)
    }

    private fun loadProducts(withSnack: Boolean) {
        if (isLoading) return
        isLoading = true
        if (withSnack) UiNotifier.show(this, "Cargando productos...")

        lifecycleScope.launch {
            try {
                val filtersActive = hasActiveFilters()
                val effectiveLimit = if (filtersActive) 100 else pageSize
                val effectiveOffset = if (filtersActive) 0 else currentOffset
                if (filtersActive) currentOffset = 0

                val searchNameOrId = binding.etSearchProduct.text.toString().trim()
                val nameParam = if (searchNameOrId.isNotBlank() && searchNameOrId.toIntOrNull() == null) searchNameOrId else null
                val skuParam = binding.etSearchSku.text.toString().trim().ifBlank { null }
                val barcodeParam = binding.etSearchBarcode.text.toString().trim().ifBlank { null }
                val categoryParam = resolveCategoryId(binding.etSearchCategory.text.toString().trim())

                val res = NetworkModule.api.listProducts(
                    sku = skuParam,
                    name = nameParam,
                    barcode = barcodeParam,
                    categoryId = categoryParam,
                    orderBy = "id",
                    orderDir = if (sortAsc) "asc" else "desc",
                    limit = effectiveLimit,
                    offset = effectiveOffset
                )

                if (res.code() == 401) {
                    UiNotifier.show(this@ProductListActivity, ApiErrorFormatter.format(401))
                    loadOfflineOnly()
                    session.clearToken()
                    goToLogin()
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    val pageItems = res.body()!!.items
                    if (pageItems.isEmpty() && currentOffset == 0) {
                        UiNotifier.show(this@ProductListActivity, "Sin resultados")
                    }
                    val categoryMap = categoryCache.ifEmpty { fetchCategoryMap().also { categoryCache = it } }
                    val rows = pageItems.map { ProductRowUi(it, categoryMap[it.categoryId]) }
                    products.clear()
                    products.addAll(pageItems)
                    totalCount = res.body()!!.total

                    allItems = pageItems
                    setAllItemsAndApplyFilters(pageItems)

                    updatePageInfo(pageItems.size)

                    if (!filtersActive) {
                        adapter.submit(rows)
                    } else {
                        // rows handled in filtered page
                    }
                } else {
                    val err = res.errorBody()?.string()
                    UiNotifier.show(this@ProductListActivity, ApiErrorFormatter.format(res.code(), err))
                    loadOfflineOnly()
                }
            } catch (e: Exception) {
                UiNotifier.show(this@ProductListActivity, "Error de red: ${e.message}")
                loadOfflineOnly()
            } finally {
                isLoading = false
                if (pendingFilterApply) {
                    pendingFilterApply = false
                    applySearchFiltersInternal(allowReload = false)
                }
            }
        }
    }

    private fun createProduct() {
        val sku = binding.etSku.text.toString().trim()
        val name = binding.etName.text.toString().trim()
        val rawBarcode = binding.etBarcode.text.toString().trim()
        val categoryId = resolveCategoryId(binding.etCategory.text.toString().trim())

        if (sku.isBlank()) { binding.etSku.error = "SKU requerido"; return }
        if (name.isBlank()) { binding.etName.error = "Nombre requerido"; return }
        if (rawBarcode.isBlank()) { binding.etBarcode.error = "Barcode requerido"; return }
        if (!rawBarcode.matches(Regex("^\\d{13}$"))) {
            binding.etBarcode.error = "Barcode debe tener 13 dígitos"
            return
        }
        if (categoryId == null) { binding.etCategory.error = "Categoría requerida"; return }

        binding.btnCreateProduct.isEnabled = false
        UiNotifier.show(this, "Enviando producto...")

        lifecycleScope.launch {
            try {
                val dto = ProductCreateDto(sku = sku, name = name, barcode = rawBarcode, categoryId = categoryId, active = true)
                val res = NetworkModule.api.createProduct(dto)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }

                if (res.isSuccessful && res.body() != null) {
                    UiNotifier.show(this@ProductListActivity, "Producto creado")
                    binding.etSku.setText("")
                    binding.etName.setText("")
                    binding.etBarcode.setText("")
                    binding.etCategory.setText("")
                    resetAndLoad()
                } else if (res.code() == 403) {
                    UiNotifier.showBlocking(
                        this@ProductListActivity,
                        "Permisos insuficientes",
                        "No tienes permisos para crear productos.",
                        com.example.inventoryapp.R.drawable.ic_lock
                    )
                } else {
                    UiNotifier.show(this@ProductListActivity, "Error ${res.code()}: ${res.errorBody()?.string()}")
                }
            } catch (e: IOException) {
                val dto = ProductCreateDto(sku = sku, name = name, barcode = rawBarcode, categoryId = categoryId!!, active = true)
                OfflineQueue(this@ProductListActivity).enqueue(PendingType.PRODUCT_CREATE, gson.toJson(dto))
                UiNotifier.show(this@ProductListActivity, "Sin red. Producto guardado offline")
                resetAndLoad()
            } catch (e: Exception) {
                UiNotifier.show(this@ProductListActivity, "Error: ${e.message}")
            } finally {
                binding.btnCreateProduct.isEnabled = true
            }
        }
    }

    private fun applySearchFilters() {
        applySearchFiltersInternal(allowReload = true)
    }

    private fun applySearchFiltersInternal(allowReload: Boolean) {
        val productRaw = binding.etSearchProduct.text.toString().trim()
        val skuRaw = binding.etSearchSku.text.toString().trim()
        val barcodeRaw = binding.etSearchBarcode.text.toString().trim()
        val categoryRaw = binding.etSearchCategory.text.toString().trim()

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > allItems.size)) {
            pendingFilterApply = true
            currentOffset = 0
            loadProducts(withSnack = false)
            return
        }

        var filtered = allItems
        if (productRaw.isNotBlank()) {
            val productId = productRaw.toIntOrNull()
            filtered = if (productId != null) {
                filtered.filter { it.id == productId }
            } else {
                val needle = productRaw.lowercase()
                filtered.filter { it.name.lowercase().contains(needle) }
            }
        }
        if (skuRaw.isNotBlank()) {
            val needle = skuRaw.lowercase()
            filtered = filtered.filter { it.sku.lowercase().contains(needle) }
        }
        if (barcodeRaw.isNotBlank()) {
            val needle = barcodeRaw.lowercase()
            filtered = filtered.filter { (it.barcode ?: "").lowercase().contains(needle) }
        }
        if (categoryRaw.isNotBlank()) {
            val categoryId = resolveCategoryId(categoryRaw)
            if (categoryId != null) {
                filtered = filtered.filter { it.categoryId == categoryId }
            }
        }

        filteredItems = filtered
        filteredOffset = 0
        applyFilteredPage()
    }

    private fun clearSearchFilters() {
        binding.etSearchProduct.setText("")
        binding.etSearchSku.setText("")
        binding.etSearchBarcode.setText("")
        binding.etSearchCategory.setText("")
        filteredItems = emptyList()
        filteredOffset = 0
        adapter.submit(products.map { ProductRowUi(it, categoryCache[it.categoryId]) })
        updatePageInfo(products.size)
    }

    private fun setAllItemsAndApplyFilters(ordered: List<ProductResponseDto>) {
        allItems = ordered
        if (hasActiveFilters() || pendingFilterApply) {
            applySearchFiltersInternal(allowReload = false)
        } else {
            val rows = ordered.map { ProductRowUi(it, categoryCache[it.categoryId]) }
            adapter.submit(rows)
        }
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        val rows = page.map { ProductRowUi(it, categoryCache[it.categoryId]) }
        adapter.submit(rows)
        updatePageInfo(page.size, total = filteredItems.size, filtered = true)
    }

    private fun hasActiveFilters(): Boolean {
        return binding.etSearchProduct.text?.isNotBlank() == true ||
            binding.etSearchSku.text?.isNotBlank() == true ||
            binding.etSearchBarcode.text?.isNotBlank() == true ||
            binding.etSearchCategory.text?.isNotBlank() == true
    }

    private fun updatePageInfo(pageSizeLoaded: Int, total: Int = totalCount, filtered: Boolean = false) {
        val shown = if (filtered) {
            (filteredOffset + pageSizeLoaded).coerceAtMost(total)
        } else {
            (currentOffset + pageSizeLoaded).coerceAtMost(total)
        }
        val label = if (total > 0) {
            "Mostrando $shown / $total"
        } else {
            "Mostrando 0 / 0"
        }
        binding.tvProductPageInfo.text = label
        val prevEnabled = if (filtered) filteredOffset > 0 else currentOffset > 0
        val nextEnabled = if (filtered) shown < total else (currentOffset + pageSize) < total
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

    private suspend fun fetchCategoryMap(): Map<Int, String> {
        return try {
            val res = NetworkModule.api.listCategories(limit = 100, offset = 0)
            if (res.isSuccessful && res.body() != null) {
                res.body()!!.items.associateBy({ it.id }, { it.name })
            } else emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun setupCategoryDropdowns() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listCategories(limit = 100, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items.sortedBy { it.id }
                    val values = items.map { "(${it.id}) ${it.name}" }
                    val withBlank = listOf("") + values
                    val adapter = ArrayAdapter(this@ProductListActivity, android.R.layout.simple_list_item_1, withBlank)
                    binding.etCategory.setAdapter(adapter)
                    binding.etCategory.setOnClickListener { binding.etCategory.showDropDown() }
                    binding.etCategory.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etCategory.showDropDown()
                    }
                    binding.etSearchCategory.setAdapter(adapter)
                    binding.etSearchCategory.setOnClickListener { binding.etSearchCategory.showDropDown() }
                    binding.etSearchCategory.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etSearchCategory.showDropDown()
                    }

                    categoryCache = items.associateBy({ it.id }, { it.name })
                    categoryIdByName = items.associateBy({ it.name.lowercase() }, { it.id })
                }
            } catch (_: Exception) {
                // Silent fallback
            }
        }
    }

    private fun resolveCategoryId(input: String): Int? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter("(").substringBefore(")").toIntOrNull()
        }
        trimmed.toIntOrNull()?.let { return it }
        return categoryIdByName[trimmed.lowercase()]
    }

    private fun applyCategoryDropdownIcon() {
        binding.tilCreateCategory.setEndIconTintList(null)
        binding.tilSearchCategory.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilCreateCategory.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
        binding.tilSearchCategory.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
    }

    private fun toggleCreateProductForm() {
        TransitionManager.beginDelayedTransition(binding.scrollProducts, AutoTransition().setDuration(180))
        val isVisible = binding.layoutCreateProductContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutCreateProductContent.visibility = View.GONE
            binding.layoutSearchProductContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutCreateProductContent.visibility = View.VISIBLE
            binding.layoutSearchProductContent.visibility = View.GONE
            setToggleActive(binding.layoutCreateProductHeader)
        }
    }

    private fun toggleSearchForm() {
        TransitionManager.beginDelayedTransition(binding.scrollProducts, AutoTransition().setDuration(180))
        val isVisible = binding.layoutSearchProductContent.visibility == View.VISIBLE
        if (isVisible) {
            hideSearchForm()
        } else {
            binding.layoutSearchProductContent.visibility = View.VISIBLE
            binding.layoutCreateProductContent.visibility = View.GONE
            setToggleActive(binding.layoutSearchProductHeader)
        }
    }

    private fun hideSearchForm() {
        binding.layoutSearchProductContent.visibility = View.GONE
        binding.layoutCreateProductContent.visibility = View.GONE
        setToggleActive(null)
    }

    private fun setToggleActive(active: View?) {
        if (active === binding.layoutCreateProductHeader) {
            binding.layoutCreateProductHeader.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.layoutSearchProductHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        } else if (active === binding.layoutSearchProductHeader) {
            binding.layoutCreateProductHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchProductHeader.setBackgroundResource(R.drawable.bg_toggle_active)
        } else {
            binding.layoutCreateProductHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchProductHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        }
    }

    private fun applyProductsTitleGradient() {
        binding.tvProductsTitle.post {
            val paint = binding.tvProductsTitle.paint
            val width = paint.measureText(binding.tvProductsTitle.text.toString())
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
            binding.tvProductsTitle.invalidate()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun loadOfflineOnly() {
        val items = if (sortAsc) {
            buildOfflineProducts().sortedBy { it.id }
        } else {
            buildOfflineProducts().sortedByDescending { it.id }
        }
        products.clear()
        products.addAll(items)
        totalCount = items.size
        currentOffset = 0
        lifecycleScope.launch {
            val categoryMap = fetchCategoryMap()
            adapter.submit(items.map { ProductRowUi(it, categoryMap[it.categoryId]) })
        }
        updatePageInfo(items.size)
        if (items.isNotEmpty()) {
            UiNotifier.show(this@ProductListActivity, "Mostrando productos offline")
        } else {
            UiNotifier.show(this@ProductListActivity, "Sin conexi?n y sin productos offline")
        }
    }

    private fun buildOfflineProducts(): List<ProductResponseDto> {
        val pending = offlineQueue.getAll().filter { it.type == PendingType.PRODUCT_CREATE }
        return pending.mapIndexedNotNull { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, ProductCreateDto::class.java) }.getOrNull()
            if (dto == null) return@mapIndexedNotNull null
            ProductResponseDto(
                sku = dto.sku,
                name = "${dto.name} (offline)",
                barcode = dto.barcode,
                categoryId = dto.categoryId,
                active = dto.active ?: true,
                id = -1000 - index,
                createdAt = "offline",
                updatedAt = "offline"
            )
        }
    }

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
