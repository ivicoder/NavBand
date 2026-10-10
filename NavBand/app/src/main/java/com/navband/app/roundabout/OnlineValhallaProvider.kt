package com.navband.app.roundabout

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class OnlineValhallaProvider(
    private val baseUrl: String = "https://valhalla1.openstreetmap.de",
    private val debug: (String) -> Unit = {}
) : RoundaboutDataProvider {

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
        const val CONNECT_TIMEOUT_MS = 2500
        const val READ_TIMEOUT_MS = 4000
        const val MAX_RESPONSE_CHARS = 3_000_000
    }

    override fun loadContext(
        current: GeoPoint,
        targetRoad: String,
        headingDegrees: Double?,
        distanceMeters: Double?
    ): RoundaboutContext? {
        debug("VALHALLA: loadContext target=$targetRoad heading=$headingDegrees distance=$distanceMeters")
        if (!validPoint(current)) {
            debug("VALHALLA NULL: current GPS point invalid")
            return null
        }
        if (targetRoad.isBlank()) {
            debug("VALHALLA NULL: target road is blank")
            return null
        }

        return try {
            // Prima prova il percorso Valhalla: il simulatore non dipende dal fatto
            // che il primo /locate riconosca già un edge roundabout.
            val routeGeometry = try {
                routeRoundaboutGeometry(current, headingDegrees, distanceMeters)
            } catch (routeError: Exception) {
                debug("VALHALLA ROUTE: unavailable: ${routeError.javaClass.simpleName}: ${routeError.message}")
                null
            }

            val search = buildSearchPoints(
                current,
                headingDegrees,
                distanceMeters ?: 150.0
            )
            debug("VALHALLA: initial locate points=${search.size}")
            val located = locate(search)
            debug("VALHALLA: initial locate results=${located.size}, edges=${located.sumOf { it.edges.size }}")
            if (located.isEmpty() && routeGeometry == null) {
                debug("VALHALLA NULL: both route geometry and initial /locate are unavailable")
                return null
            }

            val locateRoundaboutPoints = located
                .flatMap { it.edges }
                .filter { it.roundabout }
                .mapNotNull { it.correlatedPoint }
                .distinctBy { "${it.lat}:${it.lon}" }

            // Preferisce i punti della geometria della manovra (come nel Simulator).
            // Solo se /route non ha isolato la rotatoria usa gli edge roundabout di /locate.
            val routePoints = routeGeometry?.points.orEmpty()
            val roundaboutPoints = if (routePoints.size >= 3) {
                routePoints
            } else {
                locateRoundaboutPoints
            }

            debug("VALHALLA: route geometry points=${routePoints.size}")
            debug("VALHALLA: locate roundabout correlated points=${locateRoundaboutPoints.size}")
            if (roundaboutPoints.isEmpty()) {
                debug("VALHALLA NULL: neither /route maneuver geometry nor initial /locate identified the roundabout")
                return null
            }

            val center = average(roundaboutPoints)
            debug("VALHALLA: estimated center=${center.lat},${center.lon}; source=${if (routePoints.size >= 3) "route" else "locate"}")
            val probes = buildProbes(center)
            val probeResults = locate(probes.map { it.point })

            val radialEdges = mutableListOf<RoadEdgeEvidence>()

            for (i in probes.indices) {
                val result = probeResults.getOrNull(i) ?: continue
                val probe = probes[i]

                for (edge in result.edges) {
                    radialEdges += edge.copy(
                        probeRadiusMeters = probe.radiusMeters,
                        probeBearingDegrees = probe.bearingDegrees
                    )
                }
            }

            val uniqueEdges = radialEdges
                .filter { it.auto }
                .distinctBy {
                    listOf(
                        it.edgeId,
                        it.wayId,
                        it.names.joinToString("|"),
                        it.roundabout,
                        it.probeBearingDegrees
                    ).joinToString(":")
                }

            debug("VALHALLA: radial edges=${radialEdges.size}, unique auto edges=${uniqueEdges.size}")
            if (uniqueEdges.isEmpty()) {
                debug("VALHALLA NULL: no usable auto-accessible radial edges")
                return null
            }

            val target = uniqueEdges.firstOrNull { edge ->
                edge.names.any { roadMatches(it, targetRoad) }
            }

            debug("VALHALLA: target road matched=${target != null}; names=${uniqueEdges.flatMap { it.names }.distinct().take(12)}")
            debug("VALHALLA: context built successfully")
            RoundaboutContext(
                current = NavigationLocationEvidence(
                    point = current,
                    roadName = located.firstOrNull()?.let { firstRoadName(it.edges) },
                    edges = located.firstOrNull()?.edges.orEmpty()
                ),
                target = NavigationLocationEvidence(
                    point = target?.correlatedPoint ?: center,
                    roadName = targetRoad,
                    edges = target?.let { listOf(it) } ?: emptyList()
                ),
                routePoints = routePoints,
                center = center,
                radialEdges = uniqueEdges
            )
        } catch (error: Exception) {
            debug("VALHALLA ERROR: ${error.javaClass.simpleName}: ${error.message ?: "no message"}")
            null
        }
    }

    private data class Probe(
        val point: GeoPoint,
        val radiusMeters: Double,
        val bearingDegrees: Double
    )

    private data class RouteGeometry(
        val points: List<GeoPoint>,
        val maneuverIndex: Int,
        val maneuverType: Int?,
        val exitCount: Int?
    )

    /**
     * Valhalla /route + shape polyline6, porting the Simulator strategy.
     * Since the notification exposes a target road name but no target coordinates,
     * the destination is projected beyond the reported maneuver distance along the
     * current travel bearing. This is a best-effort geometry source; /locate remains
     * the fallback when the route does not contain a roundabout maneuver.
     */
    private fun routeRoundaboutGeometry(
        current: GeoPoint,
        heading: Double?,
        distance: Double?
    ): RouteGeometry? {
        if (heading == null || !heading.isFinite()) {
            debug("VALHALLA ROUTE: skipped because GPS bearing is unavailable")
            return null
        }

        val destination = destinationPoint(
            current,
            heading,
            (distance ?: 150.0).coerceIn(40.0, 600.0) + 180.0
        )
        val locations = JSONArray()
            .put(JSONObject().put("lat", current.lat).put("lon", current.lon))
            .put(JSONObject().put("lat", destination.lat).put("lon", destination.lon))
        val payload = JSONObject()
            .put("locations", locations)
            .put("costing", "auto")
            .put("directions_options", JSONObject().put("units", "kilometers"))
            .toString()

        debug("VALHALLA ROUTE REQUEST: start=${current.lat},${current.lon}; heading=$heading; distance=$distance; destination=${destination.lat},${destination.lon}")
        val response = JSONObject(post("$baseUrl/route", payload))
        val trip = response.optJSONObject("trip") ?: run {
            debug("VALHALLA ROUTE RESPONSE: missing trip; body=${response.toString().take(4000)}")
            return null
        }
        val legs = trip.optJSONArray("legs") ?: return null
        val leg = legs.optJSONObject(0) ?: return null
        val maneuvers = leg.optJSONArray("maneuvers") ?: run {
            debug("VALHALLA ROUTE RESPONSE: missing maneuvers; leg=${leg.toString().take(4000)}")
            return null
        }
        debug("VALHALLA ROUTE MANEUVERS: " + (0 until maneuvers.length()).joinToString(" || ") { i ->
            val m = maneuvers.optJSONObject(i)
            if (m == null) "#$i <invalid>" else
                "#$i type=${m.optInt("type", -1)} exitCount=${if (m.has("roundabout_exit_count") && !m.isNull("roundabout_exit_count")) m.opt("roundabout_exit_count") else "null"} length=${m.optDouble("length", 0.0)} instruction=${m.optString("instruction", "")}"
        })
        var maneuverIndex = -1
        var maneuver: JSONObject? = null

        for (i in 0 until maneuvers.length()) {
            val candidate = maneuvers.optJSONObject(i) ?: continue
            val hasExitCount = candidate.has("roundabout_exit_count") &&
                !candidate.isNull("roundabout_exit_count")
            val type = candidate.optInt("type", -1)
            if (hasExitCount || type == 26 || type == 27) {
                maneuverIndex = i
                maneuver = candidate
                if (hasExitCount) break
            }
        }
        if (maneuverIndex < 0 || maneuver == null) {
            debug("VALHALLA ROUTE: no roundabout maneuver found; falling back to /locate")
            return null
        }

        val shape = leg.optString("shape", "")
        if (shape.isBlank()) {
            debug("VALHALLA ROUTE: maneuver found but shape is missing")
            return null
        }
        val allPoints = decodePolyline6(shape)
        if (allPoints.size < 3) return null

        var maneuverStartMeters = 0.0
        for (i in 0 until maneuverIndex) {
            maneuverStartMeters += (maneuvers.optJSONObject(i)?.optDouble("length", 0.0) ?: 0.0) * 1000.0
        }
        val maneuverLengthMeters = maneuver.optDouble("length", 0.0) * 1000.0
        val windowStart = (maneuverStartMeters - 35.0).coerceAtLeast(0.0)
        val windowEnd = maneuverStartMeters + maneuverLengthMeters + 35.0

        val selected = mutableListOf<GeoPoint>()
        var cumulative = 0.0
        for (i in allPoints.indices) {
            if (i > 0) cumulative += pointDistanceMeters(allPoints[i - 1], allPoints[i])
            if (cumulative in windowStart..windowEnd) selected += allPoints[i]
        }

        if (selected.size < 3) {
            val fraction = (maneuverStartMeters + maneuverLengthMeters / 2.0) /
                (leg.optJSONObject("summary")?.optDouble("length", 1.0)?.times(1000.0) ?: 1.0).coerceAtLeast(1.0)
            val centerIndex = (allPoints.size * fraction).toInt().coerceIn(0, allPoints.lastIndex)
            selected.clear()
            for (i in (centerIndex - 4).coerceAtLeast(0)..(centerIndex + 4).coerceAtMost(allPoints.lastIndex)) {
                selected += allPoints[i]
            }
        }

        debug("VALHALLA ROUTE: maneuver index=$maneuverIndex type=${maneuver.optInt("type", -1)} exitCount=${if (maneuver.has("roundabout_exit_count")) maneuver.optInt("roundabout_exit_count") else null} selectedPoints=${selected.size} length=${maneuverLengthMeters}m")
        return RouteGeometry(
            points = selected.distinctBy { "${it.lat}:${it.lon}" },
            maneuverIndex = maneuverIndex,
            maneuverType = maneuver.optInt("type", -1).takeIf { it >= 0 },
            exitCount = if (maneuver.has("roundabout_exit_count")) maneuver.optInt("roundabout_exit_count") else null
        )
    }

    private fun decodePolyline6(encoded: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var byte: Int
            do {
                if (index >= encoded.length) return points
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            lat += if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)

            result = 0
            shift = 0
            do {
                if (index >= encoded.length) return points
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            lon += if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            points += GeoPoint(lat / 1_000_000.0, lon / 1_000_000.0)
        }
        return points
    }

    private fun pointDistanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return 2.0 * EARTH_RADIUS_M * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun buildSearchPoints(
        current: GeoPoint,
        heading: Double?,
        distance: Double
    ): List<GeoPoint> {
        val d = distance.coerceIn(40.0, 600.0)

        if (heading == null || !heading.isFinite()) {
            return listOf(
                current,
                destinationPoint(current, 0.0, d),
                destinationPoint(current, 90.0, d),
                destinationPoint(current, 180.0, d),
                destinationPoint(current, 270.0, d)
            )
        }

        val h = normalizeBearing(heading)
        val forward = destinationPoint(current, h, d)

        return listOf(
            current,
            forward,
            destinationPoint(forward, h - 90.0, 45.0),
            destinationPoint(forward, h + 90.0, 45.0)
        )
    }

    private fun buildProbes(center: GeoPoint): List<Probe> {
        val result = mutableListOf<Probe>()

        for (radius in listOf(55.0, 75.0)) {
            for (bearing in 0 until 360 step 15) {
                result += Probe(
                    point = destinationPoint(
                        center,
                        bearing.toDouble(),
                        radius
                    ),
                    radiusMeters = radius,
                    bearingDegrees = bearing.toDouble()
                )
            }
        }

        return result
    }

    private fun locate(
        points: List<GeoPoint>
    ): List<LocatedResult> {
        if (points.isEmpty()) return emptyList()

        val locations = JSONArray()

        points.forEach {
            locations.put(
                JSONObject()
                    .put("lat", it.lat)
                    .put("lon", it.lon)
            )
        }

        val body = JSONObject()
            .put("verbose", true)
            .put("costing", "auto")
            .put("locations", locations)
            .toString()

        val response = post(
            "$baseUrl/locate",
            body
        )

        val array = JSONArray(response)
        val result = mutableListOf<LocatedResult>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val edges = mutableListOf<RoadEdgeEvidence>()
            val jsonEdges = item.optJSONArray("edges")

            if (jsonEdges != null) {
                for (j in 0 until jsonEdges.length()) {
                    val edge = jsonEdges.optJSONObject(j) ?: continue
                    val info = edge.optJSONObject("edge_info")
                    val data = edge.optJSONObject("edge")
                    val access = data?.optJSONObject("access")
                    val classification =
                        data?.optJSONObject("classification")

                    val point =
                        if (
                            edge.has("correlated_lat") &&
                            edge.has("correlated_lon")
                        ) {
                            GeoPoint(
                                edge.getDouble("correlated_lat"),
                                edge.getDouble("correlated_lon")
                            )
                        } else {
                            null
                        }

                    edges += RoadEdgeEvidence(
                        edgeId =
                            edge.optJSONObject("edge_id")
                                ?.optLong("value")
                                ?.takeIf { it != 0L },
                        wayId =
                            info?.optLong("way_id")
                                ?.takeIf { it != 0L },
                        names = readNames(info),
                        roundabout =
                            data?.optBoolean(
                                "round_about",
                                false
                            ) == true,
                        auto =
                            access?.optBoolean(
                                "car",
                                true
                            ) ?: true,
                        correlatedPoint = point,
                        use =
                            classification
                                ?.optString("use")
                                ?.takeIf { it.isNotBlank() },
                        classification =
                            classification
                                ?.optString("classification")
                                ?.takeIf { it.isNotBlank() }
                    )
                }
            }

            result += LocatedResult(edges)
        }

        return result
    }

    private fun readNames(
        info: JSONObject?
    ): List<String> {
        val names = info?.optJSONArray("names")
            ?: return emptyList()

        return buildList {
            for (i in 0 until names.length()) {
                val value = names.optString(i, "")
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun firstRoadName(
        edges: List<RoadEdgeEvidence>
    ): String? {
        return edges.firstOrNull {
            it.auto &&
                !it.roundabout &&
                it.names.isNotEmpty()
        }?.names?.first()
    }

    private fun roadMatches(
        actual: String,
        target: String
    ): Boolean {
        val a = normalizeRoad(actual)
        val t = normalizeRoad(target)

        return a == t ||
            a.contains(t) ||
            t.contains(a)
    }

    private fun normalizeRoad(
        value: String
    ): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(
                Regex("""[/,.;()_'’\-]+"""),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun average(
        points: List<GeoPoint>
    ): GeoPoint {
        return GeoPoint(
            points.map { it.lat }.average(),
            points.map { it.lon }.average()
        )
    }

    private fun destinationPoint(
        origin: GeoPoint,
        bearingDegrees: Double,
        distanceMeters: Double
    ): GeoPoint {
        val angular = distanceMeters / EARTH_RADIUS_M
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(origin.lat)
        val lon1 = Math.toRadians(origin.lon)

        val lat2 = asin(
            sin(lat1) * kotlin.math.cos(angular) +
                kotlin.math.cos(lat1) *
                sin(angular) *
                kotlin.math.cos(bearing)
        )

        val lon2 = lon1 + atan2(
            sin(bearing) *
                sin(angular) *
                kotlin.math.cos(lat1),
            kotlin.math.cos(angular) -
                sin(lat1) * sin(lat2)
        )

        return GeoPoint(
            Math.toDegrees(lat2),
            Math.toDegrees(lon2)
        )
    }

    private fun normalizeBearing(
        value: Double
    ): Double {
        val result = value % 360.0
        return if (result < 0) result + 360.0 else result
    }

    private fun validPoint(
        point: GeoPoint
    ): Boolean {
        return point.lat.isFinite() &&
            point.lon.isFinite() &&
            point.lat in -90.0..90.0 &&
            point.lon in -180.0..180.0
    }

    private fun post(
        endpoint: String,
        body: String
    ): String {
        val connection =
            URL(endpoint).openConnection()
                as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )
            connection.setRequestProperty(
                "User-Agent",
                "NavBand/1.0"
            )
            connection.setRequestProperty(
                "X-Client-Id",
                "ivicoder-navband"
            )

            connection.outputStream
                .bufferedWriter(Charsets.UTF_8)
                .use { it.write(body) }

            val status = connection.responseCode
            debug("VALHALLA HTTP: endpoint=$endpoint status=$status")
            val stream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val text =
                stream?.bufferedReader()?.use { reader ->
                    val out = StringBuilder()
                    while (true) {
                        val line = reader.readLine() ?: break
                        out.append(line).append('\n')
                        if (out.length > MAX_RESPONSE_CHARS) {
                            throw IOException("Valhalla response too large")
                        }
                    }
                    out.toString()
                } ?: ""

            if (status !in 200..299) {
                throw IOException("Valhalla HTTP $status; responseChars=${text.length}")
            }

            debug("VALHALLA HTTP: success responseChars=${text.length}")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private data class LocatedResult(
        val edges: List<RoadEdgeEvidence>
    )
}
