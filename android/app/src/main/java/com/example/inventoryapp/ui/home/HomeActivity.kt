package com.example.inventoryapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.OfflineQueue
import com.example.inventoryapp.data.local.OfflineSyncer
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityHomeBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import com.example.inventoryapp.ui.events.EventsActivity
import com.example.inventoryapp.ui.movements.MovimientosActivity
import com.example.inventoryapp.ui.products.ProductListActivity
import com.example.inventoryapp.ui.stock.StockActivity
import kotlinx.coroutines.launch
import com.example.inventoryapp.ui.offline.OfflineErrorsActivity


class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // ✅ Si no hay token, fuera
        if (session.getToken().isNullOrBlank()) {
            goToLogin()
            return
        }

        setSupportActionBar(binding.toolbar)

        // Pop-up bienvenida si venías de registro
                AlertDialog.Builder(this)
                    .setTitle("Bienvenido")
                    .setMessage("¡Bienvenido, $email!")
                    .setPositiveButton("OK", null)
                    .show()
            }


        binding.btnScan.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }

        binding.btnProducts.setOnClickListener {
            startActivity(Intent(this, ProductListActivity::class.java))
        }

        binding.btnStock.setOnClickListener {
            startActivity(Intent(this, StockActivity::class.java))
        }

        binding.btnMovements.setOnClickListener {
            startActivity(Intent(this, MovimientosActivity::class.java))
        }

        binding.btnEvents.setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }

        binding.btnOfflineErrors.setOnClickListener {
            startActivity(Intent(this, OfflineErrorsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            val report = OfflineSyncer.flush(this@HomeActivity)

            if (report.sent > 0) {
                Toast.makeText(
                    this@HomeActivity,
                    "Reenviados ${report.sent} pendientes ✅",
                    Toast.LENGTH_LONG
                ).show()
            }

            if (report.movedToFailed > 0) {
                Toast.makeText(
                    this@HomeActivity,
                    "${report.movedToFailed} pendientes con error ❗ Revisa 'Pendientes'",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_system_status -> { showSystemStatus(); true }
            R.id.action_profile -> { showProfile(); true }
            R.id.action_logout -> { confirmLogout(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }





    private fun logout() {
        session.clearToken()
        goToLogin()
    }

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
