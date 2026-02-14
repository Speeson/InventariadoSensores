package com.niimbot.jcdemo.utils;

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import com.niimbot.jcdemo.bean.Dish

private const val TAG = "ImgUtil"
/**
 * @author Zhang Bin
 * @date 2023-12-11 1:36 PM
 */
class ImgUtil {


    companion object {
        /**
         * Get text width
         * @param paint Paint
         * @param text String
         * @return Int
         */
        fun getFontWidth(paint: Paint, text: String): Int {
            val rect = Rect()
            paint.getTextBounds(text, 0, text.length, rect)
            return rect.width()
        }

        /**
         * Determine how many lines a dish needs to display
         *
         * @param paint Paint
         * @param text String
         * @return Int
         */
        fun getFontHeight(paint: Paint, text: String): Int {
            val rect = Rect()
            paint.getTextBounds(text, 0, 1, rect)
            return rect.height()
        }

        /**
         * Get the height needed to print dishes on the receipt image
         *
         * @param list Dish list
         * @param normalFontHeight Normal font height
         * @param perLineDistance Distance between lines
         * @param toKitchen Whether to send to kitchen
         * @return Height needed to print dishes
         */
        fun getProBmpHeight(
            list: ArrayList<Dish>, normalFontHeight: Int, perLineDistance: Int, toKitchen: Boolean
        ): Int {
            var h = 0 // Total height
            for (i in list.indices) {
                // Display multiple lines based on dish text length
                var lines = getShowLine(list[i].name)
                for (j in 0 until lines) {
                    h += normalFontHeight + perLineDistance
                }

                // If sending to kitchen, calculate note height
                if (toKitchen) {
                    // Display note
                    lines = getShowLine(list[i].note)
                    for (j in 0 until lines) {
                        h += normalFontHeight + perLineDistance
                    }
                }
                h += perLineDistance // Larger distance between dishes for better distinction
            }
            return h
        }

        /**
         * Calculate the number of lines needed to display a dish
         *
         * @param dish Dish name
         * @return Number of lines needed to display the dish
         */
        fun getShowLine(dish: String): Int {
            var line = 1 // Default display one line

            // If dish name length exceeds 12 characters, calculate the number of lines needed
            if (dish.length > 12) {
                line = if (dish.length % 12 == 0) {
                    dish.length / 12 // Number of lines when divisible
                } else {
                    dish.length / 12 + 1 // Number of lines when there's a remainder
                }
            }

            return line
        }


        /**
         * Extract the dish text to display for each line
         *
         * @param dishName Dish name
         * @param lineIndex Line index
         * @param lines Total number of lines
         * @return Dish text to display for each line
         */
        fun getDisplayTextForLine(dishName: String, lineIndex: Int, lines: Int): String {
            val subStr: String = if (lines == 1) {
                dishName
            } else if (lineIndex != lines - 1 || dishName.length % 12 == 0) {
                dishName.substring(lineIndex * 12, (lineIndex + 1) * 12)
            } else {
                dishName.substring(lineIndex * 12)
            }
            return subStr
        }

        /**
         * Convert POS receipt information to image
         *
         * @return
         */
        fun generatePosReceiptImage(pros: ArrayList<Dish>): Bitmap {
            val paint = Paint().apply {
                textSize = 48f
                color = Color.BLACK
                style = Paint.Style.FILL
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = false
                isDither = false
                hinting = Paint.ANTI_ALIAS_FLAG
            }

            val restaurantInfo = arrayOf(
                "Niimbot Canteen",
                "Tel: 18546544545",
                "Date: 2020/03/25   15:50",
                "Area: Hall",
                "Table: A04",
                "Guests: 5",
                "Waiter: Anny",
                "Order No: 4884977449494949494"
            )

            val restaurantNameHeight = getFontHeight(paint, "Nimmbot Canteen")
            val blackFontHeight = getFontHeight(paint.apply { textSize = 32f }, "Nimmbot Canteen")
            val normalFontHeight =
                getFontHeight(paint.apply { typeface = Typeface.DEFAULT }, "Nimmbot Canteen")

            Log.d(TAG, "blackFontHeight:${blackFontHeight} ,normalFontHeight:${normalFontHeight} ")

            val perLineDistance = 28
            val perFontDistance = 28
            //小票宽度
            val receiptCanvasWidth = 432
            val topBlank = 64
            //订单信息与店铺名称之间的间距
            val restaurantOrderSpacing = 100
            //店铺名高度+标准字号高度+行间距
            val receiptCanvasHeadHeight =topBlank+ restaurantNameHeight+normalFontHeight+perLineDistance+restaurantOrderSpacing+(restaurantInfo.size-3)*(normalFontHeight+perLineDistance)

            //底部留白（可能要撕纸，需要留白）
            val bottomBlank = 200
            val welcomeMsgDistance = 100
            //小票菜单标题栏高度+总计金额
            val receiptCanvasBottomHeight =perLineDistance*7+blackFontHeight*3+ perFontDistance*2+welcomeMsgDistance+normalFontHeight*2+ bottomBlank
            //小票高度
            val receiptCanvasHeight =
                receiptCanvasHeadHeight + receiptCanvasBottomHeight + getProBmpHeight(pros, normalFontHeight, perLineDistance, false)



            //创建小票图片
            val bmp = Bitmap.createBitmap(
                receiptCanvasWidth,
                receiptCanvasHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)

            var textWidth: Int

            var y: Int = restaurantNameHeight+topBlank
            var x: Float
            restaurantInfo.forEachIndexed { index, str ->
                when (index) {
                    0 -> {
                        paint.apply {
                            textSize = 48f
                            typeface = Typeface.DEFAULT_BOLD
                        }


                    }

                    1 -> {
                        paint.apply {
                            textSize = 28f
                            typeface = Typeface.DEFAULT
                        }
                        y += normalFontHeight + perLineDistance
                    }

                    2 -> {
                        paint.apply {
                            textSize = 28f
                        }
                        y += restaurantOrderSpacing
                    }

                    3,4, 5,6, 7 -> {
                        y += blackFontHeight + perLineDistance
                    }

                    else -> {
                        paint.apply {
                            typeface = Typeface.DEFAULT
                        }
                    }
                }

                textWidth = getFontWidth(paint, str)
                x = when (index) {
                    0, 1 -> {
                        (receiptCanvasWidth / 2 - textWidth / 2).toFloat()
                    }

                    2, 3, 4,5,6, 7 -> {
                        16f
                    }

                    else -> {
                        receiptCanvasWidth - textWidth - 50f - 16f
                    }
                }
                canvas.drawText(
                    str,
                    x,
                    y.toFloat(),
                    paint
                )

            }

            paint.apply {
                typeface = Typeface.DEFAULT_BOLD
                strokeWidth = 4f
            }
            //画线
            y += perLineDistance
            canvas.drawLine(0f, y.toFloat(), receiptCanvasWidth.toFloat(), y.toFloat(), paint)


            //title(Item, Unit Price, Quantity, Subtotal)
            y += perLineDistance * 2
            canvas.drawText("Item", 16f, y.toFloat(), paint)
            canvas.drawText(
                "Price",
                (receiptCanvasWidth - getFontWidth(paint, "Subtotal") - 50 - 100 - 100).toFloat(),
                y.toFloat(),
                paint
            )
            canvas.drawText(
                "Qty",
                (receiptCanvasWidth - getFontWidth(paint, "Subtotal") - 50 - 100).toFloat(),
                y.toFloat(),
                paint
            )
            canvas.drawText(
                "Subtotal",
                (receiptCanvasWidth - getFontWidth(paint, "Subtotal") - 50).toFloat(),
                y.toFloat(),
                paint
            )
            // Draw line
            y += blackFontHeight
            canvas.drawLine(0f, y.toFloat(), receiptCanvasWidth.toFloat(), y.toFloat(), paint)
            paint.apply {
                typeface = Typeface.DEFAULT
            }
            y += perLineDistance * 2
            //下面是画菜品文本
            for (i in 0 until pros.size) {
                //先画后面的值，菜品名称后面画，因为菜品名称可能多行显示，以菜品显示高度为准；
                canvas.drawText(
                    "￥" + pros[i].price,
                    (receiptCanvasWidth - getFontWidth(paint, "小计") - 50 - 100 - 100).toFloat(),
                    y.toFloat(),
                    paint
                )
                canvas.drawText(
                    java.lang.String.valueOf(pros[i].count),
                    (receiptCanvasWidth - getFontWidth(paint, "小计") - 50 - 100).toFloat(),
                    y.toFloat(),
                    paint
                )
                canvas.drawText(
                    "￥ ${pros[i].price * pros[i].count}",
                    (receiptCanvasWidth - getFontWidth(paint, "小计") - 50).toFloat(),
                    y.toFloat(),
                    paint
                )
                var dish: String
                //根据菜品文本长度显示多行
                val lines: Int = getShowLine(pros[i].name)
                for (j in 0 until lines) {
                    dish = getDisplayTextForLine(pros[i].name, j, lines)
                    canvas.drawText(dish, 16f, y.toFloat(), paint)
                    y += normalFontHeight + perLineDistance
                }
                y += perLineDistance
            }
            // Draw line
            canvas.drawLine(0f, y.toFloat(), receiptCanvasWidth.toFloat(), y.toFloat(), paint)


            // Various amounts below
            y += perLineDistance * 2
            paint.apply {
                typeface = Typeface.DEFAULT_BOLD
            }

            canvas.drawText("Store Receipt", 16f, y.toFloat(), paint)
            canvas.drawText(
                "￥1000",
                (receiptCanvasWidth - getFontWidth(paint, "￥1000") - 50).toFloat(),
                y.toFloat(),
                paint
            )

            y += blackFontHeight + perLineDistance
            canvas.drawText("Consumption Tax", 16f, y.toFloat(), paint)
            canvas.drawText(
                "￥50",
                (receiptCanvasWidth - getFontWidth(paint, "￥50") - 50).toFloat(),
                y.toFloat(),
                paint
            )

            y += blackFontHeight + perFontDistance
            canvas.drawText("Service Fee", 16f, y.toFloat(), paint)
            canvas.drawText(
                "￥100",
                (receiptCanvasWidth - getFontWidth(paint, "￥100") - 50).toFloat(),
                y.toFloat(),
                paint
            )

            y += blackFontHeight + perFontDistance
            canvas.drawText("Discount Amount", 16f, y.toFloat(), paint)
            canvas.drawText(
                "-￥100",
                (receiptCanvasWidth - getFontWidth(paint, "-￥100") - 50).toFloat(),
                y.toFloat(),
                paint
            )


            // Draw line
            y += blackFontHeight + perFontDistance
            canvas.drawLine(0f, y.toFloat(), receiptCanvasWidth.toFloat(), y.toFloat(), paint)

            paint.apply {
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
            }
            y += blackFontHeight + perFontDistance
            canvas.drawText("Final Receipt", 16f, y.toFloat(), paint)
            canvas.drawText(
                "￥20000",
                (receiptCanvasWidth - getFontWidth(paint, "￥20000") - 50).toFloat(),
                y.toFloat(),
                paint
            )


            paint.apply {
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
            }
            y += welcomeMsgDistance
            var welcomeMsg = "Powered By Bin"
            textWidth = getFontWidth(paint, welcomeMsg)
            canvas.drawText(
                welcomeMsg,
                (receiptCanvasWidth / 2 - textWidth / 2).toFloat(),
                y.toFloat(),
                paint
            )

            y += normalFontHeight
            welcomeMsg = "Thank you,Please come again"
            textWidth = getFontWidth(paint, welcomeMsg)
            canvas.drawText(
                welcomeMsg,
                (receiptCanvasWidth / 2 - textWidth / 2).toFloat(),
                y.toFloat(),
                paint
            )

            y += bottomBlank

            return bmp
        }

    }
}