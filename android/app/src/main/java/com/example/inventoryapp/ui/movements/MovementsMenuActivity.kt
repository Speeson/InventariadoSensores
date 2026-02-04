package com.example.inventoryapp.ui.movements

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.databinding.ActivityMovementsMenuBinding
import com.example.inventoryapp.ui.alerts.AlertsActivity

class MovementsMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovementsMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementsMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAlertsQuick.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnGoList.setOnClickListener {
            startActivity(Intent(this, MovementsListActivity::class.java))
        }

        binding.btnGoCreate.setOnClickListener {
            startActivity(Intent(this, MovimientosActivity::class.java))
        }
    }
}
