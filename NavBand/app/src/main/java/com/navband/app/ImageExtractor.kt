package com.navband.app

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build

object ImageExtractor {

    fun extract(notification: Notification): Bitmap? {
        val extras = notification.extras ?: return null

        val largeIcon = extras.get(Notification.EXTRA_LARGE_ICON)

        if (largeIcon is Bitmap) {
            return largeIcon
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (largeIcon is Icon) {
                try {
                    val drawable = largeIcon.loadDrawable(null)

                    if (drawable != null) {
                        val bitmap = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )

                        val canvas = android.graphics.Canvas(bitmap)

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
                    // Se l'icona non può essere convertita, continuiamo
                }
            }
        }

        val picture = extras.get(Notification.EXTRA_PICTURE)

        if (picture is Bitmap) {
            return picture
        }

        return null
    }
}
