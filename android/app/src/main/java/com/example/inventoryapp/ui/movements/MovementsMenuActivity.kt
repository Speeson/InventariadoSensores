package com.example.inventoryapp.ui.movements

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.databinding.ActivityMovementsMenuBinding

class MovementsMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMovementsMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMovementsMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnGoList.setOnClickListener {
            startActivity(Intent(this, MovementsListActivity::class.java))
        }

        binding.btnGoCreate.setOnClickListener {
            startActivity(Intent(this, MovimientosActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
