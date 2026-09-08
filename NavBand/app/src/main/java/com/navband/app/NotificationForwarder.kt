package com.navband.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.RemoteViews
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

                if (event.distance.isNotBlank()) {
                    append(event.distance)
                }

                if (event.instruction.isNotBlank()) {
                    if (isNotEmpty()) {
                        append("\n")
                    }

                    append(event.instruction)
                }

                if (isEmpty()) {
                    append(arrow)
                }
            }

        /*
         * Layout personalizzato della notifica.
         *
         * La freccia di Google Maps viene inserita
         * direttamente nella ImageView.
         */
        val remoteViews =
            RemoteViews(
                context.packageName,
                R.layout.notification_navigation
            )

        remoteViews.setTextViewText(
            R.id.notification_navigation_title,
            title
        )

        remoteViews.setTextViewText(
            R.id.notification_navigation_text,
            text
        )

        val image: Bitmap? =
            event.image

        if (image != null) {

            remoteViews.setImageViewBitmap(
                R.id.notification_navigation_image,
                image
            )

        } else {

            /*
             * Se Google Maps non fornisce un'immagine,
             * utilizziamo comunque la freccia testuale
             * come fallback.
             */
            remoteViews.setTextViewText(
                R.id.notification_navigation_text,
                "$arrow  $text"
            )
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
                .setCustomContentView(
                    remoteViews
                )
                .setCustomBigContentView(
                    remoteViews
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setCategory(
                    NotificationCompat.CATEGORY_NAVIGATION
                )

        /*
         * IMPORTANTE:
         *
         * Non utilizziamo setLargeIcon().
         * La Bitmap viene inserita direttamente
         * nella ImageView della RemoteViews.
         */

        manager.notify(
            NOTIFICATION_ID,
            builder.build()
        )
    }
}
