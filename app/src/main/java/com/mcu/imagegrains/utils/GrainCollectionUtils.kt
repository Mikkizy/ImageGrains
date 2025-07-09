package com.mcu.imagegrains.utils

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.operation.buffer.BufferOp
import kotlin.math.*

object GrainCollectionUtils {

    private val geometryFactory = GeometryFactory()

    /**
     * Collect polygon from mask and append to grains list
     */
    fun collectPolygonFromMask(
        labels: Array<IntArray>,
        mask: Array<BooleanArray>,
        imagePred: Array<Array<FloatArray>>,
        allGrains: MutableList<Polygon>,
        sx: FloatArray,
        sy: FloatArray,
        minArea: Int = 100,
        maxNLargeGrains: Int = 10,
        maxBgFraction: Float = 0.7f
    ): MutableList<Polygon> {

        // Find labels in mask
        val labelsInMask = mutableSetOf<Int>()
        for (i in mask.indices) {
            for (j in mask[i].indices) {
                if (mask[i][j]) {
                    labelsInMask.add(labels[i][j])
                }
            }
        }

        // Find large labels in mask
        val largeLabelCounts = mutableMapOf<Int, Int>()
        for (label in labelsInMask) {
            var count = 0
            for (i in mask.indices) {
                for (j in mask[i].indices) {
                    if (mask[i][j] && labels[i][j] == label) {
                        count++
                    }
                }
            }
            if (count >= minArea) {
                largeLabelCounts[label] = count
            }
        }

        // Calculate background fraction
        var bgSum = 0f
        var totalPixels = 0
        for (i in mask.indices) {
            for (j in mask[i].indices) {
                if (mask[i][j]) {
                    bgSum += imagePred[i][j][0] // Background channel
                    totalPixels++
                }
            }
        }
        val avgBgFraction = if (totalPixels > 0) bgSum / totalPixels else 1f

        // Check conditions for valid grain
        if (largeLabelCounts.size < maxNLargeGrains && avgBgFraction < maxBgFraction) {
            try {
                // Create polygon from coordinates
                val coordinates = Array(sx.size) { i ->
                    Coordinate(sx[i].toDouble(), sy[i].toDouble())
                }

                // Ensure polygon is closed
                if (coordinates.isNotEmpty() &&
                    (coordinates.first().x != coordinates.last().x ||
                            coordinates.first().y != coordinates.last().y)) {
                    val closedCoords = Array(coordinates.size + 1) { i ->
                        if (i < coordinates.size) coordinates[i] else coordinates[0]
                    }
                    val polygon = geometryFactory.createPolygon(closedCoords)

                    // Fix invalid geometry
                    val validPolygon = if (!polygon.isValid) {
                        GeometryFixer.fix(polygon) as? Polygon ?: polygon
                    } else {
                        polygon
                    }

                    allGrains.add(validPolygon)
                } else if (coordinates.size >= 3) {
                    val polygon = geometryFactory.createPolygon(coordinates)
                    val validPolygon = if (!polygon.isValid) {
                        GeometryFixer.fix(polygon) as? Polygon ?: polygon
                    } else {
                        polygon
                    }
                    allGrains.add(validPolygon)
                }

            } catch (e: Exception) {
                println("❌ Failed to create polygon: ${e.message}")
            }
        }

        return allGrains
    }

    /**
     * Find contours in boolean mask (simplified version of skimage.measure.find_contours)
     */
    fun findContours(mask: Array<BooleanArray>, level: Float = 0.5f): List<Pair<FloatArray, FloatArray>> {
        val contours = mutableListOf<Pair<FloatArray, FloatArray>>()
        val height = mask.size
        val width = mask[0].size
        val visited = Array(height) { BooleanArray(width) { false } }

        // Simple contour following algorithm
        for (i in 0 until height - 1) {
            for (j in 0 until width - 1) {
                if (mask[i][j] && !visited[i][j]) {
                    val contour = traceContour(mask, visited, i, j)
                    if (contour.first.size >= 3) { // Minimum points for a valid contour
                        contours.add(contour)
                    }
                }
            }
        }

        return contours
    }

    private fun traceContour(
        mask: Array<BooleanArray>,
        visited: Array<BooleanArray>,
        startI: Int,
        startJ: Int
    ): Pair<FloatArray, FloatArray> {
        val height = mask.size
        val width = mask[0].size
        val contourX = mutableListOf<Float>()
        val contourY = mutableListOf<Float>()

        // 8-connected neighbors (clockwise)
        val di = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
        val dj = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

        var i = startI
        var j = startJ
        var direction = 0

        do {
            contourX.add(j.toFloat())
            contourY.add(i.toFloat())
            visited[i][j] = true

            // Find next point on contour
            var found = false
            for (k in 0 until 8) {
                val nextDir = (direction + k) % 8
                val ni = i + di[nextDir]
                val nj = j + dj[nextDir]

                if (ni >= 0 && ni < height && nj >= 0 && nj < width && mask[ni][nj]) {
                    i = ni
                    j = nj
                    direction = (nextDir + 6) % 8 // Turn left for next search
                    found = true
                    break
                }
            }

            if (!found) break

        } while (!(i == startI && j == startJ) && contourX.size < width * height)

        return Pair(contourX.toFloatArray(), contourY.toFloatArray())
    }

    /**
     * Create labeled image from polygons
     */
    fun createLabeledImage(
        allGrains: List<Polygon>,
        imageWidth: Int,
        imageHeight: Int
    ): Pair<Array<IntArray>, Array<IntArray>> {

        val rasterized = Array(imageHeight) { IntArray(imageWidth) { 0 } }
        val maskAll = Array(imageHeight) { IntArray(imageWidth) { 0 } }

        // Rasterize each grain
        allGrains.forEachIndexed { index, grain ->
            val label = index + 1
            rasterizePolygon(grain, rasterized, label)

            // Create boundary
            val boundary = grain.boundary.buffer(2.0)
            if (boundary is Polygon) {
                rasterizePolygon(boundary, maskAll, 2) // Boundary value
            }
        }

        // Set grain pixels in mask
        for (i in 0 until imageHeight) {
            for (j in 0 until imageWidth) {
                if (rasterized[i][j] > 0) {
                    maskAll[i][j] = 1 // Grain value
                }
            }
        }

        return Pair(rasterized, maskAll)
    }

    private fun rasterizePolygon(polygon: Polygon, raster: Array<IntArray>, value: Int) {
        val envelope = polygon.envelopeInternal
        val minX = max(0, envelope.minX.toInt())
        val maxX = min(raster[0].size - 1, envelope.maxX.toInt())
        val minY = max(0, envelope.minY.toInt())
        val maxY = min(raster.size - 1, envelope.maxY.toInt())

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val point = geometryFactory.createPoint(Coordinate(x.toDouble(), y.toDouble()))
                if (polygon.contains(point)) {
                    raster[y][x] = value
                }
            }
        }
    }
}