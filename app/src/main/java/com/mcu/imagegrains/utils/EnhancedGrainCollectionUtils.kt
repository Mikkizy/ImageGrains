package com.mcu.imagegrains.utils

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryFixer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

object EnhancedGrainCollectionUtils {

    /**
     * Find connected components using spatial indexing for better performance
     */
    suspend fun findConnectedComponents(
        allGrains: List<Polygon>,
        minArea: Double,
        progressCallback: (Float) -> Unit = {}
    ): Triple<List<Polygon>, List<Set<Int>>, List<Pair<Int, Int>>> = withContext(Dispatchers.Default) {

        println("🔄 Finding connected components with spatial indexing...")

        // Find overlapping polygons using spatial index
        val overlappingPairs = SpatialIndexingUtils.findOverlappingPolygons(
            allGrains,
            minOverlap = 0.4,
            progressCallback = { progress -> progressCallback(progress * 0.7f) }
        )

        progressCallback(0.7f)

        // Build adjacency graph
        val adjacencyMap = mutableMapOf<Int, MutableSet<Int>>()
        for (i in allGrains.indices) {
            adjacencyMap[i] = mutableSetOf()
        }

        for ((i, j) in overlappingPairs) {
            adjacencyMap[i]?.add(j)
            adjacencyMap[j]?.add(i)
        }

        // Find connected components using DFS
        val visited = BooleanArray(allGrains.size) { false }
        val components = mutableListOf<Set<Int>>()

        for (i in allGrains.indices) {
            if (!visited[i]) {
                val component = mutableSetOf<Int>()
                dfsConnectedComponent(i, adjacencyMap, visited, component)
                if (component.isNotEmpty()) {
                    components.add(component)
                }
            }
        }

        progressCallback(0.8f)

        // Collect non-overlapping grains
        val connectedGrains = components.flatten().toSet()
        val newGrains = mutableListOf<Polygon>()

        for (i in allGrains.indices) {
            if (i !in connectedGrains && allGrains[i].area >= minArea) {
                val grain = if (!allGrains[i].isValid) {
                    GeometryFixer.fix(allGrains[i]) as? Polygon ?: allGrains[i]
                } else {
                    allGrains[i]
                }
                newGrains.add(grain)
            }
        }

        progressCallback(1.0f)

        println("✅ Found ${components.size} connected components, ${newGrains.size} non-overlapping grains")

        Triple(newGrains, components, overlappingPairs)
    }

    private fun dfsConnectedComponent(
        node: Int,
        adjacencyMap: Map<Int, Set<Int>>,
        visited: BooleanArray,
        component: MutableSet<Int>
    ) {
        visited[node] = true
        component.add(node)

        adjacencyMap[node]?.forEach { neighbor ->
            if (!visited[neighbor]) {
                dfsConnectedComponent(neighbor, adjacencyMap, visited, component)
            }
        }
    }

