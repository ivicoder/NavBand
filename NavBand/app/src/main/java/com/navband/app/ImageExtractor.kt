package com.navband.app

import android.app.Notification
import android.graphics.Bitmap
import android.os.Build

object ImageExtractor {

    fun extract(
        notification: Notification
    ): Bitmap? {

        val extras =
            notification.extras ?: return null

        if (Build.VERSION.SDK_INT >= 33) {

            extras.getParcelable(
                Notification.EXTRA_PICTURE,
                Bitmap::class.java
            )?.let {
                return it
            }

            extras.getParcelable(
                Notification.EXTRA_LARGE_ICON,
                Bitmap::class.java
            )?.let {
                return it
            }

        } else {

            @Suppress("DEPRECATION")
            val picture =
                extras.getParcelable(
                    Notification.EXTRA_PICTURE
                )

            if (picture is Bitmap) {
                return picture
            }

            @Suppress("DEPRECATION")
            val icon =
                extras.getParcelable(
                    Notification.EXTRA_LARGE_ICON
                )

            if (icon is Bitmap) {
                return icon
            }
        }

        return null
    }
}
