package com.navband.app

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build

object ImageExtractor {

    fun extract(notification: Notification): Bitmap? {
        val extras = notification.extras ?: return null

        val largeIcon = extras.get(Notification.EXTRA_LARGE_ICON)

        if (largeIcon is Bitmap) {
            return prepareBitmap(largeIcon)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (largeIcon is Icon) {
                try {
                    val drawable = largeIcon.loadDrawable(null)

                    if (drawable != null) {
                        val width =
                            drawable.intrinsicWidth.coerceAtLeast(90)

                        val height =
                            drawable.intrinsicHeight.coerceAtLeast(90)

                        val bitmap =
                            Bitmap.createBitmap(
                                width,
                                height,
                                Bitmap.Config.ARGB_8888
                            )

                        val canvas = Canvas(bitmap)

                        // Sfondo scuro per rendere visibili
                        // eventuali icone/frecce bianche.
                        canvas.drawColor(Color.rgb(30, 30, 30))

                        drawable.setBounds(
                            0,
                            0,
                            canvas.width,
                            canvas.height
                        )

                        drawable.draw(canvas)

                        return bitmap
                    }
                } catch (_: Exception) {
                    // Icon non convertibile.
                }
            }
        }

        val picture =
            extras.get(Notification.EXTRA_PICTURE)

        if (picture is Bitmap) {
            return prepareBitmap(picture)
        }

        return null
    }

    private fun prepareBitmap(source: Bitmap): Bitmap {
        val width = source.width.coerceAtLeast(90)
        val height = source.height.coerceAtLeast(90)

        val bitmap =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.rgb(30, 30, 30))

        val left =
            (width - source.width) / 2f

        val top =
            (height - source.height) / 2f

        canvas.drawBitmap(
            source,
            left,
            top,
            null
        )

        return bitmap
    }
}
