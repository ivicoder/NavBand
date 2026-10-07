package com.navband.app

import com.navband.app.roundabout.RoundaboutRuntime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import com.navband.app.roundabout.RoundaboutRuntime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

        // TEMPORARY DIAGNOSTIC: Android Auto navigation notification.
        private const val ANDROID_AUTO_PACKAGE =
            "com.google.android.projection.gearhead"

        private const val ANDROID_AUTO_NAV_NOTIFICATION_ID = 2

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
    private lateinit var roundaboutRuntime: RoundaboutRuntime
    private val roundaboutExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var roundaboutRuntime: RoundaboutRuntime
    private val roundaboutExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()

        forwarder =
            NotificationForwarder(this)
        roundaboutRuntime = RoundaboutRuntime(this)
        roundaboutRuntime =
            RoundaboutRuntime(this)
    }

    override fun onDestroy() {
        roundaboutExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onDestroy() {
        roundaboutExecutor.shutdownNow()
        super.onDestroy()
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
         * TEMPORARY ANDROID AUTO DIAGNOSTIC
         *
         * Inspect ONLY Android Auto navigation notification ID 2.
         * Nothing is parsed, vibrated or forwarded from this branch.
         */
        if (
            packageName == ANDROID_AUTO_PACKAGE &&
            sbn.id == ANDROID_AUTO_NAV_NOTIFICATION_ID
        ) {
            val result = StringBuilder()

            result.append("ANDROID AUTO NAVIGATION NOTIFICATION\n")
            result.append("PACKAGE\n$packageName\n\n")
            result.append("ID\n${sbn.id}\n\n")
            result.append("KEY\n${sbn.key}\n\n")
            result.append("ONGOING\n${sbn.isOngoing}\n\n")
            result.append("POST_TIME\n${sbn.postTime}\n\n")
            result.append("TITLE\n${title ?: "null"}\n\n")
            result.append("TEXT\n${text ?: "null"}\n\n")
            result.append("SUBTEXT\n${subText ?: "null"}\n\n")
            result.append("EXTRAS\n")

            fun dump(label: String, value: Any?, indent: String = "", depth: Int = 0) {
                result.append(indent).append(label).append("\n")
                if (value == null) {
                    result.append(indent).append("null\n\n")
                    return
                }
                result.append(indent).append("TYPE: ").append(value.javaClass.name).append("\n")
                if (depth >= 8) {
                    result.append(indent).append("<max depth>\n\n")
                    return
                }
                when (value) {
                    is android.os.Bundle -> {
                        for (k in value.keySet().sorted()) {
                            try { dump(k, value.get(k), "$indent  ", depth + 1) }
                            catch (e: Exception) {
                                result.append(indent).append("  ").append(k)
                                    .append("\n    <errore: ").append(e.javaClass.name).append(">\n")
                            }
                        }
                    }
                    is ByteArray -> {
                        result.append(indent).append("VALUE_HEX: ")
                            .append(value.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
                            .append("\n")
                        result.append(indent).append("VALUE_SIZE: ").append(value.size).append("\n\n")
                    }
                    is ShortArray -> result.append(indent).append("VALUE: ").append(value.joinToString(",")).append("\n\n")
                    is IntArray -> result.append(indent).append("VALUE: ").append(value.joinToString(",")).append("\n\n")
                    is LongArray -> result.append(indent).append("VALUE: ").append(value.joinToString(",")).append("\n\n")
                    is FloatArray -> result.append(indent).append("VALUE: ").append(value.joinToString(",")).append("\n\n")
                    is DoubleArray -> result.append(indent).append("VALUE: ").append(value.joinToString(",")).append("\n\n")
                    is BooleanArray -> result.append(indent).append("VALUE: ").append(value.joinToString(",")).append("\n\n")
                    is Array<*> -> value.forEachIndexed { i, v -> dump("[$i]", v, "$indent  ", depth + 1) }
                    is java.util.ArrayList<*> -> value.forEachIndexed { i, v -> dump("[$i]", v, "$indent  ", depth + 1) }
                    else -> result.append(indent).append("VALUE: ").append(value.toString()).append("\n\n")
                }
            }

            for (key in extras.keySet().sorted()) {
                try { dump(key, extras.get(key)) }
                catch (e: Exception) {
                    result.append(key).append("\n<errore: ").append(e.javaClass.name).append(">\n\n")
                }
            }

            result.append("SPECIAL CHECKS\n")
            dump("android.textLines", extras.get(Notification.EXTRA_TEXT_LINES))
            dump("android.title.big", extras.get(Notification.EXTRA_TITLE_BIG))
            dump("android.summaryText", extras.get(Notification.EXTRA_SUMMARY_TEXT))
            dump("android.template", extras.get(Notification.EXTRA_TEMPLATE))
            dump("contentView", notification.contentView)
            dump("bigContentView", notification.bigContentView)
            dump("headsUpContentView", notification.headsUpContentView)

            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DEBUG, result.toString())
                .apply()

            Log.d("NavBandAndroidAuto", "Captured Android Auto notification ID 2")

            // Do NOT parse, vibrate or forward Android Auto data.
            return
        }

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

        if (event.direction == NavigationDirection.ROUNDABOUT) {
            roundaboutExecutor.execute {
                val resolvedEvent = try {
                    roundaboutRuntime.resolve(event)
                } catch (_: Exception) {
                    event
                }

                VibrationEngine.vibrate(
                    context = this,
                    event = resolvedEvent
                )
                forwarder.send(resolvedEvent)
            }
        } else {
            VibrationEngine.vibrate(
                context = this,
                event = event
            )
            forwarder.send(event)
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?
    ) {
        super.onNotificationRemoved(sbn)
    }
}
