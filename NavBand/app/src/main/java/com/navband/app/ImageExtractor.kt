package com.navband.app

import android.app.Notification
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle

object ImageExtractor {

    fun extract(notification: Notification): Bitmap? {
        val extras: Bundle = notification.extras ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val picture = extras.getParcelable(
                Notification.EXTRA_PICTURE,
                Bitmap::class.java
            )

            if (picture != null) {
                return picture
            }

            val icon = extras.getParcelable(
                Notification.EXTRA_LARGE_ICON,
                Bitmap::class.java
            )

            if (icon != null) {
                return icon
            }

        } else {

            @Suppress("DEPRECATION")
            val picture = extras.getParcelable(
                Notification.EXTRA_PICTURE
            )

            if (picture is Bitmap) {
                return picture
            }

            @Suppress("DEPRECATION")
            val icon = extras.getParcelable(
                Notification.EXTRA_LARGE_ICON
            )

            if (icon is Bitmap) {
                return icon
            }
        }

        return null
    }
}
