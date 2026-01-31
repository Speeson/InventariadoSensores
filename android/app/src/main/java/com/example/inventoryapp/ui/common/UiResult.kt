package com.example.inventoryapp.ui.common

sealed class UiResult {
    data class Success(val msg: String) : UiResult()
    data class Error(val msg: String) : UiResult()
    object SessionExpired : UiResult()
}