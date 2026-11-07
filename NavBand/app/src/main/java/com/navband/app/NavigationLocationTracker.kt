package com.navband.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Passive observer of the system fused-location stream.
 *
 * It does not activate GPS itself. While another app (for example Google Maps)
 * requests locations, NavBand receives passive copies and keeps a short history
 * so a notification can be paired with the position available at arrival time.
 */
class NavigationLocationTracker(
    context: Context,
    private val debug: (String) -> Unit = {}
) {
    private data class Candidate(
        val location: Location,
        val source: String
    )

    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val lock = Any()
    private val history = ArrayDeque<Candidate>()
    private var locationCallback: LocationCallback? = null

    fun start() {
        if (!hasLocationPermission()) {
            debug("FLP PASSIVE: not started; location permission missing")
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { record(it, "FLP_PASSIVE") }
            }
        }
        locationCallback = callback

        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) record(location, "FLP_LAST_LOCATION")
                else debug("FLP LAST LOCATION: null at tracker startup")
            }
            .addOnFailureListener { error ->
                debug("FLP LAST LOCATION ERROR: ${error.javaClass.simpleName}: ${error.message}")
            }

        fusedClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )
            .addOnSuccessListener {
                debug("FLP PASSIVE: location observer started")
            }
            .addOnFailureListener { error ->
                debug("FLP PASSIVE ERROR: ${error.javaClass.simpleName}: ${error.message}")
            }
    }

    fun stop() {
        val callback = locationCallback ?: return
        locationCallback = null
        fusedClient.removeLocationUpdates(callback)
            .addOnSuccessListener { debug("FLP PASSIVE: location observer stopped") }
            .addOnFailureListener { error ->
                debug("FLP PASSIVE STOP ERROR: ${error.javaClass.simpleName}: ${error.message}")
            }
    }

    /**
     * Synchronous snapshot: called immediately when the Maps notification arrives,
     * before the event is queued for route resolution.
     */
    fun snapshotNow(): NavigationLocationSnapshot? {
        val requestedAt = SystemClock.elapsedRealtimeNanos()
        val candidates = mutableListOf<Candidate>()

        synchronized(lock) {
            history.forEach { candidates += Candidate(Location(it.location), it.source) }
        }

        if (hasLocationPermission()) {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null) candidates += Candidate(Location(location), "LOCATION_MANAGER_$provider")
                } catch (error: SecurityException) {
                    debug("LOCATION MANAGER $provider: permission error")
                } catch (error: Exception) {
                    debug("LOCATION MANAGER $provider ERROR: ${error.javaClass.simpleName}: ${error.message}")
                }
            }
        }

        val maxAgeNanos = TimeUnit.SECONDS.toNanos(MAX_LOCATION_AGE_SECONDS)
        val eligible = candidates.filter { candidate ->
            val fixNanos = candidate.location.elapsedRealtimeNanos
            val ageNanos = requestedAt - fixNanos
            candidate.location.latitude.isFinite() &&
                candidate.location.longitude.isFinite() &&
                candidate.location.latitude in -90.0..90.0 &&
                candidate.location.longitude in -180.0..180.0 &&
                fixNanos > 0L &&
                ageNanos >= 0L &&
                ageNanos <= maxAgeNanos
        }

        val selected = eligible.maxWithOrNull(
            compareBy<Candidate> { it.location.elapsedRealtimeNanos }
                .thenBy { -accuracyMeters(it.location) }
        )

        if (selected == null) {
            val newestAge = candidates.maxOfOrNull {
                val fix = it.location.elapsedRealtimeNanos
                if (fix > 0L) max(0L, requestedAt - fix) / 1_000_000L else Long.MAX_VALUE
            }
            debug("LOCATION SNAPSHOT: unavailable; candidates=${candidates.size}; newestAgeMs=${newestAge ?: "none"}")
            return null
        }

        val capturedAt = SystemClock.elapsedRealtimeNanos()
        return NavigationLocationSnapshot(
            location = Location(selected.location),
            source = selected.source,
            requestedAtElapsedRealtimeNanos = requestedAt,
            capturedAtElapsedRealtimeNanos = capturedAt
        )
    }

    private fun record(location: Location, source: String) {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        if (location.elapsedRealtimeNanos <= 0L) return

        synchronized(lock) {
            val duplicate = history.lastOrNull()?.location?.elapsedRealtimeNanos ==
                location.elapsedRealtimeNanos
            if (!duplicate) {
                history.addLast(Candidate(Location(location), source))
                while (history.size > MAX_HISTORY_ITEMS) history.removeFirst()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun accuracyMeters(location: Location): Double =
        if (location.hasAccuracy()) location.accuracy.toDouble() else 10_000.0

    companion object {
        private const val MAX_HISTORY_ITEMS = 120
        private const val MAX_LOCATION_AGE_SECONDS = 20L
    }
}

data class NavigationLocationSnapshot(
    val location: Location,
    val source: String,
    val requestedAtElapsedRealtimeNanos: Long,
    val capturedAtElapsedRealtimeNanos: Long
) {
    fun ageMillis(nowElapsedRealtimeNanos: Long = SystemClock.elapsedRealtimeNanos()): Long =
        ((nowElapsedRealtimeNanos - location.elapsedRealtimeNanos).coerceAtLeast(0L)) / 1_000_000L

    fun isMock(): Boolean = LocationCompat.isMock(location)
}
