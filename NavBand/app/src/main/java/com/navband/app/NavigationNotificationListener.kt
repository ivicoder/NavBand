package com.navband.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NavigationNotificationListener : NotificationListenerService() {

    private lateinit var forwarder: NotificationForwarder

    override fun onCreate() {
        super.onCreate()
        forwarder = NotificationForwarder(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (sbn == null) {
            return
        }

        // Evita di elaborare le notifiche generate da NavBand stessa.
        if (sbn.packageName == packageName) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val subText = extras.getCharSequence("android.subText")?.toString()

        val image = ImageExtractor.extract(notification)

        val event = NavigationParser.parse(
            title = title,
            text = text,
            subText = subText,
            image = image
        ) ?: return

        // Ignora notifiche che non sembrano contenere
        // informazioni di navigazione.
        if (event.direction == NavigationDirection.UNKNOWN &&
            event.distance.isBlank() &&
            event.instruction.isBlank()
        ) {
            return
        }

        forwarder.send(event)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
