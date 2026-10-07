package com.navband.app.roundabout

/**
 * Sorgente dei dati geospaziali necessari al resolver.
 *
 * Il resolver non conosce la provenienza dei dati:
 * in futuro potranno arrivare da OnlineValhallaProvider
 * oppure da un OfflineProvider.
 */
interface RoundaboutDataProvider {

    fun loadContext(
        current: GeoPoint,
        targetRoad: String,
        headingDegrees: Double? = null,
        distanceMeters: Double? = null
    ): RoundaboutContext?
}
