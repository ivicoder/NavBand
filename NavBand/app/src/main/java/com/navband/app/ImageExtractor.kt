package com.navband.app

import android.app.Notification
import android.graphics.Bitmap

object ImageExtractor {

    fun extract(notification: Notification): Bitmap? {
        val extras = notification.extras ?: return null

        val picture = extras.get(Notification.EXTRA_PICTURE)

        if (picture is Bitmap) {
            return picture
        }

        val icon = extras.get(Notification.EXTRA_LARGE_ICON)

        if (icon is Bitmap) {
            return icon
        }

        return null
    }
}
