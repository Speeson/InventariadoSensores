package com.example.inventoryapp.ui.rotation
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.cache.CacheStore
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementTypeDto
import com.example.inventoryapp.databinding.ActivityRotationBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.TopCenterActionHost
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.common.GradientIconUtil
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import java.io.IOException
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RotationActivity : AppCompatActivity(), TopCenterActionHost {
    companion object {
        @Volatile
        private var cacheNoticeShownInOfflineSession = false
    }

    private lateinit var binding: ActivityRotationBinding
    private lateinit var snack: SendSnack
    private lateinit var adapter: RotationAdapter
    private lateinit var cacheStore: CacheStore

    private val pageSize = 5
    private var currentPage = 0
    private var allRows: List<RotationRow> = emptyList()
    private var filteredRows: List<RotationRow> = emptyList()
    private val rotationCacheKey = "rotation:rows:v1"
    private var searchDialog: AlertDialog? = null
    private var locationDropdownValues: List<String> = listOf("")
    private var searchMovementIdFilter: String = ""
    private var searchProductFilter: String = ""
    private var searchFromLocationFilter: String = ""
    private var searchToLocationFilter: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRotationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        binding.btnRefreshRotation.setImageResource(R.drawable.glass_refresh)
        applyRotationTitleGradient()
        applyRefreshIconTint()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        snack = SendSnack(binding.root)
        cacheStore = CacheStore.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = RotationAdapter(emptyList())
        binding.rvRotation.layoutManager = LinearLayoutManager(this)
        binding.rvRotation.adapter = adapter
        lifecycleScope.launch {
            NetworkModule.offlineState.collectLatest { offline ->
                if (!offline) {
                    cacheNoticeShownInOfflineSession = false
                }
            }
        }

        binding.btnRefreshRotation.setOnClickListener { loadRotation(withSnack = true) }

        binding.btnPrevRotationPage.setOnClickListener {
            if (currentPage <= 0) return@setOnClickListener
            currentPage -= 1
            updatePage()
            binding.rvRotation.scrollToPosition(0)
        }
        binding.btnNextRotationPage.setOnClickListener {
            val total = filteredRows.size
            val nextStart = (currentPage + 1) * pageSize
            if (nextStart >= total) return@setOnClickListener
            currentPage += 1
            updatePage()
            binding.rvRotation.scrollToPosition(0)
        }

        loadLocationOptions()

        applyPagerButtonStyle(binding.btnPrevRotationPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextRotationPage, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        ensureRotationAccessThenLoad()
        updateRotationListAdaptiveHeight()
    }

    override fun onTopCreateAction() {
        UiNotifier.showBlockingTimed(
            this,
            "Crear no disponible en Traslados",
            R.drawable.ic_lock,
            timeoutMs = 2200L
        )
    }

    override fun onTopFilterAction() {
        openSearchRotationDialog()
    }

    private fun ensureRotationAccessThenLoad() {
        loadRotation(withSnack = false)
    }

    private fun loadRotation(withSnack: Boolean) {
        var postLoadingNotice: (() -> Unit)? = null
        val loading = if (withSnack) {
            CreateUiFeedback.showListLoading(
                this,
                message = "Cargando traslados",
                animationRes = R.raw.glass_loading_list,
                minCycles = 2
            )
        } else {
            null
        }

        lifecycleScope.launch {
            try {
                val movRes = NetworkModule.api.listMovements(limit = 100, offset = 0)
                val prodRes = NetworkModule.api.listProducts(limit = 100, offset = 0)

                if (movRes.code() == 401 || prodRes.code() == 401) return@launch

                if (movRes.code() == 403 || prodRes.code() == 403) {
                    UiNotifier.showBlocking(
                        this@RotationActivity,
                        "Permisos insuficientes",
                        "No tienes permisos para acceder a esta secci?n.",
                        R.drawable.ic_lock
                    )
                    return@launch
                }

                if (!movRes.isSuccessful || movRes.body() == null) {
                    snack.showError("Error movimientos: HTTP ${movRes.code()}")
                    return@launch
                }
                if (!prodRes.isSuccessful || prodRes.body() == null) {
                    snack.showError("Error productos: HTTP ${prodRes.code()}")
                    return@launch
                }

                val products = prodRes.body()!!.items.associateBy { it.id }
                val movements = movRes.body()!!.items

                val grouped = movements
                    .filter { !it.transferId.isNullOrBlank() }
                    .groupBy { it.transferId!! }

                val rows = grouped.values.mapNotNull { group ->
                    val outMov = group.firstOrNull { it.movementType == MovementTypeDto.OUT }
                    val inMov = group.firstOrNull { it.movementType == MovementTypeDto.IN }
                    val base = outMov ?: inMov ?: return@mapNotNull null
                    val product = products[base.productId]

                    RotationRow(
                        movementId = base.id,
                        productId = base.productId,
                        sku = product?.sku ?: "SKU-${base.productId}",
                        name = product?.name ?: "Producto ${base.productId}",
                        quantity = outMov?.quantity ?: inMov?.quantity ?: base.quantity,
                        fromLocation = outMov?.location ?: "N/A",
                        toLocation = inMov?.location ?: "N/A",
                        createdAt = base.createdAt
                    )
                }.sortedByDescending { it.createdAt }

                cacheStore.put(rotationCacheKey, RotationCachePayload(rows))
                allRows = rows
                applySearchFilters(resetPage = true)
                if (withSnack) {
                    postLoadingNotice = {
                        CreateUiFeedback.showStatusPopup(
                            activity = this@RotationActivity,
                            title = "Traslados cargados",
                            details = "Se han cargado correctamente.",
                            animationRes = R.raw.correct_create,
                            autoDismissMs = 2500L
                        )
                    }
                }

            } catch (e: Exception) {
                val cachedRows = loadCachedRotationRows()
                if (e is IOException) {
                    if (cachedRows.isNotEmpty() || allRows.isNotEmpty()) {
                        if (cachedRows.isNotEmpty()) {
                            allRows = cachedRows
                            applySearchFilters(resetPage = true)
                        }
                        if (withSnack && !cacheNoticeShownInOfflineSession) {
                            postLoadingNotice = { showRotationCacheNoticeOnce() }
                        } else {
                            showRotationCacheNoticeOnce()
                        }
                    } else {
                        UiNotifier.showBlockingTimed(
                            this@RotationActivity,
                            "Sin conexiÃ³n. No hay traslados en cache.",
                            R.drawable.offline,
                            timeoutMs = 3_200L
                        )
                    }
                } else if (cachedRows.isNotEmpty() || allRows.isNotEmpty()) {
                    if (cachedRows.isNotEmpty()) {
                        allRows = cachedRows
                        applySearchFilters(resetPage = true)
                    }
                    if (withSnack && !cacheNoticeShownInOfflineSession) {
                        postLoadingNotice = { showRotationCacheNoticeOnce() }
                    } else {
                        showRotationCacheNoticeOnce()
                    }
                } else {
                    snack.showError(UiNotifier.buildConnectionMessage(this@RotationActivity, e.message))
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
            }
        }
    }

    private suspend fun loadCachedRotationRows(): List<RotationRow> {
        val cached = cacheStore.get(rotationCacheKey, RotationCachePayload::class.java)
        return cached?.items.orEmpty().sortedByDescending { it.createdAt }
    }

    private fun showRotationCacheNoticeOnce() {
        if (cacheNoticeShownInOfflineSession) return
        UiNotifier.showBlockingTimed(
            this,
            "Mostrando traslados en cache y pendientes offline",
            R.drawable.sync,
            timeoutMs = 3_200L
        )
        cacheNoticeShownInOfflineSession = true
    }

    private fun applySearchFilters(resetPage: Boolean = true, showNotFoundDialog: Boolean = false) {
        val movementIdRaw = searchMovementIdFilter.trim()
        val productRaw = searchProductFilter.trim()
        val fromRaw = normalizeLocationInput(searchFromLocationFilter.trim())
        val toRaw = normalizeLocationInput(searchToLocationFilter.trim())

        var filtered = allRows

        if (movementIdRaw.isNotBlank()) {
            val movementId = movementIdRaw.toIntOrNull()
            filtered = if (movementId != null) {
                filtered.filter { it.movementId == movementId }
            } else {
                emptyList()
            }
        }

        if (productRaw.isNotBlank()) {
            val productId = productRaw.toIntOrNull()
            filtered = if (productId != null) {
                filtered.filter { it.productId == productId }
            } else {
                val needle = productRaw.lowercase()
                filtered.filter { it.name.lowercase().contains(needle) }
            }
        }

        if (fromRaw.isNotBlank()) {
            val needle = fromRaw.lowercase()
            filtered = filtered.filter { it.fromLocation.lowercase().contains(needle) }
        }

        if (toRaw.isNotBlank()) {
            val needle = toRaw.lowercase()
            filtered = filtered.filter { it.toLocation.lowercase().contains(needle) }
        }

        filteredRows = filtered
        if (resetPage) currentPage = 0
        updatePage()
        val hasFilters =
            movementIdRaw.isNotBlank() || productRaw.isNotBlank() || fromRaw.isNotBlank() || toRaw.isNotBlank()
        if (showNotFoundDialog && hasFilters && filtered.isEmpty()) {
            CreateUiFeedback.showErrorPopup(
                activity = this,
                title = "No se encontraron traslados",
                details = buildRotationSearchNotFoundDetails(movementIdRaw, productRaw, fromRaw, toRaw),
                animationRes = R.raw.notfound
            )
        }
    }

    private fun clearSearchFilters() {
        searchMovementIdFilter = ""
        searchProductFilter = ""
        searchFromLocationFilter = ""
        searchToLocationFilter = ""
        filteredRows = allRows
        currentPage = 0
        updatePage()
    }

    private fun buildRotationSearchNotFoundDetails(
        movementIdRaw: String,
        productRaw: String,
        fromRaw: String,
        toRaw: String
    ): String {
        val parts = mutableListOf<String>()
        if (movementIdRaw.isNotBlank()) parts.add("movimiento ID $movementIdRaw")
        if (productRaw.isNotBlank()) {
            val productLabel = if (productRaw.toIntOrNull() != null) {
                "producto ID $productRaw"
            } else {
                "producto \"$productRaw\""
            }
            parts.add(productLabel)
        }
        if (fromRaw.isNotBlank()) parts.add("origen \"$fromRaw\"")
        if (toRaw.isNotBlank()) parts.add("destino \"$toRaw\"")
        return if (parts.isEmpty()) {
            "No se encontraron traslados con los filtros actuales."
        } else {
            "No se encontraron traslados para ${parts.joinToString(separator = ", ")}."
        }
    }

    private fun updatePage() {
        val total = filteredRows.size
        val start = currentPage * pageSize
        val end = (start + pageSize).coerceAtMost(total)
        val page = if (start < end) filteredRows.subList(start, end) else emptyList()
        adapter.submit(page)

        val shown = if (total == 0) 0 else end
        val currentPageLabel = if (total == 0) 0 else currentPage + 1
        val totalPages = if (total == 0) 0 else ((total + pageSize - 1) / pageSize)
        binding.tvRotationPageNumber.text = "Pagina $currentPageLabel/$totalPages"
        binding.tvRotationPageInfo.text = "Mostrando $shown/$total"
        val prevEnabled = currentPage > 0
        val nextEnabled = end < total
        binding.btnPrevRotationPage.isEnabled = prevEnabled
        binding.btnNextRotationPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevRotationPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextRotationPage, nextEnabled)
        updateRotationListAdaptiveHeight()
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
            binding.btnRefreshRotation.setColorFilter(blue)
        } else {
            binding.btnRefreshRotation.clearColorFilter()
        }
    }

    private fun loadLocationOptions() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    val values = items.sortedBy { it.id }
                        .map { "(${it.id}) ${it.code}" }
                        .distinct()
                    locationDropdownValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    return@launch
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun bindLocationDropdown(auto: MaterialAutoCompleteTextView) {
        val adapter = ArrayAdapter(this, R.layout.item_liquid_dropdown, locationDropdownValues)
        auto.setAdapter(adapter)
        auto.setOnClickListener { auto.showDropDown() }
        auto.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) auto.showDropDown() }
    }

    private fun openSearchRotationDialog() {
        if (searchDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_rotation_search_master, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnSearchRotationDialogClose)
        val btnSearch = view.findViewById<Button>(R.id.btnDialogSearchRotation)
        val btnClear = view.findViewById<Button>(R.id.btnDialogClearSearchRotation)
        val etMovementId = view.findViewById<TextInputEditText>(R.id.etDialogSearchMovementId)
        val etProduct = view.findViewById<TextInputEditText>(R.id.etDialogSearchProduct)
        val etFrom = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogSearchFromLocation)
        val etTo = view.findViewById<MaterialAutoCompleteTextView>(R.id.etDialogSearchToLocation)
        val tilFrom = view.findViewById<TextInputLayout>(R.id.tilDialogSearchFromLocation)
        val tilTo = view.findViewById<TextInputLayout>(R.id.tilDialogSearchToLocation)

        etMovementId.setText(searchMovementIdFilter)
        etProduct.setText(searchProductFilter)
        etFrom.setText(searchFromLocationFilter, false)
        etTo.setText(searchToLocationFilter, false)

        bindLocationDropdown(etFrom)
        bindLocationDropdown(etTo)
        applyDialogDropdownStyle(listOf(tilFrom, tilTo), listOf(etFrom, etTo))

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnSearch.setOnClickListener {
            searchMovementIdFilter = etMovementId.text?.toString().orEmpty().trim()
            searchProductFilter = etProduct.text?.toString().orEmpty().trim()
            searchFromLocationFilter = etFrom.text?.toString().orEmpty().trim()
            searchToLocationFilter = etTo.text?.toString().orEmpty().trim()
            hideKeyboard()
            dialog.dismiss()
            applySearchFilters(showNotFoundDialog = true)
        }
        btnClear.setOnClickListener {
            etMovementId.setText("")
            etProduct.setText("")
            etFrom.setText("", false)
            etTo.setText("", false)
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

    private fun updateRotationListAdaptiveHeight() {
        binding.scrollRotation.post {
            val topSpacerLp = binding.viewRotationTopSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val bottomSpacerLp = binding.viewRotationBottomSpacer.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val cardLp = binding.cardRotationList.layoutParams as? LinearLayout.LayoutParams ?: return@post
            val rvLp = binding.rvRotation.layoutParams as? LinearLayout.LayoutParams ?: return@post

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
                binding.rvRotation.isNestedScrollingEnabled = false
            } else {
                topSpacerLp.height = 0
                topSpacerLp.weight = 0f
                bottomSpacerLp.height = 0
                bottomSpacerLp.weight = 0f
                cardLp.height = 0
                cardLp.weight = 1f
                rvLp.height = 0
                rvLp.weight = 1f
                binding.rvRotation.isNestedScrollingEnabled = true
            }
            binding.viewRotationTopSpacer.layoutParams = topSpacerLp
            binding.viewRotationBottomSpacer.layoutParams = bottomSpacerLp
            binding.cardRotationList.layoutParams = cardLp
            binding.rvRotation.layoutParams = rvLp
        }
    }

    private fun normalizeLocationInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("(") && trimmed.contains(") ")) {
            return trimmed.substringAfter(") ").trim()
        }
        return trimmed
    }

    private fun applyRotationTitleGradient() {
        binding.tvRotationTitle.post {
            val paint = binding.tvRotationTitle.paint
            val width = paint.measureText(binding.tvRotationTitle.text.toString())
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
            binding.tvRotationTitle.invalidate()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val view = currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

}

data class RotationCachePayload(
    val items: List<RotationRow>
)
