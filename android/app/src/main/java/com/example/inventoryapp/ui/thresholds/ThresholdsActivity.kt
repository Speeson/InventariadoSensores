package com.example.inventoryapp.ui.thresholds
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.PendingType
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.local.cache.CacheKeys
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.ThresholdCreateDto
import com.example.inventoryapp.data.remote.model.ThresholdListResponseDto
import com.example.inventoryapp.data.remote.model.ThresholdResponseDto
import com.example.inventoryapp.data.remote.model.ThresholdUpdateDto
import com.example.inventoryapp.databinding.ActivityThresholdsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.ApiErrorFormatter
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.auth.LoginActivity
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R
import android.widget.ArrayAdapter
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import android.view.View
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button

class ThresholdsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdsBinding
    private lateinit var session: SessionManager
    private lateinit var adapter: ThresholdListAdapter
    private lateinit var cacheStore: CacheStore
    private var items: List<ThresholdResponseDto> = emptyList()
    private val gson = Gson()
    private var productNameById: Map<Int, String> = emptyMap()
    private var currentOffset = 0
    private val pageSize = 5
    private var totalCount = 0
    private var isLoading = false
    private var filteredItems: List<ThresholdResponseDto> = emptyList()
    private var filteredOffset = 0
    private var pendingFilterApply = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThresholdsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivCreateThresholdAdd, R.drawable.add)
        GradientIconUtil.applyGradient(binding.ivSearchThreshold, R.drawable.search)
        applyThresholdTitleGradient()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        session = SessionManager(this)
        cacheStore = CacheStore.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = ThresholdListAdapter { threshold ->
            showEditDialog(threshold)
        }
        binding.rvThresholds.layoutManager = LinearLayoutManager(this)
        binding.rvThresholds.adapter = adapter

        binding.btnCreate.setOnClickListener { createThreshold() }
        binding.btnRefresh.setOnClickListener { loadThresholds() }
        binding.layoutCreateThresholdHeader.setOnClickListener { toggleCreateForm() }
        binding.layoutSearchThresholdHeader.setOnClickListener { toggleSearchForm() }
        binding.btnSearch.setOnClickListener {
            hideKeyboard()
            applySearchFilters()
        }
        binding.btnClear.setOnClickListener {
            hideKeyboard()
            clearSearchFilters()
        }

        setupLocationDropdowns()
        binding.tilCreateLocation.post { applyThresholdDropdownIcons() }

        binding.btnPrevThresholdPage.setOnClickListener {
            if (hasActiveFilters()) {
                if (filteredOffset <= 0) return@setOnClickListener
                filteredOffset = (filteredOffset - pageSize).coerceAtLeast(0)
                applyFilteredPage()
                binding.rvThresholds.scrollToPosition(0)
                return@setOnClickListener
            }
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadThresholds()
            binding.rvThresholds.scrollToPosition(0)
        }
        binding.btnNextThresholdPage.setOnClickListener {
            if (hasActiveFilters()) {
                val shown = (filteredOffset + items.size).coerceAtMost(filteredItems.size)
                if (shown >= filteredItems.size) return@setOnClickListener
                filteredOffset += pageSize
                applyFilteredPage()
                binding.rvThresholds.scrollToPosition(0)
                return@setOnClickListener
            }
            val shown = (currentOffset + items.size).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadThresholds()
            binding.rvThresholds.scrollToPosition(0)
        }

        applyPagerButtonStyle(binding.btnPrevThresholdPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextThresholdPage, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        currentOffset = 0
        loadThresholds()
    }

    private fun applySearchFilters() {
        applySearchFiltersInternal(allowReload = true)
    }

    private fun applySearchFiltersInternal(allowReload: Boolean) {
        val productRaw = binding.etSearchProductId.text.toString().trim()
        val locationRaw = normalizeLocationInput(binding.etSearchLocation.text.toString().trim())

        if (allowReload && !isLoading && (currentOffset > 0 || totalCount > items.size)) {
            pendingFilterApply = true
            currentOffset = 0
            loadThresholds()
            return
        }

        var filtered = items
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
            filtered = filtered.filter { (it.location ?: "").lowercase().contains(needle) }
        }

        filteredItems = filtered
        filteredOffset = 0
        applyFilteredPage()
    }

    private fun clearSearchFilters() {
        binding.etSearchProductId.setText("")
        binding.etSearchLocation.setText("")
        filteredItems = emptyList()
        filteredOffset = 0
        val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
        adapter.submit(rows)
        updatePageInfo(rows.size)
    }

    private fun loadThresholds(productId: Int? = null, location: String? = null) {
        if (isLoading) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val filtersActive = hasActiveFilters()
                val effectiveLimit = if (filtersActive) 200 else pageSize
                val effectiveOffset = if (filtersActive) 0 else currentOffset
                if (filtersActive) currentOffset = 0
                val cacheKey = CacheKeys.list(
                    "thresholds",
                    mapOf(
                        "product_id" to productId,
                        "location" to location,
                        "limit" to effectiveLimit,
                        "offset" to effectiveOffset
                    )
                )
                val cached = cacheStore.get(cacheKey, ThresholdListResponseDto::class.java)
                if (cached != null) {
                    val pending = buildPendingThresholds()
                    items = pending + cached.items
                    totalCount = cached.total
                    productNameById = fetchProductNames(items.map { it.productId }.toSet())
                    val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
                    adapter.submit(rows)
                    updatePageInfo(rows.size)
                    isLoading = false
                }
                val res = NetworkModule.api.listThresholds(productId = productId, location = location, limit = effectiveLimit, offset = effectiveOffset)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful && res.body() != null) {
                    cacheStore.put(cacheKey, res.body()!!)
                    val pending = buildPendingThresholds()
                    items = pending + res.body()!!.items
                    totalCount = res.body()!!.total
                    productNameById = fetchProductNames(items.map { it.productId }.toSet())
                    val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
                    if (hasActiveFilters() || pendingFilterApply) {
                        applySearchFiltersInternal(allowReload = false)
                    } else {
                        adapter.submit(rows)
                        updatePageInfo(rows.size)
                    }
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code()))
                    val cachedOnError = cacheStore.get(cacheKey, ThresholdListResponseDto::class.java)
                    if (cachedOnError != null) {
                        val pending = buildPendingThresholds()
                        items = pending + cachedOnError.items
                        totalCount = cachedOnError.total
                        productNameById = fetchProductNames(items.map { it.productId }.toSet())
                        val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
                        adapter.submit(rows)
                        updatePageInfo(rows.size)
                        UiNotifier.show(this@ThresholdsActivity, "Mostrando thresholds en cache")
                    }
                }
            } catch (e: Exception) {
                val cacheKey = CacheKeys.list(
                    "thresholds",
                    mapOf(
                        "product_id" to productId,
                        "location" to location,
                        "limit" to if (hasActiveFilters()) 200 else pageSize,
                        "offset" to if (hasActiveFilters()) 0 else currentOffset
                    )
                )
                val cachedOnError = cacheStore.get(cacheKey, ThresholdListResponseDto::class.java)
                if (cachedOnError != null) {
                    val pending = buildPendingThresholds()
                    items = pending + cachedOnError.items
                    totalCount = cachedOnError.total
                    productNameById = fetchProductNames(items.map { it.productId }.toSet())
                    val rows = items.map { ThresholdRowUi(it, productNameById[it.productId]) }
                    adapter.submit(rows)
                    updatePageInfo(rows.size)
                    UiNotifier.show(this@ThresholdsActivity, "Mostrando thresholds en cache")
                } else {
                    val pending = buildPendingThresholds()
                    productNameById = fetchProductNames(pending.map { it.productId }.toSet())
                    val rows = pending.map { ThresholdRowUi(it, productNameById[it.productId]) }
                    adapter.submit(rows)
                    updatePageInfo(rows.size)
                    if (e is IOException) {
                        UiNotifier.show(this@ThresholdsActivity, "Sin conexion a Internet")
                    } else {
                        UiNotifier.show(this@ThresholdsActivity, "Error de red: ${e.message}")
                    }
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

    private fun createThreshold() {
        val productInput = binding.etProductId.text.toString().trim()
        val productId = productInput.toIntOrNull() ?: resolveProductIdByName(productInput)
        val location = normalizeLocationInput(binding.etLocation.text.toString().trim()).ifBlank { null }
        val minQty = binding.etMinQty.text.toString().trim().toIntOrNull()

        if (productId == null) { binding.etProductId.error = "Producto ID o nombre valido"; return }
        if (minQty == null || minQty < 0) { binding.etMinQty.error = "Min >= 0"; return }

        binding.btnCreate.isEnabled = false
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.createThreshold(
                    ThresholdCreateDto(productId = productId, location = location, minQuantity = minQty)
                )
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    binding.etProductId.setText("")
                    binding.etLocation.setText("")
                    binding.etMinQty.setText("")
                    cacheStore.invalidatePrefix("thresholds")
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    val dto = ThresholdCreateDto(productId = productId, location = location, minQuantity = minQty)
                    OfflineQueue(this@ThresholdsActivity).enqueue(PendingType.THRESHOLD_CREATE, gson.toJson(dto))
                    UiNotifier.show(this@ThresholdsActivity, "Sin conexion. Threshold guardado offline")
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, "Error de red: ${e.message}")
                }
            } finally {
                binding.btnCreate.isEnabled = true
            }
        }
    }

    private fun showEditDialog(threshold: ThresholdResponseDto) {
        val inputLocation = android.widget.EditText(this).apply {
            hint = "Ubicacion"
            setText(threshold.location ?: "")
        }
        val inputMin = android.widget.EditText(this).apply {
            hint = "Min quantity"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(threshold.minQuantity.toString())
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(inputLocation)
            addView(inputMin)
            setPadding(32, 16, 32, 0)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar threshold #${threshold.id}")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Eliminar") { _, _ -> deleteThreshold(threshold.id) }
            .setPositiveButton("Guardar") { _, _ ->
                val newLoc = inputLocation.text.toString().trim().ifBlank { null }
                val newMin = inputMin.text.toString().trim().toIntOrNull()
                if (newMin == null || newMin < 0) {
                    UiNotifier.show(this, "Min >= 0")
                } else {
                    updateThreshold(threshold.id, newLoc, newMin)
                }
            }
            .show()
    }

    private fun updateThreshold(id: Int, location: String?, minQty: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.updateThreshold(
                    id,
                    ThresholdUpdateDto(location = location, minQuantity = minQty)
                )
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    cacheStore.invalidatePrefix("thresholds")
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    UiNotifier.show(this@ThresholdsActivity, "Sin conexion a Internet")
                } else {
                    UiNotifier.show(this@ThresholdsActivity, "Error de red: ${e.message}")
                }
            }
        }
    }

    private fun deleteThreshold(id: Int) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.deleteThreshold(id)
                if (res.code() == 401) { session.clearToken(); goToLogin(); return@launch }
                if (res.isSuccessful) {
                    cacheStore.invalidatePrefix("thresholds")
                    loadThresholds()
                } else {
                    UiNotifier.show(this@ThresholdsActivity, ApiErrorFormatter.format(res.code(), res.errorBody()?.string()))
                }
            } catch (e: Exception) {
                if (e is IOException) {
                    UiNotifier.show(this@ThresholdsActivity, "Sin conexion a Internet")
                } else {
                    UiNotifier.show(this@ThresholdsActivity, "Error de red: ${e.message}")
                }
            }
        }
    }

    private fun buildPendingThresholds(): List<ThresholdResponseDto> {
        val pending = OfflineQueue(this).getAll().filter { it.type == PendingType.THRESHOLD_CREATE }
        return pending.mapIndexed { index, p ->
            val dto = runCatching { gson.fromJson(p.payloadJson, ThresholdCreateDto::class.java) }.getOrNull()
            ThresholdResponseDto(
                id = -1 - index,
                productId = dto?.productId ?: 0,
                locationId = null,
                location = dto?.location,
                minQuantity = dto?.minQuantity ?: 0,
                createdAt = "offline",
                updatedAt = null
            )
        }
    }

    private suspend fun fetchProductNames(ids: Set<Int>): Map<Int, String> {
        val out = mutableMapOf<Int, String>()
        ids.forEach { id ->
            try {
                val res = NetworkModule.api.getProduct(id)
                if (res.isSuccessful && res.body() != null) {
                    out[id] = res.body()!!.name
                }
            } catch (_: Exception) {
                // Keep fallback labels if lookup fails.
            }
        }
        return out
    }

    private fun resolveProductIdByName(input: String): Int? {
        val needle = input.trim().lowercase()
        if (needle.isBlank()) return null
        val match = productNameById.entries.firstOrNull { it.value.lowercase() == needle }
        return match?.key
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun hasActiveFilters(): Boolean {
        return binding.etSearchProductId.text?.isNotBlank() == true ||
            binding.etSearchLocation.text?.isNotBlank() == true
    }

    private fun applyFilteredPage() {
        val from = filteredOffset.coerceAtLeast(0)
        val to = (filteredOffset + pageSize).coerceAtMost(filteredItems.size)
        val page = if (from < to) filteredItems.subList(from, to) else emptyList()
        val rows = page.map { ThresholdRowUi(it, productNameById[it.productId]) }
        adapter.submit(rows)
        updatePageInfo(rows.size)
    }

    private fun updatePageInfo(pageSizeLoaded: Int) {
        if (hasActiveFilters()) {
            val shown = (filteredOffset + pageSizeLoaded).coerceAtMost(filteredItems.size)
            binding.tvThresholdPageInfo.text = "Mostrando $shown/${filteredItems.size}"
            val prevEnabled = filteredOffset > 0
            val nextEnabled = shown < filteredItems.size
            binding.btnPrevThresholdPage.isEnabled = prevEnabled
            binding.btnNextThresholdPage.isEnabled = nextEnabled
            applyPagerButtonStyle(binding.btnPrevThresholdPage, prevEnabled)
            applyPagerButtonStyle(binding.btnNextThresholdPage, nextEnabled)
            return
        }
        val shown = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        val label = if (totalCount > 0) "Mostrando $shown/$totalCount" else "Mostrando ${pageSizeLoaded}/${pageSizeLoaded}"
        binding.tvThresholdPageInfo.text = label
        val prevEnabled = currentOffset > 0
        val nextEnabled = shown < totalCount
        binding.btnPrevThresholdPage.isEnabled = prevEnabled
        binding.btnNextThresholdPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevThresholdPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextThresholdPage, nextEnabled)
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

    private fun toggleCreateForm() {
        TransitionManager.beginDelayedTransition(binding.scrollThresholds, AutoTransition().setDuration(180))
        val isVisible = binding.layoutCreateThresholdContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutCreateThresholdContent.visibility = View.GONE
            binding.layoutSearchThresholdContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutCreateThresholdContent.visibility = View.VISIBLE
            binding.layoutSearchThresholdContent.visibility = View.GONE
            setToggleActive(binding.layoutCreateThresholdHeader)
        }
    }

    private fun toggleSearchForm() {
        TransitionManager.beginDelayedTransition(binding.scrollThresholds, AutoTransition().setDuration(180))
        val isVisible = binding.layoutSearchThresholdContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutSearchThresholdContent.visibility = View.GONE
            binding.layoutCreateThresholdContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutSearchThresholdContent.visibility = View.VISIBLE
            binding.layoutCreateThresholdContent.visibility = View.GONE
            setToggleActive(binding.layoutSearchThresholdHeader)
        }
    }

    private fun setToggleActive(active: View?) {
        if (active === binding.layoutCreateThresholdHeader) {
            binding.layoutCreateThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_active)
            binding.layoutSearchThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        } else if (active === binding.layoutSearchThresholdHeader) {
            binding.layoutCreateThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_active)
        } else {
            binding.layoutCreateThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
            binding.layoutSearchThresholdHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
        }
    }

    private fun setupLocationDropdowns() {
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
                    val adapter = ArrayAdapter(this@ThresholdsActivity, android.R.layout.simple_list_item_1, allValues)
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
                val adapter = ArrayAdapter(this@ThresholdsActivity, android.R.layout.simple_list_item_1, allValues)
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
        }
    }

    private fun applyThresholdDropdownIcons() {
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

    private fun applyThresholdTitleGradient() {
        binding.tvThresholdsTitle.post {
            val paint = binding.tvThresholdsTitle.paint
            val width = paint.measureText(binding.tvThresholdsTitle.text.toString())
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
            binding.tvThresholdsTitle.invalidate()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun goToLogin() {
        if (!session.isTokenExpired()) return
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
