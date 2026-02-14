package com.example.inventoryapp.ui.products

import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.cache.CacheKeys
import com.example.inventoryapp.data.local.cache.ProductNameCache
import com.example.inventoryapp.data.local.cache.ProductNameItem
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ProductListResponseDto
import com.example.inventoryapp.data.remote.model.CategoryResponseDto
import com.example.inventoryapp.data.remote.model.ProductCreateDto
import com.example.inventoryapp.data.remote.model.ProductResponseDto
import com.example.inventoryapp.data.remote.model.ProductUpdateDto
import com.example.inventoryapp.databinding.ActivityProductListBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import android.graphics.drawable.GradientDrawable

class ProductListActivity : AppCompatActivity() {
    companion object {
        @Volatile
        private var cacheNoticeShownInOfflineSession = false
    }

    private lateinit var binding: ActivityProductListBinding
    private lateinit var offlineQueue: OfflineQueue
    private lateinit var cacheStore: CacheStore
    private val gson = Gson()

    private val products = mutableListOf<ProductResponseDto>()
    private var allItems: List<ProductResponseDto> = emptyList()
    private var filteredItems: List<ProductResponseDto> = emptyList()
    private var filteredOffset = 0
    private var pendingFilterApply = false
    private var pendingSearchNotFoundDialog = false

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
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateProductAdd, R.drawable.add)
        GradientIconUtil.applyGradient(binding.ivCreateProductSearch, R.drawable.search)
        applyProductsTitleGradient()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        offlineQueue = OfflineQueue(this)
        cacheStore = CacheStore.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = ProductListAdapter(
            onClick = { p ->
                showEditDialog(p)
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
        lifecycleScope.launch {
            NetworkModule.offlineState.collectLatest { offline ->
                if (!offline) {
                    cacheNoticeShownInOfflineSession = false
                }
            }
        }

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
        resetAndLoad()
    }

    override fun onResume() {
        super.onResume()
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
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
        var postLoadingNotice: (() -> Unit)? = null
        val loading = if (withSnack) {
            CreateUiFeedback.showListLoading(
                this,
                message = "Cargando productos",
                animationRes = R.raw.loading_list,
                minCycles = 2
            )
        } else {
            null
        }

        lifecycleScope.launch {
            val filtersActive = hasActiveFilters()
            val effectiveLimit = if (filtersActive) 100 else pageSize
            val effectiveOffset = if (filtersActive) 0 else currentOffset
            if (filtersActive) currentOffset = 0

            val searchNameOrId = binding.etSearchProduct.text.toString().trim()
            val nameParam = if (searchNameOrId.isNotBlank() && searchNameOrId.toIntOrNull() == null) searchNameOrId else null
            val skuParam = binding.etSearchSku.text.toString().trim().ifBlank { null }
            val barcodeParam = binding.etSearchBarcode.text.toString().trim().ifBlank { null }
            val categoryParam = resolveCategoryId(binding.etSearchCategory.text.toString().trim())
            val pendingAll = if (!filtersActive) buildOfflineProducts() else emptyList()
            val pendingTotalCount = pendingAll.size
            val cachedRemoteTotal = resolveCachedRemoteProductsTotal(
                skuParam = skuParam,
                nameParam = nameParam,
                barcodeParam = barcodeParam,
                categoryParam = categoryParam,
                effectiveLimit = effectiveLimit
            )

            val cacheKey = CacheKeys.list(
                "products",
                mapOf(
                    "sku" to skuParam,
                    "name" to nameParam,
                    "barcode" to barcodeParam,
                    "category" to categoryParam,
                    "order_by" to "id",
                    "order_dir" to if (sortAsc) "asc" else "desc",
                    "limit" to effectiveLimit,
                    "offset" to effectiveOffset
                )
            )
            try {
                val cached = cacheStore.get(cacheKey, ProductListResponseDto::class.java)
                if (cached != null) {
                    applyCachedProducts(
                        cached = cached,
                        filtersActive = filtersActive,
                        effectiveOffset = effectiveOffset,
                        pendingAll = pendingAll,
                        pendingTotalCount = pendingTotalCount
                    )
                    isLoading = false
                }

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

                if (res.code() == 401) return@launch
                if (res.isSuccessful && res.body() != null) {
                    cacheStore.put(cacheKey, res.body()!!)
                    val pageItems = res.body()!!.items
                    if (pageItems.isEmpty() && currentOffset == 0 && !filtersActive) {
                        UiNotifier.show(this@ProductListActivity, "Sin resultados")
                    }
                    val pending = pendingProductsForPage(
                        offset = effectiveOffset,
                        remoteTotal = res.body()!!.total,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    val combined = pageItems + pending
                    val categoryMap = categoryCache.ifEmpty { fetchCategoryMap().also { categoryCache = it } }
                    updateProductNameCache(pageItems)
                    val rows = combined.map { ProductRowUi(it, categoryMap[it.categoryId]) }
                    products.clear()
                    products.addAll(combined)
                    totalCount = res.body()!!.total + pendingTotalCount

                    allItems = combined
                    setAllItemsAndApplyFilters(combined)

                    updatePageInfo(combined.size)

                    if (!filtersActive) {
                        adapter.submit(rows)
                    } else {
                        // rows handled in filtered page
                    }
                    if (withSnack) {
                        postLoadingNotice = {
                            UiNotifier.showBlockingTimed(
                                this@ProductListActivity,
                                "Productos cargados",
                                R.drawable.loaded,
                                timeoutMs = 2_500L
                            )
                        }
                    }
                } else {
                    val err = res.errorBody()?.string()
                    UiNotifier.show(this@ProductListActivity, ApiErrorFormatter.format(res.code(), err))
                    val cachedOnError = cacheStore.get(cacheKey, ProductListResponseDto::class.java)
                    if (cachedOnError != null) {
                        applyCachedProducts(
                            cached = cachedOnError,
                            filtersActive = filtersActive,
                            effectiveOffset = effectiveOffset,
                            pendingAll = pendingAll,
                            pendingTotalCount = pendingTotalCount
                        )
                        if (withSnack && !cacheNoticeShownInOfflineSession) {
                            postLoadingNotice = { showProductsCacheNoticeOnce() }
                        } else {
                            showProductsCacheNoticeOnce()
                        }
                    } else {
                        val pending = pendingProductsForPage(
                            offset = effectiveOffset,
                            remoteTotal = cachedRemoteTotal,
                            filtersActive = filtersActive,
                            pendingAll = pendingAll
                        )
                        products.clear()
                        products.addAll(pending)
                        totalCount = cachedRemoteTotal + pendingTotalCount
                        allItems = pending
                        setAllItemsAndApplyFilters(pending)
                        updatePageInfo(pending.size)
                    }
                }
            } catch (e: Exception) {
                val cachedFallback = cacheStore.get(cacheKey, ProductListResponseDto::class.java)
                if (cachedFallback != null) {
                    applyCachedProducts(
                        cached = cachedFallback,
                        filtersActive = filtersActive,
                        effectiveOffset = effectiveOffset,
                        pendingAll = pendingAll,
                        pendingTotalCount = pendingTotalCount
                    )
                    if (withSnack && !cacheNoticeShownInOfflineSession) {
                        postLoadingNotice = { showProductsCacheNoticeOnce() }
                    } else {
                        showProductsCacheNoticeOnce()
                    }
                } else {
                    val pending = pendingProductsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedRemoteTotal,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    )
                    products.clear()
                    products.addAll(pending)
                    totalCount = cachedRemoteTotal + pendingTotalCount
                    allItems = pending
                    setAllItemsAndApplyFilters(pending)
                    updatePageInfo(pending.size)
                }
            } finally {
                if (loading != null) {
                    val action = postLoadingNotice
                    if (action != null) {
                        loading.dismissThen { action() }
                    } else {
                        loading.dismiss()
                    }
                } else {
                    postLoadingNotice?.invoke()
                }
                isLoading = false
                if (pendingFilterApply) {
                    pendingFilterApply = false
                    val showDialog = pendingSearchNotFoundDialog
                    pendingSearchNotFoundDialog = false
                    applySearchFiltersInternal(allowReload = false, showNotFoundDialog = showDialog)
                }
            }
        }
    }

    private fun createProduct() {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear productos.",
                com.example.inventoryapp.R.drawable.ic_lock
            )
            return
        }

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
        val loading = CreateUiFeedback.showLoading(this, "producto")

        lifecycleScope.launch {
            var loadingHandled = false
            try {
                val dto = ProductCreateDto(sku = sku, name = name, barcode = rawBarcode, categoryId = categoryId, active = true)
                val res = NetworkModule.api.createProduct(dto)
                if (res.code() == 401) return@launch

                if (res.isSuccessful && res.body() != null) {
                    val created = res.body()!!
                    val categoryLabel = categoryCache[created.categoryId]?.let { "(${created.categoryId}) $it" }
                        ?: created.categoryId.toString()
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showCreatedPopup(
                            this@ProductListActivity,
                            "Producto creado",
                            "ID: ${created.id}\nSKU: ${created.sku}\nNombre: ${created.name}\nBarcode: ${created.barcode ?: "-"}\nCategoría: $categoryLabel"
                        )
                    }
                    binding.etSku.setText("")
                    binding.etName.setText("")
                    binding.etBarcode.setText("")
                    binding.etCategory.setText("")
                    cacheStore.invalidatePrefix("products")
                    resetAndLoad()
                } else if (res.code() == 403) {
                    UiNotifier.showBlocking(
                        this@ProductListActivity,
                        "Permisos insuficientes",
                        "No tienes permisos para crear productos.",
                        com.example.inventoryapp.R.drawable.ic_lock
                    )
                } else {
                    val raw = runCatching { res.errorBody()?.string() }.getOrNull()
                    val details = formatCreateProductError(res.code(), raw)
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showErrorPopup(
                            activity = this@ProductListActivity,
                            title = "No se pudo crear producto",
                            details = details
                        )
                    }
                }
            } catch (e: IOException) {
                val dto = ProductCreateDto(sku = sku, name = name, barcode = rawBarcode, categoryId = categoryId!!, active = true)
                OfflineQueue(this@ProductListActivity).enqueue(PendingType.PRODUCT_CREATE, gson.toJson(dto))
                loadingHandled = true
                loading.dismissThen {
                    CreateUiFeedback.showCreatedPopup(
                        this@ProductListActivity,
                        "Producto creado (offline)",
                        "SKU: ${dto.sku}\nNombre: ${dto.name}\nBarcode: ${dto.barcode}\nCategoría: ${dto.categoryId} (offline)",
                        accentColorRes = R.color.offline_text
                    )
                }
                resetAndLoad()
            } catch (e: Exception) {
                loadingHandled = true
                val details = if (canShowTechnicalCreateErrors()) {
                    "Ha ocurrido un error inesperado.\n${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}"
                } else {
                    "Ha ocurrido un error inesperado al crear el producto."
                }
                loading.dismissThen {
                    CreateUiFeedback.showErrorPopup(
                        activity = this@ProductListActivity,
                        title = "No se pudo crear producto",
                        details = details
                    )
                }
            } finally {
                if (!loadingHandled) {
                    loading.dismiss()
                }
                binding.btnCreateProduct.isEnabled = true
            }
        }
    }

    private fun showEditDialog(product: ProductResponseDto) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_product, null)
        val title = view.findViewById<android.widget.TextView>(R.id.tvEditProductTitle)
        val skuInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditProductSku)
        val nameInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditProductName)
        val barcodeInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditProductBarcode)
        val categoryInput = view.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.etEditProductCategory)
        val btnSave = view.findViewById<android.widget.Button>(R.id.btnEditProductSave)
        val btnDelete = view.findViewById<android.widget.Button>(R.id.btnEditProductDelete)
        val btnClose = view.findViewById<ImageButton>(R.id.btnEditProductClose)
        val isOffline = product.id < 0

        title.text = if (isOffline) {
            "Editar: ${product.name} (offline)"
        } else {
            "Editar: ${product.name} (ID ${product.id})"
        }
        skuInput.setText(product.sku)
        nameInput.setText(product.name)
        barcodeInput.setText(product.barcode ?: "")

        val categoryLabel = categoryCache[product.categoryId]?.let { "(${product.categoryId}) $it" }
            ?: product.categoryId.toString()
        categoryInput.setText(categoryLabel, false)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val values = buildCategoryDropdownValues()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, values)
        categoryInput.setAdapter(adapter)
        categoryInput.setOnClickListener { categoryInput.showDropDown() }
        categoryInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) categoryInput.showDropDown()
        }

        if (isOffline) {
            nameInput.isEnabled = false
            barcodeInput.isEnabled = false
            categoryInput.isEnabled = false
            btnSave.isEnabled = false
            btnDelete.text = "Eliminar offline"
        }

        btnSave.setOnClickListener {
            val newName = nameInput.text?.toString()?.trim().orEmpty()
            val rawBarcode = barcodeInput.text?.toString()?.trim().orEmpty()
            val barcode = rawBarcode.ifBlank { null }
            val categoryId = resolveCategoryId(categoryInput.text?.toString().orEmpty().trim())

            if (newName.isBlank()) {
                nameInput.error = "Nombre requerido"
                return@setOnClickListener
            }
            if (rawBarcode.isNotBlank() && !rawBarcode.matches(Regex("^\\d{13}$"))) {
                barcodeInput.error = "Barcode debe tener 13 dígitos"
                return@setOnClickListener
            }
            if (categoryId == null) {
                categoryInput.error = "Categoría requerida"
                return@setOnClickListener
            }

            updateProduct(product.id, ProductUpdateDto(name = newName, barcode = barcode, categoryId = categoryId))
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            val titleText = if (isOffline) "Eliminar producto offline" else "Eliminar producto"
            val bodyText = if (isOffline) {
                "Se eliminará de la cola offline para que no se sincronice. ¿Continuar?"
            } else {
                "¿Seguro que quieres eliminar este producto?"
            }
            AlertDialog.Builder(this)
                .setTitle(titleText)
                .setMessage(bodyText)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar") { _, _ ->
                    if (isOffline) {
                        val removed = removeOfflineProduct(product)
                        if (removed) {
                            UiNotifier.show(this@ProductListActivity, "Producto offline eliminado")
                            resetAndLoad()
                        } else {
                            UiNotifier.show(this@ProductListActivity, "No se pudo eliminar de la cola offline")
                        }
                    } else {
                        deleteProduct(product.id)
                    }
                    dialog.dismiss()
                }
                .show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun removeOfflineProduct(product: ProductResponseDto): Boolean {
        val pending = offlineQueue.getAll().toMutableList()
        val idx = pending.indexOfFirst { req ->
            if (req.type != PendingType.PRODUCT_CREATE) return@indexOfFirst false
            val dto = runCatching { gson.fromJson(req.payloadJson, ProductCreateDto::class.java) }.getOrNull()
                ?: return@indexOfFirst false
            dto.sku == product.sku &&
                dto.name == product.name.replace(" (offline)", "") &&
                dto.barcode == (product.barcode ?: "") &&
                dto.categoryId == product.categoryId
        }
        if (idx < 0) return false
        pending.removeAt(idx)
        offlineQueue.saveAll(pending)
        return true
    }

    private fun updateProduct(id: Int, body: ProductUpdateDto) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateProduct(id, body)
                if (res.code() == 401) return@launch
                if (res.isSuccessful) {
                    UiNotifier.show(this@ProductListActivity, "Producto actualizado")
                    cacheStore.invalidatePrefix("products")
                    resetAndLoad()
                } else if (res.code() == 403) {
                    UiNotifier.showBlocking(
                        this@ProductListActivity,
                        "Permisos insuficientes",
                        "No tienes permisos para editar productos.",
                        com.example.inventoryapp.R.drawable.ic_lock
                    )
                } else {
                    UiNotifier.show(this@ProductListActivity, "Error ${res.code()}: ${res.errorBody()?.string()}")
                }
            } catch (e: IOException) {
                val payload = OfflineSyncer.ProductUpdatePayload(id, body)
                OfflineQueue(this@ProductListActivity).enqueue(PendingType.PRODUCT_UPDATE, gson.toJson(payload))
                UiNotifier.show(this@ProductListActivity, "Sin conexión. Actualización guardada offline")
                resetAndLoad()
            } catch (e: Exception) {
                UiNotifier.show(this@ProductListActivity, "Error: ${e.message}")
            }
        }
    }

    private fun deleteProduct(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.deleteProduct(id)
                if (res.code() == 401) return@launch
                if (res.isSuccessful) {
                    UiNotifier.show(this@ProductListActivity, "Producto eliminado")
                    cacheStore.invalidatePrefix("products")
                    resetAndLoad()
                } else if (res.code() == 403) {
                    UiNotifier.showBlocking(
                        this@ProductListActivity,
                        "Permisos insuficientes",
                        "No tienes permisos para eliminar productos.",
                        com.example.inventoryapp.R.drawable.ic_lock
                    )
                } else {
                    UiNotifier.show(this@ProductListActivity, "Error ${res.code()}: ${res.errorBody()?.string()}")
                }
            } catch (e: IOException) {
                val payload = OfflineSyncer.ProductDeletePayload(id)
                OfflineQueue(this@ProductListActivity).enqueue(PendingType.PRODUCT_DELETE, gson.toJson(payload))
                UiNotifier.show(this@ProductListActivity, "Sin conexión. Eliminado guardado offline")
                resetAndLoad()
            } catch (e: Exception) {
                UiNotifier.show(this@ProductListActivity, "Error: ${e.message}")
            }
        }
    }

    private fun applySearchFilters() {
        applySearchFiltersInternal(allowReload = true, showNotFoundDialog = true)
    }

    private fun applySearchFiltersInternal(
        allowReload: Boolean,
        showNotFoundDialog: Boolean = false
    ) {
        val productRaw = binding.etSearchProduct.text.toString().trim()
        val skuRaw = binding.etSearchSku.text.toString().trim()
        val barcodeRaw = binding.etSearchBarcode.text.toString().trim()
        val categoryRaw = binding.etSearchCategory.text.toString().trim()

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > allItems.size)) {
            pendingFilterApply = true
            pendingSearchNotFoundDialog = showNotFoundDialog
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
        if (showNotFoundDialog && hasActiveFilters() && filtered.isEmpty()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se encontraron productos",
                details = buildProductSearchNotFoundDetails(productRaw, skuRaw, barcodeRaw, categoryRaw),
                animationRes = R.raw.notfound
            )
        }
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

    private fun buildProductSearchNotFoundDetails(
        productRaw: String,
        skuRaw: String,
        barcodeRaw: String,
        categoryRaw: String
    ): String {
        val parts = mutableListOf<String>()
        if (productRaw.isNotBlank()) {
            val label = if (productRaw.toIntOrNull() != null) {
                "ID $productRaw"
            } else {
                "\"$productRaw\""
            }
            parts.add("producto $label")
        }
        if (skuRaw.isNotBlank()) parts.add("SKU \"$skuRaw\"")
        if (barcodeRaw.isNotBlank()) parts.add("barcode \"$barcodeRaw\"")
        if (categoryRaw.isNotBlank()) parts.add("categoría \"$categoryRaw\"")
        return if (parts.isEmpty()) {
            "No se encontraron productos con los filtros actuales."
        } else {
            "No se encontraron productos para ${parts.joinToString(separator = ", ")}."
        }
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
                val items = res.body()!!.items
                cacheStore.put(CacheKeys.list("categories", mapOf("name" to null, "order_by" to "id", "order_dir" to "asc", "limit" to 100, "offset" to 0)), res.body()!!)
                items.associateBy({ it.id }, { it.name })
            } else emptyMap()
        } catch (_: Exception) {
            val cached = cacheStore.get(
                CacheKeys.list("categories", mapOf("name" to null, "order_by" to "id", "order_dir" to "asc", "limit" to 100, "offset" to 0)),
                com.example.inventoryapp.data.remote.model.CategoryListResponseDto::class.java
            )
            cached?.items?.associateBy({ it.id }, { it.name }) ?: emptyMap()
        }
    }

    private fun setupCategoryDropdowns() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listCategories(limit = 100, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items.sortedBy { it.id }
                    cacheStore.put(CacheKeys.list("categories", mapOf("name" to null, "order_by" to "id", "order_dir" to "asc", "limit" to 100, "offset" to 0)), res.body()!!)
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
                    return@launch
                }
            } catch (_: Exception) { }

            val cached = cacheStore.get(
                CacheKeys.list("categories", mapOf("name" to null, "order_by" to "id", "order_dir" to "asc", "limit" to 100, "offset" to 0)),
                com.example.inventoryapp.data.remote.model.CategoryListResponseDto::class.java
            )
            if (cached != null) {
                val items = cached.items.sortedBy { it.id }
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
        }
    }

    private fun buildCategoryDropdownValues(): List<String> {
        if (categoryCache.isNotEmpty()) {
            val values = categoryCache.entries.sortedBy { it.key }.map { "(${it.key}) ${it.value}" }
            return listOf("") + values
        }
        return listOf("")
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
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear productos.",
                com.example.inventoryapp.R.drawable.ic_lock
            )
            return
        }
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
        }
    }

    private fun showProductsCacheNoticeOnce() {
        if (cacheNoticeShownInOfflineSession) return
        UiNotifier.showBlockingTimed(
            this,
            "Mostrando productos en cache y pendientes offline",
            R.drawable.sync,
            timeoutMs = 3_200L
        )
        cacheNoticeShownInOfflineSession = true
    }

    private fun applyCachedProducts(
        cached: ProductListResponseDto,
        filtersActive: Boolean,
        effectiveOffset: Int,
        pendingAll: List<ProductResponseDto>,
        pendingTotalCount: Int
    ) {
        val pending = pendingProductsForPage(
            offset = effectiveOffset,
            remoteTotal = cached.total,
            filtersActive = filtersActive,
            pendingAll = pendingAll
        )
        val pageItems = cached.items
        val combined = pageItems + pending
        updateProductNameCache(pageItems)
        products.clear()
        products.addAll(combined)
        totalCount = cached.total + pendingTotalCount
        allItems = combined
        setAllItemsAndApplyFilters(combined)
        updatePageInfo(combined.size)
        // Only notify on API failure; cache-first rendering stays silent.
    }

    private fun pendingProductsForPage(
        offset: Int,
        remoteTotal: Int,
        filtersActive: Boolean,
        pendingAll: List<ProductResponseDto>
    ): List<ProductResponseDto> {
        if (filtersActive) return emptyList()
        if (pendingAll.isEmpty()) return emptyList()
        val startInPending = (offset - remoteTotal).coerceAtLeast(0)
        if (startInPending >= pendingAll.size) return emptyList()
        val endInPending = (offset + pageSize - remoteTotal)
            .coerceAtMost(pendingAll.size)
            .coerceAtLeast(startInPending)
        return pendingAll.subList(startInPending, endInPending)
    }

    private suspend fun resolveCachedRemoteProductsTotal(
        skuParam: String?,
        nameParam: String?,
        barcodeParam: String?,
        categoryParam: Int?,
        effectiveLimit: Int
    ): Int {
        val keyAtStart = CacheKeys.list(
            "products",
            mapOf(
                "sku" to skuParam,
                "name" to nameParam,
                "barcode" to barcodeParam,
                "category" to categoryParam,
                "order_by" to "id",
                "order_dir" to if (sortAsc) "asc" else "desc",
                "limit" to effectiveLimit,
                "offset" to 0
            )
        )
        val cachedAtStart = cacheStore.get(keyAtStart, ProductListResponseDto::class.java)
        if (cachedAtStart != null) return cachedAtStart.total

        if (effectiveLimit != pageSize) {
            val keyDefault = CacheKeys.list(
                "products",
                mapOf(
                    "sku" to skuParam,
                    "name" to nameParam,
                    "barcode" to barcodeParam,
                    "category" to categoryParam,
                    "order_by" to "id",
                    "order_dir" to if (sortAsc) "asc" else "desc",
                    "limit" to pageSize,
                    "offset" to 0
                )
            )
            val cachedDefault = cacheStore.get(keyDefault, ProductListResponseDto::class.java)
            if (cachedDefault != null) return cachedDefault.total
        }
        return 0
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

    private fun updateProductNameCache(items: List<ProductResponseDto>) {
        if (items.isEmpty()) return
        lifecycleScope.launch {
            val existing = cacheStore.get("products:names", ProductNameCache::class.java)
            val map = existing?.items?.associateBy({ it.id }, { it.name })?.toMutableMap() ?: mutableMapOf()
            items.forEach { p -> map[p.id] = p.name }
            val merged = map.entries.map { ProductNameItem(it.key, it.value) }
            cacheStore.put("products:names", ProductNameCache(merged))
        }
    }

    private fun canShowTechnicalCreateErrors(): Boolean {
        val role = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("cached_role", null)
        return role.equals("ADMIN", ignoreCase = true) || role.equals("MANAGER", ignoreCase = true)
    }

    private fun isUserRole(): Boolean {
        val role = getSharedPreferences("ui_prefs", MODE_PRIVATE).getString("cached_role", null)
        return role.equals("USER", ignoreCase = true)
    }

    private fun formatCreateProductError(code: Int, rawError: String?): String {
        val raw = rawError?.trim().orEmpty()
        val normalized = raw.lowercase()
        val technical = canShowTechnicalCreateErrors()

        val looksLikeDuplicateSku =
            normalized.contains("sku") &&
                (normalized.contains("existe") || normalized.contains("exists") || normalized.contains("duplic"))
        val looksLikeDuplicateBarcode =
            normalized.contains("barcode") &&
                (normalized.contains("existe") || normalized.contains("exists") || normalized.contains("duplic"))

        if (looksLikeDuplicateSku) {
            return if (technical) {
                buildString {
                    append("SKU duplicado: ya existe un producto con ese SKU.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "Ese SKU ya está en uso. Introduce un SKU diferente."
            }
        }

        if (looksLikeDuplicateBarcode) {
            return if (technical) {
                buildString {
                    append("Barcode duplicado: ya existe un producto con ese código.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "Ese código de barras ya está en uso. Introduce otro diferente."
            }
        }

        return if (technical) {
            buildString {
                append(
                    when (code) {
                        400, 422 -> "Datos inválidos para crear producto."
                        409 -> "Conflicto al crear producto."
                        500 -> "Error interno del servidor al crear producto."
                        else -> "No se pudo crear el producto."
                    }
                )
                if (raw.isNotBlank()) append("\nDetalle: ${compactErrorDetail(raw)}")
                if (code > 0) append("\nHTTP $code")
            }
        } else {
            when (code) {
                400, 422 -> "No se pudo crear el producto. Revisa los datos introducidos."
                409 -> "No se pudo crear el producto porque entra en conflicto con otro existente."
                500 -> "No se pudo crear el producto por un problema del servidor. Inténtalo de nuevo."
                else -> "No se pudo crear el producto. Inténtalo de nuevo."
            }
        }
    }

    private fun compactErrorDetail(raw: String, maxLen: Int = 180): String {
        val singleLine = raw.replace("\\s+".toRegex(), " ").trim()
        return if (singleLine.length <= maxLen) singleLine else singleLine.take(maxLen) + "..."
    }

}

