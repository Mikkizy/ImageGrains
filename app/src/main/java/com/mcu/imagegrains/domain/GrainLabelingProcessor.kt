package com.mcu.imagegrains.domain

import com.mcu.imagegrains.domain.models.LabelingResult
import com.mcu.imagegrains.utils.DBSCANUtils
import com.mcu.imagegrains.utils.ImageAnalysisUtils
import com.mcu.imagegrains.utils.WatershedUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

class GrainLabelingProcessor {

    /**
     * Label grains in semantic segmentation result and generate prompts for SAM model
     */
    suspend fun labelGrains(
        image: Array<Array<FloatArray>>,
        imagePred: Array<Array<FloatArray>>,
        dbsMaxDist: Double = 20.0
    ): LabelingResult = withContext(Dispatchers.Default) {

        println("🔄 Starting grain labeling process...")

        val height = imagePred.size
        val width = imagePred[0].size

        // Extract grain prediction from semantic segmentation result (channel 1)
        val grains = Array(height) { i ->
            BooleanArray(width) { j ->
                imagePred[i][j][1] >= 0.5f
            }
        }

        // Label connected components
        val (labelsSimple, nElems) = ImageAnalysisUtils.labelConnectedComponents(grains, 1)

        println("✅ Found $nElems simple grain components")

        // Calculate region properties for simple grains
        val grainDataSimple = ImageAnalysisUtils.calculateRegionProperties(labelsSimple, image)

        // Get centroids as simple prompts
        var coordsSimple = grainDataSimple.map { grain ->
            intArrayOf(grain.centroidX.toInt(), grain.centroidY.toInt())
        }.toTypedArray()

        // Filter out prompts that are likely background
        coordsSimple = filterBackgroundPrompts(coordsSimple, imagePred)

        println("✅ Generated ${coordsSimple.size} simple prompts")

        // Process grain boundaries (channel 2)
        val bounds = Array(height) { i ->
            BooleanArray(width) { j ->
                imagePred[i][j][2] >= 0.5f
            }
        }

        val (tempLabels, nBoundElems) = ImageAnalysisUtils.labelConnectedComponents(bounds, 1)

        // Find largest boundary component
        val labelCounts = IntArray(nBoundElems + 1)
        for (i in 0 until height) {
            for (j in 0 until width) {
                labelCounts[tempLabels[i][j]]++
            }
        }

        val validLabels = labelCounts.drop(1).withIndex()
            .filter { it.value > 100 }
            .map { it.index + 1 }

        var allCoords = coordsSimple

        if (validLabels.isNotEmpty()) {
            println("🔄 Processing boundary-based watershed segmentation...")

            // Find largest label
            val largestLabel = labelCounts.drop(1).withIndex().maxByOrNull { it.value }?.index?.plus(1) ?: 1

            // Merge all valid labels into largest label
            val processedBounds = Array(height) { i ->
                BooleanArray(width) { j ->
                    validLabels.contains(tempLabels[i][j])
                }
            }

            // Invert bounds for distance transform
            val invertedBounds = Array(height) { i ->
                BooleanArray(width) { j ->
                    !processedBounds[i][j]
                }
            }

            // Distance transform
            val distance = ImageAnalysisUtils.distanceTransformEDT(invertedBounds)

            // Find local maxima
            val coords = ImageAnalysisUtils.findLocalMaxima(distance, invertedBounds, 3)

            if (coords.isNotEmpty()) {
                // Filter background prompts
                val filteredCoords = coords.map { (i, j) ->
                    intArrayOf(j, i) // Convert (i,j) to (x,y)
                }.toTypedArray()

                val coordsWS = filterBackgroundPrompts(filteredCoords, imagePred)

                if (coordsWS.isNotEmpty()) {
                    println("✅ Found ${coordsWS.size} watershed coordinates")

                    // Perform watershed segmentation
                    val markers = Array(height) { IntArray(width) { 0 } }
                    coordsWS.forEachIndexed { index, coord ->
                        val x = coord[0]
                        val y = coord[1]
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            markers[y][x] = index + 1
                        }
                    }

                    val watershedLabels = WatershedUtils.watershed(
                        Array(height) { i -> DoubleArray(width) { j -> -distance[i][j] } },
                        markers,
                        invertedBounds
                    )

                    // Calculate properties from watershed result
                    val grainDataWS = ImageAnalysisUtils.calculateRegionProperties(watershedLabels, image)

                    if (grainDataWS.isNotEmpty()) {
                        // Apply DBSCAN clustering
                        val points = grainDataWS.map { grain ->
                            doubleArrayOf(grain.centroidX, grain.centroidY)
                        }.toTypedArray()

                        val dbscanResult = DBSCANUtils.dbscan(points, dbsMaxDist, 2)

                        // Process DBSCAN results
                        val coordsWSClustered = mutableListOf<IntArray>()

                        // Add noise points (outliers)
                        dbscanResult.labels.forEachIndexed { index, label ->
                            if (label == -1) {
                                val grain = grainDataWS[index]
                                coordsWSClustered.add(intArrayOf(grain.centroidX.toInt(), grain.centroidY.toInt()))
                            }
                        }

                        // Add cluster centroids
                        for (clusterId in 0 until dbscanResult.numClusters) {
                            val clusterPoints = dbscanResult.labels.withIndex()
                                .filter { it.value == clusterId }
                                .map { grainDataWS[it.index] }

                            if (clusterPoints.isNotEmpty()) {
                                val meanX = clusterPoints.map { it.centroidX }.average()
                                val meanY = clusterPoints.map { it.centroidY }.average()
                                coordsWSClustered.add(intArrayOf(meanX.toInt(), meanY.toInt()))
                            }
                        }

                        // Filter background prompts again
                        val finalCoordsWS = filterBackgroundPrompts(coordsWSClustered.toTypedArray(), imagePred)

                        // Combine watershed and simple coordinates
                        allCoords = finalCoordsWS + coordsSimple

                        println("✅ Generated ${finalCoordsWS.size} watershed prompts")
                    }
                }
            }
        }

        println("✅ Grain labeling completed. Total prompts: ${allCoords.size}")

        LabelingResult(labelsSimple, allCoords)
    }

    /**
     * Filter out prompts that are likely to be background
     */
    private fun filterBackgroundPrompts(
        coords: Array<IntArray>,
        imagePred: Array<Array<FloatArray>>,
        threshold: Float = 0.3f
    ): Array<IntArray> {
        return coords.filter { coord ->
            val x = coord[0]
            val y = coord[1]

            if (y >= 0 && y < imagePred.size && x >= 0 && x < imagePred[0].size) {
                val backgroundProb = imagePred[y][x][0] // Channel 0 is background
                backgroundProb < threshold
            } else {
                false
            }
        }.toTypedArray()
    }
}