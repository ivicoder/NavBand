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
            """(?i)\b(prima|seconda|terza|quarta|quinta|sesta|settima|ottava|nona|decima)\s+(?:uscita|exit)\b"""
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

        /*
         * Uniamo tutti i campi perché Google Maps
         * può distribuire le informazioni tra title,
         * text e subText.
         */
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
                .replace(Regex("\\s+"), " ")

        val distance =
            distanceRegex
                .find(source)
                ?.value
                ?: ""

        val exit =
            findExit(normalized)

        /*
         * La rotatoria ha priorità assoluta.
         *
         * Se troviamo "rotatoria", "rotonda" o
         * "roundabout", l'evento viene classificato
         * come ROUNDABOUT anche se nel testo compare
         * una parola come "destra" o "sinistra".
         */
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

                /*
                 * INVERSIONE
                 */
                containsAny(
                    normalized,
                    "inversione a u",
                    "inversione a u",
                    "fai inversione",
                    "fai un'inversione",
                    "fai una inversione",
                    "inversione",
                    "u-turn",
                    "u turn",
                    "u turn"
                ) ->
                    NavigationDirection.U_TURN

                /*
                 * LEGGERA SINISTRA
                 *
                 * Deve essere controllata PRIMA di
                 * "a sinistra", altrimenti verrebbe
                 * classificata come LEFT.
                 */
                containsAny(
                    normalized,
                    "leggermente a sinistra",
                    "leggera sinistra",
                    "leggero sinistra",
                    "mantieni la sinistra",
                    "tieni la sinistra",
                    "mantieni a sinistra",
                    "tieni a sinistra",
                    "leggermente verso sinistra",
                    "slight left",
                    "slightly left",
                    "keep left",
                    "keep to the left"
                ) ->
                    NavigationDirection.SLIGHT_LEFT

                /*
                 * LEGGERA DESTRA
                 */
                containsAny(
                    normalized,
                    "leggermente a destra",
                    "leggera destra",
                    "leggero destra",
                    "mantieni la destra",
                    "tieni la destra",
                    "mantieni a destra",
                    "tieni a destra",
                    "leggermente verso destra",
                    "slight right",
                    "slightly right",
                    "keep right",
                    "keep to the right"
                ) ->
                    NavigationDirection.SLIGHT_RIGHT

                /*
                 * SINISTRA
                 */
                containsAny(
                    normalized,
                    "svolta a sinistra",
                    "svolta sinistra",
                    "gira a sinistra",
                    "gira sinistra",
                    "turn left",
                    "left turn",
                    "a sinistra"
                ) ->
                    NavigationDirection.LEFT

                /*
                 * DESTRA
                 */
                containsAny(
                    normalized,
                    "svolta a destra",
                    "svolta destra",
                    "gira a destra",
                    "gira destra",
                    "turn right",
                    "right turn",
                    "a destra"
                ) ->
                    NavigationDirection.RIGHT

                /*
                 * DRITTO
                 */
                containsAny(
                    normalized,
                    "prosegui dritto",
                    "prosegui",
                    "continua dritto",
                    "continua diritto",
                    "vai dritto",
                    "vai diritto",
                    "sempre dritto",
                    "sempre diritto",
                    "dritto",
                    "diritto",
                    "straight",
                    "go straight",
                    "keep straight",
                    "continue straight"
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

        /*
         * Esempi:
         *
         * uscita 3
         * uscita n. 3
         * uscita numero 3
         * exit 3
         */
        numericExitRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let {
                return it
            }

        /*
         * Esempi:
         *
         * 3ª uscita
         * 3a uscita
         * 3° uscita
         * 3 uscita
         * 3 exit
         */
        reverseExitRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let {
                return it
            }

        /*
         * Esempi:
         *
         * prima uscita
         * seconda uscita
         * terza uscita
         * ...
         */
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
            "nona" -> 9
            "decima" -> 10
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
