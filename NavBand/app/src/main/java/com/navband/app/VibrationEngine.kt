package com.navband.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationEngine {

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager

            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun vibrate(context: Context, event: NavigationEvent) {
        val vibrator = vibrator(context) ?: return

        if (!vibrator.hasVibrator()) {
            return
        }

        val pattern = patternFor(context, event) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    pattern,
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun patternFor(
        context: Context,
        event: NavigationEvent
    ): LongArray? {

        return when (event.direction) {

            NavigationDirection.LEFT ->
                longArrayOf(0L, 180L)

            NavigationDirection.RIGHT ->
                longArrayOf(0L, 180L, 120L, 180L)

            NavigationDirection.SLIGHT_LEFT ->
                longArrayOf(0L, 100L)

            NavigationDirection.SLIGHT_RIGHT ->
                longArrayOf(0L, 100L, 100L, 100L)

            NavigationDirection.STRAIGHT ->
                longArrayOf(0L, 70L)

            NavigationDirection.U_TURN ->
                longArrayOf(
                    0L, 180L,
                    120L, 180L,
                    120L, 180L
                )

            NavigationDirection.ROUNDABOUT -> {
                if (!NavBandPreferences.roundaboutVibrationEnabled(context)) {
                    return null
                }

                roundaboutPattern(context, event.roundaboutExit)
            }

            NavigationDirection.UNKNOWN ->
                null
        }
    }

    private fun roundaboutPattern(
        context: Context,
        exit: Int?
    ): LongArray {

        val pause = NavBandPreferences.vibrationPauseMs(context)
            .coerceAtLeast(0L)

        val pulses = when {
            exit == null -> 1
            exit <= 1 -> 1
            exit == 2 -> 2
            exit == 3 -> 3
            exit == 4 -> 4
            exit == 5 -> 5
            else -> 6
        }

        val pattern = ArrayList<Long>()

        pattern.add(0L)

        repeat(pulses) { index ->
            pattern.add(160L)

            if (index < pulses - 1) {
                pattern.add(pause)
            }
        }

        return pattern.toLongArray()
    }
}
