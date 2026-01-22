package com.example.inventoryapp

import android.app.Application
import com.example.inventoryapp.data.remote.NetworkModule

class InventoryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
    }
}
