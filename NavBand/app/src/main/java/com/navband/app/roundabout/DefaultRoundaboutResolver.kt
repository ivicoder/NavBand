package com.navband.app.roundabout

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class DefaultRoundaboutResolver : RoundaboutResolver {

    private data class Branch(
        val id: String,
        val names: MutableList<String>,
        val wayIds: MutableSet<Long>,
        val bearings: MutableList<Double>
    ) {
        val bearing: Double
            get() {
                if (bearings.isEmpty()) return 0.0

                var x = 0.0
                var y = 0.0

                bearings.forEach { value ->
                    val radians = Math.toRadians(value)
                    x += cos(radians)
                    y += sin(radians)
                }

                var result = Math.toDegrees(atan2(y, x)) % 360.0
                if (result < 0.0) result += 360.0
                return result
            }
    }

    override fun resolve(
        context: RoundaboutContext
    ): RoundaboutDecision {
        val center = context.center
            ?: return lowDecision(
                context,
                "centro rotatoria assente"
            )

        val branches = buildBranches(
            context.radialEdges,
            center
        )

        if (branches.size < 2) {
            return lowDecision(
                context,
                "uscite geometriche insufficienti"
            )
        }

        val entry = findEntryBranch(
            branches,
            context.current,
            center,
            context.routePoints
        )

        val target = findTargetBranch(
            branches,
            context.target,
            center,
            context.routePoints
        )

        if (entry == null || target == null) {
            return lowDecision(
                context,
                if (entry == null) {
                    "braccio di ingresso non determinato"
                } else {
                    "strada target non determinata"
                }
            )
        }

        val traversalDirection = inferTraversalDirection(context.routePoints)
        val ordered = branches
            .filter { it.id != entry.id }
            .map { branch ->
                branch to traversalDistance(
                    entry.bearing,
                    branch.bearing,
                    traversalDirection
                )
            }
            .filter { it.second > 0.001 }
            .sortedBy { it.second }
            .map { it.first }

        val exitIndex = ordered.indexOfFirst {
            it.id == target.id
        }

        if (exitIndex < 0) {
            return lowDecision(
                context,
                "target non presente tra le uscite"
            )
        }

        val exit = exitIndex + 1

        val entryWayMatch =
            sameWay(entry, context.current.edges)

        val entryRoadScore =
            roadScore(entry.names, context.current.roadName)

        val entryAngle =
            angularDistance(
                entry.bearing,
                bearing(center, context.current.point)
            )

        val entryStrong =
            entryWayMatch || entryRoadScore >= 82

        val targetWayMatch =
            sameWay(target, context.target.edges)

        val targetScore =
            roadScore(
                target.names,
                context.target.roadName
            )

        // A weak or geometrically uncertain entry must never produce HIGH
        // confidence just because the target road name matches perfectly.
        val score = min(
            100,
            20 +
                (if (entryStrong) 30 else 0) +
                when {
                    targetScore >= 100 -> 35
                    targetScore >= 82 -> 25
                    targetScore >= 40 -> 15
                    else -> 0
                } +
                20
        )

        val confidence =
            when {
                score >= 85 -> RoundaboutConfidence.HIGH
                score >= 65 -> RoundaboutConfidence.MEDIUM
                else -> RoundaboutConfidence.LOW
            }

        val resolved =
            exit > 0 &&
                entryStrong &&
                targetScore >= 82 &&
                score >= 85

        val reasons = mutableListOf(
            "branches=${branches.size}",
            "branchCandidates=" + branches.joinToString(" || ") {
                "${it.names.joinToString("/")};bearing=${"%.1f".format(java.util.Locale.ROOT, it.bearing)};wayIds=${it.wayIds}"
            },
            "entry=${entry.names.joinToString(" / ")}",
            "entryBearing=${"%.1f".format(java.util.Locale.ROOT, entry.bearing)}",
            "entryWayMatch=$entryWayMatch",
            "entryRoadScore=$entryRoadScore",
            "entryAngle=${"%.1f".format(java.util.Locale.ROOT, entryAngle)}",
            "target=${target.names.joinToString(" / ")}",
            "targetPoint=${context.target.point.lat},${context.target.point.lon}",
            "targetEvidenceEdges=" + context.target.edges.joinToString(" || ") {
                "edgeId=${it.edgeId};wayId=${it.wayId};names=${it.names};probeBearing=${it.probeBearingDegrees};correlated=${it.correlatedPoint}"
            },
            "targetWayMatch=$targetWayMatch",
            "targetRoadScore=$targetScore",
            "orderedExits=" + ordered.mapIndexed { index, branch ->
                "${index + 1}:${branch.names.joinToString("/")};bearing=${"%.1f".format(java.util.Locale.ROOT, branch.bearing)}"
            }.joinToString(" || "),
            "exit=$exit",
            "resolved=$resolved"
        )

        if (!entryStrong) {
            reasons += "entry match non forte: uscita non confermata"
        }

        return RoundaboutDecision(
            resolved = resolved,
            exit = if (resolved) exit else null,
            confidence = confidence,
            score = score,
            targetRoad = context.target.roadName,
            matchedRoad = target.names.joinToString(" / "),
            source = "roundabout-geometric",
            reasons = reasons
        )
    }

    private fun buildBranches(
        edges: List<RoadEdgeEvidence>,
        center: GeoPoint
    ): List<Branch> {
        val near = edges.filter {
            it.probeRadiusMeters != null &&
                it.probeRadiusMeters!! <= 55.0
        }
        val nearBranches = buildBranchGroups(near, center)
        val source = if (nearBranches.size >= 2) near else edges.filter {
            it.probeRadiusMeters == null || it.probeRadiusMeters!! <= 75.0
        }

        val usable = source
            .filter { it.auto }
            .filterNot { it.roundabout }
            .filterNot { isExcludedUse(it) }
            .filter { it.names.isNotEmpty() }

        val branches = mutableListOf<Branch>()

        for (edge in usable) {
            val bearing = edgeBearing(
                edge,
                center
            ) ?: continue

            val existing = branches.firstOrNull { branch ->
                sameRoadFamily(
                    branch.names,
                    edge.names
                ) &&
                    angularDistance(
                        branch.bearing,
                        bearing
                    ) <= 35.0
            }

            if (existing == null) {
                branches += Branch(
                    id = "branch-${branches.size + 1}",
                    names = edge.names.toMutableList(),
                    wayIds = mutableSetOf<Long>().apply {
                        edge.wayId?.let { add(it) }
                    },
                    bearings = mutableListOf(bearing)
                )
            } else {
                existing.bearings += bearing
                edge.wayId?.let {
                    existing.wayIds += it
                }

                edge.names.forEach { name ->
                    if (!existing.names.contains(name)) {
                        existing.names += name
                    }
                }
            }
        }

        return branches
    }

    private fun buildBranchGroups(
        edges: List<RoadEdgeEvidence>,
        center: GeoPoint
    ): List<Branch> {
        val usable = edges.filter { it.auto && !it.roundabout && it.names.isNotEmpty() }
        val groups = mutableListOf<Branch>()
        for (edge in usable) {
            val direction = edgeBearing(edge, center) ?: continue
            val existing = groups.firstOrNull { branch ->
                sameRoadFamily(branch.names, edge.names) &&
                    angularDistance(branch.bearing, direction) <= 35.0
            }
            if (existing == null) {
                groups += Branch(
                    id = "branch-${groups.size + 1}",
                    names = edge.names.toMutableList(),
                    wayIds = mutableSetOf<Long>().apply { edge.wayId?.let { add(it) } },
                    bearings = mutableListOf(direction)
                )
            } else {
                existing.bearings += direction
                edge.wayId?.let { existing.wayIds += it }
                edge.names.forEach { name -> if (name !in existing.names) existing.names += name }
            }
        }
        return groups
    }

    private fun findEntryBranch(
        branches: List<Branch>,
        current: NavigationLocationEvidence,
        center: GeoPoint,
        routePoints: List<GeoPoint>
    ): Branch? {
        val currentWayIds =
            current.edges.mapNotNull { it.wayId }.toSet()

        val currentBearing =
            routeEdgeBearing(routePoints, center, last = false)
                ?: bearing(center, current.point)

        return branches
            .map { branch ->
                val wayMatch =
                    branch.wayIds.any {
                        currentWayIds.contains(it)
                    }

                val road =
                    roadScore(
                        branch.names,
                        current.roadName
                    )

                val angle =
                    angularDistance(
                        branch.bearing,
                        currentBearing
                    )

                val score =
                    (if (wayMatch) 1000 else 0) +
                        road * 5 -
                        angle.toInt()

                Triple(
                    branch,
                    wayMatch,
                    score
                )
            }
            .maxByOrNull { it.third }
            ?.let { candidate ->
                if (
                    !candidate.second &&
                    roadScore(
                        candidate.first.names,
                        current.roadName
                    ) < 82 &&
                    angularDistance(
                        candidate.first.bearing,
                        currentBearing
                    ) > 45.0
                ) {
                    null
                } else {
                    candidate.first
                }
            }
    }

    private fun findTargetBranch(
        branches: List<Branch>,
        target: NavigationLocationEvidence,
        center: GeoPoint,
        routePoints: List<GeoPoint>
    ): Branch? {
        val targetWayIds =
            target.edges.mapNotNull { it.wayId }.toSet()
        val targetBearing =
            routeEdgeBearing(routePoints, center, last = true)

        val candidates = branches.map { branch ->
            val wayMatch = branch.wayIds.any { targetWayIds.contains(it) }
            val score = roadScore(branch.names, target.roadName)
            val angleScore = targetBearing?.let {
                max(0.0, 100.0 - angularDistance(branch.bearing, it))
            } ?: 0.0

            TargetCandidate(
                branch = branch,
                wayMatch = wayMatch,
                roadScore = score,
                totalScore = (if (wayMatch) 1000 else 0) + score * 5.0 + angleScore
            )
        }

        val bestScore = candidates.maxOfOrNull { it.totalScore } ?: return null
        val best = candidates.filter { kotlin.math.abs(it.totalScore - bestScore) < 0.001 }
        if (best.size != 1) {
            // A repeated street name without a unique way ID or route geometry
            // is ambiguous: do not guess an exit and risk vibrating the wrong one.
            return null
        }

        val candidate = best.single()
        return candidate.branch.takeIf {
            candidate.wayMatch || candidate.roadScore >= 82
        }
    }

    private data class TargetCandidate(
        val branch: Branch,
        val wayMatch: Boolean,
        val roadScore: Int,
        val totalScore: Double
    )

    private fun routeEdgeBearing(
        routePoints: List<GeoPoint>,
        center: GeoPoint,
        last: Boolean
    ): Double? {
        if (routePoints.isEmpty()) return null
        val point = if (last) routePoints.last() else routePoints.first()
        return bearing(center, point)
    }

    private fun inferTraversalDirection(routePoints: List<GeoPoint>): Int {
        if (routePoints.size < 3) return -1

        var total = 0.0
        for (i in 1 until routePoints.size) {
            val incoming = bearing(routePoints[i - 1], routePoints[i])
            val nextIndex = (i + 1).coerceAtMost(routePoints.lastIndex)
            val outgoing = bearing(routePoints[i], routePoints[nextIndex])
            val delta = normalizeSigned(outgoing - incoming)
            if (abs(delta) <= 120.0) total += delta
        }

        return if (abs(total) >= 10.0 && total > 0.0) 1 else -1
    }

    private fun traversalDistance(from: Double, to: Double, direction: Int): Double =
        if (direction < 0) ccwDistance(from, to) else normalizeBearing(to - from)

    private fun normalizeSigned(value: Double): Double {
        var result = value % 360.0
        if (result > 180.0) result -= 360.0
        if (result < -180.0) result += 360.0
        return result
    }

    private fun isExcludedUse(edge: RoadEdgeEvidence): Boolean {
        val use = normalize(edge.use.orEmpty())
        val classification = normalize(edge.classification.orEmpty())
        return use == "alley" ||
            use == "service" ||
            classification == "service other"
    }

    private fun sameWay(
        branch: Branch,
        edges: List<RoadEdgeEvidence>
    ): Boolean {
        val ids =
            edges.mapNotNull { it.wayId }.toSet()

        return branch.wayIds.any { ids.contains(it) }
    }

    private fun roadScore(
        names: List<String>,
        target: String?
    ): Int {
        if (target.isNullOrBlank()) return 0

        val normalizedTarget =
            normalize(target)

        if (normalizedTarget.isBlank()) return 0

        var best = 0

        for (name in names) {
            val candidate = normalize(name)

            when {
                candidate == normalizedTarget ->
                    best = max(best, 100)

                // Same words in a different order are equivalent road names.
                // Example: "Via Gennaro Papa" and "Via Papa Gennaro".
                candidate.split(" ").filter { it.isNotBlank() }.toSet() ==
                    normalizedTarget.split(" ").filter { it.isNotBlank() }.toSet() ->
                    best = max(best, 100)

                candidate.contains(normalizedTarget) ||
                    normalizedTarget.contains(candidate) ->
                    best = max(best, 82)

                tokenOverlap(candidate, normalizedTarget) >= 2 ->
                    best = max(best, 70)

                tokenOverlap(candidate, normalizedTarget) == 1 ->
                    best = max(best, 40)
            }
        }

        return best
    }

    private fun tokenOverlap(
        a: String,
        b: String
    ): Int {
        val left = a.split(" ")
            .filter { it.isNotBlank() }
            .toSet()

        val right = b.split(" ")
            .filter { it.isNotBlank() }
            .toSet()

        return left.intersect(right).size
    }

    private fun sameRoadFamily(
        left: List<String>,
        right: List<String>
    ): Boolean {
        return left.any { a ->
            right.any { b ->
                val na = normalize(a)
                val nb = normalize(b)

                na.isNotBlank() &&
                    nb.isNotBlank() &&
                    (na == nb || na.contains(nb) || nb.contains(na))
            }
        }
    }

    private fun edgeBearing(
        edge: RoadEdgeEvidence,
        center: GeoPoint
    ): Double? {
        // The correlated point is the actual map-matched road position.
        // Probe bearing is only a fallback because the probe can snap to a
        // different nearby carriageway or to the roundabout itself.
        edge.correlatedPoint
            ?.let { return bearing(center, it) }

        return edge.probeBearingDegrees
            ?.takeIf { it.isFinite() }
            ?.let { normalizeBearing(it) }
    }

    private fun bearing(
        from: GeoPoint,
        to: GeoPoint
    ): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLon =
            Math.toRadians(to.lon - from.lon)

        val y =
            sin(dLon) * cos(lat2)

        val x =
            cos(lat1) * sin(lat2) -
                sin(lat1) *
                cos(lat2) *
                cos(dLon)

        return normalizeBearing(
            Math.toDegrees(
                atan2(y, x)
            )
        )
    }

    private fun ccwDistance(
        from: Double,
        to: Double
    ): Double {
        return normalizeBearing(from - to)
    }

    private fun angularDistance(
        a: Double,
        b: Double
    ): Double {
        val delta =
            abs(
                normalizeBearing(a) -
                    normalizeBearing(b)
            )

        return min(delta, 360.0 - delta)
    }

    private fun normalizeBearing(
        value: Double
    ): Double {
        val result = value % 360.0
        return if (result < 0) {
            result + 360.0
        } else {
            result
        }
    }

    private fun normalize(
        value: String
    ): String {
        return Normalizer.normalize(
            value.lowercase(),
            Normalizer.Form.NFD
        )
            .replace(
                Regex("\\p{M}+"),
                ""
            )
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

    private fun lowDecision(
        context: RoundaboutContext,
        reason: String
    ): RoundaboutDecision {
        return RoundaboutDecision(
            resolved = false,
            exit = null,
            confidence = RoundaboutConfidence.LOW,
            score = 0,
            targetRoad = context.target.roadName,
            matchedRoad = null,
            source = "roundabout-geometric",
            reasons = listOf(reason)
        )
    }

    private fun circularMean(
        values: List<Double>
    ): Double {
        if (values.isEmpty()) return 0.0

        var x = 0.0
        var y = 0.0

        values.forEach { value ->
            val radians =
                Math.toRadians(value)
            x += cos(radians)
            y += sin(radians)
        }

        return normalizeBearing(
            Math.toDegrees(
                atan2(y, x)
            )
        )
    }
}
