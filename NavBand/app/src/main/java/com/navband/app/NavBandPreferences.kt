package com.navband.app

import android.content.Context

object NavBandPreferences {

    private const val PREFS = "navband"

    private const val KEY_ROUNDABOUT =
        "roundabout_vibration"

    private const val KEY_PAUSE =
        "vibration_pause"

    private const val KEY_TRANSPORT =
        "transport"

    fun roundaboutVibrationEnabled(
        context: Context
    ): Boolean {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                KEY_ROUNDABOUT,
                true
            )
    }

    fun setRoundaboutVibration(
        context: Context,
        enabled: Boolean
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_ROUNDABOUT,
                enabled
            )
            .apply()
    }

    fun vibrationPauseMs(
        context: Context
    ): Long {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getLong(
                KEY_PAUSE,
                300L
            )
    }

    fun setVibrationPause(
        context: Context,
        value: Long
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putLong(
                KEY_PAUSE,
                value
            )
            .apply()
    }

    fun transport(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_TRANSPORT,
                "Mi Fitness"
            ) ?: "Mi Fitness"
    }

    fun setTransport(
        context: Context,
        value: String
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_TRANSPORT,
                value
            )
            .apply()
    }
}