    /**
     * Merge overlapping polygons using spatial indexing for efficiency
     */
    suspend fun mergeOverlappingPolygons(
        allGrains: List<Polygon>,
        newGrains: MutableList<Polygon>,
        components: List<Set<Int>>,
        minArea: Double,
        imagePred: Array<Array<FloatArray>>,
        progressCallback: (Float) -> Unit = {}
    ): List<Polygon> = withContext(Dispatchers.Default) {

        println("🔄 Merging ${components.size} overlapping polygon groups...")

        components.forEachIndexed { componentIndex, component ->

            // Get polygons in this component
            val polygonsInComponent = component.map { allGrains[it] }

            if (polygonsInComponent.isNotEmpty()) {
                // Find most similar polygon (simplified version)
                val mostSimilarPolygon = pickMostSimilarPolygon(polygonsInComponent, imagePred)

                // Process difference polygons
                val differencePolygons = mutableListOf<Polygon>()

                for (polygon in polygonsInComponent) {
                    if (polygon != mostSimilarPolygon) {
                        try {
                            val difference = polygon.difference(mostSimilarPolygon)

                            when (difference) {
                                is Polygon -> {
                                    if (difference.area >= minArea) {
                                        differencePolygons.add(difference)
                                    }
                                }
                                is MultiPolygon -> {
                                    // Get largest polygon from MultiPolygon
                                    val largestPoly = (0 until difference.numGeometries)
                                        .map { difference.getGeometryN(it) as Polygon }
                                        .maxByOrNull { it.area }

                                    if (largestPoly != null && largestPoly.area >= minArea) {
                                        differencePolygons.add(largestPoly)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            println("❌ Error computing difference: ${e.message}")
                        }
                    }
                }

                // Select non-overlapping difference polygons using spatial indexing
                val selectedPolygons = selectNonOverlappingPolygons(
                    differencePolygons,
                    minArea,
                    imagePred
                )

                // Apply morphological opening (erosion followed by dilation)
                val openedPolygons = selectedPolygons.mapNotNull { polygon ->
                    try {
                        val eroded = polygon.buffer(-5.0) // Erosion
                        val opened = eroded.buffer(5.0)   // Dilation

                        if (opened is Polygon && opened.area >= minArea) {
                            opened
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }

                // Add valid polygons to result
                if (mostSimilarPolygon.area >= minArea && mostSimilarPolygon !in newGrains) {
                    newGrains.add(mostSimilarPolygon)
                }
                newGrains.addAll(openedPolygons)
            }

            // Update progress
            val progress = (componentIndex + 1).toFloat() / components.size
            progressCallback(progress)
        }

        println("✅ Merged overlapping polygons: ${newGrains.size} final grains")
        return@withContext newGrains.toList()
    }

    private fun pickMostSimilarPolygon(
        polygons: List<Polygon>,
        imagePred: Array<Array<FloatArray>>
    ): Polygon {
        if (polygons.size == 1) return polygons[0]

        // Simplified similarity metric based on grain probability
        var bestPolygon = polygons[0]
        var bestScore = Double.MIN_VALUE

        for (polygon in polygons) {
            try {
                val score = calculatePolygonGrainScore(polygon, imagePred)
                if (score > bestScore) {
                    bestScore = score
                    bestPolygon = polygon
                }
            } catch (e: Exception) {
                // Continue with next polygon if error
            }
        }

        return bestPolygon
    }

    private fun calculatePolygonGrainScore(
        polygon: Polygon,
        imagePred: Array<Array<FloatArray>>
    ): Double {
        val envelope = polygon.envelopeInternal
        val minX = envelope.minX.toInt().coerceAtLeast(0)
        val maxX = envelope.maxX.toInt().coerceAtMost(imagePred[0].size - 1)
        val minY = envelope.minY.toInt().coerceAtLeast(0)
        val maxY = envelope.maxY.toInt().coerceAtMost(imagePred.size - 1)

        var grainSum = 0.0
        var pixelCount = 0
        val geometryFactory = GeometryFactory()

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val point = geometryFactory.createPoint(Coordinate(x.toDouble(), y.toDouble()))
                if (polygon.contains(point)) {
                    grainSum += imagePred[y][x][1] // Grain channel
                    pixelCount++
                }
            }
        }

        return if (pixelCount > 0) grainSum / pixelCount else 0.0
    }

    private suspend fun selectNonOverlappingPolygons(
        polygons: List<Polygon>,
        minArea: Double,
        imagePred: Array<Array<FloatArray>>
    ): List<Polygon> = withContext(Dispatchers.Default) {

        if (polygons.isEmpty()) return@withContext emptyList()
        if (polygons.size == 1) {
            return@withContext if (polygons[0].area >= minArea) polygons else emptyList()
        }

        // Use spatial indexing to efficiently find overlaps
        val overlappingPairs = SpatialIndexingUtils.findOverlappingPolygons(
            polygons,
            minOverlap = 0.1
        )

        // Start with the most similar polygon
        val selectedPolygons = mutableListOf<Polygon>()
        val mostSimilar = pickMostSimilarPolygon(polygons, imagePred)
        selectedPolygons.add(mostSimilar)

        // Add non-overlapping polygons
        for (polygon in polygons) {
            if (polygon == mostSimilar) continue
            if (polygon.area < minArea) continue

            var hasOverlap = false
            for (selected in selectedPolygons) {
                val polyIndex = polygons.indexOf(polygon)
                val selectedIndex = polygons.indexOf(selected)

                if (overlappingPairs.any {
                        (it.first == polyIndex && it.second == selectedIndex) ||
                                (it.first == selectedIndex && it.second == polyIndex)
                    }) {
                    hasOverlap = true
                    break
                }
            }

            if (!hasOverlap) {
                selectedPolygons.add(polygon)
            }
        }

        selectedPolygons
    }
}