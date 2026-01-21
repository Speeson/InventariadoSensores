package com.example.inventoryapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityLoginBinding
import com.example.inventoryapp.ui.home.HomeActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Si ya hay token, podrías saltar el login (opcional)
        // if (!session.getToken().isNullOrBlank()) { ... }

        binding.btnLogin.setOnClickListener {
            val user = binding.etUser.text.toString().trim()
            val pass = binding.etPass.text.toString().trim()

            if (user.isEmpty()) { 
                binding.etUser.error = "Usuario requerido"
                return@setOnClickListener 
            }
            if (pass.isEmpty()) { 
                binding.etPass.error = "Contraseña requerida"
                return@setOnClickListener 
            }

            loginWithApi(user, pass)
        }
    }

    private fun loginWithApi(user: String, pass: String) {
        binding.btnLogin.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val response = NetworkModule.api.login(user, pass)
                
                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.accessToken
                    session.saveToken(token)
                    
                    Toast.makeText(this@LoginActivity, "¡Bienvenido!", Toast.LENGTH_SHORT).show()
                    
                    // Ir a la Home
                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                } else {
                    val errorMsg = when(response.code()) {
                        401 -> "Usuario o contraseña incorrectos"
                        else -> "Error en el servidor: ${response.code()}"
                    }
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnLogin.isEnabled = true
            }
        }
    }
}