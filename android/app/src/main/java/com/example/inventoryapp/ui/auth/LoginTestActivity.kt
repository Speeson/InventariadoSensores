package com.example.inventoryapp.ui.auth

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.example.inventoryapp.databinding.ActivityLoginTestBinding

class LoginTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBackdropBlur()
        setupHintOnFocus(binding.etLoginTestEmail)
        setupHintOnFocus(binding.etLoginTestPassword)
        setupKeyboardFocus(binding.etLoginTestEmail)
        setupKeyboardFocus(binding.etLoginTestPassword)
        setupBiometricTransition()
        binding.loginTestRoot.isFocusableInTouchMode = true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val focusedView = currentFocus
            val touchInsideEmail = isTouchInsideView(ev, binding.etLoginTestEmail)
            val touchInsidePassword = isTouchInsideView(ev, binding.etLoginTestPassword)
            if (focusedView is EditText && !touchInsideEmail && !touchInsidePassword) {
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
            .setBlurRadius(3f)
            .setBlurAutoUpdate(true)
    }

    private fun setupHintOnFocus(view: View) {
        when (view) {
            binding.etLoginTestEmail -> {
                val originalHint = binding.etLoginTestEmail.hint
                binding.etLoginTestEmail.setOnFocusChangeListener { _, hasFocus ->
                    binding.etLoginTestEmail.hint = if (hasFocus) "" else originalHint
                }
            }
            binding.etLoginTestPassword -> {
                val originalHint = binding.etLoginTestPassword.hint
                binding.etLoginTestPassword.setOnFocusChangeListener { _, hasFocus ->
                    binding.etLoginTestPassword.hint = if (hasFocus) "" else originalHint
                }
            }
        }
    }

    private fun setupKeyboardFocus(view: View) {
        view.setOnClickListener {
            view.requestFocus()
            getSystemService<InputMethodManager>()?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun clearInputFocus() {
        currentFocus?.clearFocus()
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.loginTestRoot.requestFocus()
    }

    private fun setupBiometricTransition() {
        binding.btnBiometricAction.setOnClickListener {
            binding.layoutPrimaryActions.visibility = View.GONE
            binding.layoutFingerprintOnly.visibility = View.VISIBLE
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
}
