package com.navband.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationForwarder(
    private val context: Context
) {

    companion object {
        private const val CHANNEL = "navband_test"
        private const val NOTIFICATION_ID = 5000
    }

    private val manager =
        context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "NavBand navigazione",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            manager.createNotificationChannel(channel)
        }
    }

    fun send(event: NavigationEvent) {

        val arrow =
            when (event.direction) {
                NavigationDirection.LEFT -> "←"
                NavigationDirection.RIGHT -> "→"
                NavigationDirection.SLIGHT_LEFT -> "↖"
                NavigationDirection.SLIGHT_RIGHT -> "↗"
                NavigationDirection.STRAIGHT -> "↑"
                NavigationDirection.U_TURN -> "↶"
                NavigationDirection.ROUNDABOUT -> "⟳"
                NavigationDirection.UNKNOWN -> "•"
            }

        val title =
            if (
                event.direction ==
                    NavigationDirection.ROUNDABOUT &&
                event.roundaboutExit != null
            ) {
                "Rotatoria • " +
                    event.roundaboutExit +
                    "ª uscita"
            } else {
                "Navigazione"
            }

        val distanceText =
            event.distance.ifBlank {
                arrow
            }

        val instructionText = event.instruction
        val subText = event.subText

        val text = buildString {
            append(distanceText)

            if (instructionText.isNotBlank()) {
                append("\n")
                append(instructionText)
            }

            if (subText.isNotBlank()) {
                append("\n")
                append(subText)
            }
        }

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_navband)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(text)
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setCategory(
                    NotificationCompat.CATEGORY_NAVIGATION
                )

        manager.notify(
            NOTIFICATION_ID,
            builder.build()
        )
    }

    fun sendStandardTest() {

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_navband)
                .setContentTitle("NavBand test")
                .setContentText(
                    "Notifica Android standard"
                )
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .setAutoCancel(false)

        manager.notify(
            NOTIFICATION_ID,
            builder.build()
        )
    }
}
