package com.mcu.imagegrains.utils

import org.locationtech.jts.geom.Polygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EnhancedGrainCollectionUtils {

    /**
     * Find connected components using JTS spatial indexing
     */
    suspend fun findConnectedComponents(
        polygons: List<Polygon>,
        overlapThreshold: Double = 0.1
    ): List<List<Polygon>> = withContext(Dispatchers.Default) {

        if (polygons.isEmpty()) return@withContext emptyList()

        println("🔄 Finding connected components with JTS spatial indexing...")

        try {
            // Use the appropriate method based on dataset size
            val overlappingGroups = if (polygons.size > 1000) {
                // For large datasets, use spatial indexing
                SpatialIndexingUtils.findOverlappingPolygons(polygons, overlapThreshold)
            } else {
                // For smaller datasets, use simple method
                SpatialIndexingUtils.findOverlappingPolygonsSimple(polygons, overlapThreshold)
            }

            // Convert index groups to polygon groups
            val components = overlappingGroups.map { indexGroup ->
                indexGroup.mapNotNull { index ->
                    if (index < polygons.size) polygons[index] else null
                }
            }

            println("✅ Found ${components.size} connected components")
            components

        } catch (e: Exception) {
            println("❌ Error finding connected components: ${e.message}")
            e.printStackTrace()

            // Fallback: return each polygon as its own component
            polygons.map { listOf(it) }
        }
    }

    /**
     * Merge overlapping polygons within each component
     */
    suspend fun mergeOverlappingPolygons(
        components: List<List<Polygon>>
    ): List<Polygon> = withContext(Dispatchers.Default) {

        val mergedPolygons = mutableListOf<Polygon>()

        components.forEachIndexed { index, component ->
            try {
                println("🔄 Merging component ${index + 1}/${components.size} with ${component.size} polygons...")

                if (component.size == 1) {
                    mergedPolygons.add(component.first())
                } else {
                    val merged = mergePolygonGroup(component)
                    if (merged != null) {
                        mergedPolygons.add(merged)
                    } else {
                        // If merging fails, keep original polygons
                        mergedPolygons.addAll(component)
                    }
                }
            } catch (e: Exception) {
                println("⚠️ Error merging component $index: ${e.message}")
                // Add original polygons if merging fails
                mergedPolygons.addAll(component)
            }
        }

        println("✅ Merged ${components.size} components into ${mergedPolygons.size} polygons")
        mergedPolygons
    }

    /**
     * Merge a group of polygons into a single polygon
     */
    private fun mergePolygonGroup(polygons: List<Polygon>): Polygon? {
        return try {
            when (polygons.size) {
                0 -> null
                1 -> polygons.first()
                else -> {
                    // Start with first polygon and union with others
                    var result = polygons.first()

                    for (i in 1 until polygons.size) {
                        val union = result.union(polygons[i])

                        // Ensure result is a polygon
                        result = when {
                            union is Polygon -> union
                            union.numGeometries == 1 && union.getGeometryN(0) is Polygon ->
                                union.getGeometryN(0) as Polygon
                            else -> {
                                println("⚠️ Union resulted in non-polygon geometry, keeping original")
                                result
                            }
                        }
                    }

                    result
                }
            }
        } catch (e: Exception) {
            println("⚠️ Error merging polygon group: ${e.message}")
            null
        }
    }

    /**
     * Post-process grains by removing overlaps and merging close polygons
     */
    suspend fun postProcessGrains(
        grains: List<Polygon>,
        minArea: Double = 100.0,
        overlapThreshold: Double = 0.1
    ): List<Polygon> = withContext(Dispatchers.Default) {

        if (grains.isEmpty()) return@withContext emptyList()

        println("🔄 Post-processing ${grains.size} grains...")

        try {
            // Filter by minimum area first
            val filteredGrains = grains.filter { grain ->
                try {
                    grain.area >= minArea
                } catch (e: Exception) {
                    println("⚠️ Error calculating area for grain: ${e.message}")
                    false
                }
            }

            println("🔄 After area filtering: ${filteredGrains.size} grains")

            if (filteredGrains.isEmpty()) return@withContext emptyList()

            // Find connected components
            val components = findConnectedComponents(filteredGrains, overlapThreshold)

            // Merge overlapping polygons
            val mergedGrains = mergeOverlappingPolygons(components)

            println("✅ Post-processing completed: ${grains.size} -> ${mergedGrains.size} grains")
            mergedGrains

        } catch (e: Exception) {
            println("❌ Error in post-processing: ${e.message}")
            e.printStackTrace()
            grains // Return original grains if post-processing fails
        }
    }
}