package com.mcu.imagegrains.domain

import android.content.Context
import android.graphics.Bitmap
import com.mcu.imagegrains.domain.models.GrainProperties
import com.mcu.imagegrains.domain.models.LabelingResult
import com.mcu.imagegrains.utils.EnhancedGrainCollectionUtils
import com.mcu.imagegrains.utils.EnhancedVisualizationUtils
import com.mcu.imagegrains.utils.GrainCollectionUtils
import com.mcu.imagegrains.utils.GrainPatchUtils
import com.mcu.imagegrains.utils.ImageAnalysisUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.locationtech.jts.geom.Polygon

data class InstanceSegmentationResult(
    val allGrains: List<Polygon>,
    val labelsOut: Array<IntArray>,
    val maskAll: Array<IntArray>,
    val grainData: List<GrainProperties>,
    val processingStats: ProcessingStats
)

data class CompleteSegmentationResult(
    val initialResult: InstanceSegmentationResult,
    val finalGrains: List<Polygon>,
    val finalLabels: Array<IntArray>,
    val finalMask: Array<IntArray>,
    val finalGrainData: List<GrainProperties>,
    val finalVisualization: Bitmap
)

data class ProcessingStats(
    val totalCoordinates: Int,
    val successfulCoordinates: Int,
    val finalGrainCount: Int,
    val processingTimeMs: Long
)

