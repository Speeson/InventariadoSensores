package com.example.inventoryapp.ui.auth

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.CompoundButtonCompat
import androidx.lifecycle.lifecycleScope
import com.example.inventoryapp.R
import com.example.inventoryapp.data.local.SessionManager
import com.example.inventoryapp.data.remote.AlertsWebSocketManager
import com.example.inventoryapp.data.remote.FcmTokenManager
import com.example.inventoryapp.data.remote.NetworkModule
import com.example.inventoryapp.databinding.ActivityLoginBinding
import com.example.inventoryapp.ui.common.CreateUiFeedback
import com.example.inventoryapp.ui.common.UiNotifier
import com.example.inventoryapp.ui.home.HomeActivity
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.LinkedHashSet

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private lateinit var emailAdapter: ArrayAdapter<String>
    private var loginPanelHeightPx = 0
    private var registerPanelHeightPx = 0
    private var isRegisterVisible = false
    private var emailValidationActive = false
    private var passwordValidationActive = false

    private val defaultHintColor by lazy { ColorStateList.valueOf(Color.parseColor("#F3FAFF")) }
    private val errorHintColor by lazy { ColorStateList.valueOf(Color.parseColor("#FF7272")) }
    private val passwordEyeTint by lazy { ColorStateList.valueOf(Color.parseColor("#F2F8FF")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loginPanelHeightPx = dpToPx(610)
        registerPanelHeightPx = dpToPx(800)
        session = SessionManager(this)

        setupBackdropBlur()
        setupTextFieldBehaviors()
        setupBiometricTransition()
        setupRememberEmailCheckbox()
        setupWhiteCursor()
        setupRegisterTransition()
        setupGenderDropdown()
        setupEmailAutocomplete()
        setupLoginValidationFeedback()
        setupFunctionalActions()
        playEntryMotion()

        binding.loginTestRoot.isFocusableInTouchMode = true
        binding.tvLoginTitle.setOnLongClickListener {
            showHostDialog()
            true
        }

        NetworkModule.setManualOffline(false)

        val token = session.getToken()
        val alreadyNotified = intent.getBooleanExtra(NetworkModule.EXTRA_AUTH_EXPIRED_NOTIFIED, false)
        if (alreadyNotified) {
            UiNotifier.showBlockingTimed(
                this@LoginActivity,
                "Sesion caducada. Inicia sesion.",
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
                    "Sesion caducada. Inicia sesion.",
                    R.drawable.expired,
                    timeoutMs = 20_000L
                )
            }
        }

        if (!session.getToken().isNullOrBlank()) {
            validateExistingSession()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (binding.layoutFingerprintOnly.visibility == View.VISIBLE &&
                !isTouchInsideView(ev, binding.btnFingerprintLogin)
            ) {
                showPrimaryActions()
                return true
            }

            val focusedView = currentFocus
            val touchInsideAnyInput = allInputViews().any { isTouchInsideView(ev, it) }
            if (focusedView is EditText && !touchInsideAnyInput) {
                clearInputFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupFunctionalActions() {
        val loginAction = View.OnClickListener {
            if (!binding.btnLoginAction.isEnabled) return@OnClickListener
            val user = binding.etLoginTestEmail.text?.toString().orEmpty().trim()
            val pass = binding.etLoginTestPassword.text?.toString().orEmpty().trim()

            if (!validateLoginInputs()) {
                return@OnClickListener
            }
            loginWithApi(user, pass)
        }
        binding.btnLoginAction.setOnClickListener(loginAction)
        binding.btnLoginAction.getChildAt(0)?.setOnClickListener(loginAction)
    }

    private fun setupLoginValidationFeedback() {
        binding.etLoginTestEmail.addTextChangedListener { editable ->
            if (!editable.isNullOrBlank() && emailValidationActive) {
                emailValidationActive = false
                applyLoginEmailState()
            }
        }

        binding.etLoginTestPassword.addTextChangedListener { editable ->
            if (!editable.isNullOrBlank() && passwordValidationActive) {
                passwordValidationActive = false
                applyLoginPasswordState()
            }
        }

        applyLoginEmailState()
        applyLoginPasswordState()
    }

    private fun validateLoginInputs(): Boolean {
        val emailEmpty = binding.etLoginTestEmail.text?.toString().orEmpty().trim().isEmpty()
        val passwordEmpty = binding.etLoginTestPassword.text?.toString().orEmpty().trim().isEmpty()

        emailValidationActive = emailEmpty
        passwordValidationActive = passwordEmpty
        applyLoginEmailState()
        applyLoginPasswordState()

        return !emailEmpty && !passwordEmpty
    }

    private fun setupBackdropBlur() {
        val windowBackground = window.decorView.background
        binding.layoutLoginPanel
            .setupWith(binding.blurTarget)
            .setFrameClearDrawable(windowBackground)
            .setBlurRadius(8f)
            .setBlurAutoUpdate(true)
    }

    private fun setupTextFieldBehaviors() {
        allInputViews().forEach { view ->
            setupHintOnFocus(view)
            setupKeyboardFocus(view)
        }
    }

    private fun setupHintOnFocus(view: TextView) {
        view.setOnFocusChangeListener { _, hasFocus ->
            view.hint = if (hasFocus && !shouldKeepHintVisibleOnFocus(view)) "" else resolveHintFor(view)
        }
    }

    private fun setupKeyboardFocus(view: View) {
        view.setOnClickListener {
            view.requestFocus()
            if (view === binding.actvRegisterGender) {
                binding.actvRegisterGender.showDropDown()
            }
            if (view === binding.etLoginTestEmail) {
                binding.etLoginTestEmail.showDropDown()
            }
            getSystemService<InputMethodManager>()?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupBiometricTransition() {
        val openBiometric = View.OnClickListener { showFingerprintOnly() }
        binding.btnBiometricAction.setOnClickListener(openBiometric)
        binding.btnBiometricAction.getChildAt(0)?.setOnClickListener(openBiometric)
    }

    private fun setupRegisterTransition() {
        val openRegister = View.OnClickListener { showRegisterState() }
        val backToLogin = View.OnClickListener { showLoginState() }
        binding.tvSignupAction.setOnClickListener(openRegister)
        binding.btnBackToLoginAction.setOnClickListener(backToLogin)
        binding.btnBackToLoginAction.getChildAt(0)?.setOnClickListener(backToLogin)
    }

    private fun setupRememberEmailCheckbox() {
        CompoundButtonCompat.setButtonTintList(binding.cbRememberEmail, null)
        binding.cbRememberEmail.buttonTintList = null
        binding.cbRememberEmail.compoundDrawablePadding = dpToPx(8)
    }

    private fun setupGenderDropdown() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.register_test_gender_options,
            android.R.layout.simple_dropdown_item_1line
        )
        binding.actvRegisterGender.setAdapter(adapter)
        binding.actvRegisterGender.setDropDownBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#B8344558"))
            }
        )
    }

    private fun setupWhiteCursor() {
        forceCursorDrawable(binding.etLoginTestEmail, R.drawable.bg_cursor_white)
        forceCursorDrawable(binding.etLoginTestPassword, R.drawable.bg_cursor_white)
        forceCursorDrawable(binding.etRegisterUsername, R.drawable.bg_cursor_white)
        forceCursorDrawable(binding.etRegisterEmail, R.drawable.bg_cursor_white)
        forceCursorDrawable(binding.etRegisterPhonePrefix, R.drawable.bg_cursor_white)
        forceCursorDrawable(binding.etRegisterPhone, R.drawable.bg_cursor_white)
        forceCursorDrawable(binding.etRegisterAge, R.drawable.bg_cursor_white)
        forceCursorDrawable(binding.actvRegisterGender, R.drawable.bg_cursor_white)
        forceCursorDrawable(binding.etRegisterPassword, R.drawable.bg_cursor_white)
        forceCursorDrawable(binding.etRegisterConfirmPassword, R.drawable.bg_cursor_white)
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
        binding.etLoginTestEmail.setAdapter(emailAdapter)
        binding.etLoginTestEmail.setDropDownBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18).toFloat()
                setColor(Color.parseColor("#CC5A7085"))
                setStroke(dpToPx(1), Color.parseColor("#A8EAF6FF"))
            }
        )
        binding.etLoginTestEmail.dropDownVerticalOffset = dpToPx(8)
        if (savedEmail.isNotBlank()) {
            binding.etLoginTestEmail.setText(savedEmail, false)
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
                val iterator = recent.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
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

    private fun clearInputFocus() {
        currentFocus?.clearFocus()
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.loginTestRoot.requestFocus()
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
                    UiNotifier.show(this@LoginActivity, "Bienvenido")
                    persistEmailIfNeeded(user)
                    navigateToHome(Intent(this@LoginActivity, HomeActivity::class.java))
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> getString(R.string.login_invalid_credentials_message)
                        else -> "Error en el servidor: ${response.code()}"
                    }
                    if (response.code() == 401) {
                        showInvalidCredentialsDialog()
                    } else {
                        UiNotifier.show(this@LoginActivity, errorMsg)
                    }
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
                    UiNotifier.showBlockingTimed(
                        this@LoginActivity,
                        "Sesion caducada. Inicia sesion.",
                        R.drawable.expired
                    )
                }
            } catch (_: Exception) {
                UiNotifier.showBlockingTimed(
                    this@LoginActivity,
                    "Sin conexion. No se puede validar la sesion.",
                    R.drawable.offline
                )
            } finally {
                setUiEnabled(true)
                setLoading(false)
            }
        }
    }

    private fun clearCachedUiRole() {
        val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("cached_role")
            .remove("cached_user_id")
            .remove("cached_token")
            .apply()
    }

    private fun setUiEnabled(enabled: Boolean) {
        setActionEnabled(binding.btnLoginAction, enabled)
        binding.tvSignupAction.isEnabled = enabled
        binding.etLoginTestEmail.isEnabled = enabled
        binding.etLoginTestPassword.isEnabled = enabled
        binding.cbRememberEmail.isEnabled = enabled
    }

    private fun setLoading(loading: Boolean) {
        val enabled = !loading
        setUiEnabled(enabled)
        val loginLabel = binding.btnLoginAction.getChildAt(0) as? TextView
        loginLabel?.text = if (loading) "Entrando..." else getString(R.string.login_test_login_action)
    }

    private fun setActionEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.getChildAtOrNull(0)?.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.75f
    }

    private fun showFingerprintOnly() {
        binding.layoutPrimaryActions.visibility = View.GONE
        binding.layoutFingerprintOnly.visibility = View.VISIBLE
    }

    private fun showPrimaryActions() {
        binding.layoutPrimaryActions.visibility = View.VISIBLE
        binding.layoutFingerprintOnly.visibility = View.GONE
    }

    private fun showRegisterState() {
        if (isRegisterVisible) return
        isRegisterVisible = true
        showPrimaryActions()
        animatePanelHeight(loginPanelHeightPx, registerPanelHeightPx)
        binding.layoutRegisterState.alpha = 0f
        binding.layoutRegisterState.visibility = View.VISIBLE
        binding.layoutLoginState.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction { binding.layoutLoginState.visibility = View.GONE }
            .start()
        binding.layoutRegisterState.animate()
            .alpha(1f)
            .setDuration(220)
            .start()
    }

    private fun showLoginState() {
        if (!isRegisterVisible) return
        isRegisterVisible = false
        clearInputFocus()
        animatePanelHeight(registerPanelHeightPx, loginPanelHeightPx)
        binding.layoutLoginState.alpha = 0f
        binding.layoutLoginState.visibility = View.VISIBLE
        binding.layoutRegisterState.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction { binding.layoutRegisterState.visibility = View.GONE }
            .start()
        binding.layoutLoginState.animate()
            .alpha(1f)
            .setDuration(220)
            .start()
    }

    private fun animatePanelHeight(from: Int, to: Int) {
        ValueAnimator.ofInt(from, to).apply {
            duration = 260L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                binding.layoutLoginPanel.layoutParams = binding.layoutLoginPanel.layoutParams.apply {
                    height = animator.animatedValue as Int
                }
            }
            start()
        }
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
        val view = layoutInflater.inflate(R.layout.dialog_liquid_host_config, null)
        val input = view.findViewById<EditText>(R.id.etHostValue)
        input.setText(NetworkModule.getCustomHost() ?: "")

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        view.findViewById<android.widget.ImageButton>(R.id.btnHostClose)?.setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<android.widget.Button>(R.id.btnHostReset)?.setOnClickListener {
            NetworkModule.setCustomHost(null)
            NetworkModule.setManualOffline(false)
            dialog.dismiss()
            CreateUiFeedback.showCreatedPopup(
                activity = this,
                title = "Host reiniciado",
                details = "Se restauro la configuracion del host."
            )
        }
        view.findViewById<android.widget.Button>(R.id.btnHostSave)?.setOnClickListener {
            NetworkModule.setCustomHost(input.text.toString())
            NetworkModule.setManualOffline(false)
            dialog.dismiss()
            CreateUiFeedback.showCreatedPopup(
                activity = this,
                title = "Host actualizado",
                details = "Nueva configuracion de host guardada."
            )
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showInvalidCredentialsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_create_failure, null)
        view.findViewById<TextView>(R.id.tvSuccessTitle)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.tvSuccessDetails)?.apply {
            text = getString(R.string.login_invalid_credentials_message)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        view.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun applyLoginEmailState() {
        val isError = emailValidationActive && binding.etLoginTestEmail.text?.toString().orEmpty().trim().isEmpty()
        binding.etLoginTestEmail.hint =
            if (binding.etLoginTestEmail.hasFocus() && !isError) "" else resolveHintFor(binding.etLoginTestEmail)
        binding.etLoginTestEmail.setHintTextColor(if (isError) errorHintColor else defaultHintColor)

        if (isError) {
            binding.tilLoginTestEmail.endIconMode = TextInputLayout.END_ICON_CUSTOM
            binding.tilLoginTestEmail.endIconDrawable = getDrawable(R.drawable.ic_error_red)
            binding.tilLoginTestEmail.setEndIconTintList(null)
            binding.tilLoginTestEmail.setEndIconOnClickListener(null)
            binding.tilLoginTestEmail.endIconContentDescription =
                getString(R.string.login_test_email_required)
        } else {
            binding.tilLoginTestEmail.endIconMode = TextInputLayout.END_ICON_NONE
        }
    }

    private fun applyLoginPasswordState() {
        val isError = passwordValidationActive && binding.etLoginTestPassword.text?.toString().orEmpty().trim().isEmpty()
        binding.etLoginTestPassword.hint =
            if (binding.etLoginTestPassword.hasFocus() && !isError) "" else resolveHintFor(binding.etLoginTestPassword)
        binding.etLoginTestPassword.setHintTextColor(if (isError) errorHintColor else defaultHintColor)

        if (isError) {
            binding.tilLoginTestPassword.endIconMode = TextInputLayout.END_ICON_CUSTOM
            binding.tilLoginTestPassword.endIconDrawable = getDrawable(R.drawable.ic_error_red)
            binding.tilLoginTestPassword.setEndIconTintList(null)
            binding.tilLoginTestPassword.setEndIconOnClickListener(null)
            binding.tilLoginTestPassword.endIconContentDescription =
                getString(R.string.login_test_password_required)
        } else {
            binding.tilLoginTestPassword.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            binding.tilLoginTestPassword.setEndIconTintList(passwordEyeTint)
            binding.tilLoginTestPassword.endIconContentDescription =
                getString(R.string.login_test_toggle_password)
        }
    }

    private fun resolveHintFor(view: TextView): CharSequence = when (view) {
        binding.etLoginTestEmail -> {
            if (emailValidationActive && binding.etLoginTestEmail.text?.toString().orEmpty().trim().isEmpty()) {
                getString(R.string.login_test_email_required)
            } else {
                getString(R.string.login_test_email_hint)
            }
        }
        binding.etLoginTestPassword -> {
            if (passwordValidationActive && binding.etLoginTestPassword.text?.toString().orEmpty().trim().isEmpty()) {
                getString(R.string.login_test_password_required)
            } else {
                getString(R.string.login_test_password_hint)
            }
        }
        binding.etRegisterUsername -> getString(R.string.register_test_username_hint)
        binding.etRegisterEmail -> getString(R.string.login_test_email_hint)
        binding.etRegisterPhonePrefix -> getString(R.string.register_test_phone_prefix_hint)
        binding.etRegisterPhone -> getString(R.string.register_test_phone_hint)
        binding.etRegisterAge -> getString(R.string.register_test_age_hint)
        binding.actvRegisterGender -> getString(R.string.register_test_gender_hint)
        binding.etRegisterPassword -> getString(R.string.login_test_password_hint)
        binding.etRegisterConfirmPassword -> getString(R.string.register_test_confirm_password_hint)
        else -> view.hint ?: ""
    }

    private fun shouldKeepHintVisibleOnFocus(view: TextView): Boolean = when (view) {
        binding.etLoginTestEmail ->
            emailValidationActive && binding.etLoginTestEmail.text?.toString().orEmpty().trim().isEmpty()
        binding.etLoginTestPassword ->
            passwordValidationActive && binding.etLoginTestPassword.text?.toString().orEmpty().trim().isEmpty()
        else -> false
    }

    private fun isTouchInsideView(event: MotionEvent, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = event.rawX
        val y = event.rawY
        return x >= location[0] &&
            x <= location[0] + view.width &&
            y >= location[1] &&
            y <= location[1] + view.height
    }

    private fun allInputViews(): List<TextView> = listOf(
        binding.etLoginTestEmail,
        binding.etLoginTestPassword,
        binding.etRegisterUsername,
        binding.etRegisterEmail,
        binding.etRegisterPhonePrefix,
        binding.etRegisterPhone,
        binding.etRegisterAge,
        binding.actvRegisterGender,
        binding.etRegisterPassword,
        binding.etRegisterConfirmPassword
    )

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun forceCursorDrawable(textView: TextView, drawableRes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textView.textCursorDrawable = getDrawable(drawableRes)
            return
        }

        runCatching {
            val field = TextView::class.java.getDeclaredField("mCursorDrawableRes")
            field.isAccessible = true
            field.set(textView, drawableRes)
        }
    }

    private fun View.getChildAtOrNull(index: Int): View? = if (this is ViewGroup && childCount > index) getChildAt(index) else null
}
