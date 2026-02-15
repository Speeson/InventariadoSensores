package com.example.inventoryapp.ui.alerts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.inventoryapp.R
import com.example.inventoryapp.databinding.ActivityAlertsBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.example.inventoryapp.ui.common.NetworkStatusBar

class AlertsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusBar.bind(this, findViewById(R.id.viewNetworkBar))
        applyAlertsTitleGradient()

        binding.btnBack.setOnClickListener { finish() }

        val adapter = AlertsPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Alertas" else "Alertas Offline"
        }.attach()
    }

    private fun applyAlertsTitleGradient() {
        binding.tvAlertsTitle.post {
            val paint = binding.tvAlertsTitle.paint
            val width = paint.measureText(binding.tvAlertsTitle.text.toString())
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
            binding.tvAlertsTitle.invalidate()
        }
    }
}
