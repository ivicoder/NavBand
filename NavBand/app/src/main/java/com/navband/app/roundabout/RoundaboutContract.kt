package com.navband.app.roundabout

/**
 * Contratto indipendente da Android, UI e provider di navigazione.
 *
 * Il producer (futuro adapter NavBand) costruisce questo contesto.
 * Il resolver consuma il contesto e restituisce una decisione.
 */

data class GeoPoint(
    val lat: Double,
    val lon: Double
)

data class RoadEdgeEvidence(
    val edgeId: Long? = null,
    val wayId: Long? = null,
    val names: List<String> = emptyList(),
    val roundabout: Boolean = false,
    val auto: Boolean = true,
    val correlatedPoint: GeoPoint? = null,
    val probeRadiusMeters: Double? = null,
    val probeBearingDegrees: Double? = null,
    val use: String? = null,
    val classification: String? = null
)

data class NavigationLocationEvidence(
    val point: GeoPoint,
    val roadName: String? = null,
    val edges: List<RoadEdgeEvidence> = emptyList()
)

data class RoundaboutContext(
    val current: NavigationLocationEvidence,
    val target: NavigationLocationEvidence,
    val routePoints: List<GeoPoint> = emptyList(),
    val center: GeoPoint? = null,
    val radialEdges: List<RoadEdgeEvidence> = emptyList()
)

enum class RoundaboutConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class RoundaboutDecision(
    val resolved: Boolean,
    val exit: Int?,
    val confidence: RoundaboutConfidence,
    val score: Int,
    val targetRoad: String? = null,
    val matchedRoad: String? = null,
    val source: String = "roundabout-geometric",
    val reasons: List<String> = emptyList()
) {
    init {
        require(score in 0..100) {
            "RoundaboutDecision.score must be between 0 and 100"
        }

        require(exit == null || exit > 0) {
            "RoundaboutDecision.exit must be null or greater than zero"
        }

        if (resolved) {
            require(exit != null) {
                "A resolved roundabout decision must have an exit"
            }
        }
    }
}

interface RoundaboutResolver {
    fun resolve(context: RoundaboutContext): RoundaboutDecision
}
