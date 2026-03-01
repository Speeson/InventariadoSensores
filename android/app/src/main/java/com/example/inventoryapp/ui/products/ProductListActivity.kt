package com.example.inventoryapp.ui.products

import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.example.inventoryapp.ui.common.TopCenterActionHost
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ProductListActivity : AppCompatActivity(), TopCenterActionHost {
    companion object {
        private const val OFFLINE_DELETE_MARKER = "offline_delete"
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

    private var categoryCache: Map<Int, String> = emptyMap()
    private var categoryIdByName: Map<String, Int> = emptyMap()
    private var searchProductFilter: String = ""
    private var searchSkuFilter: String = ""
    private var searchBarcodeFilter: String = ""
    private var searchCategoryFilter: String = ""
    private var createDialog: AlertDialog? = null
    private var searchDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateProductAdd, R.drawable.glass_add)
        GradientIconUtil.applyGradient(binding.ivCreateProductSearch, R.drawable.glass_search)
        binding.btnRefresh.setImageResource(R.drawable.glass_refresh)
        applyProductsTitleGradient()
        applyRefreshIconTint()

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
                val popup = LabelPreviewDialogFragment.newInstance(
                    productId = p.id,
                    sku = p.sku,
                    barcode = p.barcode ?: ""
                )
                popup.show(supportFragmentManager, "label_preview_popup")
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

        binding.layoutCreateProductHeader.setOnClickListener { openCreateProductDialog() }
        binding.layoutSearchProductHeader.setOnClickListener { openSearchProductDialog() }
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


        applyPagerButtonStyle(binding.btnPrevPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextPage, enabled = false)
        setupCategoryDropdowns()

        resetAndLoad()
    }

    override fun onResume() {
        super.onResume()
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        updateProductsListAdaptiveHeight()
    }

    override fun onTopCreateAction() {
        openCreateProductDialog()
    }

    override fun onTopFilterAction() {
        openSearchProductDialog()
    }

    private fun resetAndLoad() {
        currentOffset = 0
        isLoading = false
        products.clear()
        allItems = emptyList()
        filteredItems = emptyList()
        filteredOffset = 0
        adapter.submit(emptyList())
        updateProductsListAdaptiveHeight()
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
                animationRes = R.raw.glass_loading_list,
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

            val nameParam: String? = null
            val skuParam: String? = null
            val barcodeParam: String? = null
            val categoryParam: Int? = null
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
                    "order_dir" to "asc",
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
                    orderDir = "asc",
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
                    val combined = markPendingDeletedProducts(pageItems + pending)
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
                            CreateUiFeedback.showStatusPopup(
                                activity = this@ProductListActivity,
                                title = "Productos cargados",
                                details = "Se han cargado correctamente.",
                                animationRes = R.raw.correct_create,
                                autoDismissMs = 2500L
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
                        val pending = markPendingDeletedProducts(pendingProductsForPage(
                            offset = effectiveOffset,
                            remoteTotal = cachedRemoteTotal,
                            filtersActive = filtersActive,
                            pendingAll = pendingAll
                        ))
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
                    val pending = markPendingDeletedProducts(pendingProductsForPage(
                        offset = effectiveOffset,
                        remoteTotal = cachedRemoteTotal,
                        filtersActive = filtersActive,
                        pendingAll = pendingAll
                    ))
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

    private fun createProduct(
        sku: String,
        name: String,
        rawBarcode: String,
        categoryId: Int,
        onFinished: () -> Unit = {}
    ) {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear productos.",
                com.example.inventoryapp.R.drawable.ic_lock
            )
            onFinished()
            return
        }

        val loading = CreateUiFeedback.showLoading(this, "producto")

        lifecycleScope.launch {
            var loadingHandled = false
            try {
                val dto = ProductCreateDto(sku = sku, name = name, barcode = rawBarcode, categoryId = categoryId, active = true)
                val res = NetworkModule.api.createProduct(dto)
                if (res.code() == 401) {
                    onFinished()
                    return@launch
                }

                if (res.isSuccessful && res.body() != null) {
                    val created = res.body()!!
                    val categoryLabel = categoryCache[created.categoryId]?.let { "(${created.categoryId}) $it" }
                        ?: created.categoryId.toString()
                    loadingHandled = true
                    loading.dismissThen {
                        CreateUiFeedback.showCreatedPopup(
                            this@ProductListActivity,
                            "Producto creado",
                            "ID: ${created.id}\nSKU: ${created.sku}\nNombre: ${created.name}\nBarcode: ${created.barcode ?: "-"}\nCategoria: $categoryLabel"
                        )
                    }
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
                val dto = ProductCreateDto(sku = sku, name = name, barcode = rawBarcode, categoryId = categoryId, active = true)
                OfflineQueue(this@ProductListActivity).enqueue(PendingType.PRODUCT_CREATE, gson.toJson(dto))
                loadingHandled = true
                loading.dismissThen {
                    CreateUiFeedback.showCreatedPopup(
                        this@ProductListActivity,
                        "Producto creado (offline)",
                        "SKU: ${dto.sku}\nNombre: ${dto.name}\nBarcode: ${dto.barcode}\nCategoria: ${dto.categoryId} (offline)",
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
                onFinished()
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
        val tilCategory = view.findViewById<TextInputLayout>(R.id.tilEditProductCategory)
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
        val adapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, values)
        categoryInput.setAdapter(adapter)
        categoryInput.setOnClickListener { categoryInput.showDropDown() }
        categoryInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) categoryInput.showDropDown()
        }
        applyDialogDropdownStyle(listOf(tilCategory), listOf(categoryInput))

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
                barcodeInput.error = "Barcode debe tener 13 dÃƒÆ’Ã‚Â­gitos"
                return@setOnClickListener
            }
            if (categoryId == null) {
                categoryInput.error = "CategorÃƒÆ’Ã‚Â­a requerida"
                return@setOnClickListener
            }

            updateProduct(product.id, ProductUpdateDto(name = newName, barcode = barcode, categoryId = categoryId))
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            val titleText = if (isOffline) "Eliminar producto offline" else "Eliminar producto"
            val bodyText = if (isOffline) {
                "Se eliminarÃƒÂ¡ de la cola offline para que no se sincronice. Ã‚Â¿Continuar?"
            } else {
                "Ã‚Â¿Seguro que quieres eliminar este producto?"
            }
            CreateUiFeedback.showQuestionConfirmDialog(
                activity = this,
                title = titleText,
                message = bodyText,
                confirmText = "Eliminar",
                cancelText = "Cancelar"
            ) {
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
                UiNotifier.show(this@ProductListActivity, "Sin conexiÃƒÆ’Ã‚Â³n. ActualizaciÃƒÆ’Ã‚Â³n guardada offline")
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
                    val raw = runCatching { res.errorBody()?.string() }.getOrNull()
                    val details = formatDeleteProductError(res.code(), raw)
                    CreateUiFeedback.showErrorPopup(
                        activity = this@ProductListActivity,
                        title = "No se pudo eliminar producto",
                        details = details,
                        animationRes = R.raw.error
                    )
                }
            } catch (e: IOException) {
                val payload = OfflineSyncer.ProductDeletePayload(id)
                OfflineQueue(this@ProductListActivity).enqueue(PendingType.PRODUCT_DELETE, gson.toJson(payload))
                CreateUiFeedback.showErrorPopup(
                    activity = this@ProductListActivity,
                    title = "Sin conexion",
                    details = "No se pudo eliminar ahora. La solicitud se guardo en cola offline.",
                    animationRes = R.raw.error
                )
                resetAndLoad()
            } catch (e: Exception) {
                val details = if (canShowTechnicalCreateErrors()) {
                    "Ha ocurrido un error inesperado al eliminar el producto.\n${e.javaClass.simpleName}: ${e.message ?: "sin detalle"}"
                } else {
                    "Ha ocurrido un error inesperado al eliminar el producto."
                }
                CreateUiFeedback.showErrorPopup(
                    activity = this@ProductListActivity,
                    title = "No se pudo eliminar producto",
                    details = details,
                    animationRes = R.raw.error
                )
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
        val productRaw = searchProductFilter.trim()
        val skuRaw = searchSkuFilter.trim()
        val barcodeRaw = searchBarcodeFilter.trim()
        val categoryRaw = searchCategoryFilter.trim()

        if (allowReload && !isLoading) {
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
        searchProductFilter = ""
        searchSkuFilter = ""
        searchBarcodeFilter = ""
        searchCategoryFilter = ""
        filteredItems = emptyList()
        filteredOffset = 0
        adapter.submit(products.map { ProductRowUi(it, categoryCache[it.categoryId]) })
        updateProductsListAdaptiveHeight()
        updatePageInfo(products.size)
    }

    private fun setAllItemsAndApplyFilters(ordered: List<ProductResponseDto>) {
        allItems = ordered
        if (hasActiveFilters() || pendingFilterApply) {
            applySearchFiltersInternal(allowReload = false)
        } else {
            val rows = ordered.map { ProductRowUi(it, categoryCache[it.categoryId]) }
            adapter.submit(rows)
            updateProductsListAdaptiveHeight()
        }
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        val rows = page.map { ProductRowUi(it, categoryCache[it.categoryId]) }
        adapter.submit(rows)
        updateProductsListAdaptiveHeight()
        updatePageInfo(page.size, total = filteredItems.size, filtered = true)
    }

    private fun hasActiveFilters(): Boolean {
        return searchProductFilter.isNotBlank() ||
            searchSkuFilter.isNotBlank() ||
            searchBarcodeFilter.isNotBlank() ||
            searchCategoryFilter.isNotBlank()
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
        if (categoryRaw.isNotBlank()) parts.add("categorÃƒÆ’Ã‚Â­a \"$categoryRaw\"")
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
        val currentPage = if (total <= 0) 0 else ((if (filtered) filteredOffset else currentOffset) / pageSize) + 1
        val totalPages = if (total <= 0) 0 else ((total + pageSize - 1) / pageSize)
        binding.tvProductPageNumber.text = "Pagina $currentPage/$totalPages"
        binding.tvProductPageInfo.text = "Mostrando $shown/$total"
        val prevEnabled = if (filtered) filteredOffset > 0 else currentOffset > 0
        val nextEnabled = if (filtered) shown < total else (currentOffset + pageSize) < total
        binding.btnPrevPage.isEnabled = prevEnabled
        binding.btnNextPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextPage, nextEnabled)
    }

    private fun applyPagerButtonStyle(button: Button, enabled: Boolean) {
        button.backgroundTintList = null
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (!enabled) {
            val colors = intArrayOf(
                if (isDark) 0x334F6480 else 0x33A7BED8,
                if (isDark) 0x33445A74 else 0x338FA9C6
            )
            val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
                cornerRadius = resources.displayMetrics.density * 16f
                setStroke((resources.displayMetrics.density * 1f).toInt(), if (isDark) 0x44AFCBEB else 0x5597BCD9)
            }
            button.background = drawable
            button.setTextColor(ContextCompat.getColor(this, R.color.liquid_popup_hint))
            return
        }
        val colors = intArrayOf(
            if (isDark) 0x66789BC4 else 0x99D6EBFA.toInt(),
            if (isDark) 0x666D8DB4 else 0x99C5E0F4.toInt()
        )
        val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = resources.displayMetrics.density * 16f
            setStroke((resources.displayMetrics.density * 1f).toInt(), if (isDark) 0x88B5D5F4.toInt() else 0x88A7CBE6.toInt())
        }
        button.background = drawable
        button.setTextColor(ContextCompat.getColor(this, R.color.liquid_popup_button_text))
    }

    private fun applyRefreshIconTint() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (!isDark) {
            val blue = ContextCompat.getColor(this, R.color.icon_grad_mid2)
            binding.btnRefresh.setColorFilter(blue)
        } else {
            binding.btnRefresh.clearColorFilter()
        }
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

    private fun updateProductsListAdaptiveHeight() {
        binding.scrollProducts.post {
            val topSpacerLp = binding.viewProductsTopSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val bottomSpacerLp = binding.viewProductsBottomSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val cardLp = binding.cardProductsList.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val rvLp = binding.rvProducts.layoutParams as? LinearLayout.LayoutParams ?: return@post

            val visibleCount = if (::adapter.isInitialized) adapter.itemCount else 0
            if (visibleCount in 1..pageSize) {
                topSpacerLp.height = 0
                topSpacerLp.weight = 1f
                bottomSpacerLp.height = 0
                bottomSpacerLp.weight = 1f
                cardLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                cardLp.weight = 0f
                rvLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                rvLp.weight = 0f
                binding.rvProducts.isNestedScrollingEnabled = false
            } else {
                topSpacerLp.height = 0
                topSpacerLp.weight = 0f
                bottomSpacerLp.height = 0
                bottomSpacerLp.weight = 0f
                cardLp.height = 0
                cardLp.weight = 1f
                rvLp.height = 0
                rvLp.weight = 1f
                binding.rvProducts.isNestedScrollingEnabled = true
            }
            binding.viewProductsTopSpacer.layoutParams = topSpacerLp
            binding.viewProductsBottomSpacer.layoutParams = bottomSpacerLp
            binding.cardProductsList.layoutParams = cardLp
            binding.rvProducts.layoutParams = rvLp
        }
    }

    private fun openCreateProductDialog() {
        if (isUserRole()) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear productos.",
                com.example.inventoryapp.R.drawable.ic_lock
            )
            return
        }
        if (createDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_products_create_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCreateProductDialogClose)
        val btnCreate = view.findViewById<Button>(R.id.btnDialogCreateProduct)
        val etSku = view.findViewById<TextInputEditText>(R.id.etDialogProductSku)
        val etName = view.findViewById<TextInputEditText>(R.id.etDialogProductName)
        val etBarcode = view.findViewById<TextInputEditText>(R.id.etDialogProductBarcode)
        val etCategory = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogProductCategory)
        val tilCategory = view.findViewById<TextInputLayout>(R.id.tilDialogCreateProductCategory)

        val values = buildCategoryDropdownValues()
        val categoryAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, values)
        etCategory.setAdapter(categoryAdapter)
        etCategory.setOnClickListener { etCategory.showDropDown() }
        etCategory.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etCategory.showDropDown() }
        applyDialogDropdownStyle(listOf(tilCategory), listOf(etCategory))

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val sku = etSku.text?.toString()?.trim().orEmpty()
            val name = etName.text?.toString()?.trim().orEmpty()
            val barcode = etBarcode.text?.toString()?.trim().orEmpty()
            val categoryText = etCategory.text?.toString()?.trim().orEmpty()
            val categoryId = resolveCategoryId(categoryText)

            etSku.error = null
            etName.error = null
            etBarcode.error = null
            etCategory.error = null

            if (sku.isBlank()) {
                etSku.error = "SKU requerido"
                return@setOnClickListener
            }
            if (name.isBlank()) {
                etName.error = "Nombre requerido"
                return@setOnClickListener
            }
            if (barcode.isBlank()) {
                etBarcode.error = "Barcode requerido"
                return@setOnClickListener
            }
            if (!barcode.matches(Regex("^\\d{13}$"))) {
                etBarcode.error = "Barcode debe tener 13 digitos"
                return@setOnClickListener
            }
            if (categoryId == null) {
                etCategory.error = "Categoria requerida"
                return@setOnClickListener
            }

            btnCreate.isEnabled = false
            createProduct(
                sku = sku,
                name = name,
                rawBarcode = barcode,
                categoryId = categoryId,
                onFinished = { btnCreate.isEnabled = true }
            )
            dialog.dismiss()
        }
        dialog.setOnDismissListener {
            createDialog = null
            hideKeyboard()
        }
        createDialog = dialog
        dialog.show()
    }

    private fun openSearchProductDialog() {
        if (searchDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_products_search_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnSearchProductDialogClose)
        val btnSearch = view.findViewById<Button>(R.id.btnDialogSearchProduct)
        val btnClear = view.findViewById<Button>(R.id.btnDialogClearSearchProduct)
        val etProduct = view.findViewById<TextInputEditText>(R.id.etDialogSearchProduct)
        val etSku = view.findViewById<TextInputEditText>(R.id.etDialogSearchSku)
        val etBarcode = view.findViewById<TextInputEditText>(R.id.etDialogSearchBarcode)
        val etCategory = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogSearchCategory)
        val tilCategory = view.findViewById<TextInputLayout>(R.id.tilDialogSearchProductCategory)

        etProduct.setText(searchProductFilter)
        etSku.setText(searchSkuFilter)
        etBarcode.setText(searchBarcodeFilter)
        etCategory.setText(searchCategoryFilter, false)

        val values = buildCategoryDropdownValues()
        val categoryAdapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, values)
        etCategory.setAdapter(categoryAdapter)
        etCategory.setOnClickListener { etCategory.showDropDown() }
        etCategory.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etCategory.showDropDown() }
        applyDialogDropdownStyle(listOf(tilCategory), listOf(etCategory))

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnSearch.setOnClickListener {
            searchProductFilter = etProduct.text?.toString().orEmpty()
            searchSkuFilter = etSku.text?.toString().orEmpty()
            searchBarcodeFilter = etBarcode.text?.toString().orEmpty()
            searchCategoryFilter = etCategory.text?.toString().orEmpty()
            hideKeyboard()
            dialog.dismiss()
            applySearchFilters()
        }
        btnClear.setOnClickListener {
            etProduct.setText("")
            etSku.setText("")
            etBarcode.setText("")
            etCategory.setText("", false)
            clearSearchFilters()
            hideKeyboard()
        }
        dialog.setOnDismissListener {
            searchDialog = null
            hideKeyboard()
        }
        searchDialog = dialog
        dialog.show()
    }

    private fun applyDialogDropdownStyle(
        textInputLayouts: List<TextInputLayout>,
        dropdowns: List<MaterialAutoCompleteTextView>
    ) {
        val popupDrawable = ContextCompat.getDrawable(this, R.drawable.bg_liquid_dropdown_popup)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        textInputLayouts.forEach { til ->
            til.setEndIconTintList(null)
            til.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
                GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
            }
        }
        dropdowns.forEach { auto ->
            if (popupDrawable != null) auto.setDropDownBackgroundDrawable(popupDrawable)
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
        val items = buildOfflineProducts().sortedBy { it.id }
        products.clear()
        products.addAll(items)
        totalCount = items.size
        currentOffset = 0
        lifecycleScope.launch {
            val categoryMap = fetchCategoryMap()
            adapter.submit(items.map { ProductRowUi(it, categoryMap[it.categoryId]) })
            updateProductsListAdaptiveHeight()
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
        val combined = markPendingDeletedProducts(pageItems + pending)
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
                "order_dir" to "asc",
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
                    "order_dir" to "asc",
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
                "Ese SKU ya estÃƒÆ’Ã‚Â¡ en uso. Introduce un SKU diferente."
            }
        }

        if (looksLikeDuplicateBarcode) {
            return if (technical) {
                buildString {
                    append("Barcode duplicado: ya existe un producto con ese cÃƒÆ’Ã‚Â³digo.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "Ese cÃƒÆ’Ã‚Â³digo de barras ya estÃƒÆ’Ã‚Â¡ en uso. Introduce otro diferente."
            }
        }

        return if (technical) {
            buildString {
                append(
                    when (code) {
                        400, 422 -> "Datos invÃƒÆ’Ã‚Â¡lidos para crear producto."
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
                500 -> "No se pudo crear el producto por un problema del servidor. IntÃƒÆ’Ã‚Â©ntalo de nuevo."
                else -> "No se pudo crear el producto. IntÃƒÆ’Ã‚Â©ntalo de nuevo."
            }
        }
    }

    private fun markPendingDeletedProducts(rows: List<ProductResponseDto>): List<ProductResponseDto> {
        val pendingDeleteIds = offlineQueue.getAll()
            .asSequence()
            .filter { it.type == PendingType.PRODUCT_DELETE }
            .mapNotNull {
                runCatching {
                    gson.fromJson(it.payloadJson, OfflineSyncer.ProductDeletePayload::class.java).productId
                }.getOrNull()
            }
            .toSet()
        if (pendingDeleteIds.isEmpty()) return rows
        return rows.map { product ->
            if (product.id > 0 && pendingDeleteIds.contains(product.id)) {
                val markedName = if (product.name.contains("(pendiente eliminar)", ignoreCase = true)) {
                    product.name
                } else {
                    "${product.name} (pendiente eliminar)"
                }
                product.copy(name = markedName, updatedAt = OFFLINE_DELETE_MARKER)
            } else {
                product
            }
        }
    }

    private fun formatDeleteProductError(code: Int, rawError: String?): String {
        val raw = rawError?.trim().orEmpty()
        val normalized = raw.lowercase()
        val technical = canShowTechnicalCreateErrors()
        val hasHistoryConflict = normalized.contains("movements_product_id_fkey") ||
            normalized.contains("events_product_id_fkey") ||
            normalized.contains("movimientos historicos") ||
            normalized.contains("referenced from table \"movements\"") ||
            normalized.contains("referenced from table \"events\"")

        if (hasHistoryConflict || code == 409) {
            return if (technical) {
                buildString {
                    append("Error de integridad referencial al eliminar el producto.")
                    append("\nDescripcion: el producto tiene eventos o movimientos asociados.")
                    if (raw.isNotBlank()) append("\nDetalle: ${compactErrorDetail(raw)}")
                    if (code > 0) append("\nHTTP $code")
                }
            } else {
                "No se puede eliminar el producto porque tiene eventos o movimientos asociados."
            }
        }

        return if (technical) {
            buildString {
                append(
                    when (code) {
                        400, 422 -> "Datos invalidos para eliminar producto."
                        404 -> "El producto no existe o ya fue eliminado."
                        500 -> "Error interno del servidor al eliminar producto."
                        else -> "No se pudo eliminar el producto."
                    }
                )
                if (raw.isNotBlank()) append("\nDetalle: ${compactErrorDetail(raw)}")
                if (code > 0) append("\nHTTP $code")
            }
        } else {
            when (code) {
                400, 422 -> "No se pudo eliminar el producto. Revisa el estado actual."
                404 -> "El producto ya no existe."
                500 -> "No se pudo eliminar el producto por un problema del servidor. Intentalo de nuevo."
                else -> "No se pudo eliminar el producto. Intentalo de nuevo."
            }
        }
    }

    private fun compactErrorDetail(raw: String, maxLen: Int = 180): String {
        val singleLine = raw.replace("\\s+".toRegex(), " ").trim()
        return if (singleLine.length <= maxLen) singleLine else singleLine.take(maxLen) + "..."
    }

}