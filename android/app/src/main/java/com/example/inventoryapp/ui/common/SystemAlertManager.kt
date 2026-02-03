package com.example.inventoryapp.ui.common

import android.content.Context
import com.example.inventoryapp.data.local.SystemAlert
import com.example.inventoryapp.data.local.SystemAlertStore
import com.example.inventoryapp.data.local.SystemAlertType
import java.util.UUID

object SystemAlertManager {

    fun record(
        context: Context,
        type: SystemAlertType,
        title: String,
        message: String,
        blocking: Boolean = true
    ) {
        val store = SystemAlertStore(context)
        if (!store.shouldRecord(type, message)) return

        val alert = SystemAlert(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            message = message
        )
        store.add(alert)
        store.rememberLast(type, message)

        if (blocking) {
            val activity = ActivityTracker.getCurrent()
            if (activity != null) {
                activity.runOnUiThread {
                    UiNotifier.showBlocking(activity, title, message)
                }
            }
        }
    }
}
