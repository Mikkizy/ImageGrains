package com.mcu.imagegrains.utils

import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Envelope
import kotlin.math.*

/**
 * Fast grid-based spatial index optimized for polygon overlap detection
 * Much faster than JTS STRtree for our use case
 */
class FastSpatialIndex(
    imageWidth: Int,
    imageHeight: Int,
    private val cellSize: Int = 50
) {
    private val gridWidth = (imageWidth + cellSize - 1) / cellSize
    private val gridHeight = (imageHeight + cellSize - 1) / cellSize
    private val grid = Array(gridHeight) { Array(gridWidth) { mutableListOf<Int>() } }
    private val polygons = mutableListOf<Polygon>()

    /**
     * Insert polygon into spatial index
     */
    fun insert(polygon: Polygon, index: Int) {
        if (index >= polygons.size) {
            // Expand polygons list if needed
            while (polygons.size <= index) {
                polygons.add(polygon) // This will be overwritten
            }
        }
        polygons[index] = polygon

        val envelope = polygon.envelopeInternal

        val minCellX = max(0, (envelope.minX / cellSize).toInt())
        val maxCellX = min(gridWidth - 1, (envelope.maxX / cellSize).toInt())
        val minCellY = max(0, (envelope.minY / cellSize).toInt())
        val maxCellY = min(gridHeight - 1, (envelope.maxY / cellSize).toInt())

        for (y in minCellY..maxCellY) {
            for (x in minCellX..maxCellX) {
                grid[y][x].add(index)
            }
        }
    }

    /**
     * Find all overlapping groups using fast grid-based detection
     */
    fun findOverlappingGroups(overlapThreshold: Double = 0.1): List<Set<Int>> {
        val visited = BooleanArray(polygons.size)
        val groups = mutableListOf<Set<Int>>()

        for (i in polygons.indices) {
            if (!visited[i]) {
                val group = findConnectedGroup(i, visited, overlapThreshold)
                if (group.size > 1) {
                    groups.add(group)
                }
            }
        }

        return groups
    }

    /**
     * Find all polygons connected to the starting polygon
     */
    private fun findConnectedGroup(
        startIndex: Int,
        visited: BooleanArray,
        overlapThreshold: Double
    ): Set<Int> {
        val group = mutableSetOf<Int>()
        val toCheck = ArrayDeque<Int>()

        toCheck.add(startIndex)

        while (toCheck.isNotEmpty()) {
            val currentIndex = toCheck.removeFirst()

            if (visited[currentIndex]) continue

            visited[currentIndex] = true
            group.add(currentIndex)

            try {
                val currentPolygon = polygons[currentIndex]
                val candidates = queryCandidates(currentPolygon)

                for (candidateIndex in candidates) {
                    if (!visited[candidateIndex] && candidateIndex != currentIndex) {
                        if (checkFastOverlap(currentPolygon, polygons[candidateIndex], overlapThreshold)) {
                            toCheck.add(candidateIndex)
                        }
                    }
                }

            } catch (e: Exception) {
                println("⚠️ Error processing polygon $currentIndex: ${e.message}")
            }
        }

        return group
    }

    /**
     * Query candidate polygons using grid index
     */
    private fun queryCandidates(polygon: Polygon): Set<Int> {
        val candidates = mutableSetOf<Int>()
        val envelope = polygon.envelopeInternal

        // Expand envelope slightly to catch nearby polygons
        val expandedEnvelope = Envelope(envelope)
        expandedEnvelope.expandBy(cellSize.toDouble())

        val minCellX = max(0, (expandedEnvelope.minX / cellSize).toInt())
        val maxCellX = min(gridWidth - 1, (expandedEnvelope.maxX / cellSize).toInt())
        val minCellY = max(0, (expandedEnvelope.minY / cellSize).toInt())
        val maxCellY = min(gridHeight - 1, (expandedEnvelope.maxY / cellSize).toInt())

        for (y in minCellY..maxCellY) {
            for (x in minCellX..maxCellX) {
                candidates.addAll(grid[y][x])
            }
        }

        return candidates
    }

    /**
     * Fast overlap check with early termination
     */
    private fun checkFastOverlap(
        polygon1: Polygon,
        polygon2: Polygon,
        threshold: Double
    ): Boolean {
        return try {
            // Quick envelope check first
            val env1 = polygon1.envelopeInternal
            val env2 = polygon2.envelopeInternal

            if (!env1.intersects(env2)) {
                return false
            }

            // Calculate envelope intersection area for quick estimation
            val intersection = env1.intersection(env2)
            if (intersection == null) return false

            val intersectionArea = intersection.area
            val env1Area = env1.area
            val env2Area = env2.area
            val smallerArea = min(env1Area, env2Area)

            // If envelope overlap is below threshold, skip expensive geometry check
            val envelopeOverlap = if (smallerArea > 0) intersectionArea / smallerArea else 0.0
            if (envelopeOverlap < threshold * 0.5) {
                return false
            }

            // Only do expensive geometry intersection if envelope suggests significant overlap
            if (!polygon1.intersects(polygon2)) {
                return false
            }

            // Calculate actual overlap
            val actualIntersection = polygon1.intersection(polygon2)
            val actualIntersectionArea = actualIntersection.area

            val area1 = polygon1.area
            val area2 = polygon2.area
            val actualSmallerArea = min(area1, area2)

            val overlapPercentage = if (actualSmallerArea > 0) {
                actualIntersectionArea / actualSmallerArea
            } else {
                0.0
            }

            overlapPercentage >= threshold

        } catch (_: Exception) {
            false
        }
    }
}