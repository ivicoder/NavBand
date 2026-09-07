package com.navband.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationForwarder(
    private val context: Context
) {

    companion object {

        private const val CHANNEL =
            "navband_navigation"

        private const val NOTIFICATION_ID =
            5000
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

            val channel =
                NotificationChannel(
                    CHANNEL,
                    "NavBand navigazione",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.setSound(null, null)
            channel.enableVibration(false)

            manager.createNotificationChannel(
                channel
            )
        }
    }

    fun send(
        event: NavigationEvent
    ) {

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

        val text =
            buildString {

                append(arrow)

                if (
                    event.distance.isNotBlank()
                ) {
                    append("   ")
                    append(event.distance)
                }

                if (
                    event.instruction.isNotBlank()
                ) {
                    append("\n")
                    append(event.instruction)
                }
            }

        val builder =
            NotificationCompat
                .Builder(
                    context,
                    CHANNEL
                )
                .setSmallIcon(
                    R.drawable.ic_navband
                )
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(text)
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_LOW
                )
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setCategory(
                    NotificationCompat
                        .CATEGORY_NAVIGATION
                )

        val image: Bitmap? =
            event.image

        if (image != null) {
            builder.setLargeIcon(image)
        }

        manager.notify(
            NOTIFICATION_ID,
            builder.build()
        )
    }
}
