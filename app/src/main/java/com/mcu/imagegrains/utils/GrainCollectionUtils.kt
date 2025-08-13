package com.mcu.imagegrains.utils

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryFixer
import kotlin.math.*

object GrainCollectionUtils {

    private val geometryFactory = GeometryFactory()

    // Memory optimization constants
    private const val MAX_CONTOUR_POINTS = 2000
    private const val MAX_GRAIN_SIZE = 100000 // Maximum pixels per grain
    private const val BATCH_SIZE = 100 // Process in batches to prevent OOM

    /**
     * Collect polygon from mask and append to grains list with memory optimization
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

        // Check memory before processing
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()

        if (memoryUsagePercent > 80) {
            println("⚠️ High memory usage (${memoryUsagePercent}%), forcing GC...")
            System.gc()
        }

        try {
            // Find labels in mask with memory optimization
            val labelsInMask = mutableSetOf<Int>()

            // Process mask in chunks to reduce memory pressure
            val chunkSize = 1000
            for (iStart in 0 until mask.size step chunkSize) {
                val iEnd = minOf(iStart + chunkSize, mask.size)

                for (i in iStart until iEnd) {
                    for (j in mask[i].indices) {
                        if (mask[i][j]) {
                            labelsInMask.add(labels[i][j])

                            // Limit set size to prevent memory issues
                            if (labelsInMask.size > 10000) {
                                println("⚠️ Too many labels, truncating to prevent memory issues")
                                break
                            }
                        }
                    }
                }
            }

            // Find large labels in mask with memory optimization
            val largeLabelCounts = mutableMapOf<Int, Int>()

            for (label in labelsInMask.take(1000)) { // Limit processing to prevent OOM
                var count = 0
                var processed = 0

                outerLoop@ for (i in mask.indices) {
                    for (j in mask[i].indices) {
                        if (mask[i][j] && labels[i][j] == label) {
                            count++
                        }
                        processed++

                        // Break if taking too long (memory safety)
                        if (processed > MAX_GRAIN_SIZE) {
                            break@outerLoop
                        }
                    }
                }

                if (count >= minArea && count <= MAX_GRAIN_SIZE) {
                    largeLabelCounts[label] = count
                }
            }

            // Calculate background fraction with sampling for large images
            var bgSum = 0f
            var totalPixels = 0
            val samplingRate = if (mask.size * mask[0].size > 1000000) 4 else 1 // Sample every 4th pixel for large images

            for (i in mask.indices step samplingRate) {
                for (j in mask[i].indices step samplingRate) {
                    if (mask[i][j]) {
                        bgSum += imagePred[i][j][0] // Background channel
                        totalPixels++
                    }
                }
            }
            val avgBgFraction = if (totalPixels > 0) bgSum / totalPixels else 1f

            // Check conditions for valid grain
            if (largeLabelCounts.size < maxNLargeGrains && avgBgFraction < maxBgFraction) {
                // Limit coordinate array size to prevent OOM
                val maxCoords = minOf(sx.size, MAX_CONTOUR_POINTS)
                val decimationFactor = if (sx.size > MAX_CONTOUR_POINTS) {
                    sx.size / MAX_CONTOUR_POINTS
                } else {
                    1
                }

                val coordinates = Array(maxCoords) { i ->
                    val sourceIndex = i * decimationFactor
                    Coordinate(sx[sourceIndex].toDouble(), sy[sourceIndex].toDouble())
                }

                // Ensure polygon is closed
                val finalCoords = if (coordinates.isNotEmpty() &&
                    (coordinates.first().x != coordinates.last().x ||
                            coordinates.first().y != coordinates.last().y)) {
                    Array(coordinates.size + 1) { i ->
                        if (i < coordinates.size) coordinates[i] else coordinates[0]
                    }
                } else {
                    coordinates
                }

                if (finalCoords.size >= 4) { // Minimum for valid polygon (3 points + close)
                    val polygon = geometryFactory.createPolygon(finalCoords)

                    // Fix invalid geometry with timeout to prevent hanging
                    val validPolygon = if (!polygon.isValid) {
                        try {
                            GeometryFixer.fix(polygon) as? Polygon ?: polygon
                        } catch (e: Exception) {
                            println("⚠️ Geometry fixing failed, using original: ${e.message}")
                            polygon
                        }
                    } else {
                        polygon
                    }

                    allGrains.add(validPolygon)
                }
            }

        } catch (e: OutOfMemoryError) {
            println("❌ OutOfMemoryError in collectPolygonFromMask: ${e.message}")
            System.gc()
        } catch (e: Exception) {
            println("❌ Failed to create polygon: ${e.message}")
        }

        return allGrains
    }

    /**
     * Find contours with memory optimization
     */
    fun findContours(
        mask: Array<BooleanArray>
    ): List<Pair<FloatArray, FloatArray>> {

        val contours = mutableListOf<Pair<FloatArray, FloatArray>>()
        val height = mask.size
        val width = mask[0].size
        val visited = Array(height) { BooleanArray(width) { false } }

        println("🔍 Finding contours in ${width}x${height} mask...")

        // Check memory before processing
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()

        println("📊 Memory before contour finding: ${memoryUsagePercent}%")

        if (memoryUsagePercent > 80) {
            println("⚠️ High memory usage, forcing GC...")
            System.gc()
        }

        var processedContours = 0

        // Process in smaller chunks for large images
        val chunkSize = if (memoryUsagePercent > 60) 500 else 1000

        try {
            for (iStart in 0 until height - 1 step chunkSize) {
                val iEnd = minOf(iStart + chunkSize, height - 1)

                for (i in iStart until iEnd) {
                    for (j in 0 until width - 1) {
                        if (mask[i][j] && !visited[i][j]) {
                            try {
                                val contour = traceContourOptimized(mask, visited, i, j)

                                if (contour.first.size >= 3 && contour.first.size <= MAX_CONTOUR_POINTS) {
                                    contours.add(contour)
                                    processedContours++

                                    // Limit total contours to prevent memory issues
                                    if (processedContours >= 10000) {
                                        println("⚠️ Maximum contour limit reached, stopping...")
                                        return contours
                                    }
                                }

                                // Check memory periodically
                                if (processedContours % 100 == 0) {
                                    val currentUsed = runtime.totalMemory() - runtime.freeMemory()
                                    val currentPercent = (currentUsed.toFloat() / maxMemory * 100).toInt()

                                    if (currentPercent > 85) {
                                        println("⚠️ Memory usage critical: ${currentPercent}%, stopping contour finding")
                                        return contours
                                    }
                                }

                            } catch (_: OutOfMemoryError) {
                                println("❌ OOM during contour tracing at ($i, $j)")
                                System.gc()
                                return contours
                            }
                        }
                    }
                }

                // Force GC between chunks
                if ((iStart / chunkSize) % 5 == 0) {
                    System.gc()
                }
            }

        } catch (e: OutOfMemoryError) {
            println("❌ OutOfMemoryError in findContours: ${e.message}")
            System.gc()
        }

        println("✅ Found ${contours.size} contours")
        return contours
    }

