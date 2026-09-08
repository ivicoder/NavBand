package com.navband.app

import android.graphics.Bitmap
import java.util.Locale

object NavigationParser {

    /*
     * Distanze riconoscibili:
     *
     * 210 m
     * 1.2 km
     * 1,2 km
     * 500 ft
     * 2 mi
     */
    private val distanceRegex =
        Regex(
            """(?i)\b\d+(?:[.,]\d+)?\s*(?:m|km|ft|mi)\b"""
        )

    /*
     * Esempi:
     *
     * uscita 3
     * uscita n. 3
     * uscita numero 3
     * exit 3
     * exit no. 3
     * exit number 3
     */
    private val numericExitRegex =
        Regex(
            """(?i)\b(?:uscita|exit)\s*(?:n\.?|numero|number|no\.)?\s*(\d+)\b"""
        )

    /*
     * Esempi:
     *
     * 3ª uscita
     * 3a uscita
     * 3° uscita
     * 3º uscita
     * 3 uscita
     * 3 exit
     */
    private val reverseExitRegex =
        Regex(
            """(?i)\b(\d+)\s*(?:ª|a|°|º)?\s*(?:uscita|exit)\b"""
        )

    /*
     * Ordinali italiani:
     *
     * prima uscita
     * seconda uscita
     * terza uscita
     * ...
     * decima uscita
     */
    private val italianOrdinalExitRegex =
        Regex(
            """(?i)\b(prima|seconda|terza|quarta|quinta|sesta|settima|ottava|nona|decima)\s+(?:uscita|exit)\b"""
        )

    /*
     * Ordinali inglesi:
     *
     * first exit
     * second exit
     * third exit
     * ...
     * tenth exit
     */
    private val englishOrdinalExitRegex =
        Regex(
            """(?i)\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth)\s+exit\b"""
        )

    /*
     * Forme inglesi abbreviate:
     *
     * 1st exit
     * 2nd exit
     * 3rd exit
     * 4th exit
     */
    private val englishNumericOrdinalExitRegex =
        Regex(
            """(?i)\b(\d+)\s*(?:st|nd|rd|th)\s+exit\b"""
        )

    fun parse(
        title: String?,
        text: String?,
        subText: String?,
        image: Bitmap? = null
    ): NavigationEvent? {

        /*
         * Google Maps può distribuire le informazioni
         * tra title, text e subText.
         *
         * L'instruction continua a privilegiare il text,
         * come nella versione precedente.
         */
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
         * Uniamo tutti i campi per l'interpretazione.
         */
        val source =
            listOf(
                title,
                text,
                subText
            )
                .filterNotNull()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()

        /*
         * Normalizzazione usata solamente per il parsing.
         *
         * L'instruction originale NON viene modificata.
         */
        val normalized =
            source
                .lowercase(Locale.ITALIAN)
                .replace("’", "'")
                .replace("`", "'")
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
         * Se il testo contiene "rotatoria", "rotonda"
         * o "roundabout", l'evento è ROUNDABOUT anche
         * se nello stesso testo compare destra/sinistra.
         *
         * Questo è importante perché l'uscita della rotatoria
         * verrà gestita dal VibrationEngine in una fase separata.
         */
        val isRoundabout =
            containsAny(
                normalized,
                "rotatoria",
                "rotonda",
                "roundabout",
                "round about",
                "alla rotonda",
                "in rotatoria",
                "at the roundabout",
                "at roundabout"
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
                    "fai inversione",
                    "fai un'inversione",
                    "fai una inversione",
                    "inversione",
                    "u-turn",
                    "u turn",
                    "make a u-turn",
                    "make a u turn"
                ) ->
                    NavigationDirection.U_TURN

                /*
                 * LEGGERA SINISTRA
                 *
                 * Deve essere controllata prima di LEFT.
                 */
                containsAny(
                    normalized,
                    "leggermente a sinistra",
                    "leggera sinistra",
                    "leggero sinistra",
                    "leggermente verso sinistra",
                    "mantieni la sinistra",
                    "tieni la sinistra",
                    "mantieni a sinistra",
                    "tieni a sinistra",
                    "mantieni sulla sinistra",
                    "tieni sulla sinistra",
                    "slight left",
                    "slightly left",
                    "keep left",
                    "keep to the left",
                    "keep left at the fork"
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
                    "leggermente verso destra",
                    "mantieni la destra",
                    "tieni la destra",
                    "mantieni a destra",
                    "tieni a destra",
                    "mantieni sulla destra",
                    "tieni sulla destra",
                    "slight right",
                    "slightly right",
                    "keep right",
                    "keep to the right",
                    "keep right at the fork"
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
                    "vai a sinistra",
                    "turn left",
                    "left turn",
                    "turn to the left",
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
                    "vai a destra",
                    "turn right",
                    "right turn",
                    "turn to the right",
                    "a destra"
                ) ->
                    NavigationDirection.RIGHT

                /*
                 * DRITTO
                 */
                containsAny(
                    normalized,
                    "prosegui dritto",
                    "prosegui diritto",
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
                    "continue straight",
                    "continue on",
                    "go on"
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
         * Prima cerchiamo le forme esplicite:
         *
         * uscita 3
         * uscita n. 3
         * uscita numero 3
         * exit 3
         * exit number 3
         */
        numericExitRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let {
                return it
            }

        /*
         * Poi le forme numeriche inverse:
         *
         * 3ª uscita
         * 3a uscita
         * 3° uscita
         * 3º uscita
         * 3 uscita
         * 3 exit
         */
        reverseExitRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let {
                return it
            }

        /*
         * Ordinali inglesi abbreviati:
         *
         * 1st exit
         * 2nd exit
         * 3rd exit
         * 4th exit
         * ...
         */
        englishNumericOrdinalExitRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let {
                return it
            }

        /*
         * Ordinali italiani.
         */
        val italianOrdinal =
            italianOrdinalExitRegex
                .find(text)
                ?.groupValues
                ?.getOrNull(1)

        when (italianOrdinal) {
            "prima" -> return 1
            "seconda" -> return 2
            "terza" -> return 3
            "quarta" -> return 4
            "quinta" -> return 5
            "sesta" -> return 6
            "settima" -> return 7
            "ottava" -> return 8
            "nona" -> return 9
            "decima" -> return 10
        }

        /*
         * Ordinali inglesi.
         */
        val englishOrdinal =
            englishOrdinalExitRegex
                .find(text)
                ?.groupValues
                ?.getOrNull(1)

        return when (englishOrdinal) {
            "first" -> 1
            "second" -> 2
            "third" -> 3
            "fourth" -> 4
            "fifth" -> 5
            "sixth" -> 6
            "seventh" -> 7
            "eighth" -> 8
            "ninth" -> 9
            "tenth" -> 10
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
