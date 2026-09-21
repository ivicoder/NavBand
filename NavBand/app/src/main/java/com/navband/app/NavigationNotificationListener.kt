package com.navband.app

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import android.service.notification.StatusBarNotification

class NavigationNotificationListener : NotificationListenerService() {

    companion object {

        private const val PREFS = "navband_debug"

        private const val KEY_DEBUG =
            "notification_debug"

        private const val MAPS_PACKAGE =
            "com.google.android.apps.maps"

        private const val NAVBAND_PACKAGE =
            "com.navband.app"

        fun getDebug(context: Context): String {
            return context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_DEBUG,
                    "Nessuna notifica ricevuta."
                ) ?: ""
        }
    }

    private lateinit var forwarder: NotificationForwarder

    override fun onCreate() {
        super.onCreate()

        forwarder =
            NotificationForwarder(this)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?
    ) {
        super.onNotificationPosted(sbn)

        if (sbn == null) return

        val packageName =
            sbn.packageName

        if (packageName == NAVBAND_PACKAGE) {
            return
        }

        val notification =
            sbn.notification

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

        /*
         * Elaboriamo soltanto le notifiche
         * provenienti da Google Maps.
         */

        if (packageName != MAPS_PACKAGE) {
            return
        }

        /*
         * IMAGE EXTRACTION
         */

        val image =
            ImageExtractor.extract(
                context = this,
                notification = notification
            )

        /*
         * Salva temporaneamente la Bitmap estratta
         * per poterla visualizzare nella schermata debug.
         */

        if (image != null) {
            try {
                openFileOutput(
                    "debug_navigation_image.png",
                    Context.MODE_PRIVATE
                ).use { output ->
                    image.compress(
                        android.graphics.Bitmap.CompressFormat.PNG,
                        100,
                        output
                    )
                }
            } catch (_: Exception) {
            }
        }

        /*
         * DEBUG
         */

        val result =
            StringBuilder()

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

        result.append("IMAGE DEBUG\n")

        if (image != null) {

            result.append("ImageExtractor result: PRESENT\n")
            result.append("Bitmap size: ")
            result.append(image.width)
            result.append(" x ")
            result.append(image.height)
            result.append("\n")

            result.append("Bitmap config: ")
            result.append(image.config)

        } else {

            result.append("ImageExtractor result: NULL")
        }

        result.append("\n\n")

        result.append("EXTRAS\n")

        fun appendDebugValue(
            label: String,
            value: Any?,
            indent: String = "",
            depth: Int = 0
        ) {
            result.append(indent)
            result.append(label)
            result.append("\n")

            if (value == null) {
                result.append(indent)
                result.append("null\n\n")
                return
            }

            result.append(indent)
            result.append(value.javaClass.name)
            result.append("\n")

            if (depth >= 6) {
                result.append(indent)
                result.append("<max depth>\n\n")
                return
            }

            when (value) {
                is android.os.Bundle -> {
                    for (nestedKey in value.keySet().sorted()) {
                        try {
                            appendDebugValue(
                                label = nestedKey,
                                value = value.get(nestedKey),
                                indent = "$indent  ",
                                depth = depth + 1
                            )
                        } catch (e: Exception) {
                            result.append(indent)
                            result.append("  ")
                            result.append(nestedKey)
                            result.append("\n    <errore: ")
                            result.append(e.javaClass.name)
                            result.append(">\n")
                        }
                    }
                }

                is Array<*> -> {
                    for ((index, item) in value.withIndex()) {
                        appendDebugValue(
                            label = "[$index]",
                            value = item,
                            indent = "$indent  ",
                            depth = depth + 1
                        )
                    }
                }

                is java.util.ArrayList<*> -> {
                    for ((index, item) in value.withIndex()) {
                        appendDebugValue(
                            label = "[$index]",
                            value = item,
                            indent = "$indent  ",
                            depth = depth + 1
                        )
                    }
                }

                else -> {
                    result.append(indent)
                    result.append(value.toString())
                    result.append("\n")
                }
            }

            result.append("\n")
        }

        for (key in extras.keySet().sorted()) {
            try {
                appendDebugValue(
                    label = key,
                    value = extras.get(key)
                )
            } catch (e: Exception) {
                result.append(key)
                result.append("\n<errore: ")
                result.append(e.javaClass.name)
                result.append(">\n\n")
            }
        }

        result.append("SPECIAL CHECKS\n")

        appendDebugValue(
            label = "android.textLines",
            value = extras.get(Notification.EXTRA_TEXT_LINES)
        )

        appendDebugValue(
            label = "android.title.big",
            value = extras.get(Notification.EXTRA_TITLE_BIG)
        )

        appendDebugValue(
            label = "android.summaryText",
            value = extras.get(Notification.EXTRA_SUMMARY_TEXT)
        )

        appendDebugValue(
            label = "android.template",
            value = extras.get(Notification.EXTRA_TEMPLATE)
        )

        appendDebugValue(
            label = "contentView",
            value = notification.contentView
        )

        appendDebugValue(
            label = "bigContentView",
            value = notification.bigContentView
        )

        appendDebugValue(
            label = "headsUpContentView",
            value = notification.headsUpContentView
        )

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
         * PARSER
         */

        val parsedEvent =
            NavigationParser.parse(
                title = title,
                text = text,
                subText = subText,
                image = image
            ) ?: return

        /*
         * BITMAP CLASSIFIER
         *
         * Il parser testuale mantiene la priorita\u2019.
         * Usiamo la bitmap di Google Maps soltanto quando
         * il parser non ha riconosciuto una direzione.
         */
        val event =
            if (
                parsedEvent.direction == NavigationDirection.UNKNOWN &&
                parsedEvent.image != null
            ) {

                try {
                    val prediction =
                        ManeuverBitmapClassifier.classify(
                            context = this,
                            bitmap = parsedEvent.image
                        )

                    if (prediction != null) {
                        Log.d(
                            "NavBandBitmap",
                            "Classifier: label=${prediction.label}, " +
                                "direction=${prediction.direction}, " +
                                "confidence=${prediction.confidence}"
                        )

                        parsedEvent.copy(
                            direction = prediction.direction
                        )
                    } else {
                        val fallbackDirection =
                            ImageDirectionDetector.detect(parsedEvent.image)

                        Log.d(
                            "NavBandBitmap",
                            "Classifier: no confident result; fallback=$fallbackDirection"
                        )

                        if (fallbackDirection != null) {
                            parsedEvent.copy(
                                direction = fallbackDirection
                            )
                        } else {
                            parsedEvent
                        }
                    }
                } catch (error: Exception) {
                    Log.e(
                        "NavBandBitmap",
                        "Classifier error; keeping parser result",
                        error
                    )
                    parsedEvent
                }

            } else {
                parsedEvent
            }

        /*
         * VIBRATION
         *
         * La vibrazione viene generata direttamente
         * dall'evento finale, dopo l'eventuale classificazione bitmap.
         */

        VibrationEngine.vibrate(
            context = this,
            event = event
        )

        /*
         * FORWARDER
         */

        forwarder.send(event)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?
    ) {
        super.onNotificationRemoved(sbn)
    }
}
