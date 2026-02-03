package com.example.inventoryapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.RegisterRequest
import com.example.inventoryapp.databinding.ActivityLoginBinding
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.home.HomeActivity
import kotlinx.coroutines.launch
import com.example.inventoryapp.R

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Si ya hay token, podrías saltar el login (opcional)
        if (!session.getToken().isNullOrBlank()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

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

        // ✅ Ocultar teclado antes de llamar a la API
        currentFocus?.let { view ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        // Texto "Crear cuenta"
        binding.tvCreateAccount.setOnClickListener {
            showRegisterDialog()
        }

        binding.imgLogo.setOnLongClickListener {
            showHostDialog()
            true
        }
    }

    private fun loginWithApi(user: String, pass: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = NetworkModule.api.login(user, pass)

                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.accessToken
                    session.saveToken(token)

                    UiNotifier.show(this@LoginActivity, "¡Bienvenido!")

                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "Usuario o contraseña incorrectos"
                        else -> "Error en el servidor: ${response.code()}"
                    }
                    UiNotifier.show(this@LoginActivity, errorMsg)
                }
            } catch (e: Exception) {
                UiNotifier.show(this@LoginActivity, "Error de conexión: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showRegisterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_register, null)
        val etUsername = dialogView.findViewById<EditText>(R.id.etRegUsername)
        val etEmail = dialogView.findViewById<EditText>(R.id.etRegEmail)
        val etPass = dialogView.findViewById<EditText>(R.id.etRegPassword)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Crear", null) // lo sobreescribimos para validar
            .create()

        dialog.setOnShowListener {
            val btnCreate = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnCreate.setOnClickListener {
                val username = etUsername.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val pass = etPass.text.toString()

                if (username.isBlank()) { etUsername.error = "Username requerido"; return@setOnClickListener }
                if (username.length < 3) { etUsername.error = "Minimo 3 caracteres"; return@setOnClickListener }
                if (email.isBlank()) { etEmail.error = "Email requerido"; return@setOnClickListener }
                if (pass.length < 8) { etPass.error = "Minimo 8 caracteres"; return@setOnClickListener }

                registerWithApi(username, email, pass, dialog)
            }
        }

        dialog.show()
    }

    private fun registerWithApi(username: String, email: String, pass: String, dialog: AlertDialog) {
        setUiEnabled(false)

        lifecycleScope.launch {
            try {
                val response = NetworkModule.api.register(RegisterRequest(username, email, pass))

                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.accessToken
                    session.saveToken(token)

                    UiNotifier.show(this@LoginActivity, "Cuenta creada")
                    dialog.dismiss()

                    // Ir a Home y pasar email para mostrar bienvenida
                    val i = Intent(this@LoginActivity, HomeActivity::class.java)
                    i.putExtra("welcome_email", email)
                    startActivity(i)
                    finish()
                } else {
                    val msg = when (response.code()) {
                        409 -> "Ese email ya existe"
                        422 -> "Datos invalidos (revisa username/email/contrasena)"
                        else -> "Error (${response.code()}): ${response.errorBody()?.string() ?: "sin detalle"}"
                    }
                    UiNotifier.show(this@LoginActivity, msg)
                }
            } catch (e: Exception) {
                UiNotifier.show(this@LoginActivity, "Error de conexión: ${e.message}")
            } finally {
                setUiEnabled(true)
            }
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.btnLogin.isEnabled = enabled
        // Si el botón existe en tu layout, esto compila; si no, comenta esta línea.
        binding.tvCreateAccount.isEnabled = enabled
        binding.etUser.isEnabled = enabled
        binding.etPass.isEnabled = enabled
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.tvCreateAccount.isEnabled = !loading
        binding.etUser.isEnabled = !loading
        binding.etPass.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Entrando..." else "Login"
    }

    private fun showHostDialog() {
        val input = EditText(this).apply {
            hint = "IP del servidor (ej. 192.168.1.50)"
            setText(NetworkModule.getCustomHost() ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle("Configurar servidor")
            .setMessage("Se guardará solo en este dispositivo.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Limpiar") { _, _ ->
                NetworkModule.setCustomHost(null)
                UiNotifier.show(this, "Servidor restablecido")
            }
            .setPositiveButton("Guardar") { _, _ ->
                NetworkModule.setCustomHost(input.text.toString())
                UiNotifier.show(this, "Servidor actualizado")
            }
            .show()
    }

}
