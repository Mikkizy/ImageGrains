package com.mcu.imagegrains.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*
import androidx.core.graphics.get
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

object ImageProcessingUtils {

    /**
     * Load image from URI as 3D float array for TFLite processing
     */
    suspend fun loadImageForTFLite(
        context: Context,
        uri: Uri,
        colorMode: ColorMode = ColorMode.RGB
    ): Array<Array<FloatArray>>? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return@withContext null

            convertBitmapToFloatArray(bitmap, colorMode)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Convert bitmap to 3D float array
     */
    fun convertBitmapToFloatArray(
        bitmap: Bitmap,
        colorMode: ColorMode = ColorMode.RGB
    ): Array<Array<FloatArray>> {
        val width = bitmap.width
        val height = bitmap.height

        return when (colorMode) {
            ColorMode.RGB -> {
                Array(height) { h ->
                    Array(width) { w ->
                        val pixel = bitmap[w, h]
                        floatArrayOf(
                            ((pixel shr 16) and 0xFF) / 255f, // Red
                            ((pixel shr 8) and 0xFF) / 255f,  // Green
                            (pixel and 0xFF) / 255f           // Blue
                        )
                    }
                }
            }
            ColorMode.GRAYSCALE -> {
                Array(height) { h ->
                    Array(width) { w ->
                        val pixel = bitmap[w, h]
                        val gray = (0.299 * ((pixel shr 16) and 0xFF) +
                                0.587 * ((pixel shr 8) and 0xFF) +
                                0.114 * (pixel and 0xFF)) / 255f
                        floatArrayOf(gray.toFloat(), gray.toFloat(), gray.toFloat())
                    }
                }
            }
        }
    }

    /**
     * Convert 3D float array back to bitmap
     */
    fun convertFloatArrayToBitmap(imageArray: Array<Array<FloatArray>>): Bitmap {
        val height = imageArray.size
        val width = imageArray[0].size
        val channels = imageArray[0][0].size

        val bitmap = createBitmap(width, height)

        for (h in 0 until height) {
            for (w in 0 until width) {
                val pixel = when (channels) {
                    1 -> {
                        val gray = (imageArray[h][w][0] * 255f).roundToInt().coerceIn(0, 255)
                        (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    }
                    3 -> {
                        val r = (imageArray[h][w][0] * 255f).roundToInt().coerceIn(0, 255)
                        val g = (imageArray[h][w][1] * 255f).roundToInt().coerceIn(0, 255)
                        val b = (imageArray[h][w][2] * 255f).roundToInt().coerceIn(0, 255)
                        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    else -> 0xFF000000.toInt() // Black for invalid channels
                }
                bitmap[w, h] = pixel
            }
        }

        return bitmap
    }

    /**
     * Generate modified Hanning windows for edge handling
     */
    fun generateModifiedHanningWindows(size: Int): Triple<Array<FloatArray>, Array<FloatArray>, Array<FloatArray>> {
        val halfSize = size / 2
        val window1D = FloatArray(size) { i ->
            (0.5 * (1 - cos(2 * PI * i / (size - 1)))).toFloat()
        }

        // Regular window
        val W = Array(size) { i ->
            FloatArray(size) { j ->
                window1D[i] * window1D[j]
            }
        }

        // Upper window (for first row)
        val Wup = Array(size) { i ->
            FloatArray(size) { j ->
                if (i < halfSize) window1D[j] else window1D[i] * window1D[j]
            }
        }

        // Lower window (for last row)
        val Wdown = Array(size) { i ->
            FloatArray(size) { j ->
                if (i >= halfSize) window1D[j] else window1D[i] * window1D[j]
            }
        }

        return Triple(W, Wup, Wdown)
    }

    enum class ColorMode {
        RGB, GRAYSCALE
    }
}