package com.mcu.imagegrains.domain

import android.content.Context
import android.graphics.Bitmap
import com.mcu.imagegrains.domain.models.GrainProperties
import com.mcu.imagegrains.domain.models.LabelingResult
import com.mcu.imagegrains.utils.EnhancedVisualizationUtils
import com.mcu.imagegrains.utils.FastSpatialIndex
import com.mcu.imagegrains.utils.GrainCollectionUtils
import com.mcu.imagegrains.utils.GrainPatchUtils
import com.mcu.imagegrains.utils.ImageAnalysisUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.locationtech.jts.geom.Polygon
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

data class OptimizedInstanceSegmentationResult(
    val allGrains: List<Polygon>,
    val labelsOut: Array<IntArray>,
    val maskAll: Array<IntArray>,
    val grainData: List<GrainProperties>,
    val processingStats: OptimizedProcessingStats
) {
    fun toInstanceSegmentationResult(): InstanceSegmentationResult {
        return InstanceSegmentationResult(
            allGrains = allGrains,
            labelsOut = labelsOut,
            maskAll = maskAll,
            grainData = grainData,
            processingStats = processingStats.toProcessingStats()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OptimizedInstanceSegmentationResult

        if (allGrains != other.allGrains) return false
        if (!labelsOut.contentDeepEquals(other.labelsOut)) return false
        if (!maskAll.contentDeepEquals(other.maskAll)) return false
        if (grainData != other.grainData) return false
        if (processingStats != other.processingStats) return false

        return true
    }

    override fun hashCode(): Int {
        var result = allGrains.hashCode()
        result = 31 * result + labelsOut.contentDeepHashCode()
        result = 31 * result + maskAll.contentDeepHashCode()
        result = 31 * result + grainData.hashCode()
        result = 31 * result + processingStats.hashCode()
        return result
    }
}

data class OptimizedProcessingStats(
    val totalCoordinates: Int,
    val successfulCoordinates: Int,
    val finalGrainCount: Int,
    val processingTimeMs: Long,
    val coordinateProcessingTimeMs: Long = 0,
    val postProcessingTimeMs: Long = 0,
    val memoryUsedMB: Float = 0f
) {
    fun toProcessingStats(): ProcessingStats {
        return ProcessingStats(
            totalCoordinates = totalCoordinates,
            successfulCoordinates = successfulCoordinates,
            finalGrainCount = finalGrainCount,
            processingTimeMs = processingTimeMs
        )
    }
}

class OptimizedInstanceSegmentationProcessor(
    private val context: Context
) {
    private var mobileSAM: ONNXMobileSAMProcessor? = null
    private val geometryOptimizer = GeometryOptimizer()

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            mobileSAM = ONNXMobileSAMProcessor(context)
            mobileSAM?.initialize() ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Fixed instance segmentation with proper variable scoping
     */
    suspend fun performInstanceSegmentation(
        originalBitmap: Bitmap,
        predictionArray: Array<Array<FloatArray>>,
        labelingResult: LabelingResult,
        minArea: Int = 400,
        progressCallback: (Float) -> Unit = {}
    ): OptimizedInstanceSegmentationResult? = withContext(Dispatchers.Default) {

        val startTime = System.currentTimeMillis()
        val mobileSAM = this@OptimizedInstanceSegmentationProcessor.mobileSAM

        if (mobileSAM == null) {
            println("❌ MobileSAM not initialized")
            return@withContext null
        }

        try {
            progressCallback(0.05f)

            // Memory monitoring
            val runtime = Runtime.getRuntime()
            val initialMemory = runtime.totalMemory() - runtime.freeMemory()

            // Get image embeddings
            println("🔄 Getting image embeddings...")
            val embeddingResult = mobileSAM.getImageEmbeddings(originalBitmap)
            if (embeddingResult == null) {
                println("❌ Failed to get image embeddings")
                return@withContext null
            }

            progressCallback(0.1f)

            val coords = labelingResult.allCoords
            val labels = labelingResult.labelsSimple

            println("🔄 Processing ${coords.size} coordinates with optimized batching...")

            // Coordinate processing with optimization
            var coordinateProcessingTime: Long
            var postProcessingTime: Long

            val allGrains = mutableListOf<Polygon>()

            coordinateProcessingTime = measureTimeMillis {
                val processedGrains = processCoordinatesOptimized(
                    coords = coords.toList(),
                    labels = labels,
                    predictionArray = predictionArray,
                    embeddingResult = embeddingResult,
                    mobileSAM = mobileSAM,
                    minArea = minArea
                ) { progress -> progressCallback(0.1f + progress * 0.6f) }

                allGrains.addAll(processedGrains)
            }

            if (allGrains.isEmpty()) {
                println("⚠️ No valid grains found during coordinate processing")
                return@withContext createEmptyResult(coords.size, startTime, coordinateProcessingTime)
            }

            progressCallback(0.7f)

            // Optimized post-processing
            println("🔄 Starting optimized post-processing...")
            val finalGrains = mutableListOf<Polygon>()

            postProcessingTime = measureTimeMillis {
                val processedGrains = postProcessGrainsOptimized(
                    allGrains = allGrains,
                    minArea = minArea,
                    imageWidth = originalBitmap.width,
                    imageHeight = originalBitmap.height
                ) { progress -> progressCallback(0.7f + progress * 0.2f) }

                finalGrains.addAll(processedGrains)
            }

            progressCallback(0.9f)

            // Create labeled image
            val (labelsOut, maskAll) = if (finalGrains.isNotEmpty()) {
                GrainCollectionUtils.createLabeledImage(
                    finalGrains,
                    originalBitmap.width,
                    originalBitmap.height
                )
            } else {
                createEmptyArrays(originalBitmap.width, originalBitmap.height)
            }

            // Calculate final memory usage
            val finalMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryUsedMB = (finalMemory - initialMemory).toFloat() / (1024 * 1024)

            // Calculate grain properties
            val grainData = ImageAnalysisUtils.calculateRegionProperties(labelsOut)

            val endTime = System.currentTimeMillis()
            val totalProcessingTime = endTime - startTime

            val stats = OptimizedProcessingStats(
                totalCoordinates = coords.size,
                successfulCoordinates = allGrains.size,
                finalGrainCount = finalGrains.size,
                processingTimeMs = totalProcessingTime,
                coordinateProcessingTimeMs = coordinateProcessingTime,
                postProcessingTimeMs = postProcessingTime,
                memoryUsedMB = memoryUsedMB
            )

            progressCallback(1.0f)

            println("✅ Optimized segmentation completed:")
            println("   Total time: ${totalProcessingTime}ms")
            println("   Coordinate processing: ${coordinateProcessingTime}ms")
            println("   Post-processing: ${postProcessingTime}ms")
            println("   Memory used: ${memoryUsedMB}MB")
            println("   Final grains: ${finalGrains.size}")

            OptimizedInstanceSegmentationResult(
                allGrains = finalGrains,
                labelsOut = labelsOut,
                maskAll = maskAll,
                grainData = grainData,
                processingStats = stats
            )

        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Optimized segmentation failed: ${e.message}")
            null
        }
    }

    /**
     * Complete segmentation method for backwards compatibility
     */
    suspend fun performCompleteInstanceSegmentation(
        originalBitmap: Bitmap,
        predictionArray: Array<Array<FloatArray>>,
        labelingResult: LabelingResult,
        minArea: Int = 400,
        progressCallback: (Float) -> Unit = {}
    ): CompleteSegmentationResult? = withContext(Dispatchers.Default) {

        try {
            // Step 1: Initial instance segmentation (0% - 70%)
            val initialResult = performInstanceSegmentation(
                originalBitmap = originalBitmap,
                predictionArray = predictionArray,
                labelingResult = labelingResult,
                minArea = minArea
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
                labels = finalLabels
            )

            // Calculate final properties
            val finalGrainData = ImageAnalysisUtils.calculateRegionProperties(finalLabels)

            progressCallback(1.0f)

            println("✅ Complete segmentation: ${initialResult.allGrains.size} -> ${finalGrains.size} final grains")

            CompleteSegmentationResult(
                initialResult = initialResult.toInstanceSegmentationResult(),
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
     * Optimized coordinate processing with batch parallelization
     */
    private suspend fun processCoordinatesOptimized(
        coords: List<IntArray>,
        labels: Array<IntArray>,
        predictionArray: Array<Array<FloatArray>>,
        embeddingResult: ONNXMobileSAMProcessor.EmbeddingResult,
        mobileSAM: ONNXMobileSAMProcessor,
        minArea: Int,
        progressCallback: (Float) -> Unit
    ): List<Polygon> = withContext(Dispatchers.Default) {

        val allGrains = mutableListOf<Polygon>()
        val batchSize = 25 // Reduced batch size for better memory management
        val totalBatches = (coords.size + batchSize - 1) / batchSize

        coords.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            try {
                println("🔄 Processing batch ${batchIndex + 1}/$totalBatches (${batch.size} coordinates)")

                // Process batch sequentially to avoid memory issues
                batch.forEach { coord ->
                    val x = coord[0]
                    val y = coord[1]

                    try {
                        val mask = mobileSAM.predictMaskWithSAMCoords(x, y, embeddingResult.embeddings)

                        if (mask != null && mask.any { row -> row.any { it } }) {
                            val processedMask = handleMultipleComponents(mask, x, y)

                            if (processedMask != null) {
                                val contours = GrainCollectionUtils.findContours(processedMask)

                                if (contours.isNotEmpty()) {
                                    val largestContour = contours.maxByOrNull { it.first.size }
                                    if (largestContour != null) {
                                        val (sx, sy) = largestContour
                                        val (finalMask, finalSx, finalSy) = handleEdgeCases(processedMask, sx, sy)

                                        if (finalMask.any { row -> row.any { it } }) {
                                            val grainsBefore = allGrains.size
                                            GrainCollectionUtils.collectPolygonFromMask(
                                                labels = labels,
                                                mask = finalMask,
                                                imagePred = predictionArray,
                                                allGrains = allGrains,
                                                sx = finalSx,
                                                sy = finalSy,
                                                minArea = 50 // Lower threshold for initial collection
                                            )

                                            // Apply geometry optimization if new grain was added
                                            if (allGrains.size > grainsBefore) {
                                                val lastGrain = allGrains.last()
                                                val optimizedGrain = geometryOptimizer.optimizePolygon(lastGrain)
                                                if (optimizedGrain != null && optimizedGrain.area >= minArea) {
                                                    allGrains[allGrains.size - 1] = optimizedGrain
                                                } else {
                                                    allGrains.removeAt(allGrains.size - 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("Error processing coordinate ($x, $y): ${e.message}")
                    }
                }

                // Update progress
                val progress = (batchIndex + 1).toFloat() / totalBatches
                progressCallback(progress)

                // Force GC every 5 batches
                if (batchIndex % 5 == 0) {
                    System.gc()
                }

            } catch (_: OutOfMemoryError) {
                println("❌ OOM in batch $batchIndex, forcing GC and continuing...")
                System.gc()
            }
        }

        println("✅ Coordinate processing completed: ${allGrains.size} grains collected")
        allGrains
    }

    /**
     * Optimized post-processing with fast spatial indexing
     */
    private suspend fun postProcessGrainsOptimized(
        allGrains: List<Polygon>,
        minArea: Int,
        imageWidth: Int,
        imageHeight: Int,
        progressCallback: (Float) -> Unit
    ): List<Polygon> = withContext(Dispatchers.Default) {

        if (allGrains.isEmpty()) return@withContext emptyList()

        println("🔄 Starting optimized post-processing for ${allGrains.size} grains...")

        try {
            // Step 1: Pre-filter and optimize polygons (20% of progress)
            val validGrains = allGrains.mapNotNull { grain ->
                try {
                    val optimized = geometryOptimizer.optimizePolygon(grain)
                    if (optimized != null && optimized.area >= minArea && optimized.isValid) {
                        optimized
                    } else null
                } catch (e: Exception) {
                    println("⚠️ Error optimizing grain: ${e.message}")
                    null
                }
            }

            progressCallback(0.2f)
            println("🔄 After pre-filtering: ${validGrains.size} valid grains")

            if (validGrains.isEmpty()) return@withContext emptyList()

            // Step 2: Fast overlap detection using grid-based spatial index (60% of progress)
            val fastIndex = FastSpatialIndex(imageWidth, imageHeight, cellSize = 50)

            validGrains.forEachIndexed { index, grain ->
                fastIndex.insert(grain, index)
            }

            progressCallback(0.4f)

            val overlappingGroups = fastIndex.findOverlappingGroups(overlapThreshold = 0.1)

            progressCallback(0.8f)
            println("🔄 Found ${overlappingGroups.size} overlapping groups using fast index")

            // Step 3: Resolve overlaps by keeping largest grain in each group (20% of progress)
            val grainToRemove = mutableSetOf<Int>()

            overlappingGroups.forEach { group ->
                if (group.size > 1) {
                    val largestIndex = group.maxByOrNull { index ->
                        try {
                            validGrains[index].area
                        } catch (_: Exception) {
                            0.0
                        }
                    }

                    group.forEach { index ->
                        if (index != largestIndex) {
                            grainToRemove.add(index)
                        }
                    }
                }
            }

            val finalGrains = validGrains.filterIndexed { index, _ ->
                index !in grainToRemove
            }

            progressCallback(1.0f)

            println("✅ Optimized post-processing completed:")
            println("   📊 Input grains: ${allGrains.size}")
            println("   📊 Valid grains: ${validGrains.size}")
            println("   📊 Removed overlaps: ${grainToRemove.size}")
            println("   📊 Final grains: ${finalGrains.size}")

            finalGrains

        } catch (e: Exception) {
            println("❌ Error in optimized post-processing: ${e.message}")
            e.printStackTrace()

            // Fallback: simple area filtering
            allGrains.filter { grain ->
                try {
                    grain.area >= minArea && grain.isValid
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    // Helper methods
    private fun handleMultipleComponents(
        mask: Array<BooleanArray>,
        clickX: Int,
        clickY: Int
    ): Array<BooleanArray>? {
        val (labeledMask, numComponents) = ImageAnalysisUtils.labelConnectedComponents(mask, 1)

        if (numComponents <= 1) return mask

        var minDistance = Float.MAX_VALUE
        var bestLabel = 1
        val clickPoint = Pair(clickY, clickX)

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
                    sqrt((di * di + dj * dj).toFloat())
                } ?: Float.MAX_VALUE

                if (minDistToComponent < minDistance) {
                    minDistance = minDistToComponent
                    bestLabel = label
                }
            }
        }

        return Array(mask.size) { i ->
            BooleanArray(mask[i].size) { j ->
                labeledMask[i][j] == bestLabel
            }
        }
    }

    private fun handleEdgeCases(
        mask: Array<BooleanArray>,
        sx: FloatArray,
        sy: FloatArray
    ): Triple<Array<BooleanArray>, FloatArray, FloatArray> {
        val height = mask.size
        val width = mask[0].size

        val touchesEdge = mask[0].any { it } ||
                mask[height - 1].any { it } ||
                mask.any { it[0] } ||
                mask.any { it[width - 1] }

        if (!touchesEdge) {
            return Triple(mask, sx, sy)
        }

        val paddedMask = Array(height + 2) { i ->
            BooleanArray(width + 2) { j ->
                when {
                    i == 0 || i == height + 1 || j == 0 || j == width + 1 -> false
                    else -> mask[i - 1][j - 1]
                }
            }
        }

        val contours = GrainCollectionUtils.findContours(paddedMask)

        if (contours.isNotEmpty()) {
            val largestContour = contours.maxByOrNull { it.first.size }
            if (largestContour != null) {
                var (newSx, newSy) = largestContour

                if (mask[0].any { it }) {
                    newSy = newSy.map { it - 1 }.toFloatArray()
                }
                if (mask.any { it[0] }) {
                    newSx = newSx.map { it - 1 }.toFloatArray()
                }

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

    private fun createEmptyResult(
        totalCoords: Int,
        startTime: Long,
        coordTime: Long
    ): OptimizedInstanceSegmentationResult {
        val processingTime = System.currentTimeMillis() - startTime
        return OptimizedInstanceSegmentationResult(
            allGrains = emptyList(),
            labelsOut = arrayOf(),
            maskAll = arrayOf(),
            grainData = emptyList(),
            processingStats = OptimizedProcessingStats(
                totalCoordinates = totalCoords,
                successfulCoordinates = 0,
                finalGrainCount = 0,
                processingTimeMs = processingTime,
                coordinateProcessingTimeMs = coordTime,
                postProcessingTimeMs = 0L
            )
        )
    }

    private fun createEmptyArrays(width: Int, height: Int): Pair<Array<IntArray>, Array<IntArray>> {
        return Pair(
            Array(height) { IntArray(width) { 0 } },
            Array(height) { IntArray(width) { 0 } }
        )
    }

    fun close() {
        mobileSAM?.close()
    }
}