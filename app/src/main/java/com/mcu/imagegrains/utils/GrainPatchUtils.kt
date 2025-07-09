package com.mcu.imagegrains.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon

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

        progressCallback(0.1f)

        // Step 1: Find overlapping polygons
        val overlappingPairs = SpatialIndexingUtils.findOverlappingPolygons(
            allGrains,
            minOverlap = 0.01 // Lower threshold for removal
        ) { progress -> progressCallback(0.1f + progress * 0.4f) }

        progressCallback(0.5f)

        // Step 2: Determine which polygons to remove (keep larger ones)
        val polygonsToRemove = mutableSetOf<Int>()

        for ((i, j) in overlappingPairs) {
            val poly1Area = allGrains[i].area
            val poly2Area = allGrains[j].area

            if (poly1Area >= poly2Area) {
                polygonsToRemove.add(j)
            } else {
                polygonsToRemove.add(i)
            }
        }

        progressCallback(0.6f)

        // Step 3: Create filtered grain list
        val filteredGrains = allGrains.filterIndexed { index, _ ->
            index !in polygonsToRemove
        }

        println("✅ Removed ${polygonsToRemove.size} overlapping grains, kept ${filteredGrains.size}")

        progressCallback(0.8f)

        // Step 4: Create labeled image and mask
        val (rasterized, maskAll) = createLabeledImageAndMask(
            filteredGrains,
            imageWidth,
            imageHeight
        )

        progressCallback(1.0f)

        Triple(filteredGrains, rasterized, maskAll)
    }

    /**
     * Create labeled image and mask from grains
     */
    private fun createLabeledImageAndMask(
        allGrains: List<Polygon>,
        imageWidth: Int,
        imageHeight: Int
    ): Pair<Array<IntArray>, Array<IntArray>> {

        // Initialize arrays
        val rasterized = Array(imageHeight) { IntArray(imageWidth) { 0 } }
        val maskAll = Array(imageHeight) { IntArray(imageWidth) { 0 } }

        // Rasterize grains
        allGrains.forEachIndexed { index, grain ->
            val label = index + 1
            rasterizePolygonToArray(grain, rasterized, label)
        }

        // Create boundaries
        val boundaries = allGrains.map { grain ->
            grain.boundary.buffer(2.0)
        }

        // Rasterize boundaries
        val boundariesRasterized = Array(imageHeight) { IntArray(imageWidth) { 0 } }
        boundaries.forEachIndexed { index, boundary ->
            when (boundary) {
                is Polygon -> rasterizePolygonToArray(boundary, boundariesRasterized, 1)
                is MultiPolygon -> {
                    for (i in 0 until boundary.numGeometries) {
                        val poly = boundary.getGeometryN(i) as? Polygon
                        poly?.let { rasterizePolygonToArray(it, boundariesRasterized, 1) }
                    }
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

        return Pair(rasterized, maskAll)
    }

    /**
     * Rasterize a single polygon to an integer array
     */
    private fun rasterizePolygonToArray(
        polygon: Polygon,
        array: Array<IntArray>,
        value: Int
    ) {
        val geometryFactory = GeometryFactory()
        val envelope = polygon.envelopeInternal

        val minX = kotlin.math.max(0, envelope.minX.toInt())
        val maxX = kotlin.math.min(array[0].size - 1, envelope.maxX.toInt())
        val minY = kotlin.math.max(0, envelope.minY.toInt())
        val maxY = kotlin.math.min(array.size - 1, envelope.maxY.toInt())

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val point = geometryFactory.createPoint(Coordinate(x.toDouble(), y.toDouble()))
                if (polygon.contains(point)) {
                    array[y][x] = value
                }
            }
        }
    }
}