package com.example.inventoryapp.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.example.inventoryapp.R

object GradientIconUtil {
    fun applyGradient(imageView: ImageView, @DrawableRes resId: Int) {
        val drawable = ContextCompat.getDrawable(imageView.context, resId)
        if (drawable == null) {
            imageView.setImageResource(resId)
            return
        }
        imageView.setImageBitmap(getGradientBitmapFromDrawable(imageView.context, drawable))
    }

    private fun getGradientBitmapFromDrawable(context: Context, drawable: Drawable): Bitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
        val src = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(out)
        val colors = intArrayOf(
            ContextCompat.getColor(context, R.color.icon_grad_start),
            ContextCompat.getColor(context, R.color.icon_grad_mid2),
            ContextCompat.getColor(context, R.color.icon_grad_mid1),
            ContextCompat.getColor(context, R.color.icon_grad_end)
        )
        val shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            colors,
            floatArrayOf(0f, 0.33f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        outCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        outCanvas.drawBitmap(src, 0f, 0f, paint)
        paint.xfermode = null
        return out
    }
}
