package com.example.inventoryapp.ui.movements

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.cache.CacheKeys
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.local.cache.ProductNameCache
import com.example.inventoryapp.data.local.cache.ProductNameItem
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementAdjustOperationRequest
import com.example.inventoryapp.data.remote.model.MovementOperationRequest
import com.example.inventoryapp.data.remote.model.MovementListResponseDto
import com.example.inventoryapp.data.remote.model.MovementResponseDto
import com.example.inventoryapp.data.remote.model.MovementSourceDto
import com.example.inventoryapp.data.remote.model.MovementTransferOperationRequest
import com.example.inventoryapp.data.remote.model.MovementTypeDto
import com.example.inventoryapp.databinding.ActivityMovementsMenuBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

class MovementsMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovementsMenuBinding
    private lateinit var snack: SendSnack
    private val gson = Gson()
    private lateinit var cacheStore: CacheStore

    private var items: List<MovementRowUi> = emptyList()
    private var allItems: List<MovementRowUi> = emptyList()
    private lateinit var adapter: MovementsListAdapter
    private var productNamesById: Map<Int, String> = emptyMap()

    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var isLoading = false
    private var filteredItems: List<MovementRowUi> = emptyList()
    private var filteredOffset = 0
    private var pendingFilterApply = false

    private var quantityHint: String = "Cantidad"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementsMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateMovementAdd, R.drawable.add)
        GradientIconUtil.applyGradient(binding.ivCreateMovementSearch, R.drawable.search)
        applyMovementsTitleGradient()
        binding.tilCreateType.post { applyDropdownIcons() }

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        snack = SendSnack(binding.root)
        cacheStore = CacheStore.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = MovementsListAdapter()
        binding.rvMovements.layoutManager = LinearLayoutManager(this)
        binding.rvMovements.adapter = adapter

        setupTypeDropdowns()
        setupSourceDropdowns()
        setupLocationDropdown()
        setupQuantityFocusHint()

        binding.layoutCreateMovementHeader.setOnClickListener { toggleCreateForm() }
        binding.layoutSearchMovementHeader.setOnClickListener { toggleSearchForm() }
        binding.btnCreateMovement.setOnClickListener { createMovement() }
        binding.btnSearchMovements.setOnClickListener {
            hideKeyboard()
            applySearchFilters()
        }
        binding.btnClearMovements.setOnClickListener {
            hideKeyboard()
            clearSearchFilters()
        }
        binding.btnRefreshMovements.setOnClickListener { loadMovements(withSnack = true) }

        binding.btnPrevPageMovements.setOnClickListener {
            if (hasActiveFilters()) {
                if (filteredOffset <= 0) return@setOnClickListener
                filteredOffset = (filteredOffset - pageSize).coerceAtLeast(0)
                applyFilteredPage()
                binding.rvMovements.scrollToPosition(0)
                return@setOnClickListener
            }
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadMovements(withSnack = false)
            binding.rvMovements.scrollToPosition(0)
        }
        binding.btnNextPageMovements.setOnClickListener {
            if (hasActiveFilters()) {
                val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
                if (shown >= filteredItems.size) return@setOnClickListener
                filteredOffset += pageSize
                applyFilteredPage()
                binding.rvMovements.scrollToPosition(0)
                return@setOnClickListener
            }
            val shown = (currentOffset + items.size).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadMovements(withSnack = false)
            binding.rvMovements.scrollToPosition(0)
        }

        applyPagerButtonStyle(binding.btnPrevPageMovements, enabled = false)
        applyPagerButtonStyle(binding.btnNextPageMovements, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        currentOffset = 0
        loadMovements(withSnack = false)
    }

    private fun createMovement() {
        val type = binding.etCreateType.text.toString().trim().uppercase()
        val sourceRaw = binding.etCreateSource.text.toString().trim().uppercase()
        val productInput = binding.etCreateProduct.text.toString().trim()
        val quantity = binding.etCreateQuantity.text.toString().trim().toIntOrNull()
        val location = normalizeLocationInput(binding.etCreateLocation.text.toString().trim())
        val toLocation = normalizeLocationInput(binding.etCreateToLocation.text.toString().trim())

        if (type.isBlank()) { binding.etCreateType.error = "Tipo requerido"; return }
        val source = when (sourceRaw) {
            "SCAN" -> MovementSourceDto.SCAN
            "MANUAL" -> MovementSourceDto.MANUAL
            else -> { binding.etCreateSource.error = "Usa SCAN o MANUAL"; return }
        }
        if (productInput.isBlank()) { binding.etCreateProduct.error = "Producto requerido"; return }
        if (location.isBlank()) { binding.etCreateLocation.error = "Ubicación requerida"; return }

        when (type) {
            "IN", "OUT" -> if (quantity == null || quantity <= 0) { binding.etCreateQuantity.error = "Cantidad > 0"; return }
            "ADJUST" -> if (quantity == null || quantity == 0) { binding.etCreateQuantity.error = "Delta != 0"; return }
            "TRANSFER" -> {
                if (quantity == null || quantity <= 0) { binding.etCreateQuantity.error = "Cantidad > 0"; return }
                if (toLocation.isBlank()) { binding.etCreateToLocation.error = "Ubicación destino requerida"; return }
                if (location.equals(toLocation, ignoreCase = true)) {
                    binding.etCreateToLocation.error = "Origen y destino no pueden ser iguales"
                    return
                }
            }
            else -> { binding.etCreateType.error = "Usa IN / OUT / ADJUST / TRANSFER"; return }
        }

        binding.btnCreateMovement.isEnabled = false
        snack.showSending("Enviando movimiento...")

        lifecycleScope.launch {
            try {
                val productId = productInput.toIntOrNull() ?: resolveProductIdByName(productInput)
                if (productId == null) {
                    binding.etCreateProduct.error = "Producto inválido"
                    return@launch
                }

                when (type) {
                    "IN" -> {
                        val dto = MovementOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementIn(dto)
                        if (res.isSuccessful && res.body() != null) {
                            snack.showSuccess("IN OK")
                            binding.etCreateQuantity.setText("")
                            cacheStore.invalidatePrefix("movements")
                            loadMovements(withSnack = false)
                        } else {
                            handleMovementError(res.code(), res.errorBody()?.string())
                        }
                    }
                    "OUT" -> {
                        val dto = MovementOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementOut(dto)
                        if (res.isSuccessful && res.body() != null) {
                            snack.showSuccess("OUT OK")
                            binding.etCreateQuantity.setText("")
                            cacheStore.invalidatePrefix("movements")
                            loadMovements(withSnack = false)
                        } else {
                            handleMovementError(res.code(), res.errorBody()?.string())
                        }
                    }
                    "ADJUST" -> {
                        val dto = MovementAdjustOperationRequest(productId, quantity!!, location, source)
                        val res = NetworkModule.api.movementAdjust(dto)
                        if (res.isSuccessful && res.body() != null) {
                            snack.showSuccess("ADJUST OK")
                            binding.etCreateQuantity.setText("")
                            cacheStore.invalidatePrefix("movements")
                            loadMovements(withSnack = false)
                        } else {
                            handleMovementError(res.code(), res.errorBody()?.string())
                        }
                    }
                    "TRANSFER" -> {
                        val dto = MovementTransferOperationRequest(productId, quantity!!, location, toLocation, source)
                        val res = NetworkModule.api.movementTransfer(dto)
                        if (res.isSuccessful && res.body() != null) {
                            snack.showSuccess("TRANSFER OK")
                            binding.etCreateQuantity.setText("")
                            cacheStore.invalidatePrefix("movements")
                            loadMovements(withSnack = false)
                        } else {
                            handleMovementError(res.code(), res.errorBody()?.string())
                        }
                    }
                }
            } catch (e: IOException) {
                enqueueOffline(type, productInput, quantity, location, toLocation, source)
                snack.showQueuedOffline("Sin red. Guardado offline para reenviar.")
                loadMovements(withSnack = false)
            } catch (e: Exception) {
                snack.showError("Error: ${e.message}")
            } finally {
                binding.btnCreateMovement.isEnabled = true
            }
        }
    }

    private fun handleMovementError(code: Int, body: String?) {
        if (code == 403) {
            UiNotifier.showBlocking(
                this,
                "Permisos insuficientes",
                "No tienes permisos para crear movimientos.",
                R.drawable.ic_lock
            )
        } else {
            snack.showError("Error $code: ${body ?: "sin detalle"}")
        }
    }

    private fun enqueueOffline(
        type: String,
        productInput: String,
        quantity: Int?,
        location: String,
        toLocation: String,
        source: MovementSourceDto
    ) {
        val productId = productInput.toIntOrNull() ?: -1
        when (type) {
            "IN" -> {
                val dto = MovementOperationRequest(productId, quantity ?: 1, location, source)
                OfflineQueue(this).enqueue(PendingType.MOVEMENT_IN, gson.toJson(dto))
            }
            "OUT" -> {
                val dto = MovementOperationRequest(productId, quantity ?: 1, location, source)
                OfflineQueue(this).enqueue(PendingType.MOVEMENT_OUT, gson.toJson(dto))
            }
            "ADJUST" -> {
                val dto = MovementAdjustOperationRequest(productId, quantity ?: 1, location, source)
                OfflineQueue(this).enqueue(PendingType.MOVEMENT_ADJUST, gson.toJson(dto))
            }
            "TRANSFER" -> {
                val dto = MovementTransferOperationRequest(
                    productId,
                    quantity ?: 1,
                    location,
                    if (toLocation.isBlank()) location else toLocation,
                    source
                )
                OfflineQueue(this).enqueue(PendingType.MOVEMENT_TRANSFER, gson.toJson(dto))
            }
        }
    }

    private fun loadMovements(withSnack: Boolean) {
        if (isLoading) return
        isLoading = true
        if (withSnack) snack.showSending("Cargando movimientos...")

        val filtersActive = hasActiveFilters()
        val effectiveLimit = if (filtersActive) 100 else pageSize
        val effectiveOffset = if (filtersActive) 0 else currentOffset
        if (filtersActive) currentOffset = 0

        val productRaw = binding.etSearchProduct.text.toString().trim()
        val productIdFilter = productRaw.toIntOrNull()
        val typeFilter = parseTypeForApi(binding.etSearchType.text.toString())
        val sourceFilter = parseSourceForApi(binding.etSearchSource.text.toString())

        lifecycleScope.launch {
            try {
                val cacheKey = CacheKeys.list(
                    "movements",
                    mapOf(
                        "product_id" to productIdFilter,
                        "type" to typeFilter?.name,
                        "source" to sourceFilter?.name,
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cached = cacheStore.get(cacheKey, MovementListResponseDto::class.java)
                if (cached != null) {
                    val pending = if (currentOffset == 0) buildPendingRows() else emptyList()
                    val ids = (pending.map { it.productId } + cached.items.map { it.productId }).toSet()
                    productNamesById = resolveProductNames(ids)
                    val mappedRemote = cached.items.map { it.toRowUi(productNamesById) }
                    val ordered = (pending + mappedRemote).sortedByDescending { it.id }
                    totalCount = cached.total
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(cached.items.size, pending.size)
                    isLoading = false
                }
                val res = NetworkModule.api.listMovements(
                    productId = productIdFilter,
                    movementType = typeFilter,
                    movementSource = sourceFilter,
                    userId = null,
                    dateFrom = null,
                    dateTo = null,
                    limit = effectiveLimit,
                    offset = effectiveOffset
                )
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    cacheStore.put(cacheKey, body)
                    val pending = if (currentOffset == 0) buildPendingRows() else emptyList()
                    val remoteItems = body.items
                    totalCount = body.total

                    val ids = (pending.map { it.productId } + remoteItems.map { it.productId }).toSet()
                    productNamesById = resolveProductNames(ids)

                    val mappedRemote = remoteItems.map { it.toRowUi(productNamesById) }
                    val ordered = (pending + mappedRemote).sortedByDescending { it.id }
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(remoteItems.size, pending.size)
                    if (withSnack) snack.showSuccess("Movimientos cargados")
                } else {
                    if (withSnack) snack.showError("Error ${res.code()}: ${res.errorBody()?.string()}")
                    val cachedOnError = cacheStore.get(cacheKey, MovementListResponseDto::class.java)
                    if (cachedOnError != null) {
                        val pending = if (currentOffset == 0) buildPendingRows() else emptyList()
                        val ids = (pending.map { it.productId } + cachedOnError.items.map { it.productId }).toSet()
                        productNamesById = resolveProductNames(ids)
                        val mappedRemote = cachedOnError.items.map { it.toRowUi(productNamesById) }
                        val ordered = (pending + mappedRemote).sortedByDescending { it.id }
                        totalCount = cachedOnError.total
                        setAllItemsAndApplyFilters(ordered)
                        updatePageInfo(cachedOnError.items.size, pending.size)
                        snack.showError("Mostrando movimientos en cache")
                    } else {
                        val pending = buildPendingRows()
                        productNamesById = resolveProductNames(pending.map { it.productId }.toSet())
                        val ordered = pending.sortedByDescending { it.id }
                        totalCount = pending.size
                        setAllItemsAndApplyFilters(ordered)
                        updatePageInfo(ordered.size, ordered.size)
                    }
                }
            } catch (e: Exception) {
                if (withSnack) snack.showError("Error de red: ${e.message}")
                val cacheKey = CacheKeys.list(
                    "movements",
                    mapOf(
                        "product_id" to productIdFilter,
                        "type" to typeFilter?.name,
                        "source" to sourceFilter?.name,
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cachedOnError = cacheStore.get(cacheKey, MovementListResponseDto::class.java)
                if (cachedOnError != null) {
                    val pending = if (currentOffset == 0) buildPendingRows() else emptyList()
                    val ids = (pending.map { it.productId } + cachedOnError.items.map { it.productId }).toSet()
                    productNamesById = resolveProductNames(ids)
                    val mappedRemote = cachedOnError.items.map { it.toRowUi(productNamesById) }
                    val ordered = (pending + mappedRemote).sortedByDescending { it.id }
                    totalCount = cachedOnError.total
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(cachedOnError.items.size, pending.size)
                    snack.showError("Mostrando movimientos en cache")
                } else {
                    val pending = buildPendingRows()
                    productNamesById = resolveProductNames(pending.map { it.productId }.toSet())
                    val ordered = pending.sortedByDescending { it.id }
                    totalCount = pending.size
                    setAllItemsAndApplyFilters(ordered)
                    updatePageInfo(ordered.size, ordered.size)
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

    private fun buildPendingRows(): List<MovementRowUi> {
        val queue = OfflineQueue(this)
        val pending = queue.getAll()
        val out = mutableListOf<MovementRowUi>()

        pending.forEachIndexed { index, p ->
            when (p.type) {
                PendingType.MOVEMENT_IN -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        out.add(
                            MovementRowUi(
                                id = -1 - index,
                                movementType = "IN",
                                productId = dto.productId,
                                productName = null,
                                quantity = dto.quantity,
                                delta = null,
                                location = dto.location,
                                source = dto.movementSource.name,
                                createdAt = "offline",
                                transferId = null,
                                isPending = true
                            )
                        )
                    }
                }
                PendingType.MOVEMENT_OUT -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        out.add(
                            MovementRowUi(
                                id = -1 - index,
                                movementType = "OUT",
                                productId = dto.productId,
                                productName = null,
                                quantity = dto.quantity,
                                delta = null,
                                location = dto.location,
                                source = dto.movementSource.name,
                                createdAt = "offline",
                                transferId = null,
                                isPending = true
                            )
                        )
                    }
                }
                PendingType.MOVEMENT_ADJUST -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementAdjustOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        out.add(
                            MovementRowUi(
                                id = -1 - index,
                                movementType = "ADJUST",
                                productId = dto.productId,
                                productName = null,
                                quantity = dto.delta,
                                delta = dto.delta,
                                location = dto.location,
                                source = dto.movementSource.name,
                                createdAt = "offline",
                                transferId = null,
                                isPending = true
                            )
                        )
                    }
                }
                PendingType.MOVEMENT_TRANSFER -> {
                    val dto = runCatching { gson.fromJson(p.payloadJson, MovementTransferOperationRequest::class.java) }.getOrNull()
                    if (dto != null) {
                        out.add(
                            MovementRowUi(
                                id = -1 - index,
                                movementType = "TRANSFER",
                                productId = dto.productId,
                                productName = null,
                                quantity = dto.quantity,
                                delta = null,
                                location = "${dto.fromLocation} -> ${dto.toLocation}",
                                source = dto.movementSource.name,
                                createdAt = "offline",
                                transferId = "offline",
                                isPending = true
                            )
                        )
                    }
                }
                else -> Unit
            }
        }

        return out
    }

    private suspend fun resolveProductNames(ids: Set<Int>): Map<Int, String> {
        if (ids.isEmpty()) return emptyMap()
        val resolved = mutableMapOf<Int, String>()
        val cachedMap = getCachedProductNames()
        val listRes = runCatching { NetworkModule.api.listProducts(limit = 200, offset = 0) }.getOrNull()
        if (listRes?.isSuccessful == true && listRes.body() != null) {
            val items = listRes.body()!!.items
            resolved.putAll(items.associate { it.id to it.name })
            updateProductNameCache(items)
        }
        for (id in ids) {
            if (resolved.containsKey(id)) continue
            val singleRes = runCatching { NetworkModule.api.getProduct(id) }.getOrNull()
            if (singleRes?.isSuccessful == true && singleRes.body() != null) {
                val name = singleRes.body()!!.name
                resolved[id] = name
                updateProductNameCache(listOf(singleRes.body()!!))
            } else {
                cachedMap[id]?.let { resolved[id] = it }
            }
        }
        ids.forEach { id ->
            if (!resolved.containsKey(id)) cachedMap[id]?.let { resolved[id] = it }
        }
        return resolved
    }

    private suspend fun getCachedProductNames(): Map<Int, String> {
        val cached = cacheStore.get("products:names", ProductNameCache::class.java)
        return cached?.items?.associateBy({ it.id }, { it.name }) ?: emptyMap()
    }

    private suspend fun updateProductNameCache(items: List<com.example.inventoryapp.data.remote.model.ProductResponseDto>) {
        if (items.isEmpty()) return
        val existing = cacheStore.get("products:names", ProductNameCache::class.java)
        val map = existing?.items?.associateBy({ it.id }, { it.name })?.toMutableMap() ?: mutableMapOf()
        items.forEach { p -> map[p.id] = p.name }
        val merged = map.entries.map { ProductNameItem(it.key, it.value) }
        cacheStore.put("products:names", ProductNameCache(merged))
    }

    private suspend fun resolveProductIdByName(input: String): Int? {
        val needle = input.trim().lowercase()
        if (needle.isBlank()) return null
        val cached = productNamesById.entries.firstOrNull { it.value.lowercase() == needle }
        if (cached != null) return cached.key

        val res = runCatching { NetworkModule.api.listProducts(name = input, limit = 50, offset = 0) }.getOrNull()
        if (res?.isSuccessful == true && res.body() != null) {
            val match = res.body()!!.items.firstOrNull { it.name.equals(input, ignoreCase = true) }
            if (match != null) return match.id
            return res.body()!!.items.firstOrNull()?.id
        }
        return null
    }

    private fun MovementResponseDto.toRowUi(productNames: Map<Int, String>): MovementRowUi {
        return MovementRowUi(
            id = id,
            movementType = movementType.name,
            productId = productId,
            productName = productNames[productId],
            quantity = quantity,
            delta = delta,
            location = location ?: locationId?.toString() ?: "n/a",
            source = movementSource.name,
            createdAt = createdAt,
            transferId = transferId,
            isPending = false
        )
    }

    private fun setAllItemsAndApplyFilters(ordered: List<MovementRowUi>) {
        allItems = ordered
        if (hasActiveFilters() || pendingFilterApply) {
            applySearchFiltersInternal(allowReload = false)
        } else {
            items = ordered
            adapter.submit(toAdapterRows(ordered))
        }
    }

    private fun toAdapterRows(rows: List<MovementRowUi>): List<MovementRow> {
        return rows.map { row ->
            val typeLabel = if (row.transferId != null || row.movementType == "TRANSFER") "TRANSFER" else row.movementType
            val productLabel = row.productName ?: "Producto ${row.productId}"
            val qtyValue = row.delta ?: row.quantity
            val qtyLabel = formatQuantity(qtyValue)
            val title = "Producto: $productLabel"
            val meta = "Cantidad: $qtyLabel  ·  Localización: ${row.location}"
            val sub = "Src ${row.source}  ·  ${row.createdAt}"
            MovementRow(
                movementId = row.id,
                type = typeLabel,
                title = title,
                meta = meta,
                sub = sub,
                isPending = row.isPending
            )
        }
    }

    private fun formatQuantity(value: Int): String {
        val nf = NumberFormat.getIntegerInstance(Locale("es", "ES"))
        return nf.format(value)
    }

    private fun updatePageInfo(pageSizeLoaded: Int, pendingCount: Int) {
        if (hasActiveFilters()) {
            val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
            binding.tvMovementsPageInfo.text = "Mostrando $shown / ${filteredItems.size}"
            val prevEnabled = filteredOffset > 0
            val nextEnabled = shown < filteredItems.size
            binding.btnPrevPageMovements.isEnabled = prevEnabled
            binding.btnNextPageMovements.isEnabled = nextEnabled
            applyPagerButtonStyle(binding.btnPrevPageMovements, prevEnabled)
            applyPagerButtonStyle(binding.btnNextPageMovements, nextEnabled)
            return
        }
        val shownOnline = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        val label = if (totalCount > 0) {
            "Mostrando $shownOnline / $totalCount"
        } else {
            "Mostrando $pendingCount / $pendingCount"
        }
        binding.tvMovementsPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = shownOnline < totalCount
        binding.btnPrevPageMovements.isEnabled = prevEnabled
        binding.btnNextPageMovements.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevPageMovements, prevEnabled)
        applyPagerButtonStyle(binding.btnNextPageMovements, nextEnabled)
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

    private fun setupTypeDropdowns() {
        val createValues = listOf("", "IN", "OUT", "ADJUST", "TRANSFER")
        val createAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, createValues)
        binding.etCreateType.setAdapter(createAdapter)
        binding.etCreateType.setOnItemClickListener { _, _, position, _ ->
            if (createValues[position].isBlank()) binding.etCreateType.setText("", false)
        }
        binding.etCreateType.setOnClickListener { binding.etCreateType.showDropDown() }
        binding.etCreateType.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etCreateType.showDropDown()
        }
        binding.etCreateType.addTextChangedListener { text ->
            val type = text?.toString()?.trim()?.uppercase() ?: ""
            updateTransferVisibility(type)
            updateQuantityForType(type)
        }

        val searchValues = listOf("", "IN", "OUT", "ADJUST")
        val searchAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, searchValues)
        binding.etSearchType.setAdapter(searchAdapter)
        binding.etSearchType.setOnItemClickListener { _, _, position, _ ->
            if (searchValues[position].isBlank()) binding.etSearchType.setText("", false)
        }
        binding.etSearchType.setOnClickListener { binding.etSearchType.showDropDown() }
        binding.etSearchType.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etSearchType.showDropDown()
        }
    }

    private fun setupSourceDropdowns() {
        val values = listOf("", "SCAN", "MANUAL")
        val createAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, values)
        binding.etCreateSource.setAdapter(createAdapter)
        binding.etCreateSource.setOnItemClickListener { _, _, position, _ ->
            if (values[position].isBlank()) binding.etCreateSource.setText("", false)
        }
        binding.etCreateSource.setOnClickListener { binding.etCreateSource.showDropDown() }
        binding.etCreateSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etCreateSource.showDropDown()
        }

        val searchAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, values)
        binding.etSearchSource.setAdapter(searchAdapter)
        binding.etSearchSource.setOnItemClickListener { _, _, position, _ ->
            if (values[position].isBlank()) binding.etSearchSource.setText("", false)
        }
        binding.etSearchSource.setOnClickListener { binding.etSearchSource.showDropDown() }
        binding.etSearchSource.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etSearchSource.showDropDown()
        }
    }

    private fun setupLocationDropdown() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    cacheStore.put(
                        CacheKeys.list("locations", mapOf("limit" to 200, "offset" to 0)),
                        res.body()!!
                    )
                    val values = items.sortedBy { it.id }
                        .map { "(${it.id}) ${it.code}" }
                        .distinct()
                    val allValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = ArrayAdapter(this@MovementsMenuActivity, android.R.layout.simple_list_item_1, allValues)
                    binding.etCreateLocation.setAdapter(adapter)
                    binding.etCreateLocation.setOnClickListener { binding.etCreateLocation.showDropDown() }
                    binding.etCreateLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etCreateLocation.showDropDown()
                    }
                    binding.etCreateToLocation.setAdapter(adapter)
                    binding.etCreateToLocation.setOnClickListener { binding.etCreateToLocation.showDropDown() }
                    binding.etCreateToLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etCreateToLocation.showDropDown()
                    }
                    return@launch
                }
            } catch (_: Exception) { }

            val cached = cacheStore.get(
                CacheKeys.list("locations", mapOf("limit" to 200, "offset" to 0)),
                com.example.inventoryapp.data.remote.model.LocationListResponseDto::class.java
            )
            if (cached != null) {
                val values = cached.items.sortedBy { it.id }
                    .map { "(${it.id}) ${it.code}" }
                    .distinct()
                val allValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                val adapter = ArrayAdapter(this@MovementsMenuActivity, android.R.layout.simple_list_item_1, allValues)
                binding.etCreateLocation.setAdapter(adapter)
                binding.etCreateLocation.setOnClickListener { binding.etCreateLocation.showDropDown() }
                binding.etCreateLocation.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) binding.etCreateLocation.showDropDown()
                }
                binding.etCreateToLocation.setAdapter(adapter)
                binding.etCreateToLocation.setOnClickListener { binding.etCreateToLocation.showDropDown() }
                binding.etCreateToLocation.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) binding.etCreateToLocation.showDropDown()
                }
            }
        }
    }

    private fun applyDropdownIcons() {
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        val tilList = listOf(
            binding.tilCreateType,
            binding.tilCreateSource,
            binding.tilCreateLocation,
            binding.tilCreateToLocation,
            binding.tilSearchType,
            binding.tilSearchSource
        )
        tilList.forEach { til ->
            til.setEndIconTintList(null)
            til.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
                GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
                iv.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }
        }
    }

    private fun updateTransferVisibility(type: String) {
        binding.tilCreateToLocation.visibility =
            if (type == "TRANSFER") View.VISIBLE else View.GONE
    }

    private fun updateQuantityForType(type: String) {
        if (type == "ADJUST") {
            quantityHint = "(-)Negativo para retirar | (+)Positivo para introducir"
            binding.etCreateQuantity.hint = quantityHint
            binding.etCreateQuantity.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        } else {
            quantityHint = "Cantidad"
            binding.etCreateQuantity.hint = quantityHint
            binding.etCreateQuantity.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }

    private fun setupQuantityFocusHint() {
        binding.etCreateQuantity.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.etCreateQuantity.hint = ""
            } else if (binding.etCreateQuantity.text.isNullOrBlank()) {
                binding.etCreateQuantity.hint = quantityHint
            }
        }
    }

    private fun toggleCreateForm() {
        TransitionManager.beginDelayedTransition(binding.scrollMovements, AutoTransition().setDuration(180))
        val isVisible = binding.layoutCreateMovementContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutCreateMovementContent.visibility = View.GONE
            binding.layoutSearchMovementContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutCreateMovementContent.visibility = View.VISIBLE
            binding.layoutSearchMovementContent.visibility = View.GONE
            setToggleActive(binding.layoutCreateMovementHeader)
        }
    }

    private fun toggleSearchForm() {
        TransitionManager.beginDelayedTransition(binding.scrollMovements, AutoTransition().setDuration(180))
        val isVisible = binding.layoutSearchMovementContent.visibility == View.VISIBLE
        if (isVisible) {
            hideSearchForm()
        } else {
            binding.layoutSearchMovementContent.visibility = View.VISIBLE
            binding.layoutCreateMovementContent.visibility = View.GONE
            setToggleActive(binding.layoutSearchMovementHeader)
        }
    }

    private fun hideSearchForm() {
        binding.layoutSearchMovementContent.visibility = View.GONE
        binding.layoutCreateMovementContent.visibility = View.GONE
        setToggleActive(null)
    }

    private fun setToggleActive(active: View?) {
        if (active === binding.layoutCreateMovementHeader) {
            binding.layoutCreateMovementHeader.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.layoutSearchMovementHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        } else if (active === binding.layoutSearchMovementHeader) {
            binding.layoutCreateMovementHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchMovementHeader.setBackgroundResource(R.drawable.bg_toggle_active)
        } else {
            binding.layoutCreateMovementHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchMovementHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        }
    }

    private fun applyMovementsTitleGradient() {
        binding.tvMovementsTitle.post {
            val paint = binding.tvMovementsTitle.paint
            val width = paint.measureText(binding.tvMovementsTitle.text.toString())
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
            binding.tvMovementsTitle.invalidate()
        }
    }

    private fun applySearchFilters() {
        applySearchFiltersInternal(allowReload = true)
    }

    private fun applySearchFiltersInternal(allowReload: Boolean) {
        val typeRaw = binding.etSearchType.text.toString().trim().uppercase()
        val sourceRaw = binding.etSearchSource.text.toString().trim().uppercase()
        val productRaw = binding.etSearchProduct.text.toString().trim()

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > allItems.size)) {
            pendingFilterApply = true
            currentOffset = 0
            loadMovements(withSnack = false)
            return
        }

        var filtered = allItems
        if (typeRaw.isNotBlank()) {
            filtered = filtered.filter { row ->
                val typeLabel = if (row.transferId != null || row.movementType == "TRANSFER") "TRANSFER" else row.movementType
                typeLabel.equals(typeRaw, ignoreCase = true)
            }
        }
        if (sourceRaw.isNotBlank()) {
            filtered = filtered.filter { it.source.equals(sourceRaw, ignoreCase = true) }
        }
        if (productRaw.isNotBlank()) {
            val productId = productRaw.toIntOrNull()
            filtered = if (productId != null) {
                filtered.filter { it.productId == productId }
            } else {
                val needle = productRaw.lowercase()
                filtered.filter { (it.productName ?: "").lowercase().contains(needle) }
            }
        }

        filteredItems = filtered
        filteredOffset = 0
        applyFilteredPage()
    }

    private fun clearSearchFilters() {
        binding.etSearchType.setText("", false)
        binding.etSearchSource.setText("", false)
        binding.etSearchProduct.setText("")
        filteredItems = emptyList()
        filteredOffset = 0
        items = allItems
        adapter.submit(toAdapterRows(allItems))
        updatePageInfo(items.size, items.size)
    }

    private fun hasActiveFilters(): Boolean {
        return binding.etSearchType.text?.isNotBlank() == true ||
            binding.etSearchSource.text?.isNotBlank() == true ||
            binding.etSearchProduct.text?.isNotBlank() == true
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        items = page
        adapter.submit(toAdapterRows(page))
        updatePageInfo(page.size, page.size)
    }

    private fun parseTypeForApi(raw: String): MovementTypeDto? {
        return when (raw.trim().uppercase()) {
            "IN" -> MovementTypeDto.IN
            "OUT" -> MovementTypeDto.OUT
            "ADJUST" -> MovementTypeDto.ADJUST
            else -> null
        }
    }

    private fun parseSourceForApi(raw: String): MovementSourceDto? {
        return when (raw.trim().uppercase()) {
            "SCAN" -> MovementSourceDto.SCAN
            "MANUAL" -> MovementSourceDto.MANUAL
            else -> null
        }
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private data class MovementRowUi(
        val id: Int,
        val movementType: String,
        val productId: Int,
        val productName: String?,
        val quantity: Int,
        val delta: Int?,
        val location: String,
        val source: String,
        val createdAt: String,
        val transferId: String?,
        val isPending: Boolean
    )
}
