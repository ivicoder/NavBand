package com.navband.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object ImageDirectionDetector {

    fun detect(bitmap: Bitmap?): NavigationDirection? {
        if (bitmap == null || bitmap.width < 8 || bitmap.height < 8) {
            return null
        }

        val points = mutableListOf<Pair<Int, Int>>()

        val width = bitmap.width
        val height = bitmap.height

        val border = mutableListOf<Int>()

        for (x in 0 until width) {
            border += bitmap.getPixel(x, 0)
            border += bitmap.getPixel(x, height - 1)
        }

        for (y in 1 until height - 1) {
            border += bitmap.getPixel(0, y)
            border += bitmap.getPixel(width - 1, y)
        }

        val backgroundRed =
            border.map { Color.red(it) }.average()

        val backgroundGreen =
            border.map { Color.green(it) }.average()

        val backgroundBlue =
            border.map { Color.blue(it) }.average()

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val pixel = bitmap.getPixel(x, y)

                val alpha = Color.alpha(pixel)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                val distance =
                    abs(red - backgroundRed) +
                    abs(green - backgroundGreen) +
                    abs(blue - backgroundBlue)

                val foreground =
                    alpha > 80 && distance > 55

                if (foreground) {
                    points += x to y
                }
            }
        }

        if (points.size < 8) {
            return null
        }

        val centerX =
            points.map { it.first }.average()

        val centerY =
            points.map { it.second }.average()

        val tip =
            points.maxByOrNull {
                val dx = it.first - centerX
                val dy = it.second - centerY

                dx * dx + dy * dy
            } ?: return null

        val dx = tip.first - centerX
        val dy = tip.second - centerY

        if (abs(dx) < 2 && abs(dy) < 2) {
            return null
        }

        return when {
            abs(dx) > abs(dy) * 1.8 ->
                if (dx < 0) {
                    NavigationDirection.LEFT
                } else {
                    NavigationDirection.RIGHT
                }

            dy < 0 && abs(dy) > abs(dx) * 1.8 ->
                NavigationDirection.STRAIGHT

            dy < 0 && dx < 0 ->
                NavigationDirection.SLIGHT_LEFT

            dy < 0 && dx > 0 ->
                NavigationDirection.SLIGHT_RIGHT

            else -> null
        }
    }
}