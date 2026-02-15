package com.example.inventoryapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.data.remote.AlertsWebSocketManager
import com.example.inventoryapp.data.remote.FcmTokenManager
import com.example.inventoryapp.data.remote.RegisterRequest
import com.example.inventoryapp.databinding.ActivityLoginBinding
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.home.HomeActivity
import kotlinx.coroutines.launch
import com.example.inventoryapp.R
import java.util.LinkedHashSet
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.widget.TextView

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private lateinit var emailAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        playEntryMotion()
        applyBluePurpleGradient(binding.tvTitle)

        session = SessionManager(this)
        setupEmailAutocomplete()
        // Failsafe: avoid being locked out of login if debug offline was left enabled.
        NetworkModule.setManualOffline(false)

        val token = session.getToken()
        val alreadyNotified = intent.getBooleanExtra(NetworkModule.EXTRA_AUTH_EXPIRED_NOTIFIED, false)
        if (alreadyNotified) {
            UiNotifier.showBlockingTimed(
                this@LoginActivity,
                "Sesión caducada. Inicia sesión.",
                R.drawable.expired,
                timeoutMs = 20_000L
            )
        }
        if (!token.isNullOrBlank() && session.isTokenExpired(token)) {
            session.clearToken()
            clearCachedUiRole()
            if (!alreadyNotified) {
                UiNotifier.showBlockingTimed(
                    this@LoginActivity,
                    "Sesión caducada. Inicia sesión.",
                    R.drawable.expired,
                    timeoutMs = 20_000L
                )
            }
        }

        // Si ya hay token, validar contra la API antes de entrar.
        if (!session.getToken().isNullOrBlank()) {
            validateExistingSession()
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

    private fun setupEmailAutocomplete() {
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val savedEmail = prefs.getString("saved_email", "") ?: ""
        val recent = prefs.getStringSet("recent_emails", emptySet()) ?: emptySet()
        val list = recent.toMutableList()
        if (savedEmail.isNotBlank() && !list.contains(savedEmail)) {
            list.add(0, savedEmail)
        }
        emailAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list)
        binding.etUser.setAdapter(emailAdapter)
        if (savedEmail.isNotBlank()) {
            binding.etUser.setText(savedEmail, false)
            binding.cbRememberEmail.isChecked = true
        }
    }

    private fun persistEmailIfNeeded(email: String) {
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val recent = LinkedHashSet(prefs.getStringSet("recent_emails", emptySet()) ?: emptySet())
        if (email.isNotBlank()) {
            recent.remove(email)
            recent.add(email)
            while (recent.size > 8) {
                val it = recent.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
        prefs.edit().putStringSet("recent_emails", recent).apply()
        if (binding.cbRememberEmail.isChecked && email.isNotBlank()) {
            prefs.edit().putString("saved_email", email).apply()
        } else {
            prefs.edit().remove("saved_email").apply()
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
                    clearCachedUiRole()
                    NetworkModule.resetAuthRedirectGuard()
                    NetworkModule.forceOnline()
                    AlertsWebSocketManager.connect(this@LoginActivity)
                    FcmTokenManager.sync(this@LoginActivity)
                    FcmTokenManager.sync(this@LoginActivity)

                    UiNotifier.show(this@LoginActivity, "¡Bienvenido!")

                    persistEmailIfNeeded(user)
                    navigateToHome(Intent(this@LoginActivity, HomeActivity::class.java))
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "Usuario o contraseña incorrectos"
                        else -> "Error en el servidor: ${response.code()}"
                    }
                    UiNotifier.show(this@LoginActivity, errorMsg)
                }
            } catch (e: Exception) {
                UiNotifier.showConnectionError(this@LoginActivity, e.message, allowTechnical = false)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun validateExistingSession() {
        setUiEnabled(false)
        setLoading(true)
        lifecycleScope.launch {
            try {
                val res = NetworkModule.api.me()
                if (res.isSuccessful && res.body() != null) {
                    NetworkModule.resetAuthRedirectGuard()
                    navigateToHome(Intent(this@LoginActivity, HomeActivity::class.java))
                    FcmTokenManager.sync(this@LoginActivity)
                } else {
                    session.clearToken()
                    clearCachedUiRole()
                    UiNotifier.showBlockingTimed(this@LoginActivity, "Sesión caducada. Inicia sesión.", R.drawable.expired)
                }
            } catch (e: Exception) {
                UiNotifier.showBlockingTimed(this@LoginActivity, "Sin conexión. No se puede validar la sesión.", R.drawable.offline)
            } finally {
                setUiEnabled(true)
                setLoading(false)
            }
        }
    }

    private fun hasCachedSession(): Boolean {
        val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val cachedRole = prefs.getString("cached_role", null)
        val cachedUserId = prefs.getInt("cached_user_id", -1)
        val cachedToken = prefs.getString("cached_token", null)
        val token = session.getToken()
        return !cachedRole.isNullOrBlank() && cachedUserId > 0 && !cachedToken.isNullOrBlank() && cachedToken == token
    }

    private fun clearCachedUiRole() {
        val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("cached_role")
            .remove("cached_user_id")
            .remove("cached_token")
            .apply()
    }

    private fun showRegisterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_register, null)
        val etUsername = dialogView.findViewById<EditText>(R.id.etRegUsername)
        val etEmail = dialogView.findViewById<EditText>(R.id.etRegEmail)
        val etPass = dialogView.findViewById<EditText>(R.id.etRegPassword)
        val tvRegisterTitle = dialogView.findViewById<TextView>(R.id.tvRegisterTitle)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnRegisterCancel)
        val btnCreate = dialogView.findViewById<android.widget.Button>(R.id.btnRegisterCreate)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.setOnShowListener {
            applyBluePurpleGradient(tvRegisterTitle)
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
            btnCancel.setOnClickListener { dialog.dismiss() }
        }

        dialog.show()
    }

    private fun applyBluePurpleGradient(textView: TextView) {
        textView.post {
            val text = textView.text?.toString().orEmpty()
            if (text.isEmpty()) return@post
            val width = textView.paint.measureText(text)
            if (width <= 0f) return@post
            val shader = LinearGradient(
                0f,
                0f,
                width,
                0f,
                intArrayOf(
                    getColor(R.color.icon_grad_start),
                    getColor(R.color.icon_grad_mid2),
                    getColor(R.color.icon_grad_end)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            textView.paint.shader = shader
            textView.invalidate()
        }
    }

    private fun registerWithApi(username: String, email: String, pass: String, dialog: AlertDialog) {
        setUiEnabled(false)

        lifecycleScope.launch {
            try {
                val response = NetworkModule.api.register(RegisterRequest(username, email, pass))

                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.accessToken
                    session.saveToken(token)
                    clearCachedUiRole()
                    NetworkModule.resetAuthRedirectGuard()
                    NetworkModule.forceOnline()
                    AlertsWebSocketManager.connect(this@LoginActivity)

                    UiNotifier.show(this@LoginActivity, "Cuenta creada")
                    dialog.dismiss()

                    // Ir a Home y pasar email para mostrar bienvenida
                    persistEmailIfNeeded(email)
                    val i = Intent(this@LoginActivity, HomeActivity::class.java)
                    i.putExtra("welcome_email", email)
                    navigateToHome(i)
                } else {
                    val msg = when (response.code()) {
                        409 -> "Ese email ya existe"
                        422 -> "Datos invalidos (revisa username/email/contrasena)"
                        else -> "Error (${response.code()}): ${response.errorBody()?.string() ?: "sin detalle"}"
                    }
                    UiNotifier.show(this@LoginActivity, msg)
                }
            } catch (e: Exception) {
                UiNotifier.showConnectionError(this@LoginActivity, e.message, allowTechnical = false)
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

    private fun navigateToHome(intent: Intent) {
        startActivity(intent)
        overridePendingTransition(R.anim.screen_enter_soft, R.anim.screen_exit_soft)
        finish()
    }

    private fun playEntryMotion() {
        binding.root.alpha = 0f
        binding.root.scaleX = 0.985f
        binding.root.scaleY = 0.985f
        binding.root.translationY = 18f
        binding.root.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(420L)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.7f))
            .start()
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
                NetworkModule.setManualOffline(false)
                UiNotifier.show(this, "Servidor restablecido")
            }
            .setPositiveButton("Guardar") { _, _ ->
                NetworkModule.setCustomHost(input.text.toString())
                NetworkModule.setManualOffline(false)
                UiNotifier.show(this, "Servidor actualizado")
            }
            .show()
    }

}

