package com.example.inventoryapp.ui.auth

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.core.widget.CompoundButtonCompat
import com.example.inventoryapp.R
import com.example.inventoryapp.databinding.ActivityLoginTestBinding

class LoginTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginTestBinding
    private var loginPanelHeightPx = 0
    private var registerPanelHeightPx = 0
    private var isRegisterVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        binding = ActivityLoginTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loginPanelHeightPx = dpToPx(610)
        registerPanelHeightPx = dpToPx(800)

        setupBackdropBlur()
        setupTextFieldBehaviors()
        setupBiometricTransition()
        setupRememberEmailCheckbox()
        setupWhiteCursor()
        setupRegisterTransition()
        setupGenderDropdown()
        binding.loginTestRoot.isFocusableInTouchMode = true
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
        val originalHint = view.hint
        view.setOnFocusChangeListener { _, hasFocus ->
            view.hint = if (hasFocus) "" else originalHint
        }
    }

    private fun setupKeyboardFocus(view: View) {
        view.setOnClickListener {
            view.requestFocus()
            if (view === binding.actvRegisterGender) {
                binding.actvRegisterGender.showDropDown()
            }
            getSystemService<InputMethodManager>()?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun clearInputFocus() {
        currentFocus?.clearFocus()
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.loginTestRoot.requestFocus()
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
            val f = TextView::class.java.getDeclaredField("mCursorDrawableRes")
            f.isAccessible = true
            f.set(textView, drawableRes)
        }
    }
}
