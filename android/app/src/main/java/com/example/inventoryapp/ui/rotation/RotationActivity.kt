package com.example.inventoryapp.ui.rotation
import com.example.inventoryapp.ui.common.AlertsBadgeUtil
import com.example.inventoryapp.R

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.MovementTypeDto
import com.example.inventoryapp.databinding.ActivityRotationBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.UiNotifier
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.common.GradientIconUtil
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import android.view.View
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import android.view.inputmethod.InputMethodManager

class RotationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRotationBinding
    private lateinit var session: SessionManager
    private lateinit var snack: SendSnack
    private lateinit var adapter: RotationAdapter

    private val pageSize = 5
    private var currentPage = 0
    private var allRows: List<RotationRow> = emptyList()
    private var filteredRows: List<RotationRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRotationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivSearchRotation, R.drawable.search)
        applyRotationTitleGradient()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        snack = SendSnack(binding.root)
        session = SessionManager(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        adapter = RotationAdapter(emptyList())
        binding.rvRotation.layoutManager = LinearLayoutManager(this)
        binding.rvRotation.adapter = adapter

        binding.btnRefreshRotation.setOnClickListener { loadRotation(withSnack = true) }
        binding.layoutSearchRotationHeader.setOnClickListener { toggleSearchForm() }

        binding.btnSearchRotation.setOnClickListener {
            hideKeyboard()
            applySearchFilters()
        }
        binding.btnClearSearchRotation.setOnClickListener {
            hideKeyboard()
            clearSearchFilters()
        }

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

        setupLocationDropdowns()
        binding.tilSearchFromLocation.post { applyRotationDropdownIcons() }

        applyPagerButtonStyle(binding.btnPrevRotationPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextRotationPage, enabled = false)
    }

    override fun onResume() {
        super.onResume()
        ensureRotationAccessThenLoad()
    }

    private fun ensureRotationAccessThenLoad() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.me()
                if (res.code() == 401) {
                    session.clearToken()
                    goToLogin()
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    val role = res.body()!!.role
                    if (role == "MANAGER" || role == "ADMIN") {
                        loadRotation(withSnack = false)
                    } else {
                        com.example.inventoryapp.ui.common.UiNotifier.showBlocking(
                            this@RotationActivity,
                            "Permisos insuficientes",
                            "No tienes permisos para acceder a esta secci�n.",
                            com.example.inventoryapp.R.drawable.ic_lock
                        )
                        finish()
                    }
                } else {
                    snack.showError("No se pudo validar permisos (${res.code()}).")
                    finish()
                }
            } catch (e: Exception) {
                snack.showError(UiNotifier.buildConnectionMessage(this@RotationActivity, e.message))
                finish()
            }
        }
    }

    private fun loadRotation(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando rotaci�n...")

        lifecycleScope.launch {
            try {
                val movRes = NetworkModule.api.listMovements(limit = 100, offset = 0)
                val prodRes = NetworkModule.api.listProducts(limit = 100, offset = 0)

                if (movRes.code() == 401 || prodRes.code() == 401) {
                    session.clearToken()
                    goToLogin()
                    return@launch
                }

                if (movRes.code() == 403 || prodRes.code() == 403) {
                    com.example.inventoryapp.ui.common.UiNotifier.showBlocking(
                        this@RotationActivity,
                        "Permisos insuficientes",
                        "No tienes permisos para acceder a esta secci�n.",
                        com.example.inventoryapp.R.drawable.ic_lock
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

                allRows = rows
                applySearchFilters(resetPage = true)
                if (withSnack) snack.showSuccess("OK: Rotaci�n cargada")

            } catch (e: Exception) {
                snack.showError(UiNotifier.buildConnectionMessage(this@RotationActivity, e.message))
            }
        }
    }

    private fun applySearchFilters(resetPage: Boolean = true) {
        val movementIdRaw = binding.etSearchMovementId.text.toString().trim()
        val productRaw = binding.etSearchProduct.text.toString().trim()
        val fromRaw = normalizeLocationInput(binding.etSearchFromLocation.text.toString().trim())
        val toRaw = normalizeLocationInput(binding.etSearchToLocation.text.toString().trim())

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
    }

    private fun clearSearchFilters() {
        binding.etSearchMovementId.setText("")
        binding.etSearchProduct.setText("")
        binding.etSearchFromLocation.setText("")
        binding.etSearchToLocation.setText("")
        filteredRows = allRows
        currentPage = 0
        updatePage()
    }

    private fun updatePage() {
        val total = filteredRows.size
        val start = currentPage * pageSize
        val end = (start + pageSize).coerceAtMost(total)
        val page = if (start < end) filteredRows.subList(start, end) else emptyList()
        adapter.submit(page)

        val shown = if (total == 0) 0 else end
        binding.tvRotationPageInfo.text = "Mostrando $shown/$total"
        val prevEnabled = currentPage > 0
        val nextEnabled = end < total
        binding.btnPrevRotationPage.isEnabled = prevEnabled
        binding.btnNextRotationPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevRotationPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextRotationPage, nextEnabled)
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

    private fun setupLocationDropdowns() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.listLocations(limit = 200, offset = 0)
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    val values = items.sortedBy { it.id }
                        .map { "(${it.id}) ${it.code}" }
                        .distinct()
                    val allValues = listOf("") + if (values.any { it.contains(") default") }) values else listOf("(0) default") + values
                    val adapter = ArrayAdapter(this@RotationActivity, android.R.layout.simple_list_item_1, allValues)
                    binding.etSearchFromLocation.setAdapter(adapter)
                    binding.etSearchFromLocation.setOnClickListener { binding.etSearchFromLocation.showDropDown() }
                    binding.etSearchFromLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etSearchFromLocation.showDropDown()
                    }
                    binding.etSearchToLocation.setAdapter(adapter)
                    binding.etSearchToLocation.setOnClickListener { binding.etSearchToLocation.showDropDown() }
                    binding.etSearchToLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etSearchToLocation.showDropDown()
                    }
                }
            } catch (_: Exception) {
                // Silent fallback to manual input.
            }
        }
    }

    private fun applyRotationDropdownIcons() {
        binding.tilSearchFromLocation.setEndIconTintList(null)
        binding.tilSearchToLocation.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilSearchFromLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
        binding.tilSearchToLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
    }

    private fun toggleSearchForm() {
        TransitionManager.beginDelayedTransition(binding.scrollRotation, AutoTransition().setDuration(180))
        val isVisible = binding.layoutSearchRotationContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutSearchRotationContent.visibility = View.GONE
            setToggleActive(false)
        } else {
            binding.layoutSearchRotationContent.visibility = View.VISIBLE
            setToggleActive(true)
        }
    }

    private fun setToggleActive(active: Boolean) {
        if (active) {
            binding.layoutSearchRotationHeader.setBackgroundResource(R.drawable.bg_toggle_active)
        } else {
            binding.layoutSearchRotationHeader.setBackgroundResource(R.drawable.bg_toggle_idle)
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

    private fun goToLogin() {
        if (!session.isTokenExpired()) return
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}

