package com.example.inventoryapp.ui.imports

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.R
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.model.AlertStatusDto
import com.example.inventoryapp.databinding.ActivityImportsBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity
import com.example.inventoryapp.ui.common.NetworkStatusBar
import com.example.inventoryapp.ui.common.GradientIconUtil
import com.example.inventoryapp.ui.common.TopCenterActionHost
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch


class ImportsActivity : AppCompatActivity(), TopCenterActionHost {

    companion object {
        private const val TAG = "ImportsActivity"
    }

    private lateinit var binding: ActivityImportsBinding
    private lateinit var pagerAdapter: ImportsPagerAdapter
    private var tabMediator: TabLayoutMediator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))
        Log.d(TAG, "onCreate()")

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
            binding.tvAlertsBadge.visibility = android.view.View.GONE
        }
        GradientIconUtil.applyGradient(binding.btnAlertsQuick, R.drawable.ic_bell)
        applyImportsTitleGradient()

        setupPager()
    }

    override fun onTopCreateAction() {
        openImportPopupAt(position = 0)
    }

    override fun onTopFilterAction() {
        openImportPopupAt(position = 1)
    }

    private fun openImportPopupAt(position: Int) {
        binding.pagerImports.setCurrentItem(position, true)
        binding.pagerImports.post {
            openImportPopupForPosition(position, retriesLeft = 4)
        }
    }

    private fun openImportPopupForPosition(position: Int, retriesLeft: Int) {
        val fragment = findPagerFragment(position)
        Log.d(
            TAG,
            "openImportPopupForPosition(pos=$position,retries=$retriesLeft) fragment=${fragment?.javaClass?.simpleName} added=${fragment?.isAdded} hasView=${fragment?.view != null}"
        )
        if (fragment is ImportFormFragment && fragment.isAdded && fragment.view != null) {
            fragment.openImportDialog()
            return
        }
        if (retriesLeft <= 0) return
        binding.pagerImports.postDelayed(
            { openImportPopupForPosition(position, retriesLeft - 1) },
            80L
        )
    }

    private fun findPagerFragment(position: Int): Fragment? {
        val itemId = pagerAdapter.getItemId(position)
        return supportFragmentManager.findFragmentByTag("f$itemId")
    }

    override fun onResume() {
        super.onResume()
        normalizeContentPaddingIfCorrupted()
        lifecycleScope.launch {
            updateAlertsBadge()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        normalizeContentPaddingIfCorrupted()
    }

    private fun setupPager() {
        tabMediator?.detach()
        pagerAdapter = ImportsPagerAdapter(this)
        binding.pagerImports.adapter = pagerAdapter
        tabMediator = TabLayoutMediator(binding.tabImports, binding.pagerImports) { tab, pos ->
            tab.text = when (pos) {
                0 -> "Eventos"
                1 -> "Transferencias"
                else -> "Revisiones"
            }
        }
        tabMediator?.attach()
    }

    private fun normalizeContentPaddingIfCorrupted() {
        val root = binding.importsContentRoot
        val oldTop = root.paddingTop
        val maxReasonableTop = (260 * resources.displayMetrics.density).toInt()
        if (oldTop <= maxReasonableTop) return
        val expectedTop = (118 * resources.displayMetrics.density).toInt() // 20dp base + 96dp top host + 2dp gap
        val horizontal = (20 * resources.displayMetrics.density).toInt()
        val bottom = (8 * resources.displayMetrics.density).toInt()
        root.setPadding(horizontal, expectedTop, horizontal, bottom)
        Log.d(
            TAG,
            "normalizeContentPaddingIfCorrupted() applied oldTop=$oldTop expectedTop=$expectedTop"
        )
    }

    private suspend fun updateAlertsBadge() {
        try {
            val res = NetworkModule.api.listAlerts(status = AlertStatusDto.PENDING, limit = 1, offset = 0)
            if (!res.isSuccessful || res.body() == null) {
                binding.tvAlertsBadge.visibility = android.view.View.GONE
                return
            }
            val total = res.body()!!.total
            if (total > 0) {
                val label = if (total > 99) "99+" else total.toString()
                binding.tvAlertsBadge.text = label
                binding.tvAlertsBadge.visibility = android.view.View.VISIBLE
            } else {
                binding.tvAlertsBadge.visibility = android.view.View.GONE
            }
        } catch (_: Exception) {
            binding.tvAlertsBadge.visibility = android.view.View.GONE
        }
    }

    private fun applyImportsTitleGradient() {
        val title = binding.tvImportsTitle
        title.post {
            val paint = title.paint
            val width = paint.measureText(title.text.toString())
            if (width <= 0f) return@post
            val c1 = androidx.core.content.ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_start)
            val c2 = androidx.core.content.ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid2)
            val c3 = androidx.core.content.ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_mid1)
            val c4 = androidx.core.content.ContextCompat.getColor(this, com.example.inventoryapp.R.color.icon_grad_end)
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
            title.invalidate()
        }
    }
}
