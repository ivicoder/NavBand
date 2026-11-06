package com.navband.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Session-based navigation diagnostics shared by the UI and NotificationListenerService.
 * A session starts explicitly from the NavBand screen and is exported as a plain TXT file.
 */
object NavigationDebugLog {
    private const val PREFS = "navband_debug"
    private const val KEY_SESSION_ACTIVE = "navigation_session_active"
    private const val LOG_FILE = "navigation_session.log"
    private const val MAX_LOG_CHARS = 1_800_000

    private val lock = Any()

    fun isSessionActive(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SESSION_ACTIVE, false)

    fun startSession(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val file = File(appContext.filesDir, LOG_FILE)
            file.writeText(
                "=== NAVBAND NAVIGATION SESSION ===\n" +
                    "START ${timestamp()}\n" +
                    "Device Android SDK: ${android.os.Build.VERSION.SDK_INT}\n\n",
                Charsets.UTF_8
            )
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SESSION_ACTIVE, true)
                .apply()
        }
    }

    fun stopSession(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (isSessionActive(appContext)) {
                writeLineLocked(
                    appContext,
                    "[${timestamp()}][SESSION] NAVIGAZIONE TERMINATA DALL'UTENTE"
                )
            }
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SESSION_ACTIVE, false)
                .apply()
        }
    }

    fun append(context: Context, component: String, message: String) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (!isSessionActive(appContext)) return
            val safeMessage = message.takeLast(60_000)
            writeLineLocked(
                appContext,
                "[${timestamp()}][${component.uppercase(Locale.ROOT)}] $safeMessage"
            )
        }
    }

    fun read(context: Context): String {
        val appContext = context.applicationContext
        synchronized(lock) {
            val file = File(appContext.filesDir, LOG_FILE)
            if (!file.exists()) return "Nessuna sessione registrata."
            return try {
                file.readText(Charsets.UTF_8)
            } catch (error: Exception) {
                "Errore lettura log: ${error.javaClass.simpleName}: ${error.message}"
            }
        }
    }

    private fun writeLineLocked(context: Context, line: String) {
        val file = File(context.filesDir, LOG_FILE)
        file.appendText("\n$line\n", Charsets.UTF_8)

        if (file.length() > MAX_LOG_CHARS) {
            val current = file.readText(Charsets.UTF_8)
            val kept = current.takeLast(MAX_LOG_CHARS)
            file.writeText(
                "=== LOG RIDOTTO: parte iniziale rimossa per limite dimensione ===\n$kept",
                Charsets.UTF_8
            )
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ITALY)
            .format(Date())
}
