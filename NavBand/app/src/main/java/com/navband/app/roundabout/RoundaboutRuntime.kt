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
    private val resolver: RoundaboutResolver = DefaultRoundaboutResolver()
) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

    fun resolve(event: NavigationEvent): NavigationEvent {
        if (event.direction != NavigationDirection.ROUNDABOUT) {
            return event
        }

        val location = lastKnownLocation() ?: return event
        val targetRoad = extractTargetRoad(event) ?: return event

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
                distanceMeters = parseDistanceMeters(
                    event.distance
                )
            )
        } catch (_: Exception) {
            null
        }

        context ?: return event

        return try {
            val decision = resolver.resolve(context)
            RoundaboutAdapter.applyDecision(
                event,
                decision
            )
        } catch (_: Exception) {
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

        if (!fine && !coarse) {
            return null
        }

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        return providers
            .mapNotNull { provider ->
                try {
                    if (!locationManager.isProviderEnabled(provider)) {
                        null
                    } else {
                        locationManager.getLastKnownLocation(provider)
                    }
                } catch (_: SecurityException) {
                    null
                }
            }
            .maxByOrNull { locationQuality(it) }
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
