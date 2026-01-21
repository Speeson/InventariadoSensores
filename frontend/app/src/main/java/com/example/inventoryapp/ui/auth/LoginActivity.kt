package com.example.inventoryapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.RegisterRequest
import com.example.inventoryapp.databinding.ActivityLoginBinding
import com.example.inventoryapp.ui.home.HomeActivity
import kotlinx.coroutines.launch
import android.widget.Spinner
import android.widget.ArrayAdapter
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

        // Botón "Crear cuenta" (asegúrate de tenerlo en el layout con id btnCreateAccount)
        binding.btnCreateAccount.setOnClickListener {
            showRegisterDialog()
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

                    Toast.makeText(this@LoginActivity, "¡Bienvenido!", Toast.LENGTH_SHORT).show()

                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "Usuario o contraseña incorrectos"
                        else -> "Error en el servidor: ${response.code()}"
                    }
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showRegisterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_register, null)
        val etEmail = dialogView.findViewById<EditText>(R.id.etRegEmail)
        val etPass = dialogView.findViewById<EditText>(R.id.etRegPassword)
        val spRole = dialogView.findViewById<Spinner>(R.id.spRole)

        // Cargar opciones de rol en el spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.roles_register,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spRole.adapter = adapter
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Crear", null) // lo sobreescribimos para validar
            .create()

        dialog.setOnShowListener {
            val btnCreate = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnCreate.setOnClickListener {
                val email = etEmail.text.toString().trim()
                val pass = etPass.text.toString()

                if (email.isBlank()) { etEmail.error = "Email requerido"; return@setOnClickListener }
                if (pass.length < 8) { etPass.error = "Mínimo 8 caracteres"; return@setOnClickListener }

                val roleSelected = spRole.selectedItem?.toString() ?: "(Sin rol)"
                val role: String? = if (roleSelected == "(Sin rol)") null else roleSelected

                registerWithApi(email, pass, role, dialog)
            }
        }

        dialog.show()
    }

    private fun registerWithApi(email: String, pass: String, role: String?, dialog: AlertDialog) {
        setUiEnabled(false)

        lifecycleScope.launch {
            try {
                val response = NetworkModule.api.register(RegisterRequest(email, pass, role))

                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.accessToken
                    session.saveToken(token)

                    Toast.makeText(this@LoginActivity, "Cuenta creada ✅", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()

                    // Ir a Home y pasar email para mostrar bienvenida
                    val i = Intent(this@LoginActivity, HomeActivity::class.java)
                    i.putExtra("welcome_email", email)
                    startActivity(i)
                    finish()
                } else {
                    val msg = when (response.code()) {
                        409 -> "Ese email ya existe"
                        422 -> "Datos inválidos (revisa email/contraseña)"
                        else -> "Error (${response.code()}): ${response.errorBody()?.string() ?: "sin detalle"}"
                    }
                    Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setUiEnabled(true)
            }
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.btnLogin.isEnabled = enabled
        // Si el botón existe en tu layout, esto compila; si no, comenta esta línea.
        binding.btnCreateAccount.isEnabled = enabled
        binding.etUser.isEnabled = enabled
        binding.etPass.isEnabled = enabled
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnCreateAccount.isEnabled = !loading
        binding.etUser.isEnabled = !loading
        binding.etPass.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Entrando..." else "Entrar"
    }

}
