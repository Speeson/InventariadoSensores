package com.example.inventoryapp.ui.common

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.R as MaterialR
import com.google.android.material.bottomappbar.BottomAppBar

/**
 * Workaround for a MaterialComponents inflation bug where setElevation can be
 * invoked before BottomAppBar internal background is initialized.
 */
class SafeBottomAppBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = MaterialR.attr.bottomAppBarStyle,
) : BottomAppBar(context, attrs, defStyleAttr) {

    private var pendingElevation: Float? = null

    override fun setElevation(elevation: Float) {
        try {
            super.setElevation(elevation)
            pendingElevation = null
        } catch (_: NullPointerException) {
            pendingElevation = elevation
            post {
                pendingElevation?.let { value ->
                    runCatching { super.setElevation(value) }
                    pendingElevation = null
                }
            }
        }
    }
}

