package com.navband.app.roundabout

/**
 * Fornisce il contesto geospaziale reale necessario al resolver.
 *
 * L'implementazione concreta verrà collegata in seguito
 * alla sorgente Android/GPS/route disponibile.
 */
interface RoundaboutContextProvider {

    /**
     * Restituisce il contesto corrente oppure null
     * quando non ci sono dati geospaziali sufficienti.
     */
    fun currentContext(): RoundaboutContext?
}
