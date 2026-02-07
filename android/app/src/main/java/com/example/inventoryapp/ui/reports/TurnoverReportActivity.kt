package com.example.inventoryapp.ui.reports

import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityTurnoverReportBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.SendSnack
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import android.widget.Button

class TurnoverReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTurnoverReportBinding
    private lateinit var adapter: TurnoverAdapter
    private lateinit var snack: SendSnack
    private var locationFilter: String? = null

    private val pageSize = 5
    private var currentOffset = 0
    private var totalCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTurnoverReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        snack = SendSnack(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        locationFilter = intent.getStringExtra(ReportsActivity.EXTRA_LOCATION)?.ifBlank { null }

        adapter = TurnoverAdapter(emptyList(), locationFilter)
        binding.rvTurnover.layoutManager = LinearLayoutManager(this)
        binding.rvTurnover.adapter = adapter

        binding.btnRefreshTurnover.setOnClickListener {
            currentOffset = 0
            loadTurnover(withSnack = true)
        }
        binding.btnPrevTurnPage.setOnClickListener {
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadTurnover(withSnack = false)
            binding.rvTurnover.scrollToPosition(0)
        }
        binding.btnNextTurnPage.setOnClickListener {
            val shown = (currentOffset + pageSize).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadTurnover(withSnack = false)
            binding.rvTurnover.scrollToPosition(0)
        }

        applyPagerButtonStyle(binding.btnPrevTurnPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextTurnPage, enabled = false)

        loadTurnover(withSnack = false)
    }

    private fun loadTurnover(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando indice de rotacion...")

        val limit = intent.getStringExtra(ReportsActivity.EXTRA_LIMIT)?.toIntOrNull() ?: pageSize
        val dateFrom = intent.getStringExtra(ReportsActivity.EXTRA_DATE_FROM)?.ifBlank { null }
        val dateTo = intent.getStringExtra(ReportsActivity.EXTRA_DATE_TO)?.ifBlank { null }
        val location = intent.getStringExtra(ReportsActivity.EXTRA_LOCATION)?.ifBlank { null }

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getTurnoverReport(
                    limit = pageSize,
                    offset = currentOffset,
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
                    totalCount = res.body()!!.total
                    updatePageInfo(rows.size)
                    if (withSnack) snack.showSuccess("OK: Indice de rotacion cargado")
                } else {
                    snack.showError("Error reporte rotacion: HTTP ${res.code()}")
                }
            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
            }
        }
    }

    private fun updatePageInfo(pageSizeLoaded: Int) {
        val shown = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        binding.tvTurnPageInfo.text = "Mostrando $shown/$totalCount"
        val prevEnabled = currentOffset > 0
        val nextEnabled = shown < totalCount
        binding.btnPrevTurnPage.isEnabled = prevEnabled
        binding.btnNextTurnPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevTurnPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextTurnPage, nextEnabled)
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
}
