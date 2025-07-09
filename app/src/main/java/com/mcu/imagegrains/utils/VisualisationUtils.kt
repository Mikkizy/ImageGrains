package com.mcu.imagegrains.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import androidx.core.graphics.set
import androidx.core.graphics.createBitmap

object VisualizationUtils {

    /**
     * Create bitmap from semantic segmentation prediction with coordinate overlays
     */
    fun createSemanticSegmentationVisualization(
        imagePred: Array<Array<FloatArray>>,
        coords: Array<IntArray>,
        dotColor: Color = Color.Black,
        dotRadius: Float = 3f
    ): Bitmap {
        val height = imagePred.size
        val width = imagePred[0].size

        // Convert prediction to bitmap
        val bitmap = convertPredictionToBitmap(imagePred)

        // Create mutable copy for drawing coordinates
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Paint for drawing dots
        val paint = Paint().apply {
            color = dotColor.toArgb()
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // Draw coordinate points
        for (coord in coords) {
            val x = coord[0].toFloat()
            val y = coord[1].toFloat()

            // Check bounds
            if (x >= 0 && x < width && y >= 0 && y < height) {
                canvas.drawCircle(x, y, dotRadius, paint)
            }
        }

        return mutableBitmap
    }

    /**
     * Convert semantic segmentation prediction to bitmap
     */
    fun convertPredictionToBitmap(imagePred: Array<Array<FloatArray>>): Bitmap {
        val height = imagePred.size
        val width = imagePred[0].size
        val channels = imagePred[0][0].size

        val bitmap = createBitmap(width, height)

        for (i in 0 until height) {
            for (j in 0 until width) {
                val pixel = when {
                    channels >= 3 -> {
                        // RGB visualization
                        val r = (imagePred[i][j][0] * 255f).roundToInt().coerceIn(0, 255)
                        val g = (imagePred[i][j][1] * 255f).roundToInt().coerceIn(0, 255)
                        val b = (imagePred[i][j][2] * 255f).roundToInt().coerceIn(0, 255)
                        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    channels == 1 -> {
                        // Grayscale visualization
                        val gray = (imagePred[i][j][0] * 255f).roundToInt().coerceIn(0, 255)
                        (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    }
                    else -> {
                        // Default to black for invalid channels
                        0xFF000000.toInt()
                    }
                }
                bitmap[j, i] = pixel
            }
        }

        return bitmap
    }

    /**
     * Create visualization showing different semantic classes in different colors
     */
    fun createColorCodedSemanticVisualization(
        imagePred: Array<Array<FloatArray>>,
        coords: Array<IntArray>,
        classColors: List<Color> = listOf(
            Color.Black,      // Background (class 0)
            Color.Green,      // Grains (class 1)
            Color.Red         // Boundaries (class 2)
        ),
        dotColor: Color = Color.Yellow,
        dotRadius: Float = 4f
    ): Bitmap {
        val height = imagePred.size
        val width = imagePred[0].size
        val channels = imagePred[0][0].size

        val bitmap = createBitmap(width, height)

        // Create color-coded visualization
        for (i in 0 until height) {
            for (j in 0 until width) {
                // Find dominant class for this pixel
                var maxClass = 0
                var maxProb = imagePred[i][j][0]

                for (c in 1 until channels) {
                    if (imagePred[i][j][c] > maxProb) {
                        maxProb = imagePred[i][j][c]
                        maxClass = c
                    }
                }

                // Get color for dominant class
                val classColor = if (maxClass < classColors.size) {
                    classColors[maxClass]
                } else {
                    Color.Gray
                }

                // Apply alpha based on confidence
                val alpha = (maxProb * 255f).roundToInt().coerceIn(0, 255)
                val pixel = (alpha shl 24) or
                        ((classColor.red * 255f).roundToInt() shl 16) or
                        ((classColor.green * 255f).roundToInt() shl 8) or
                        (classColor.blue * 255f).roundToInt()

                bitmap[j, i] = pixel
            }
        }

        // Draw coordinate points
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = dotColor.toArgb()
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        for (coord in coords) {
            val x = coord[0].toFloat()
            val y = coord[1].toFloat()

            if (x >= 0 && x < width && y >= 0 && y < height) {
                canvas.drawCircle(x, y, dotRadius, paint)
            }
        }

        return bitmap
    }

    /**
     * Create side-by-side comparison of original and prediction
     */
    fun createSideBySideVisualization(
        originalImage: Array<Array<FloatArray>>,
        imagePred: Array<Array<FloatArray>>,
        coords: Array<IntArray>
    ): Bitmap {
        val height = originalImage.size
        val width = originalImage[0].size

        // Create side-by-side bitmap
        val combinedBitmap = Bitmap.createBitmap(width * 2, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)

        // Draw original image on left
        val originalBitmap = ImageProcessingUtils.convertFloatArrayToBitmap(originalImage)
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)

        // Draw prediction with coordinates on right
        val predictionBitmap = createSemanticSegmentationVisualization(imagePred, coords)
        canvas.drawBitmap(predictionBitmap, width.toFloat(), 0f, null)

        return combinedBitmap
    }
}