package com.example.inventoryapp.ui.common

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import com.example.inventoryapp.R

object TopNavShared {

    enum class Mode {
        HOME,
        CHILD,
    }

    const val LIQUID_CRYSTAL_BLUE_HEX = "#7FD8FF"
    const val LIQUID_CRYSTAL_BLUE_ACTIVE_HEX = "#2CB8FF"
    const val LIQUID_CRYSTAL_BLUE_LIGHT_BOOST_HEX = "#58C9FF"

    fun updateTopButtonState(
        button: ImageButton,
        active: Boolean,
        leftEdgeButton: Boolean,
        baseColor: Int = Color.parseColor(LIQUID_CRYSTAL_BLUE_HEX),
        activeColor: Int = Color.parseColor(LIQUID_CRYSTAL_BLUE_ACTIVE_HEX),
        invertY: Boolean = true,
    ) {
        val selectedScale = if (leftEdgeButton) 1.04f else 1.08f
        if (active) {
            button.setBackgroundResource(R.drawable.bg_liquid_icon_selected)
            button.imageAlpha = 255
            button.scaleX = selectedScale
            button.scaleY = if (invertY) -selectedScale else selectedScale
            button.setColorFilter(activeColor, PorterDuff.Mode.SRC_IN)
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
            button.imageAlpha = 245
            button.scaleX = 1.0f
            button.scaleY = if (invertY) -1.0f else 1.0f
            button.setColorFilter(baseColor, PorterDuff.Mode.SRC_IN)
        }
    }

    fun setLiquidIcon(
        view: ImageView,
        resId: Int,
        prefs: SharedPreferences,
        plusIconDarkTint: Int = Color.parseColor("#00B8FF"),
        plusIconLightTint: Int = Color.parseColor("#F2FAFF"),
        baseColor: Int = Color.parseColor(LIQUID_CRYSTAL_BLUE_HEX),
        lightBaseColorBoost: Int = Color.parseColor(LIQUID_CRYSTAL_BLUE_LIGHT_BOOST_HEX),
    ) {
        view.setImageResource(resId)
        view.imageTintList = null
        val isDark = prefs.getBoolean("dark_mode", false)
        val base = if (isDark) baseColor else lightBaseColorBoost
        val tint = if (resId == R.drawable.glass_add) {
            if (isDark) plusIconDarkTint else plusIconLightTint
        } else {
            base
        }
        view.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        view.imageAlpha = 245
    }

    fun prefs(view: ImageView): SharedPreferences {
        return view.context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    }

    fun configureLeftButton(
        button: ImageButton?,
        mode: Mode,
        onHomeClick: (() -> Unit)? = null,
        onChildClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        iconMenuRes: Int = R.drawable.glass_menu,
        iconBackRes: Int = R.drawable.glass_back,
        setIcon: ((ImageButton, Int) -> Unit)? = null,
    ) {
        val target = button ?: return
        when (mode) {
            Mode.HOME -> {
                target.contentDescription = "Menu"
                if (setIcon != null) setIcon(target, iconMenuRes)
                target.setOnClickListener { onHomeClick?.invoke() }
                target.setOnLongClickListener {
                    onLongClick?.invoke()
                    true
                }
            }

            Mode.CHILD -> {
                target.contentDescription = "Volver"
                if (setIcon != null) setIcon(target, iconBackRes)
                target.setOnClickListener { onChildClick?.invoke() }
                target.setOnLongClickListener(null)
            }
        }
    }

    fun applyTopSelection(
        root: View,
        selectedId: Int?,
        update: (ImageButton, Boolean) -> Unit,
    ) {
        val ids = intArrayOf(
            R.id.btnMenu,
            R.id.btnTopMidLeft,
            R.id.btnTopMidRight,
            R.id.btnAlertsQuick
        )
        ids.forEach { id ->
            root.findViewById<ImageButton>(id)?.let { btn ->
                update(btn, selectedId == id)
            }
        }
    }
}
