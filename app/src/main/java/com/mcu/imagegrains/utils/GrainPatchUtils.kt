package com.mcu.imagegrains.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.prep.PreparedGeometryFactory

object GrainPatchUtils {

    /**
     * Extract grains from polygon list and remove overlapping ones
     * This is the equivalent of get_grains_from_patches in Python
     */
    suspend fun getGrainsFromPolygons(
        allGrains: List<Polygon>,
        imageWidth: Int,
        imageHeight: Int,
        progressCallback: (Float) -> Unit = {}
    ): Triple<List<Polygon>, Array<IntArray>, Array<IntArray>> = withContext(Dispatchers.Default) {

        println("🔄 Extracting grains from ${allGrains.size} polygons...")

        if (allGrains.isEmpty()) {
            val emptyRasterized = Array(imageHeight) { IntArray(imageWidth) { 0 } }
            val emptyMask = Array(imageHeight) { IntArray(imageWidth) { 0 } }
            return@withContext Triple(emptyList(), emptyRasterized, emptyMask)
        }

        progressCallback(0.1f)

        try {
            // Find overlapping polygons using updated API
            val overlappingGroups = SpatialIndexingUtils.findOverlappingPolygons(
                allGrains,
                overlapThreshold = 0.01 // Lower threshold for removal
            )

            progressCallback(0.5f)

            // Determine which polygons to remove (keep larger ones from each group)
            val polygonsToRemove = mutableSetOf<Int>()

            for (group in overlappingGroups) {
                if (group.size > 1) {
                    // Find the polygon with the largest area in this group
                    val groupWithAreas = group.map { index ->
                        index to try {
                            allGrains[index].area
                        } catch (_: Exception) {
                            0.0 // Fallback area
                        }
                    }

                    val largestIndex = groupWithAreas.maxByOrNull { it.second }?.first

                    // Remove all others in the group except the largest
                    group.forEach { index ->
                        if (index != largestIndex) {
                            polygonsToRemove.add(index)
                        }
                    }
                }
            }

            progressCallback(0.6f)

            // Create filtered grain list
            val filteredGrains = allGrains.filterIndexed { index, polygon ->
                try {
                    index !in polygonsToRemove && polygon.isValid && !polygon.isEmpty
                } catch (e: Exception) {
                    println("⚠️ Error validating polygon $index: ${e.message}")
                    false
                }
            }

            println("✅ Removed ${polygonsToRemove.size} overlapping grains, kept ${filteredGrains.size}")

            progressCallback(0.8f)

            // Create labeled image and mask
            val (rasterized, maskAll) = createLabeledImageAndMask(
                filteredGrains,
                imageWidth,
                imageHeight
            )

            progressCallback(1.0f)

            Triple(filteredGrains, rasterized, maskAll)

        } catch (e: Exception) {
            println("❌ Error in getGrainsFromPolygons: ${e.message}")
            e.printStackTrace()

            // Fallback: return original grains without overlap removal
            val (rasterized, maskAll) = createLabeledImageAndMask(
                allGrains,
                imageWidth,
                imageHeight
            )

            progressCallback(1.0f)
            Triple(allGrains, rasterized, maskAll)
        }
    }

    /**
     * Create labeled image and mask from grains with memory optimization
     */
    private fun createLabeledImageAndMask(
        allGrains: List<Polygon>,
        imageWidth: Int,
        imageHeight: Int
    ): Pair<Array<IntArray>, Array<IntArray>> {

        println("🔄 Creating labeled image ${imageWidth}x${imageHeight} for ${allGrains.size} grains...")

        // Check memory before allocating large arrays
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()

        if (memoryUsagePercent > 70) {
            println("⚠️ High memory usage (${memoryUsagePercent}%), forcing GC...")
            System.gc()
        }

        // Initialize arrays
        val rasterized = Array(imageHeight) { IntArray(imageWidth) { 0 } }
        val maskAll = Array(imageHeight) { IntArray(imageWidth) { 0 } }

        try {
            // Rasterize grains in batches
            val batchSize = 100
            allGrains.chunked(batchSize).forEachIndexed { batchIndex, grainBatch ->
                grainBatch.forEachIndexed { indexInBatch, grain ->
                    val globalIndex = batchIndex * batchSize + indexInBatch
                    val label = globalIndex + 1

                    try {
                        rasterizePolygonToArrayOptimized(grain, rasterized, label, imageWidth, imageHeight)
                    } catch (e: Exception) {
                        println("⚠️ Error rasterizing grain $globalIndex: ${e.message}")
                    }
                }

                // Force GC between batches
                if (batchIndex % 10 == 0) {
                    System.gc()
                }
            }

            // Create boundaries with simplified approach
            val boundariesRasterized = Array(imageHeight) { IntArray(imageWidth) { 0 } }

            allGrains.chunked(batchSize).forEach { grainBatch ->
                grainBatch.forEach { grain ->
                    try {
                        val boundary = grain.boundary.buffer(1.0) // Reduced buffer size
                        when (boundary) {
                            is Polygon -> rasterizePolygonToArrayOptimized(
                                boundary, boundariesRasterized, 1, imageWidth, imageHeight
                            )
                            is MultiPolygon -> {
                                for (i in 0 until boundary.numGeometries) {
                                    val poly = boundary.getGeometryN(i) as? Polygon
                                    poly?.let {
                                        rasterizePolygonToArrayOptimized(
                                            it, boundariesRasterized, 1, imageWidth, imageHeight
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("⚠️ Error creating boundary: ${e.message}")
                    }
                }
            }

            // Create final mask
            for (i in 0 until imageHeight) {
                for (j in 0 until imageWidth) {
                    when {
                        rasterized[i][j] > 0 -> maskAll[i][j] = 1 // Grain
                        boundariesRasterized[i][j] >= 1 -> maskAll[i][j] = 2 // Boundary
                        else -> maskAll[i][j] = 0 // Background
                    }
                }
            }

        } catch (e: OutOfMemoryError) {
            println("❌ OutOfMemoryError in createLabeledImageAndMask: ${e.message}")
            System.gc()
        } catch (e: Exception) {
            println("❌ Error in createLabeledImageAndMask: ${e.message}")
        }

        println("✅ Labeled image and mask created successfully")
        return Pair(rasterized, maskAll)
    }

    /**
     * Optimized polygon rasterization with memory safety
     */
    private fun rasterizePolygonToArrayOptimized(
        polygon: Polygon,
        array: Array<IntArray>,
        value: Int,
        imageWidth: Int,
        imageHeight: Int
    ) {
        try {
            val envelope = polygon.envelopeInternal

            val minX = kotlin.math.max(0, envelope.minX.toInt())
            val maxX = kotlin.math.min(imageWidth - 1, envelope.maxX.toInt())
            val minY = kotlin.math.max(0, envelope.minY.toInt())
            val maxY = kotlin.math.min(imageHeight - 1, envelope.maxY.toInt())

            // Limit processing area to prevent memory issues
            val area = (maxX - minX + 1) * (maxY - minY + 1)
            if (area > 100000) { // Skip very large polygons
                println("⚠️ Polygon too large for rasterization (${area} pixels), skipping...")
                return
            }

            // Use prepared geometry for faster contains() checks
            val preparedGeom = PreparedGeometryFactory.prepare(polygon)
            val geometryFactory = GeometryFactory()

            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    if (y < array.size && x < array[0].size) {
                        val point = geometryFactory.createPoint(Coordinate(x.toDouble(), y.toDouble()))
                        if (preparedGeom.contains(point)) {
                            array[y][x] = value
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("⚠️ Error in optimized rasterization: ${e.message}")
        }
    }

}