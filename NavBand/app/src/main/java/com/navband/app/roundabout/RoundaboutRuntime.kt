package com.navband.app.roundabout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.navband.app.NavigationDirection
import com.navband.app.NavigationEvent
import java.util.Locale

class RoundaboutRuntime(
    context: Context,
    private val dataProvider: RoundaboutDataProvider = OnlineValhallaProvider(),
    private val resolver: RoundaboutResolver = DefaultRoundaboutResolver(),
    private val debug: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

    fun resolve(event: NavigationEvent): NavigationEvent {
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

        val location = lastKnownLocation()

        if (location == null) {
            debug("GPS: NO LAST KNOWN LOCATION")
            return event
        }

        debug(
            "GPS: lat=${location.latitude}, lon=${location.longitude}, " +
                "accuracy=${if (location.hasAccuracy()) location.accuracy else "unknown"}, " +
                "bearing=${if (location.hasBearing()) location.bearing else "unknown"}"
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
            dataProvider.loadContext(
                current = GeoPoint(
                    lat = location.latitude,
                    lon = location.longitude
                ),
                targetRoad = targetRoad,
                headingDegrees =
                    if (location.hasBearing()) {
                        location.bearing.toDouble()
                    } else {
                        null
                    },
                distanceMeters = distanceMeters
            )
        } catch (error: Exception) {
            debug(
                "VALHALLA/CONTEXT ERROR: " +
                    "${error.javaClass.simpleName}: ${error.message}"
            )
            null
        }

        if (context == null) {
            debug("CONTEXT: NULL")
            return event
        }

        debug(
            "CONTEXT: OK " +
                "currentRoad=${context.current.roadName} " +
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

            val resolvedEvent =
                RoundaboutAdapter.applyDecision(
                    event,
                    decision
                )

            debug(
                "RESOLVED EVENT: " +
                    "roundaboutExit=${resolvedEvent.roundaboutExit}"
            )

            resolvedEvent
        } catch (error: Exception) {
            debug(
                "RESOLVER ERROR: " +
                    "${error.javaClass.simpleName}: ${error.message}"
            )
            event
        }
    }

    private fun lastKnownLocation(): Location? {
        val fine =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        debug("GPS PERMISSION: fine=$fine coarse=$coarse")

        if (!fine && !coarse) {
            debug("GPS PERMISSION: NOT GRANTED")
            return null
        }

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        val now = android.os.SystemClock.elapsedRealtimeNanos()
        val maxCacheAgeNanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(15)

        val cached = providers.mapNotNull { provider ->
            try {
                if (!locationManager.isProviderEnabled(provider)) {
                    debug("GPS PROVIDER DISABLED: $provider")
                    null
                } else {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location == null) {
                        debug("GPS CACHED $provider: no location")
                        null
                    } else {
                        val ageNanos = now - location.elapsedRealtimeNanos
                        val ageSeconds = ageNanos / 1_000_000_000.0
                        debug(
                            "GPS CACHED $provider: age=${"%.1f".format(java.util.Locale.ROOT, ageSeconds)}s, " +
                                "lat=${location.latitude}, lon=${location.longitude}, " +
                                "accuracy=${if (location.hasAccuracy()) location.accuracy else "unknown"}"
                        )
                        if (ageNanos >= 0 && ageNanos <= maxCacheAgeNanos) {
                            location
                        } else {
                            debug("GPS CACHED $provider: rejected because stale")
                            null
                        }
                    }
                }
            } catch (error: SecurityException) {
                debug("GPS PROVIDER $provider ERROR: ${error.message}")
                null
            }
        }.maxByOrNull { locationQuality(it) }

        if (cached != null) {
            debug("GPS: using cached location")
            return cached
        }

        debug("GPS: cache empty; requesting fresh location")

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            debug("GPS FRESH: skipped because running on main thread")
            return null
        }

        val result = java.util.concurrent.atomic.AtomicReference<Location?>(null)
        val finished = java.util.concurrent.CountDownLatch(1)
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                result.set(location)
                finished.countDown()
            }

            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: android.os.Bundle?
            ) = Unit

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        var registered = false

        try {
            val activeProviders = providers.filter { provider ->
                try {
                    val enabled = locationManager.isProviderEnabled(provider)
                    debug("GPS PROVIDER STATUS: $provider enabled=$enabled")
                    enabled
                } catch (error: Exception) {
                    debug("GPS PROVIDER STATUS ERROR: $provider: ${error.message}")
                    false
                }
            }

            if (activeProviders.isEmpty()) {
                debug("GPS FRESH: no enabled provider")
                return null
            }

            for (provider in activeProviders) {
                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        0L,
                        0f,
                        listener,
                        android.os.Looper.getMainLooper()
                    )
                    registered = true
                    debug("GPS FRESH: registered provider=$provider")
                } catch (error: Exception) {
                    debug(
                        "GPS FRESH: registration failed provider=$provider: " +
                            "${error.javaClass.simpleName}: ${error.message}"
                    )
                }
            }

            if (!registered) {
                debug("GPS FRESH: registration failed for all enabled providers")
                return null
            }

            debug(
                "GPS FRESH: waiting up to 3000 ms via " +
                    activeProviders.joinToString()
            )

            val received = finished.await(
                3000,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )

            val fresh = result.get()
            if (received && fresh != null) {
                debug(
                    "GPS FRESH: received lat=${fresh.latitude}, " +
                        "lon=${fresh.longitude}, " +
                        "accuracy=${if (fresh.hasAccuracy()) fresh.accuracy else "unknown"}"
                )
                return fresh
            }

            debug("GPS FRESH: timeout; no location received")
            return null
        } catch (error: SecurityException) {
            debug("GPS FRESH: permission error: ${error.message}")
            return null
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            debug("GPS FRESH: interrupted")
            return null
        } catch (error: Exception) {
            debug("GPS FRESH: ${error.javaClass.simpleName}: ${error.message}")
            return null
        } finally {
            if (registered) {
                try {
                    locationManager.removeUpdates(listener)
                } catch (error: Exception) {
                    debug("GPS FRESH: removeUpdates error: ${error.message}")
                }
            }
        }
    }

    private fun locationQuality(
        location: Location
    ): Double {
        val accuracy =
            if (location.hasAccuracy()) {
                location.accuracy.toDouble()
            } else {
                1000.0
            }

        return 1.0 / (1.0 + accuracy)
    }

    private fun extractTargetRoad(
        event: NavigationEvent
    ): String? {
        val candidates = listOf(
            event.subText,
            event.instruction
        )

        return candidates
            .map { it.trim() }
            .firstOrNull {
                it.isNotBlank() &&
                    looksLikeRoad(it)
            }
    }

    private fun looksLikeRoad(
        value: String
    ): Boolean {
        return Regex(
            """(?i)\b(via|viale|corso|strada|piazza|largo|vicolo|ss|sp|sr|autostrada)\b"""
        ).containsMatchIn(value)
    }

    private fun parseDistanceMeters(
        value: String
    ): Double? {
        val match = Regex(
            """(?i)\b(\d+(?:[.,]\d+)?)\s*(m|km)\b"""
        ).find(value) ?: return null

        val number =
            match.groupValues[1]
                .replace(',', '.')
                .toDoubleOrNull()
                ?: return null

        return when (
            match.groupValues[2]
                .lowercase(Locale.ROOT)
        ) {
            "m" -> number
            "km" -> number * 1000.0
            else -> null
        }
    }
}
