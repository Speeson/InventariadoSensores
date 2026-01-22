package com.example.inventoryapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.databinding.ActivityHomeBinding
import com.example.inventoryapp.ui.auth.LoginActivity
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.inventoryapp.data.remote.NetworkModule



class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // ✅ Si no hay token, fuera (evita entrar por back stack)
        if (session.getToken().isNullOrBlank()) {
            goToLogin()
            return
        }

        setSupportActionBar(binding.toolbar)

        // Pop-up bienvenida si venías de registro
        intent.getStringExtra("welcome_email")?.takeIf { it.isNotBlank() }?.let { email ->
            AlertDialog.Builder(this)
                .setTitle("Bienvenido")
                .setMessage("¡Bienvenido, $email!")
                .setPositiveButton("OK", null)
                .show()
        }

        // TODO: cambia estas Activities por las tuyas reales
        binding.btnScan.setOnClickListener {
            // startActivity(Intent(this, ScanActivity::class.java))
        }

        binding.btnProducts.setOnClickListener {
            // startActivity(Intent(this, ProductActivity::class.java))
        }

        binding.btnStock.setOnClickListener {
            // startActivity(Intent(this, StockActivity::class.java))
        }

        binding.btnMovements.setOnClickListener {
            // startActivity(Intent(this, MovementsActivity::class.java))
        }

        // De momento los dejamos con Toast o los deshabilitas si quieres
        binding.btnEvents.setOnClickListener {
            // TODO implementar EventsActivity
        }

        binding.btnMovements.setOnClickListener {
            startActivity(Intent(this, com.example.inventoryapp.ui.movements.MovimientosActivity::class.java))
        }

        binding.btnProducts.setOnClickListener {
            startActivity(Intent(this, com.example.inventoryapp.ui.products.ProductListActivity::class.java))
        }

        binding.btnStock.setOnClickListener {
            startActivity(Intent(this, com.example.inventoryapp.ui.stock.StockActivity::class.java))
        }



    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    private fun showSystemStatus() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.health()
                if (res.isSuccessful) {
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Estado del sistema")
                        .setMessage("Backend OK ✅")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Estado del sistema")
                        .setMessage("Backend respondió ${res.code()} ❌")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle("Estado del sistema")
                    .setMessage("No se pudo conectar ❌\n${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showProfile() {
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.me()
                if (res.isSuccessful && res.body() != null) {
                    val me = res.body()!!
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Mi perfil")
                        .setMessage("Email: ${me.email}\nRol: ${me.role}\nID: ${me.id}")
                        .setPositiveButton("OK", null)
                        .show()
                } else if (res.code() == 401) {
                    // token inválido/expirado: limpiar sesión y volver a login
                    Toast.makeText(this@HomeActivity, "Sesión caducada. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
                    session.clearToken() // usa el método real que tengas
                    goToLogin()
                } else {
                    AlertDialog.Builder(this@HomeActivity)
                        .setTitle("Mi perfil")
                        .setMessage("Error ${res.code()} ❌")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle("Mi perfil")
                    .setMessage("Error de conexión ❌\n${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_system_status -> {
                showSystemStatus()
                true
            }
            R.id.action_profile -> {
                showProfile()
                true
            }
            R.id.action_logout -> {
                confirmLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSystemStatusDialogPlaceholder() {
        AlertDialog.Builder(this)
            .setTitle("Estado del sistema")
            .setMessage("Pendiente de implementar (endpoint /health).")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showProfileDialogPlaceholder() {
        AlertDialog.Builder(this)
            .setTitle("Mi perfil")
            .setMessage("Pendiente de implementar (endpoint /users/me).")
            .setPositiveButton("OK", null)
            .show()
    }


    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar") { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        session.clearToken() // ⚠️ si tu método se llama distinto, cámbialo aquí
        goToLogin()
    }

    private fun goToLogin() {
        val i = Intent(this, LoginActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(i)
        finish()
    }
}
