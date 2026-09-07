package com.navband.app

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build

object ImageExtractor {

    fun extract(
        context: Context,
        notification: Notification
    ): Bitmap? {

        val extras = notification.extras ?: return null

        // Google Maps usa android.largeIcon come Icon.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            val icon = extras.getParcelable(
                Notification.EXTRA_LARGE_ICON,
                Icon::class.java
            )

            if (icon != null) {
                try {
                    val drawable = icon.loadDrawable(context)

                    if (drawable != null) {

                        val width =
                            if (drawable.intrinsicWidth > 0) {
                                drawable.intrinsicWidth
                            } else {
                                90
                            }

                        val height =
                            if (drawable.intrinsicHeight > 0) {
                                drawable.intrinsicHeight
                            } else {
                                90
                            }

                        val bitmap = Bitmap.createBitmap(
                            width,
                            height,
                            Bitmap.Config.ARGB_8888
                        )

                        val canvas = Canvas(bitmap)

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
                    // Se l'Icon non può essere convertita,
                    // proviamo gli altri formati.
                }
            }
        }

        // Compatibilità con notifiche che forniscono
        // direttamente una Bitmap.
        @Suppress("DEPRECATION")
        val picture = extras.get(Notification.EXTRA_PICTURE)

        if (picture is Bitmap) {
            return picture
        }

        return null
    }
}
