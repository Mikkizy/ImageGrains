package com.mcu.imagegrains.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mcu.imagegrains.utils.ImageProcessingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

data class SemanticSegmentationResult(
    val predictionBitmap: Bitmap,
    val predictionArray: Array<Array<FloatArray>>,
    val originalArray: Array<Array<FloatArray>>
)

class SemanticSegmentationProcessor(
    private val context: Context,
    private val modelPath: String,
    private val tileSize: Int = 256
) {
    private var model: TFLiteSemanticSegmentationModel? = null

    /**
     * Initialize the semantic segmentation model
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            model = TFLiteSemanticSegmentationModel(context, modelPath)
            model?.initialize() ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Perform semantic segmentation and return both bitmap and arrays
     */
    suspend fun predictImageComplete(
        uri: Uri,
        progressCallback: (Float) -> Unit = {}
    ): SemanticSegmentationResult? = withContext(Dispatchers.IO) {

        val model = this@SemanticSegmentationProcessor.model
        if (model == null) {
            println("❌ Model not initialized")
            return@withContext null
        }

        try {
            progressCallback(0.1f)

            // Load image as float array
            val originalArray = ImageProcessingUtils.loadImageForTFLite(context, uri)
            if (originalArray == null) {
                println("❌ Failed to load image")
                return@withContext null
            }

            println("✅ Loaded image with shape: ${originalArray.size} x ${originalArray[0].size} x ${originalArray[0][0].size}")
            progressCallback(0.2f)

            // Perform semantic segmentation (returns float array)
            val predictionArray = predictImageTiled(originalArray, model, tileSize, progressCallback)

            progressCallback(0.9f)

            // Convert result to bitmap for display
            val predictionBitmap = ImageProcessingUtils.convertFloatArrayToBitmap(predictionArray)

            progressCallback(1.0f)
            println("✅ Semantic segmentation completed")

            SemanticSegmentationResult(
                predictionBitmap = predictionBitmap,
                predictionArray = predictionArray,
                originalArray = originalArray
            )

        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Semantic segmentation failed: ${e.message}")
            null
        }
    }

    /**
     * Perform semantic segmentation on the entire image
     */
    suspend fun predictImage(
        uri: Uri,
        progressCallback: (Float) -> Unit = {}
    ): Bitmap? = withContext(Dispatchers.IO) {

        val model = this@SemanticSegmentationProcessor.model
        if (model == null) {
            println("❌ Model not initialized")
            return@withContext null
        }

        try {
            progressCallback(0.1f)

            // Load image
            val image = ImageProcessingUtils.loadImageForTFLite(context, uri)
            if (image == null) {
                println("❌ Failed to load image")
                return@withContext null
            }

            println("✅ Loaded image with shape: ${image.size} x ${image[0].size} x ${image[0][0].size}")
            progressCallback(0.2f)

            // Perform semantic segmentation
            val imagePred = predictImageTiled(image, model, tileSize, progressCallback)

            progressCallback(0.9f)

            // Convert result back to bitmap
            val resultBitmap = ImageProcessingUtils.convertFloatArrayToBitmap(imagePred)

            progressCallback(1.0f)
            println("✅ Semantic segmentation completed")

            resultBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Semantic segmentation failed: ${e.message}")
            null
        }
    }

    /**
     * Predict image using tiled approach with overlapping windows
     */
    private suspend fun predictImageTiled(
        image: Array<Array<FloatArray>>,
        model: TFLiteSemanticSegmentationModel,
        I: Int,
        progressCallback: (Float) -> Unit
    ): Array<Array<FloatArray>> = withContext(Dispatchers.Default) {

        val originalHeight = image.size
        val originalWidth = image[0].size
        val channels = image[0][0].size

        // Calculate padding
        val padRows = I - (originalHeight % I)
        val padCols = I - (originalWidth % I)

        // Convert to 3 channels if needed and add padding
        val paddedImage = Array(originalHeight + padRows) { h ->
            Array(originalWidth + padCols) { w ->
                if (h < originalHeight && w < originalWidth) {
                    if (channels == 1) {
                        // Convert grayscale to RGB
                        val gray = image[h][w][0]
                        floatArrayOf(gray, gray, gray)
                    } else {
                        image[h][w].copyOf()
                    }
                } else {
                    floatArrayOf(0f, 0f, 0f) // Zero padding
                }
            }
        }

        val r = paddedImage.size / I  // number of rows of image tiles
        val c = paddedImage[0].size / I  // number of columns of image tiles
        val I2 = I / 2

        // Generate Hanning windows
        val (W, Wup, Wdown) = ImageProcessingUtils.generateModifiedHanningWindows(I)

        // Add side padding
        val finalWidth = c * I + I
        val finalImage = Array(r * I) { h ->
            Array(finalWidth) { w ->
                when {
                    w < I2 -> floatArrayOf(0f, 0f, 0f) // Left padding
                    w >= I2 + paddedImage[0].size -> floatArrayOf(0f, 0f, 0f) // Right padding
                    else -> paddedImage[h][w - I2].copyOf()
                }
            }
        }

        // Initialize prediction array
        val imagePred = Array(finalImage.size) {
            Array(finalImage[0].size) {
                FloatArray(3) { 0f }
            }
        }

        var totalTiles = 0
        var processedTiles = 0

        // Count total tiles for progress tracking
        totalTiles = (c + 1) * (2 * r - 2) + 2 * (c + 1) + c * (2 * r - 2) + 2 * c

        println("🔄 Segmenting image tiles... (${totalTiles} tiles total)")

        // Process tiles - no offset
        for (i in 0..c) {
            for (j in 1 until 2 * r - 2) {
                val tile = extractTile(finalImage, j * I2, (j + 2) * I2, i * I, (i + 1) * I)
                val normalizedTile = normalizeTile(tile)

                val tilePred = model.predictImageTile(normalizedTile)
                if (tilePred != null) {
                    blendTileIntoResult(imagePred, tilePred, j * I2, i * I, W)
                }

                processedTiles++
                if (processedTiles % 10 == 0) {
                    val progress = 0.2f + 0.6f * (processedTiles.toFloat() / totalTiles)
                    progressCallback(progress)
                }
            }
        }

        // Process first row
        for (i in 0..c) {
            val tile = extractTile(finalImage, 0, 2 * I2, i * I, (i + 1) * I)
            val normalizedTile = normalizeTile(tile)

            val tilePred = model.predictImageTile(normalizedTile)
            if (tilePred != null) {
                blendTileIntoResult(imagePred, tilePred, 0, i * I, Wup)
            }

            processedTiles++
        }

        // Process last row
        for (i in 0..c) {
            val tile = extractTile(finalImage, (2 * r - 2) * I2, 2 * r * I2, i * I, (i + 1) * I)
            val normalizedTile = normalizeTile(tile)

            val tilePred = model.predictImageTile(normalizedTile)
            if (tilePred != null) {
                blendTileIntoResult(imagePred, tilePred, (2 * r - 2) * I2, i * I, Wdown)
            }

            processedTiles++
        }

        // Process tiles - half offset
        for (i in 0 until c) {
            for (j in 1 until 2 * r - 2) {
                val tile = extractTile(finalImage, j * I2, (j + 2) * I2, i * I + I2, (i + 1) * I + I2)
                val normalizedTile = normalizeTile(tile)

                val tilePred = model.predictImageTile(normalizedTile)
                if (tilePred != null) {
                    blendTileIntoResult(imagePred, tilePred, j * I2, i * I + I2, W)
                }

                processedTiles++
                if (processedTiles % 10 == 0) {
                    val progress = 0.2f + 0.6f * (processedTiles.toFloat() / totalTiles)
                    progressCallback(progress)
                }
            }
        }

        // Process first row - half offset
        for (i in 0 until c) {
            val tile = extractTile(finalImage, 0, 2 * I2, i * I + I2, (i + 1) * I + I2)
            val normalizedTile = normalizeTile(tile)

            val tilePred = model.predictImageTile(normalizedTile)
            if (tilePred != null) {
                blendTileIntoResult(imagePred, tilePred, 0, i * I + I2, Wup)
            }

            processedTiles++
        }

        // Process last row - half offset
        for (i in 0 until c) {
            val tile = extractTile(finalImage, (2 * r - 2) * I2, 2 * r * I2, i * I + I2, (i + 1) * I + I2)
            val normalizedTile = normalizeTile(tile)

            val tilePred = model.predictImageTile(normalizedTile)
            if (tilePred != null) {
                blendTileIntoResult(imagePred, tilePred, (2 * r - 2) * I2, i * I + I2, Wdown)
            }

            processedTiles++
        }

        println("✅ Processed ${processedTiles} tiles")

        // Crop padding and return result
        val result = Array(originalHeight) { h ->
            Array(originalWidth) { w ->
                imagePred[h][w + I2].copyOf()
            }
        }

        result
    }

    private fun extractTile(
        image: Array<Array<FloatArray>>,
        startRow: Int,
        endRow: Int,
        startCol: Int,
        endCol: Int
    ): Array<Array<FloatArray>> {
        return Array(endRow - startRow) { h ->
            Array(endCol - startCol) { w ->
                image[startRow + h][startCol + w].copyOf()
            }
        }
    }

    private fun normalizeTile(tile: Array<Array<FloatArray>>): Array<Array<FloatArray>> {
        return Array(tile.size) { h ->
            Array(tile[0].size) { w ->
                FloatArray(tile[0][0].size) { c ->
                    tile[h][w][c] // Already normalized in range [0, 1]
                }
            }
        }
    }

    private fun blendTileIntoResult(
        imagePred: Array<Array<FloatArray>>,
        tilePred: Array<Array<FloatArray>>,
        startRow: Int,
        startCol: Int,
        window: Array<FloatArray>
    ) {
        val tileHeight = tilePred.size
        val tileWidth = tilePred[0].size
        val channels = tilePred[0][0].size

        for (h in 0 until tileHeight) {
            for (w in 0 until tileWidth) {
                val targetRow = startRow + h
                val targetCol = startCol + w

                if (targetRow < imagePred.size && targetCol < imagePred[0].size) {
                    val weight = window[h][w]
                    for (c in 0 until channels) {
                        imagePred[targetRow][targetCol][c] += tilePred[h][w][c] * weight
                    }
                }
            }
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        model?.close()
        model = null
    }
}