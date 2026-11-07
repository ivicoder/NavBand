package com.navband.app.roundabout

import android.content.Context
import android.location.Location
import com.navband.app.NavigationDirection
import com.navband.app.NavigationEvent
import com.navband.app.NavigationLocationSnapshot
import java.util.Locale

class RoundaboutRuntime(
    context: Context,
    dataProvider: RoundaboutDataProvider? = null,
    private val resolver: RoundaboutResolver = DefaultRoundaboutResolver(),
    private val debug: (String) -> Unit = {}
) {
    private val activeDataProvider: RoundaboutDataProvider =
        dataProvider ?: OnlineValhallaProvider(debug = debug)

    private val appContext = context.applicationContext

    fun resolve(event: NavigationEvent, locationSnapshot: NavigationLocationSnapshot?): NavigationEvent {
        debug("=== ROUNDABOUT RUNTIME ===")
        debug("direction=${event.direction}")
        debug("instruction=${event.instruction}")
        debug("subText=${event.subText}")
        debug("distance=${event.distance}")
        debug("initial roundaboutExit=${event.roundaboutExit}")

        if (event.direction != NavigationDirection.ROUNDABOUT) {
            debug("SKIP: direction is not ROUNDABOUT")
            return event
        }

        if (event.roundaboutExit != null) {
            debug("MAPS EXIT: preserving explicit exit=${event.roundaboutExit}")
            return event
        }

        val snapshot = locationSnapshot
        if (snapshot == null) {
            debug("GPS: no fresh notification-time snapshot; no delayed GPS fallback")
            return event
        }

        val location = snapshot.location
        val ageMs = snapshot.ageMillis()
        if (ageMs > MAX_SNAPSHOT_AGE_MS) {
            debug("GPS: snapshot rejected as stale; ageMs=$ageMs maxAgeMs=$MAX_SNAPSHOT_AGE_MS source=${snapshot.source}")
            return event
        }

        debug(
            "GPS SNAPSHOT: source=${snapshot.source}; ageMs=$ageMs; " +
                "lat=${location.latitude}; lon=${location.longitude}; " +
                "accuracy=${if (location.hasAccuracy()) location.accuracy else "unknown"}; " +
                "bearing=${if (location.hasBearing()) location.bearing else "unknown"}; " +
                "speed=${if (location.hasSpeed()) location.speed else "unknown"}; " +
                "mock=${snapshot.isMock()}; provider=${location.provider}; " +
                "fixTimeMs=${location.time}; fixElapsedNs=${location.elapsedRealtimeNanos}; " +
                "snapshotRequestedElapsedNs=${snapshot.requestedAtElapsedRealtimeNanos}; " +
                "snapshotCapturedElapsedNs=${snapshot.capturedAtElapsedRealtimeNanos}"
        )

        val targetRoad = extractTargetRoad(event)
        if (targetRoad == null) {
            debug("TARGET ROAD: NOT FOUND")
            return event
        }
        debug("TARGET ROAD: $targetRoad")

        val distanceMeters = parseDistanceMeters(event.distance)
        debug("DISTANCE METERS: ${distanceMeters ?: "unknown"}")

        val context = try {
            activeDataProvider.loadContext(
                current = GeoPoint(location.latitude, location.longitude),
                targetRoad = targetRoad,
                headingDegrees = if (location.hasBearing()) location.bearing.toDouble() else null,
                distanceMeters = distanceMeters
            )
        } catch (error: Exception) {
            debug("VALHALLA/CONTEXT ERROR: ${error.javaClass.simpleName}: ${error.message}")
            null
        }

        if (context == null) {
            debug("CONTEXT: NULL")
            return event
        }

        debug(
            "CONTEXT: OK currentRoad=${context.current.roadName} " +
                "currentEdges=${context.current.edges.size} " +
                "targetRoad=${context.target.roadName} " +
                "targetEdges=${context.target.edges.size} " +
                "radialEdges=${context.radialEdges.size}"
        )

        return try {
            val decision = resolver.resolve(context)
            debug("=== ROUNDABOUT DECISION ===")
            debug("resolved=${decision.resolved}")
            debug("exit=${decision.exit}")
            debug("score=${decision.score}")
            debug("confidence=${decision.confidence}")
            debug("targetRoad=${decision.targetRoad}")
            debug("matchedRoad=${decision.matchedRoad}")
            debug("source=${decision.source}")
            debug("reasons=${decision.reasons.joinToString(" | ")}")

            val resolvedEvent = RoundaboutAdapter.applyDecision(event, decision)
            debug("RESOLVED EVENT: roundaboutExit=${resolvedEvent.roundaboutExit}")
            resolvedEvent
        } catch (error: Exception) {
            debug("RESOLVER ERROR: ${error.javaClass.simpleName}: ${error.message}")
            event
        }
    }

    private fun extractTargetRoad(event: NavigationEvent): String? =
        listOf(event.subText, event.instruction)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && looksLikeRoad(it) }

    private fun looksLikeRoad(value: String): Boolean =
        Regex("""(?i)\b(via|viale|corso|strada|piazza|largo|vicolo|ss|sp|sr|autostrada)\b""")
            .containsMatchIn(value)

    private fun parseDistanceMeters(value: String): Double? {
        val match = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*(m|km)\b""").find(value)
            ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "m" -> number
            "km" -> number * 1000.0
            else -> null
        }
    }

    companion object {
        private const val MAX_SNAPSHOT_AGE_MS = 20_000L
    }
}
