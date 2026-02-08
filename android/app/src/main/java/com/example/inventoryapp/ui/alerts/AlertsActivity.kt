package com.example.inventoryapp.ui.alerts

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = AlertsPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Alertas" else "Pendientes offline"
        }.attach()
    }
}
