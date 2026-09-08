package com.navband.app

import android.graphics.Bitmap
import java.util.Locale

object NavigationParser {

    private val distanceRegex =
        Regex(
            """(?i)\b\d+(?:[.,]\d+)?\s*(?:m|km|ft|mi)\b"""
        )

    private val numericExitRegex =
        Regex(
            """(?i)(?:uscita|exit|salida|sortie)\s*(?:n\.?|numero)?\s*(\d+)"""
        )

    private val reverseExitRegex =
        Regex(
            """(?i)\b(\d+)\s*(?:ª|a|°|º)?\s*(?:uscita|exit)\b"""
        )

    private val ordinalExitRegex =
        Regex(
            """(?i)\b(prima|seconda|terza|quarta|quinta|sesta|settima|ottava)\s+(?:uscita|exit)\b"""
        )

    fun parse(
        title: String?,
        text: String?,
        subText: String?,
        image: Bitmap? = null
    ): NavigationEvent? {

        val instruction =
            text?.trim()
                ?: title?.trim()
                ?: ""

        if (
            instruction.isBlank() &&
            subText.isNullOrBlank() &&
            title.isNullOrBlank()
        ) {
            return null
        }

        val source =
            listOf(
                title,
                text,
                subText
            )
                .filterNotNull()
                .joinToString(" ")
                .trim()

        val normalized =
            source
                .lowercase(Locale.ITALIAN)
                .replace("’", "'")
                .replace("º", "°")

        val distance =
            distanceRegex
                .find(source)
                ?.value
                ?: ""

        val exit =
            findExit(normalized)

        val isRoundabout =
            containsAny(
                normalized,
                "rotatoria",
                "rotonda",
                "roundabout",
                "round about",
                "alla rotonda",
                "in rotatoria"
            )

        val direction =
            when {

                isRoundabout ->
                    NavigationDirection.ROUNDABOUT

                containsAny(
                    normalized,
                    "inversione a u",
                    "inversione",
                    "u-turn",
                    "u turn",
                    "fai inversione"
                ) ->
                    NavigationDirection.U_TURN

                containsAny(
                    normalized,
                    "svolta a sinistra",
                    "gira a sinistra",
                    "turn left",
                    "left turn",
                    "a sinistra"
                ) ->
                    NavigationDirection.LEFT

                containsAny(
                    normalized,
                    "svolta a destra",
                    "gira a destra",
                    "turn right",
                    "right turn",
                    "a destra"
                ) ->
                    NavigationDirection.RIGHT

                containsAny(
                    normalized,
                    "leggermente a sinistra",
                    "mantieni la sinistra",
                    "tieni la sinistra",
                    "slight left"
                ) ->
                    NavigationDirection.SLIGHT_LEFT

                containsAny(
                    normalized,
                    "leggermente a destra",
                    "mantieni la destra",
                    "tieni la destra",
                    "slight right"
                ) ->
                    NavigationDirection.SLIGHT_RIGHT

                containsAny(
                    normalized,
                    "prosegui",
                    "continua dritto",
                    "vai dritto",
                    "straight",
                    "keep straight"
                ) ->
                    NavigationDirection.STRAIGHT

                else ->
                    NavigationDirection.UNKNOWN
            }

        return NavigationEvent(
            direction = direction,
            distance = distance,
            instruction = instruction,
            subText = subText ?: "",
            roundaboutExit = exit,
            image = image
        )
    }

    private fun findExit(
        text: String
    ): Int? {

        numericExitRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let {
                return it
            }

        reverseExitRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let {
                return it
            }

        val ordinal =
            ordinalExitRegex
                .find(text)
                ?.groupValues
                ?.getOrNull(1)

        return when (ordinal) {
            "prima" -> 1
            "seconda" -> 2
            "terza" -> 3
            "quarta" -> 4
            "quinta" -> 5
            "sesta" -> 6
            "settima" -> 7
            "ottava" -> 8
            else -> null
        }
    }

    private fun containsAny(
        text: String,
        vararg values: String
    ): Boolean {
        return values.any {
            text.contains(it)
        }
    }
}
