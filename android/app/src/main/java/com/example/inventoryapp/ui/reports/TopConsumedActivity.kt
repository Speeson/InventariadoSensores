package com.example.inventoryapp.ui.reports

import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.ui.common.AlertsBadgeUtil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityTopConsumedBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.SendSnack
import com.example.inventoryapp.ui.common.NetworkStatusBar
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.R
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import android.widget.Button

class TopConsumedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTopConsumedBinding
    private lateinit var adapter: TopConsumedAdapter
    private lateinit var snack: SendSnack
    private var locationFilter: String? = null

    private val pageSize = 5
    private var currentOffset = 0
    private var totalCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTopConsumedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))

        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        AlertsBadgeUtil.refresh(lifecycleScope, binding.tvAlertsBadge)
        snack = SendSnack(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        locationFilter = intent.getStringExtra(ReportsActivity.EXTRA_LOCATION)?.ifBlank { null }

        adapter = TopConsumedAdapter(emptyList(), locationFilter)
        binding.rvTopConsumed.layoutManager = LinearLayoutManager(this)
        binding.rvTopConsumed.adapter = adapter

        binding.btnRefreshTop.setOnClickListener {
            currentOffset = 0
            loadTopConsumed(withSnack = true)
        }
        binding.btnPrevTopPage.setOnClickListener {
            if (currentOffset <= 0) return@setOnClickListener
            currentOffset = (currentOffset - pageSize).coerceAtLeast(0)
            loadTopConsumed(withSnack = false)
            binding.rvTopConsumed.scrollToPosition(0)
        }
        binding.btnNextTopPage.setOnClickListener {
            val shown = (currentOffset + pageSize).coerceAtMost(totalCount)
            if (shown >= totalCount) return@setOnClickListener
            currentOffset += pageSize
            loadTopConsumed(withSnack = false)
            binding.rvTopConsumed.scrollToPosition(0)
        }

        applyPagerButtonStyle(binding.btnPrevTopPage, enabled = false)
        applyPagerButtonStyle(binding.btnNextTopPage, enabled = false)

        loadTopConsumed(withSnack = false)
    }

    private fun loadTopConsumed(withSnack: Boolean) {
        if (withSnack) snack.showSending("Cargando top consumidos...")

        val limit = intent.getStringExtra(ReportsActivity.EXTRA_LIMIT)?.toIntOrNull() ?: pageSize
        val dateFrom = intent.getStringExtra(ReportsActivity.EXTRA_DATE_FROM)?.ifBlank { null }
        val dateTo = intent.getStringExtra(ReportsActivity.EXTRA_DATE_TO)?.ifBlank { null }
        val location = intent.getStringExtra(ReportsActivity.EXTRA_LOCATION)?.ifBlank { null }

        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.getTopConsumedReport(
                    limit = pageSize,
                    offset = currentOffset,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    location = location
                )
                if (res.isSuccessful && res.body() != null) {
                    adapter.submit(res.body()!!.items)
                    totalCount = res.body()!!.total
                    updatePageInfo(res.body()!!.items.size)
                    if (withSnack) snack.showSuccess("OK: Top consumidos cargado")
                } else {
                    snack.showError("Error reporte: HTTP ${res.code()}")
                }
            } catch (e: Exception) {
                snack.showError("Error de red: ${e.message}")
            }
        }
    }

    private fun updatePageInfo(pageSizeLoaded: Int) {
        val shown = (currentOffset + pageSizeLoaded).coerceAtMost(totalCount)
        binding.tvTopPageInfo.text = "Mostrando $shown/$totalCount"
        val prevEnabled = currentOffset > 0
        val nextEnabled = shown < totalCount
        binding.btnPrevTopPage.isEnabled = prevEnabled
        binding.btnNextTopPage.isEnabled = nextEnabled
        applyPagerButtonStyle(binding.btnPrevTopPage, prevEnabled)
        applyPagerButtonStyle(binding.btnNextTopPage, nextEnabled)
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
