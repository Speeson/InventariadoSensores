package com.example.inventoryapp.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import com.example.inventoryapp.R

class NotchedLiquidBottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.1f)
        color = 0xCFE5F8FF.toInt()
    }
    private val innerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.7f)
        color = 0x66FFFFFF
    }
    private val shapePath = Path()
    private val cornerRadius = dp(16f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()

        val gradient = LinearGradient(
            0f,
            0f,
            0f,
            h,
            intArrayOf(0xCFE7F6FF.toInt(), 0x89B8E4FF.toInt(), 0x5E8FC9FF.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient

        val centerX = w / 2f
        val centerButtonSize = resources.getDimension(R.dimen.top_center_button_size)

        shapePath.reset()
        val barPath = Path().apply {
            addRoundRect(
                RectF(0f, 0f, w, h),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Socavon concavo y suave.
            val notchRx = (centerButtonSize / 2f) + dp(20f)
            val notchRy = (centerButtonSize / 2f) + dp(11f)
            val notchCy = -dp(6f)
            val notchCut = Path().apply {
                addOval(
                    RectF(
                        centerX - notchRx,
                        notchCy - notchRy,
                        centerX + notchRx,
                        notchCy + notchRy
                    ),
                    Path.Direction.CW
                )
            }
            barPath.op(notchCut, Path.Op.DIFFERENCE)
        }
        shapePath.addPath(barPath)

        canvas.drawPath(shapePath, fillPaint)
        canvas.drawPath(shapePath, strokePaint)
        canvas.drawPath(shapePath, innerStrokePaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
