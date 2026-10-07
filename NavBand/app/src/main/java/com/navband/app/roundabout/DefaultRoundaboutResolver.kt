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
            center
        )

        val target = findTargetBranch(
            branches,
            context.target
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

        val ordered = branches
            .filter { it.id != entry.id }
            .sortedBy {
                ccwDistance(
                    entry.bearing,
                    it.bearing
                )
            }

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

        val entryStrong =
            sameWay(entry, context.current.edges) ||
                roadScore(
                    entry.names,
                    context.current.roadName
                ) >= 82

        val targetScore =
            roadScore(
                target.names,
                context.target.roadName
            )

        val score = min(
            100,
            30 +
                if (entryStrong) 30 else 10 +
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
                targetScore >= 82 &&
                score >= 65

        val reasons = mutableListOf(
            "branches=${branches.size}",
            "entry=${entry.names.joinToString(" / ")}",
            "target=${target.names.joinToString(" / ")}",
            "targetRoadScore=$targetScore",
            "exit=$exit"
        )

        if (!entryStrong) {
            reasons += "entry match non forte"
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

        val source = if (near.isNotEmpty()) near else edges

        val usable = source
            .filter { it.auto }
            .filterNot { it.roundabout }
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
                    ) <= 65.0
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

    private fun findEntryBranch(
        branches: List<Branch>,
        current: NavigationLocationEvidence,
        center: GeoPoint
    ): Branch? {
        val currentWayIds =
            current.edges.mapNotNull { it.wayId }.toSet()

        val currentBearing =
            bearing(
                center,
                current.point
            )

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
        target: NavigationLocationEvidence
    ): Branch? {
        val targetWayIds =
            target.edges.mapNotNull { it.wayId }.toSet()

        return branches
            .map { branch ->
                val wayMatch =
                    branch.wayIds.any {
                        targetWayIds.contains(it)
                    }

                val score =
                    roadScore(
                        branch.names,
                        target.roadName
                    )

                Triple(
                    branch,
                    wayMatch,
                    score +
                        if (wayMatch) 1000 else 0
                )
            }
            .maxByOrNull { it.third }
            ?.let { candidate ->
                if (
                    candidate.second ||
                    roadScore(
                        candidate.first.names,
                        target.roadName
                    ) >= 82
                ) {
                    candidate.first
                } else {
                    null
                }
            }
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

                na == nb ||
                    na.contains(nb) ||
                    nb.contains(na)
            }
        }
    }

    private fun edgeBearing(
        edge: RoadEdgeEvidence,
        center: GeoPoint
    ): Double? {
        edge.probeBearingDegrees
            ?.takeIf { it.isFinite() }
            ?.let {
                return normalizeBearing(it)
            }

        val point =
            edge.correlatedPoint ?: return null

        return bearing(center, point)
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
