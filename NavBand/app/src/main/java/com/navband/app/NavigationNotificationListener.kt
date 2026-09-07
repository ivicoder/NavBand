package com.navband.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NavigationNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NavBandDebug"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (sbn == null) return

        val notification = sbn.notification
        val extras = notification.extras ?: return

        val packageName = sbn.packageName

        val title =
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        val text =
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val subText =
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        Log.d(TAG, "================================")
        Log.d(TAG, "NOTIFICA RICEVUTA")
        Log.d(TAG, "package = $packageName")
        Log.d(TAG, "title = $title")
        Log.d(TAG, "text = $text")
        Log.d(TAG, "subText = $subText")
        Log.d(TAG, "extras = ${extras.keySet()}")

        for (key in extras.keySet()) {
            try {
                val value = extras.get(key)

                Log.d(
                    TAG,
                    "EXTRA [$key] = ${value?.javaClass?.name} : $value"
                )
            } catch (e: Exception) {
                Log.d(
                    TAG,
                    "EXTRA [$key] = <errore lettura: ${e.message}>"
                )
            }
        }

        Log.d(TAG, "================================")
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?
    ) {
        super.onNotificationRemoved(sbn)
    }
}
