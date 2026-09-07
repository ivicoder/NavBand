package com.navband.app

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NavigationNotificationListener : NotificationListenerService() {

    companion object {
        private const val PREFS = "navband_debug"
        private const val KEY_DEBUG = "notification_debug"

        private const val MAPS_PACKAGE =
            "com.google.android.apps.maps"

        fun getDebug(context: Context): String {
            return context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(
                    KEY_DEBUG,
                    "Nessuna notifica ricevuta."
                ) ?: ""
        }
    }

    private lateinit var forwarder: NotificationForwarder

    override fun onCreate() {
        super.onCreate()

        forwarder = NotificationForwarder(this)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?
    ) {
        super.onNotificationPosted(sbn)

        if (sbn == null) return

        val notification = sbn.notification
        val extras = notification.extras ?: return

        val packageName = sbn.packageName

        val title =
            extras
                .getCharSequence(
                    Notification.EXTRA_TITLE
                )
                ?.toString()

        val text =
            extras
                .getCharSequence(
                    Notification.EXTRA_TEXT
                )
                ?.toString()

        val subText =
            extras
                .getCharSequence(
                    Notification.EXTRA_SUB_TEXT
                )
                ?.toString()

        /*
         * DEBUG
         */

        val result = StringBuilder()

        result.append("PACKAGE\n")
        result.append(packageName)
        result.append("\n\n")

        result.append("TITLE\n")
        result.append(title ?: "null")
        result.append("\n\n")

        result.append("TEXT\n")
        result.append(text ?: "null")
        result.append("\n\n")

        result.append("SUBTEXT\n")
        result.append(subText ?: "null")
        result.append("\n\n")

        result.append("EXTRAS\n")

        for (key in extras.keySet()) {
            try {
                val value = extras.get(key)

                result.append("\n")
                result.append(key)
                result.append("\n")

                if (value == null) {
                    result.append("null")
                } else {
                    result.append(value.javaClass.name)
                    result.append("\n")
                    result.append(value.toString())
                }

                result.append("\n")
            } catch (_: Exception) {
                result.append("\n")
                result.append(key)
                result.append("\n<errore lettura>\n")
            }
        }

        getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_DEBUG,
                result.toString()
            )
            .apply()

        /*
         * ELABORAZIONE NAVIGAZIONE
         *
         * Per ora elaboriamo esclusivamente
         * le notifiche provenienti da Google Maps.
         */

        if (packageName != MAPS_PACKAGE) {
            return
        }

        val image =
            ImageExtractor.extract(notification)

        val event =
            NavigationParser.parse(
                title = title,
                text = text,
                subText = subText,
                image = image
            )
            ?: return

        forwarder.send(event)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?
    ) {
        super.onNotificationRemoved(sbn)
    }
}
