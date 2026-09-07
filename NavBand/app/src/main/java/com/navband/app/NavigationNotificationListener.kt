package com.navband.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NavigationNotificationListener :
    NotificationListenerService() {

    private lateinit var forwarder:
        NotificationForwarder

    override fun onCreate() {

        super.onCreate()

        forwarder =
            NotificationForwarder(this)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        if (!isNavigationApp(sbn.packageName)) {
            return
        }

        val notification =
            sbn.notification ?: return

        val extras =
            notification.extras ?: return

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

        val image =
            ImageExtractor.extract(
                notification
            )

        val event =
            NavigationParser.parse(
                title = title,
                text = text,
                subText = subText,
                image = image
            ) ?: return

        forwarder.send(event)
    }

    private fun isNavigationApp(
        packageName: String
    ): Boolean {

        return packageName ==
            "com.google.android.apps.maps" ||
            packageName ==
            "com.waze"
    }
}