class InstanceSegmentationProcessor(
    private val context: Context
) {
    private var mobileSAM: ONNXMobileSAMProcessor? = null

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            mobileSAM = ONNXMobileSAMProcessor(context)
            mobileSAM?.initialize() ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun performCompleteInstanceSegmentation(
        originalBitmap: Bitmap,
        predictionArray: Array<Array<FloatArray>>,
        labelingResult: LabelingResult,
        minArea: Int = 400,
        removeEdgeGrains: Boolean = false,
        progressCallback: (Float) -> Unit = {}
    ): CompleteSegmentationResult? = withContext(Dispatchers.Default) {

        try {
            // Step 1: Initial instance segmentation (0% - 70%)
            val initialResult = performInstanceSegmentation(
                originalBitmap = originalBitmap,
                predictionArray = predictionArray,
                labelingResult = labelingResult,
                minArea = minArea,
                removeEdgeGrains = removeEdgeGrains
            ) { progress -> progressCallback(progress * 0.7f) }

            if (initialResult == null) {
                return@withContext null
            }

            progressCallback(0.7f)

            // Step 2: Extract grains from patches (post-processing) (70% - 90%)
            val (finalGrains, finalLabels, finalMask) = GrainPatchUtils.getGrainsFromPolygons(
                allGrains = initialResult.allGrains,
                imageWidth = originalBitmap.width,
                imageHeight = originalBitmap.height
            ) { progress -> progressCallback(0.7f + progress * 0.2f) }

            progressCallback(0.9f)

            // Step 3: Create final visualization (90% - 100%)
            val finalVisualization = EnhancedVisualizationUtils.createCompleteGrainVisualization(
                originalBitmap = originalBitmap,
                allGrains = finalGrains,
                labels = finalLabels,
                maskAll = finalMask
            )

            // Calculate final properties
            val finalGrainData = ImageAnalysisUtils.calculateRegionProperties(finalLabels)

            progressCallback(1.0f)

            println("✅ Complete segmentation: ${initialResult.allGrains.size} -> ${finalGrains.size} final grains")

            CompleteSegmentationResult(
                initialResult = initialResult,
                finalGrains = finalGrains,
                finalLabels = finalLabels,
                finalMask = finalMask,
                finalGrainData = finalGrainData,
                finalVisualization = finalVisualization
            )

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }



    /**
     * Perform instance segmentation using ONNX MobileSAM
     */
    suspend fun performInstanceSegmentation(
        originalBitmap: Bitmap,
        predictionArray: Array<Array<FloatArray>>,
        labelingResult: LabelingResult,
        minArea: Int = 400,
        removeEdgeGrains: Boolean = false,
        progressCallback: (Float) -> Unit = {}
    ): InstanceSegmentationResult? = withContext(Dispatchers.Default) {

        val startTime = System.currentTimeMillis()
        val mobileSAM = this@InstanceSegmentationProcessor.mobileSAM

        if (mobileSAM == null) {
            println("❌ MobileSAM not initialized")
            return@withContext null
        }

        try {
            progressCallback(0.1f)

            // Get image embeddings
            println("Getting image embeddings...")
            val embeddingResult = mobileSAM.getImageEmbeddings(originalBitmap)
            if (embeddingResult == null) {
                println("❌ Failed to get image embeddings")
                return@withContext null
            }

            progressCallback(0.2f)

            val coords = labelingResult.allCoords
            val labels = labelingResult.labelsSimple
            val allGrains = mutableListOf<Polygon>()
            var successfulCoords = 0

            println("🔄 Processing ${coords.size} coordinates with SAM...")

            // Process each coordinate
            coords.forEachIndexed { index, coord ->
                val x = coord[0]
                val y = coord[1]

                try {
                    // Predict mask using SAM
                    val mask = mobileSAM.predictMaskWithSAMCoords(x, y, embeddingResult.embeddings)

                    if (mask != null && mask.any { row -> row.any { it } }) {
                        // Handle multiple connected components
                        val processedMask = handleMultipleComponents(mask, x, y)

                        if (processedMask != null) {
                            // Find contours
                            val contours = GrainCollectionUtils.findContours(processedMask)

                            if (contours.isNotEmpty()) {
                                val largestContour = contours.maxByOrNull { it.first.size }
                                if (largestContour != null) {
                                    var (sx, sy) = largestContour

                                    // Handle edge cases
                                    val (finalMask, finalSx, finalSy) = handleEdgeCases(processedMask, sx, sy)

                                    // Collect grain if valid
                                    if (finalMask.any { row -> row.any { it } }) {
                                        GrainCollectionUtils.collectPolygonFromMask(
                                            labels = labels,
                                            mask = finalMask,
                                            imagePred = predictionArray,
                                            allGrains = allGrains,
                                            sx = finalSx,
                                            sy = finalSy,
                                            minArea = 100
                                        )
                                        successfulCoords++
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("❌ Failed to process coordinate ($x, $y): ${e.message}")
                }

                // Update progress
                if (index % 10 == 0) {
                    val progress = 0.2f + 0.6f * (index.toFloat() / coords.size)
                    progressCallback(progress)
                }
            }

            progressCallback(0.8f)

            println("✅ Successfully processed $successfulCoords/${coords.size} coordinates")

            // Post-process grains
            println("🔄 Post-processing grains...")
            val finalGrains = postProcessGrains(allGrains, minArea, predictionArray)

            progressCallback(0.9f)

            // Create labeled image
            val (labelsOut, maskAll) = if (finalGrains.isNotEmpty()) {
                GrainCollectionUtils.createLabeledImage(
                    finalGrains,
                    originalBitmap.width,
                    originalBitmap.height
                )
            } else {
                Pair(
                    Array(originalBitmap.height) { IntArray(originalBitmap.width) { 0 } },
                    Array(originalBitmap.height) { IntArray(originalBitmap.width) { 0 } }
                )
            }

            // Calculate grain properties
            val grainData = ImageAnalysisUtils.calculateRegionProperties(labelsOut)

            val endTime = System.currentTimeMillis()
            val processingTime = endTime - startTime

            val stats = ProcessingStats(
                totalCoordinates = coords.size,
                successfulCoordinates = successfulCoords,
                finalGrainCount = finalGrains.size,
                processingTimeMs = processingTime
            )

            progressCallback(1.0f)

            println("✅ Instance segmentation completed: ${finalGrains.size} grains in ${processingTime}ms")

            InstanceSegmentationResult(
                allGrains = finalGrains,
                labelsOut = labelsOut,
                maskAll = maskAll,
                grainData = grainData,
                processingStats = stats
            )

        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Instance segmentation failed: ${e.message}")
            null
        }
    }

    private fun handleMultipleComponents(
        mask: Array<BooleanArray>,
        clickX: Int,
        clickY: Int
    ): Array<BooleanArray>? {

        // Label connected components
        val (labeledMask, numComponents) = ImageAnalysisUtils.labelConnectedComponents(mask, 1)

        if (numComponents <= 1) {
            return mask
        }

        // Find closest component to click point
        var minDistance = Float.MAX_VALUE
        var bestLabel = 1
        val clickPoint = Pair(clickY, clickX) // Note: y, x order for array indexing

        for (label in 1..numComponents) {
            val componentCoords = mutableListOf<Pair<Int, Int>>()

            for (i in labeledMask.indices) {
                for (j in labeledMask[i].indices) {
                    if (labeledMask[i][j] == label) {
                        componentCoords.add(Pair(i, j))
                    }
                }
            }

            if (componentCoords.isNotEmpty()) {
                val minDistToComponent = componentCoords.minOfOrNull { coord ->
                    val di = coord.first - clickPoint.first
                    val dj = coord.second - clickPoint.second
                    kotlin.math.sqrt((di * di + dj * dj).toFloat())
                } ?: Float.MAX_VALUE

                if (minDistToComponent < minDistance) {
                    minDistance = minDistToComponent
                    bestLabel = label
                }
            }
        }

        // Create mask with only the best component
        val resultMask = Array(mask.size) { i ->
            BooleanArray(mask[i].size) { j ->
                labeledMask[i][j] == bestLabel
            }
        }

        return resultMask
    }

    private fun handleEdgeCases(
        mask: Array<BooleanArray>,
        sx: FloatArray,
        sy: FloatArray
    ): Triple<Array<BooleanArray>, FloatArray, FloatArray> {

        val height = mask.size
        val width = mask[0].size

        // Check if mask touches edges
        val touchesEdge = mask[0].any { it } ||
                mask[height - 1].any { it } ||
                mask.any { it[0] } ||
                mask.any { it[width - 1] }

        if (!touchesEdge) {
            return Triple(mask, sx, sy)
        }

        // Pad mask
        val paddedMask = Array(height + 2) { i ->
            BooleanArray(width + 2) { j ->
                when {
                    i == 0 || i == height + 1 || j == 0 || j == width + 1 -> false
                    else -> mask[i - 1][j - 1]
                }
            }
        }

        // Find contours in padded mask
        val contours = GrainCollectionUtils.findContours(paddedMask)

        if (contours.isNotEmpty()) {
            val largestContour = contours.maxByOrNull { it.first.size }
            if (largestContour != null) {
                var (newSx, newSy) = largestContour

                // Adjust coordinates back to original space
                if (mask[0].any { it }) { // Top edge touched
                    newSy = newSy.map { it - 1 }.toFloatArray()
                }
                if (mask.any { it[0] }) { // Left edge touched
                    newSx = newSx.map { it - 1 }.toFloatArray()
                }

                // Remove padding from mask
                val unpaddedMask = Array(height) { i ->
                    BooleanArray(width) { j ->
                        paddedMask[i + 1][j + 1]
                    }
                }

                return Triple(unpaddedMask, newSx, newSy)
            }
        }

        return Triple(mask, sx, sy)
    }

    private suspend fun postProcessGrains(
        allGrains: List<Polygon>,
        minArea: Int,
        imagePred: Array<Array<FloatArray>>,
        progressCallback: (Float) -> Unit = {}
    ): List<Polygon> = withContext(Dispatchers.Default) {

        if (allGrains.isEmpty()) return@withContext emptyList()

        println("🔄 Post-processing ${allGrains.size} grains with spatial indexing...")

        // Step 1: Find connected components (30% of progress)
        val (newGrains, components, overlappingPairs) = EnhancedGrainCollectionUtils.findConnectedComponents(
            allGrains,
            minArea.toDouble()
        ) { progress -> progressCallback(progress * 0.3f) }

        // Step 2: Merge overlapping polygons (70% of progress)
        val finalGrains = EnhancedGrainCollectionUtils.mergeOverlappingPolygons(
            allGrains,
            newGrains.toMutableList(),
            components,
            minArea.toDouble(),
            imagePred
        ) { progress -> progressCallback(0.3f + progress * 0.7f) }

        println("✅ Post-processing completed: ${allGrains.size} -> ${finalGrains.size} grains")

        finalGrains.filter { grain ->
            grain.area >= minArea && grain.isValid
        }
    }

    fun close() {
        mobileSAM?.close()
    }
}