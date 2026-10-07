package com.navband.app.roundabout

import com.navband.app.NavigationEvent
import com.navband.app.NavigationDirection

/**
 * Ponte tra una decisione del resolver geometrico
 * e l'evento di navigazione già usato da NavBand.
 *
 * Questo adapter non acquisisce dati GPS, route o Valhalla
 * e non viene ancora inserito nel flusso della navigazione.
 */
object RoundaboutAdapter {

    /**
     * Applica una decisione geometrica a un NavigationEvent.
     *
     * Un risultato non risolto non modifica l'evento esistente.
     * Una decisione viene applicata solo a un evento ROUNDABOUT.
     */
    fun applyDecision(
        event: NavigationEvent,
        decision: RoundaboutDecision
    ): NavigationEvent {
        if (!decision.resolved || decision.exit == null) {
            return event
        }

        if (event.direction != NavigationDirection.ROUNDABOUT) {
            return event
        }

        return event.copy(
            roundaboutExit = decision.exit
        )
    }
}