    /**
     * Memory-optimized contour tracing
     */
    private fun traceContourOptimized(
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
        var iterations = 0
        val maxIterations = minOf(MAX_CONTOUR_POINTS, width * height / 4) // Prevent infinite loops

        do {
            // Add point with decimation for large contours
            val decimationFactor = when {
                contourX.size < 500 -> 1
                contourX.size < 1000 -> 2
                contourX.size < 2000 -> 3
                else -> 4
            }

            if (iterations % decimationFactor == 0) {
                contourX.add(j.toFloat())
                contourY.add(i.toFloat())
            }

            visited[i][j] = true
            iterations++

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

            if (!found || iterations >= maxIterations) break

        } while (!(i == startI && j == startJ))

        // Ensure we have minimum points
        if (contourX.size < 3 && iterations >= 3) {
            // Add missing points from the trace
            val step = maxOf(1, iterations / 3)
            for (k in 0 until 3) {
                if (k * step < iterations) {
                    contourX.add((startJ + k).toFloat())
                    contourY.add((startI + k).toFloat())
                }
            }
        }

        return Pair(contourX.toFloatArray(), contourY.toFloatArray())
    }

    /**
     * Create labeled image with memory optimization
     */
    fun createLabeledImage(
        allGrains: List<Polygon>,
        imageWidth: Int,
        imageHeight: Int
    ): Pair<Array<IntArray>, Array<IntArray>> {

        println("Creating labeled image ${imageWidth}x${imageHeight} for ${allGrains.size} grains...")

        // Check memory before allocating large arrays
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()

        val arraySize = imageWidth.toLong() * imageHeight.toLong() * 4L // 4 bytes per int
        val requiredMemory = arraySize * 2L // Two arrays

        println("Memory: ${memoryUsagePercent}%, Required: ${requiredMemory / 1024 / 1024}MB")

        if (memoryUsagePercent > 70) {
            println("⚠️ High memory usage, forcing GC before array allocation...")
            System.gc()
        }

        val rasterized = Array(imageHeight) { IntArray(imageWidth) }
        val maskAll = Array(imageHeight) { IntArray(imageWidth) }

        try {
            // Process grains in batches to prevent memory spikes
            allGrains.chunked(BATCH_SIZE).forEachIndexed { batchIndex, grainBatch ->
                println("🔄 Processing grain batch ${batchIndex + 1} of ${(allGrains.size + BATCH_SIZE - 1) / BATCH_SIZE}")

                grainBatch.forEachIndexed { indexInBatch, grain ->
                    val globalIndex = batchIndex * BATCH_SIZE + indexInBatch
                    val label = globalIndex + 1

                    try {
                        rasterizePolygonOptimized(grain, rasterized, label, imageWidth, imageHeight)

                        // Create boundary with simplified buffer
                        val boundary = grain.boundary.buffer(1.0) // Reduced buffer size
                        if (boundary is Polygon) {
                            rasterizePolygonOptimized(boundary, maskAll, 2, imageWidth, imageHeight)
                        }

                    } catch (_: OutOfMemoryError) {
                        println("❌ OOM processing grain $globalIndex, skipping...")
                        System.gc()
                    } catch (e: Exception) {
                        println("Error processing grain $globalIndex: ${e.message}")
                    }
                }

                // Force GC between batches
                if (batchIndex % 5 == 0) {
                    System.gc()
                }
            }

            // Set grain pixels in mask (optimized)
            for (i in 0 until imageHeight) {
                for (j in 0 until imageWidth) {
                    if (rasterized[i][j] > 0) {
                        maskAll[i][j] = 1 // Grain value
                    }
                }
            }

        } catch (e: OutOfMemoryError) {
            println("❌ OutOfMemoryError in createLabeledImage: ${e.message}")
            System.gc()
        }

        println("✅ Labeled image created successfully")
        return Pair(rasterized, maskAll)
    }

    /**
     * Optimized polygon rasterization
     */
    private fun rasterizePolygonOptimized(
        polygon: Polygon,
        raster: Array<IntArray>,
        value: Int,
        imageWidth: Int,
        imageHeight: Int
    ) {
        try {
            val envelope = polygon.envelopeInternal
            val minX = max(0, envelope.minX.toInt())
            val maxX = min(imageWidth - 1, envelope.maxX.toInt())
            val minY = max(0, envelope.minY.toInt())
            val maxY = min(imageHeight - 1, envelope.maxY.toInt())

            // Limit processing area to prevent infinite loops
            val maxArea = 50000
            val area = (maxX - minX + 1) * (maxY - minY + 1)

            if (area > maxArea) {
                println("⚠️ Polygon too large for rasterization, skipping...")
                return
            }

            // Use prepared geometry for faster contains() checks
            val preparedGeom = org.locationtech.jts.geom.prep.PreparedGeometryFactory.prepare(polygon)

            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    if (y < raster.size && x < raster[0].size) {
                        val point = geometryFactory.createPoint(Coordinate(x.toDouble(), y.toDouble()))
                        if (preparedGeom.contains(point)) {
                            raster[y][x] = value
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("⚠️ Error in rasterization: ${e.message}")
        }
    }
}