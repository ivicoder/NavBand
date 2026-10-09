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
            val search = buildSearchPoints(
                current,
                headingDegrees,
                distanceMeters ?: 150.0
            )

            debug("VALHALLA: initial locate points=${search.size}")
            val located = locate(search)
            debug("VALHALLA: initial locate results=${located.size}, edges=${located.sumOf { it.edges.size }}")
            if (located.isEmpty()) {
                debug("VALHALLA NULL: initial /locate returned no results")
                return null
            }

            val roundaboutPoints = located
                .flatMap { it.edges }
                .filter { it.roundabout }
                .mapNotNull { it.correlatedPoint }
                .distinctBy { "${it.lat}:${it.lon}" }

            debug("VALHALLA: roundabout correlated points=${roundaboutPoints.size}")
            if (roundaboutPoints.isEmpty()) {
                debug("VALHALLA NULL: no roundabout edges found near current route")
                return null
            }

            val center = average(roundaboutPoints)
            debug("VALHALLA: estimated center=${center.lat},${center.lon}")
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
                    roadName = firstRoadName(located.first().edges),
                    edges = located.first().edges
                ),
                target = NavigationLocationEvidence(
                    point = target?.correlatedPoint ?: center,
                    roadName = targetRoad,
                    edges = target?.let { listOf(it) } ?: emptyList()
                ),
                routePoints = emptyList(),
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
