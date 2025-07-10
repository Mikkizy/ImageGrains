package com.mcu.imagegrains.utils

import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.strtree.STRtree
import kotlin.math.*

/**
 * Simple spatial indexing using JTS STRtree instead of Spatial4j
 * This avoids geographic coordinate system limitations
 */
object SpatialIndexingUtils {

    /**
     * Find overlapping polygons using JTS spatial indexing
     */
    fun findOverlappingPolygons(
        polygons: List<Polygon>,
        overlapThreshold: Double = 0.1
    ): List<Set<Int>> {

        if (polygons.isEmpty()) return emptyList()

        println("🔄 Building JTS spatial index for ${polygons.size} polygons...")

        // Create STRtree spatial index
        val spatialIndex = STRtree()

        // Add all polygons to the index with their envelope and index
        polygons.forEachIndexed { index, polygon ->
            try {
                val envelope = polygon.envelopeInternal
                spatialIndex.insert(envelope, index)
            } catch (e: Exception) {
                println("⚠️ Failed to index polygon $index: ${e.message}")
            }
        }

        println("✅ Spatial index built successfully")

        // Find overlapping groups
        val visited = BooleanArray(polygons.size)
        val overlappingGroups = mutableListOf<Set<Int>>()

        for (i in polygons.indices) {
            if (!visited[i]) {
                val group = findConnectedGroup(i, polygons, spatialIndex, visited, overlapThreshold)
                if (group.size > 1) {
                    overlappingGroups.add(group)
                }
            }
        }

        println("✅ Found ${overlappingGroups.size} overlapping groups")
        return overlappingGroups
    }

    /**
     * Find all polygons connected to the starting polygon
     */
    private fun findConnectedGroup(
        startIndex: Int,
        polygons: List<Polygon>,
        spatialIndex: STRtree,
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
                val envelope = currentPolygon.envelopeInternal

                // Expand envelope slightly to catch nearby polygons
                val expandedEnvelope = Envelope(envelope)
                expandedEnvelope.expandBy(10.0) // Expand by 10 pixels

                // Query spatial index for potential overlaps
                @Suppress("UNCHECKED_CAST")
                val candidates = spatialIndex.query(expandedEnvelope) as List<Int>

                for (candidateIndex in candidates) {
                    if (!visited[candidateIndex] && candidateIndex != currentIndex) {
                        val candidatePolygon = polygons[candidateIndex]

                        if (checkOverlap(currentPolygon, candidatePolygon, overlapThreshold)) {
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
     * Check if two polygons overlap above threshold
     */
    private fun checkOverlap(
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

            // Check actual intersection
            if (!polygon1.intersects(polygon2)) {
                return false
            }

            // Calculate overlap percentage
            val intersection = polygon1.intersection(polygon2)
            val intersectionArea = intersection.area

            val area1 = polygon1.area
            val area2 = polygon2.area
            val smallerArea = minOf(area1, area2)

            val overlapPercentage = if (smallerArea > 0) {
                intersectionArea / smallerArea
            } else {
                0.0
            }

            overlapPercentage >= threshold

        } catch (e: Exception) {
            println("⚠️ Error checking overlap: ${e.message}")
            false
        }
    }

    /**
     * Alternative simple implementation without spatial indexing for small datasets
     */
    fun findOverlappingPolygonsSimple(
        polygons: List<Polygon>,
        overlapThreshold: Double = 0.1
    ): List<Set<Int>> {

        if (polygons.size < 2) return emptyList()

        println("🔄 Finding overlaps using simple O(n²) method for ${polygons.size} polygons...")

        val visited = BooleanArray(polygons.size)
        val overlappingGroups = mutableListOf<Set<Int>>()

        for (i in polygons.indices) {
            if (!visited[i]) {
                val group = mutableSetOf<Int>()
                val toCheck = ArrayDeque<Int>()
                toCheck.add(i)

                while (toCheck.isNotEmpty()) {
                    val current = toCheck.removeFirst()
                    if (visited[current]) continue

                    visited[current] = true
                    group.add(current)

                    // Check against all other polygons
                    for (j in polygons.indices) {
                        if (!visited[j] && j != current) {
                            try {
                                if (checkOverlap(polygons[current], polygons[j], overlapThreshold)) {
                                    toCheck.add(j)
                                }
                            } catch (e: Exception) {
                                println("⚠️ Error checking overlap between $current and $j: ${e.message}")
                            }
                        }
                    }
                }

                if (group.size > 1) {
                    overlappingGroups.add(group)
                }
            }
        }

        println("✅ Found ${overlappingGroups.size} overlapping groups")
        return overlappingGroups
    }
}

/**
 * Fallback spatial indexing using simple grid
 */
class SimpleGridIndex(
    private val bounds: Envelope,
    private val gridSize: Int = 100
) {
    private val cellWidth = bounds.width / gridSize
    private val cellHeight = bounds.height / gridSize
    private val grid = Array(gridSize) { Array(gridSize) { mutableListOf<Int>() } }

    fun insert(polygon: Polygon, index: Int) {
        try {
            val envelope = polygon.envelopeInternal

            val minCellX = ((envelope.minX - bounds.minX) / cellWidth).toInt().coerceIn(0, gridSize - 1)
            val maxCellX = ((envelope.maxX - bounds.minX) / cellWidth).toInt().coerceIn(0, gridSize - 1)
            val minCellY = ((envelope.minY - bounds.minY) / cellHeight).toInt().coerceIn(0, gridSize - 1)
            val maxCellY = ((envelope.maxY - bounds.minY) / cellHeight).toInt().coerceIn(0, gridSize - 1)

            for (x in minCellX..maxCellX) {
                for (y in minCellY..maxCellY) {
                    grid[x][y].add(index)
                }
            }
        } catch (e: Exception) {
            println("⚠️ Error inserting into grid: ${e.message}")
        }
    }

    fun query(envelope: Envelope): List<Int> {
        val result = mutableSetOf<Int>()

        try {
            val minCellX = ((envelope.minX - bounds.minX) / cellWidth).toInt().coerceIn(0, gridSize - 1)
            val maxCellX = ((envelope.maxX - bounds.minX) / cellWidth).toInt().coerceIn(0, gridSize - 1)
            val minCellY = ((envelope.minY - bounds.minY) / cellHeight).toInt().coerceIn(0, gridSize - 1)
            val maxCellY = ((envelope.maxY - bounds.minY) / cellHeight).toInt().coerceIn(0, gridSize - 1)

            for (x in minCellX..maxCellX) {
                for (y in minCellY..maxCellY) {
                    result.addAll(grid[x][y])
                }
            }
        } catch (e: Exception) {
            println("⚠️ Error querying grid: ${e.message}")
        }

        return result.toList()
    }
}