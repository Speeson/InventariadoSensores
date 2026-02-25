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

class NotchedLiquidTopBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = 0xE6F8FFFF.toInt()
    }
    private val innerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
        color = 0x66FFFFFF
    }
    private val shapePath = Path()
    private val cornerRadius = dp(16f)
    private var statusTintColor: Int = 0
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var notchProgress: Float = 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var notchEnabled: Boolean = true
        set(value) {
            field = value
            notchProgress = if (value) 1f else 0f
        }

    fun setStatusTintColor(color: Int) {
        if (statusTintColor != color) {
            statusTintColor = color
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        // Bar body is fixed at top; notch is built relative to the center button size (not icon).
        val barHeight = minOf(dp(56f), h - dp(8f))
        val centerButtonSize = resources.getDimension(R.dimen.top_center_button_size)
        val notchGap = resources.getDimension(R.dimen.top_center_button_notch_gap)
        // Wider + shallower notch for an "open angle" resting effect.
        val notchRadiusX = (centerButtonSize / 2f) + notchGap + dp(0f)
        val notchRadiusY = (centerButtonSize / 1.5f) - dp(0f)
        val notchCenterYOffset = dp(2f)
        val gradient = LinearGradient(
            0f,
            0f,
            0f,
            barHeight,
            intArrayOf(0xB3FFFFFF.toInt(), 0x66E9F2FF.toInt(), 0x66D5E3FF.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient

        val centerX = w / 2f

        shapePath.reset()
        val barPath = Path().apply {
            addRoundRect(
                RectF(0f, 0f, w, barHeight),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )
        }
        if (notchProgress > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val p = notchProgress.coerceIn(0f, 1f)
            val notchRx = notchRadiusX * p
            val notchRy = notchRadiusY * p
            val notchCy = barHeight + (notchCenterYOffset * p)
            val notchCut = Path().apply {
                // Elliptic notch: wider and less deep so the center button looks "resting" on the bar.
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
        if (statusTintColor != 0) {
            statusPaint.shader = LinearGradient(
                0f,
                0f,
                0f,
                barHeight,
                intArrayOf(
                    withAlpha(statusTintColor, 70),
                    withAlpha(statusTintColor, 24),
                    withAlpha(statusTintColor, 0)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(shapePath, statusPaint)
        }
        canvas.drawPath(shapePath, strokePaint)
        canvas.drawPath(shapePath, innerStrokePaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val a = (alpha.coerceIn(0, 255) shl 24)
        return (color and 0x00FFFFFF) or a
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
