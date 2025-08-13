package com.mcu.imagegrains.utils

import com.mcu.imagegrains.domain.models.GrainProperties
import kotlin.math.*
import java.util.*

object ImageAnalysisUtils {

    /**
     * Connected component labeling using flood fill algorithm
     */
    fun labelConnectedComponents(
        binaryImage: Array<BooleanArray>,
        connectivity: Int = 1
    ): Pair<Array<IntArray>, Int> {
        val height = binaryImage.size
        val width = binaryImage[0].size
        val labels = Array(height) { IntArray(width) }
        var currentLabel = 0

        // 4-connectivity or 8-connectivity neighbors
        val neighbors = if (connectivity == 1) {
            arrayOf(
                intArrayOf(-1, 0), intArrayOf(1, 0),
                intArrayOf(0, -1), intArrayOf(0, 1)
            )
        } else {
            arrayOf(
                intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1),
                intArrayOf(0, -1), intArrayOf(0, 1),
                intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1)
            )
        }

        for (i in 0 until height) {
            for (j in 0 until width) {
                if (binaryImage[i][j] && labels[i][j] == 0) {
                    currentLabel++
                    floodFill(binaryImage, labels, i, j, currentLabel, neighbors)
                }
            }
        }

        return Pair(labels, currentLabel)
    }

    private fun floodFill(
        binaryImage: Array<BooleanArray>,
        labels: Array<IntArray>,
        startI: Int,
        startJ: Int,
        label: Int,
        neighbors: Array<IntArray>
    ) {
        val height = binaryImage.size
        val width = binaryImage[0].size
        val stack = Stack<Pair<Int, Int>>()
        stack.push(Pair(startI, startJ))

        while (stack.isNotEmpty()) {
            val (i, j) = stack.pop()

            if (i < 0 || i >= height || j < 0 || j >= width ||
                !binaryImage[i][j] || labels[i][j] != 0) {
                continue
            }

            labels[i][j] = label

            for (neighbor in neighbors) {
                val ni = i + neighbor[0]
                val nj = j + neighbor[1]
                stack.push(Pair(ni, nj))
            }
        }
    }

    /**
     * Calculate region properties for labeled components
     */
    fun calculateRegionProperties(
        labeledImage: Array<IntArray>,
        intensityImage: Array<Array<FloatArray>>? = null
    ): List<GrainProperties> {
        val height = labeledImage.size
        val width = labeledImage[0].size
        val labelMap = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()

        // Group pixels by label
        for (i in 0 until height) {
            for (j in 0 until width) {
                val label = labeledImage[i][j]
                if (label > 0) {
                    labelMap.getOrPut(label) { mutableListOf() }.add(Pair(i, j))
                }
            }
        }

        return labelMap.map { (label, pixels) ->
            calculateSingleRegionProperties(label, pixels, intensityImage)
        }
    }

    private fun calculateSingleRegionProperties(
        label: Int,
        pixels: List<Pair<Int, Int>>,
        intensityImage: Array<Array<FloatArray>>? = null
    ): GrainProperties {
        val area = pixels.size.toDouble()

        // Calculate centroid
        val centroidI = pixels.map { it.first }.average()
        val centroidJ = pixels.map { it.second }.average()

        // Calculate intensity statistics if intensity image is provided
        var maxIntensity = 0.0
        var meanIntensity = 0.0
        var minIntensity = Double.MAX_VALUE

        if (intensityImage != null) {
            val intensities = pixels.map { (i, j) ->
                // Convert RGB to grayscale
                val rgb = intensityImage[i][j]
                0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]
            }

            maxIntensity = intensities.maxOrNull() ?: 0.0
            meanIntensity = intensities.average()
            minIntensity = intensities.minOrNull() ?: 0.0
        }

        // Calculate moments for orientation and axis lengths
        var m20 = 0.0
        var m02 = 0.0
        var m11 = 0.0

        for ((i, j) in pixels) {
            val di = i - centroidI
            val dj = j - centroidJ
            m20 += di * di
            m02 += dj * dj
            m11 += di * dj
        }

        m20 /= area
        m02 /= area
        m11 /= area

        // Calculate orientation
        val orientation = if (abs(m11) < 1e-10 && abs(m20 - m02) < 1e-10) {
            0.0
        } else {
            0.5 * atan2(2 * m11, m20 - m02)
        }

        // Calculate major and minor axis lengths
        val temp = sqrt((m20 - m02).pow(2) + 4 * m11.pow(2))
        val majorAxisLength = 2 * sqrt(2.0) * sqrt(m20 + m02 + temp)
        val minorAxisLength = 2 * sqrt(2.0) * sqrt(m20 + m02 - temp)

        // Calculate perimeter (simplified as boundary pixels)
        val perimeter = calculatePerimeter(pixels)

        return GrainProperties(
            label = label,
            area = area,
            centroidX = centroidJ, // Note: X corresponds to column (J)
            centroidY = centroidI, // Note: Y corresponds to row (I)
            majorAxisLength = majorAxisLength,
            minorAxisLength = minorAxisLength,
            orientation = orientation,
            perimeter = perimeter,
            maxIntensity = maxIntensity,
            meanIntensity = meanIntensity,
            minIntensity = minIntensity
        )
    }

    private fun calculatePerimeter(pixels: List<Pair<Int, Int>>): Double {
        val pixelSet = pixels.toSet()
        var perimeter = 0

        val neighbors = arrayOf(
            intArrayOf(-1, 0), intArrayOf(1, 0),
            intArrayOf(0, -1), intArrayOf(0, 1)
        )

        for ((i, j) in pixels) {
            for (neighbor in neighbors) {
                val ni = i + neighbor[0]
                val nj = j + neighbor[1]
                if (!pixelSet.contains(Pair(ni, nj))) {
                    perimeter++
                }
            }
        }

        return perimeter.toDouble()
    }

    /**
     * Distance transform using Euclidean distance
     */
    fun distanceTransformEDT(binaryImage: Array<BooleanArray>): Array<DoubleArray> {
        val height = binaryImage.size
        val width = binaryImage[0].size
        val distance = Array(height) { DoubleArray(width) { Double.MAX_VALUE } }

        // Initialize distances
        for (i in 0 until height) {
            for (j in 0 until width) {
                if (!binaryImage[i][j]) {
                    distance[i][j] = 0.0
                }
            }
        }

        // Forward pass
        for (i in 0 until height) {
            for (j in 0 until width) {
                if (binaryImage[i][j]) {
                    var minDist = distance[i][j]

                    if (i > 0) minDist = min(minDist, distance[i-1][j] + 1.0)
                    if (j > 0) minDist = min(minDist, distance[i][j-1] + 1.0)
                    if (i > 0 && j > 0) minDist = min(minDist, distance[i-1][j-1] + sqrt(2.0))
                    if (i > 0 && j < width-1) minDist = min(minDist, distance[i-1][j+1] + sqrt(2.0))

                    distance[i][j] = minDist
                }
            }
        }

        // Backward pass
        for (i in height-1 downTo 0) {
            for (j in width-1 downTo 0) {
                if (binaryImage[i][j]) {
                    var minDist = distance[i][j]

                    if (i < height-1) minDist = min(minDist, distance[i+1][j] + 1.0)
                    if (j < width-1) minDist = min(minDist, distance[i][j+1] + 1.0)
                    if (i < height-1 && j < width-1) minDist = min(minDist, distance[i+1][j+1] + sqrt(2.0))
                    if (i < height-1 && j > 0) minDist = min(minDist, distance[i+1][j-1] + sqrt(2.0))

                    distance[i][j] = minDist
                }
            }
        }

        return distance
    }

    /**
     * Find local maxima in distance transform
     */
    fun findLocalMaxima(
        distance: Array<DoubleArray>,
        mask: Array<BooleanArray>,
        footprintSize: Int = 3
    ): List<Pair<Int, Int>> {
        val height = distance.size
        val width = distance[0].size
        val localMaxima = mutableListOf<Pair<Int, Int>>()
        val half = footprintSize / 2

        for (i in half until height - half) {
            for (j in half until width - half) {
                if (!mask[i][j]) continue

                val centerValue = distance[i][j]
                var isMaximum = true

                // Check if current pixel is local maximum
                for (di in -half..half) {
                    for (dj in -half..half) {
                        if (di == 0 && dj == 0) continue

                        val ni = i + di
                        val nj = j + dj

                        if (ni >= 0 && ni < height && nj >= 0 && nj < width) {
                            if (distance[ni][nj] >= centerValue) {
                                isMaximum = false
                                break
                            }
                        }
                    }
                    if (!isMaximum) break
                }

                if (isMaximum && centerValue > 1.0) { // Threshold to avoid noise
                    localMaxima.add(Pair(i, j))
                }
            }
        }

        return localMaxima
    }
}