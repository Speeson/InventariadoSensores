package com.example.inventoryapp.ui.common

import android.app.Activity
import java.lang.ref.WeakReference

object ActivityTracker {
    private var current: WeakReference<Activity>? = null

    fun setCurrent(activity: Activity?) {
        current = if (activity != null) WeakReference(activity) else null
    }

    fun getCurrent(): Activity? = current?.get()
}
