package com.example.inventoryapp.ui.reports
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import java.util.Calendar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.example.inventoryapp.databinding.ActivityReportsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.ui.common.CreateUiFeedback
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.view.Gravity
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private var topConsumedDialog: AlertDialog? = null
    private var turnoverDialog: AlertDialog? = null
    private val reportPageSize = 5
    private var topOffset = 0
    private var topTotalCount = 0
    private var turnOffset = 0
    private var turnTotalCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        GradientIconUtil.applyGradient(binding.ivTopChevron, R.drawable.triangle_down_lg)
        GradientIconUtil.applyGradient(binding.ivTurnChevron, R.drawable.triangle_down_lg)
        applyReportsTitleGradient()
        applyHeaderGradients()

        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.layoutTopHeader.setOnClickListener { toggleTopSection() }
        binding.layoutTurnHeader.setOnClickListener { toggleTurnSection() }

        binding.btnTopConsumedSearch.setOnClickListener {
            val dateFrom = binding.etTopDateFrom.text.toString().trim().ifBlank { null }
            val dateTo = binding.etTopDateTo.text.toString().trim().ifBlank { null }
            val location = binding.etTopLocation.text.toString().trim().ifBlank { null }
            openTopConsumedResultsPopup(dateFrom, dateTo, location)
        }

        binding.btnTurnoverSearch.setOnClickListener {
            val dateFrom = binding.etTurnDateFrom.text.toString().trim().ifBlank { null }
            val dateTo = binding.etTurnDateTo.text.toString().trim().ifBlank { null }
            val location = binding.etTurnLocation.text.toString().trim().ifBlank { null }
            openTurnoverResultsPopup(dateFrom, dateTo, location)
        }

        setupDatePickers()
        setupLocationDropdowns()
        binding.tilTopLocation.post { applyLocationDropdownIcons() }
    }

    private fun setupDatePickers() {
        bindDateField(binding.etTopDateFrom)
        bindDateField(binding.etTopDateTo)
        bindDateField(binding.etTurnDateFrom)
        bindDateField(binding.etTurnDateTo)
    }

    private fun bindDateField(field: android.widget.EditText) {
        field.setOnClickListener { showDatePicker(field) }
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showDatePicker(field)
        }
    }

    private fun showDatePicker(target: android.widget.EditText) {
        val cal = Calendar.getInstance()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val mm = String.format("%02d", month + 1)
                val dd = String.format("%02d", dayOfMonth)
                target.setText("$year-$mm-$dd")
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Hoy") { _, _ ->
            val now = Calendar.getInstance()
            val mm = String.format("%02d", now.get(Calendar.MONTH) + 1)
            val dd = String.format("%02d", now.get(Calendar.DAY_OF_MONTH))
            target.setText("${now.get(Calendar.YEAR)}-$mm-$dd")
        }
        dialog.show()
    }

    private fun toggleTopSection() {
        TransitionManager.beginDelayedTransition(binding.scrollReports, AutoTransition().setDuration(180))
        val isVisible = binding.layoutTopContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutTopContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutTopContent.visibility = View.VISIBLE
            binding.layoutTurnContent.visibility = View.GONE
            setToggleActive(binding.layoutTopHeader)
        }
    }

    private fun toggleTurnSection() {
        TransitionManager.beginDelayedTransition(binding.scrollReports, AutoTransition().setDuration(180))
        val isVisible = binding.layoutTurnContent.visibility == View.VISIBLE
        if (isVisible) {
            binding.layoutTurnContent.visibility = View.GONE
            setToggleActive(null)
        } else {
            binding.layoutTurnContent.visibility = View.VISIBLE
            binding.layoutTopContent.visibility = View.GONE
            setToggleActive(binding.layoutTurnHeader)
        }
    }

    private fun setToggleActive(active: View?) {
        if (active === binding.layoutTopHeader) {
            binding.layoutTopHeader.setBackgroundResource(R.drawable.bg_liquid_button_pressed)
            binding.layoutTurnHeader.setBackgroundResource(R.drawable.bg_liquid_button)
        } else if (active === binding.layoutTurnHeader) {
            binding.layoutTopHeader.setBackgroundResource(R.drawable.bg_liquid_button)
            binding.layoutTurnHeader.setBackgroundResource(R.drawable.bg_liquid_button_pressed)
        } else {
            binding.layoutTopHeader.setBackgroundResource(R.drawable.bg_liquid_button)
            binding.layoutTurnHeader.setBackgroundResource(R.drawable.bg_liquid_button)
        }
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
                    val adapter = ArrayAdapter(this@ReportsActivity, R.layout.item_liquid_dropdown, allValues)
                    binding.etTopLocation.setAdapter(adapter)
                    binding.etTopLocation.setOnClickListener { binding.etTopLocation.showDropDown() }
                    binding.etTopLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etTopLocation.showDropDown()
                    }
                    binding.etTurnLocation.setAdapter(adapter)
                    binding.etTurnLocation.setOnClickListener { binding.etTurnLocation.showDropDown() }
                    binding.etTurnLocation.setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) binding.etTurnLocation.showDropDown()
                    }
                    val popupDrawable = ContextCompat.getDrawable(this@ReportsActivity, R.drawable.bg_liquid_dropdown_popup)
                    if (popupDrawable != null) {
                        binding.etTopLocation.setDropDownBackgroundDrawable(popupDrawable)
                        binding.etTurnLocation.setDropDownBackgroundDrawable(popupDrawable)
                    }
                }
            } catch (_: Exception) {
                // Silent fallback to manual input.
            }
        }
    }

    private fun applyLocationDropdownIcons() {
        binding.tilTopLocation.setEndIconTintList(null)
        binding.tilTurnLocation.setEndIconTintList(null)
        val endIconId = com.google.android.material.R.id.text_input_end_icon
        binding.tilTopLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
        binding.tilTurnLocation.findViewById<android.widget.ImageView>(endIconId)?.let { iv ->
            GradientIconUtil.applyGradient(iv, R.drawable.triangle_down_lg)
        }
    }

    private fun applyReportsTitleGradient() {
        binding.tvReportsTitle.post {
            val paint = binding.tvReportsTitle.paint
            val width = paint.measureText(binding.tvReportsTitle.text.toString())
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
            binding.tvReportsTitle.invalidate()
        }
    }

    private fun applyHeaderGradients() {
        val blue = ContextCompat.getColor(this, R.color.liquid_header_text_blue)
        binding.tvTopHeader.paint.shader = null
        binding.tvTurnHeader.paint.shader = null
        binding.tvTopHeader.setTextColor(blue)
        binding.tvTurnHeader.setTextColor(blue)
    }

    private fun openTopConsumedResultsPopup(
        dateFrom: String?,
        dateTo: String?,
        location: String?
    ) {
        if (topConsumedDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reports_top_consumed_results, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnDialogTopResultClose)
        val btnRefresh = view.findViewById<ImageButton>(R.id.btnDialogTopRefresh)
        val btnPrev = view.findViewById<Button>(R.id.btnDialogTopPrevPage)
        val btnNext = view.findViewById<Button>(R.id.btnDialogTopNextPage)
        val tvPageNumber = view.findViewById<TextView>(R.id.tvDialogTopPageNumber)
        val tvPageInfo = view.findViewById<TextView>(R.id.tvDialogTopPageInfo)
        val rv = view.findViewById<RecyclerView>(R.id.rvDialogTopConsumed)

        val adapter = TopConsumedAdapter(emptyList(), location)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        topOffset = 0
        topTotalCount = 0
        applyPagerButtonStyle(btnPrev, enabled = false)
        applyPagerButtonStyle(btnNext, enabled = false)
        applyRefreshIconTint(btnRefresh)

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnRefresh.setOnClickListener {
            topOffset = 0
            loadTopConsumedPage(adapter, tvPageNumber, tvPageInfo, btnPrev, btnNext, dateFrom, dateTo, location, withSnack = true)
        }
        btnPrev.setOnClickListener {
            if (topOffset <= 0) return@setOnClickListener
            topOffset = (topOffset - reportPageSize).coerceAtLeast(0)
            loadTopConsumedPage(adapter, tvPageNumber, tvPageInfo, btnPrev, btnNext, dateFrom, dateTo, location)
            rv.scrollToPosition(0)
        }
        btnNext.setOnClickListener {
            val shown = (topOffset + reportPageSize).coerceAtMost(topTotalCount)
            if (shown >= topTotalCount) return@setOnClickListener
            topOffset += reportPageSize
            loadTopConsumedPage(adapter, tvPageNumber, tvPageInfo, btnPrev, btnNext, dateFrom, dateTo, location)
            rv.scrollToPosition(0)
        }

        dialog.setOnShowListener { fitDialogToScreen(dialog) }
        dialog.setOnDismissListener { topConsumedDialog = null }
        topConsumedDialog = dialog
        dialog.show()
        loadTopConsumedPage(adapter, tvPageNumber, tvPageInfo, btnPrev, btnNext, dateFrom, dateTo, location)
    }

    private fun loadTopConsumedPage(
        adapter: TopConsumedAdapter,
        tvPageNumber: TextView,
        tvPageInfo: TextView,
        btnPrev: Button,
        btnNext: Button,
        dateFrom: String?,
        dateTo: String?,
        location: String?,
        withSnack: Boolean = false
    ) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getTopConsumedReport(
                    limit = reportPageSize,
                    offset = topOffset,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    location = location
                )
                if (res.isSuccessful && res.body() != null) {
                    val items = res.body()!!.items
                    adapter.submit(items)
                    topTotalCount = res.body()!!.total
                    updateTopPageInfo(tvPageNumber, tvPageInfo, btnPrev, btnNext, items.size)
                    if (withSnack) {
                        CreateUiFeedback.showStatusPopup(
                            activity = this@ReportsActivity,
                            title = "Top consumidos cargados",
                            details = "Se han cargado correctamente.",
                            animationRes = R.raw.correct_create,
                            autoDismissMs = 1800L
                        )
                    }
                } else {
                    Toast.makeText(this@ReportsActivity, "Error reporte: HTTP ${res.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReportsActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTopPageInfo(
        tvPageNumber: TextView,
        tvPageInfo: TextView,
        btnPrev: Button,
        btnNext: Button,
        pageSizeLoaded: Int
    ) {
        val shown = (topOffset + pageSizeLoaded).coerceAtMost(topTotalCount)
        val currentPage = if (topTotalCount <= 0) 0 else (topOffset / reportPageSize) + 1
        val totalPages = if (topTotalCount <= 0) 0 else ((topTotalCount + reportPageSize - 1) / reportPageSize)
        tvPageNumber.text = "Pagina $currentPage/$totalPages"
        tvPageInfo.text = "Mostrando $shown/$topTotalCount"
        val prevEnabled = topOffset > 0
        val nextEnabled = shown < topTotalCount
        btnPrev.isEnabled = prevEnabled
        btnNext.isEnabled = nextEnabled
        applyPagerButtonStyle(btnPrev, prevEnabled)
        applyPagerButtonStyle(btnNext, nextEnabled)
    }

    private fun openTurnoverResultsPopup(
        dateFrom: String?,
        dateTo: String?,
        location: String?
    ) {
        if (turnoverDialog?.isShowing == true) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reports_turnover_results, null)
        val btnClose = view.findViewById<ImageButton>(R.id.btnDialogTurnResultClose)
        val btnRefresh = view.findViewById<ImageButton>(R.id.btnDialogTurnRefresh)
        val btnPrev = view.findViewById<Button>(R.id.btnDialogTurnPrevPage)
        val btnNext = view.findViewById<Button>(R.id.btnDialogTurnNextPage)
        val tvPageNumber = view.findViewById<TextView>(R.id.tvDialogTurnPageNumber)
        val tvPageInfo = view.findViewById<TextView>(R.id.tvDialogTurnPageInfo)
        val rv = view.findViewById<RecyclerView>(R.id.rvDialogTurnover)

        val adapter = TurnoverAdapter(emptyList(), location)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        turnOffset = 0
        turnTotalCount = 0
        applyPagerButtonStyle(btnPrev, enabled = false)
        applyPagerButtonStyle(btnNext, enabled = false)
        applyRefreshIconTint(btnRefresh)

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnClose.setOnClickListener { dialog.dismiss() }
        btnRefresh.setOnClickListener {
            turnOffset = 0
            loadTurnoverPage(adapter, tvPageNumber, tvPageInfo, btnPrev, btnNext, dateFrom, dateTo, location, withSnack = true)
        }
        btnPrev.setOnClickListener {
            if (turnOffset <= 0) return@setOnClickListener
            turnOffset = (turnOffset - reportPageSize).coerceAtLeast(0)
            loadTurnoverPage(adapter, tvPageNumber, tvPageInfo, btnPrev, btnNext, dateFrom, dateTo, location)
            rv.scrollToPosition(0)
        }
        btnNext.setOnClickListener {
            val shown = (turnOffset + reportPageSize).coerceAtMost(turnTotalCount)
            if (shown >= turnTotalCount) return@setOnClickListener
            turnOffset += reportPageSize
            loadTurnoverPage(adapter, tvPageNumber, tvPageInfo, btnPrev, btnNext, dateFrom, dateTo, location)
            rv.scrollToPosition(0)
        }

        dialog.setOnShowListener { fitDialogToScreen(dialog) }
        dialog.setOnDismissListener { turnoverDialog = null }
        turnoverDialog = dialog
        dialog.show()
        loadTurnoverPage(adapter, tvPageNumber, tvPageInfo, btnPrev, btnNext, dateFrom, dateTo, location)
    }

    private fun loadTurnoverPage(
        adapter: TurnoverAdapter,
        tvPageNumber: TextView,
        tvPageInfo: TextView,
        btnPrev: Button,
        btnNext: Button,
        dateFrom: String?,
        dateTo: String?,
        location: String?,
        withSnack: Boolean = false
    ) {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getTurnoverReport(
                    limit = reportPageSize,
                    offset = turnOffset,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    location = location
                )
                if (res.isSuccessful && res.body() != null) {
                    val rows = res.body()!!.items.map { item ->
                        TurnoverRow(
                            productId = item.productId,
                            sku = item.sku,
                            name = item.name,
                            outs = item.outs,
                            stockInitial = item.stockInitial,
                            stockFinal = item.stockFinal,
                            stockAverage = item.stockAverage,
                            turnover = item.turnover
                        )
                    }
                    adapter.submit(rows)
                    turnTotalCount = res.body()!!.total
                    updateTurnPageInfo(tvPageNumber, tvPageInfo, btnPrev, btnNext, rows.size)
                    if (withSnack) {
                        CreateUiFeedback.showStatusPopup(
                            activity = this@ReportsActivity,
                            title = "Indice de rotacion cargado",
                            details = "Se ha cargado correctamente.",
                            animationRes = R.raw.correct_create,
                            autoDismissMs = 1800L
                        )
                    }
                } else {
                    Toast.makeText(this@ReportsActivity, "Error reporte rotacion: HTTP ${res.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ReportsActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTurnPageInfo(
        tvPageNumber: TextView,
        tvPageInfo: TextView,
        btnPrev: Button,
        btnNext: Button,
        pageSizeLoaded: Int
    ) {
        val shown = (turnOffset + pageSizeLoaded).coerceAtMost(turnTotalCount)
        val currentPage = if (turnTotalCount <= 0) 0 else (turnOffset / reportPageSize) + 1
        val totalPages = if (turnTotalCount <= 0) 0 else ((turnTotalCount + reportPageSize - 1) / reportPageSize)
        tvPageNumber.text = "Pagina $currentPage/$totalPages"
        tvPageInfo.text = "Mostrando $shown/$turnTotalCount"
        val prevEnabled = turnOffset > 0
        val nextEnabled = shown < turnTotalCount
        btnPrev.isEnabled = prevEnabled
        btnNext.isEnabled = nextEnabled
        applyPagerButtonStyle(btnPrev, prevEnabled)
        applyPagerButtonStyle(btnNext, nextEnabled)
    }

    private fun fitDialogToScreen(dialog: AlertDialog) {
        val width = (resources.displayMetrics.widthPixels * 0.94f).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.88f).toInt()
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(width, height)
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

    private fun applyRefreshIconTint(button: ImageButton) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) {
            ContextCompat.getColor(this, R.color.liquid_popup_text)
        } else {
            ContextCompat.getColor(this, R.color.icon_grad_mid2)
        }
        button.setColorFilter(color)
    }

    override fun onDestroy() {
        topConsumedDialog?.dismiss()
        turnoverDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LIMIT = "extra_limit"
        const val EXTRA_DATE_FROM = "extra_date_from"
        const val EXTRA_DATE_TO = "extra_date_to"
        const val EXTRA_LOCATION = "extra_location"
    }
}
