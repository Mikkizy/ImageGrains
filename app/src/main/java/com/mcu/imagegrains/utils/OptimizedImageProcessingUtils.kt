package com.mcu.imagegrains.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import kotlin.math.*

object OptimizedImageProcessingUtils {

    // Maximum image dimensions to prevent OOM
    private const val MAX_IMAGE_WIDTH = 1024
    private const val MAX_IMAGE_HEIGHT = 1024
    private const val MAX_PROCESSING_WIDTH = 512
    private const val MAX_PROCESSING_HEIGHT = 512

    /**
     * Load bitmap from URI with automatic downsampling to prevent OOM
     */
    fun loadBitmapFromUri(context: Context, uri: Uri, maxWidth: Int = MAX_IMAGE_WIDTH, maxHeight: Int = MAX_IMAGE_HEIGHT): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // First, get image dimensions without loading the full bitmap
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // Calculate sample size to reduce memory usage
                val sampleSize = calculateInSampleSize(options, maxWidth, maxHeight)

                // Load the downsampled bitmap
                context.contentResolver.openInputStream(uri)?.use { secondInputStream ->
                    val loadOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory than ARGB_8888
                        inDither = false
                        inScaled = false
                    }

                    val bitmap = BitmapFactory.decodeStream(secondInputStream, null, loadOptions)

                    // Handle orientation
                    bitmap?.let { correctBitmapOrientation(context, uri, it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error loading bitmap: ${e.message}")
            null
        }
    }

    /**
     * Load image for TensorFlow Lite with aggressive downsampling
     */
    fun loadImageForTFLite(context: Context, uri: Uri): Array<Array<FloatArray>>? {
        return try {
            val bitmap = loadBitmapFromUri(context, uri, MAX_PROCESSING_WIDTH, MAX_PROCESSING_HEIGHT)
                ?: return null

            // Further resize if still too large
            val resizedBitmap = if (bitmap.width > MAX_PROCESSING_WIDTH || bitmap.height > MAX_PROCESSING_HEIGHT) {
                resizeBitmapSafely(bitmap, MAX_PROCESSING_WIDTH, MAX_PROCESSING_HEIGHT)
            } else {
                bitmap
            }

            val result = convertBitmapToFloatArray(resizedBitmap)

            // Clean up bitmaps to free memory
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            bitmap.recycle()

            // Force garbage collection
            System.gc()

            result
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Error loading image for TFLite: ${e.message}")
            null
        }
    }

    /**
     * Get image dimensions without loading the full bitmap
     */
    fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                Pair(options.outWidth, options.outHeight)
            }
        } catch (e: Exception) {
            println("❌ Error getting image dimensions: ${e.message}")
            null
        }
    }

    /**
     * Calculate appropriate sample size to reduce memory usage
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Safely resize bitmap without causing OOM
     */
    private fun resizeBitmapSafely(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val scale = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )

        if (scale >= 1.0f) return bitmap

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return try {
            val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            resized
        } catch (e: OutOfMemoryError) {
            println("❌ OOM during resize, using original bitmap")
            bitmap
        }
    }

    /**
     * Correct bitmap orientation using EXIF data
     */
    private fun correctBitmapOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                }

                if (!matrix.isIdentity) {
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated != bitmap) {
                        bitmap.recycle()
                    }
                    rotated
                } else {
                    bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            println("❌ Error correcting orientation: ${e.message}")
            bitmap
        }
    }

    /**
     * Convert bitmap to float array with memory optimization
     */
    fun convertBitmapToFloatArray(bitmap: Bitmap): Array<Array<FloatArray>> {
        val width = bitmap.width
        val height = bitmap.height

        println("🔄 Converting bitmap to float array: ${width}x${height}")

        // Pre-allocate the array
        val result = Array(height) {
            Array(width) {
                FloatArray(3)
            }
        }

        // Process in chunks to avoid memory spikes
        val chunkSize = 100
        val pixels = IntArray(width * chunkSize)

        for (startY in 0 until height step chunkSize) {
            val endY = minOf(startY + chunkSize, height)
            val currentHeight = endY - startY

            try {
                // Get pixels for this chunk
                bitmap.getPixels(pixels, 0, width, 0, startY, width, currentHeight)

                // Convert pixels to float values
                for (y in 0 until currentHeight) {
                    for (x in 0 until width) {
                        val pixel = pixels[y * width + x]
                        val arrayY = startY + y

                        result[arrayY][x][0] = ((pixel shr 16) and 0xFF) / 255f // Red
                        result[arrayY][x][1] = ((pixel shr 8) and 0xFF) / 255f  // Green
                        result[arrayY][x][2] = (pixel and 0xFF) / 255f          // Blue
                    }
                }
            } catch (e: OutOfMemoryError) {
                println("❌ OOM during conversion at chunk $startY")
                System.gc()
                throw e
            }
        }

        println("✅ Conversion complete")
        return result
    }

    /**
     * Convert float array back to bitmap with memory optimization
     */
    fun convertFloatArrayToBitmap(floatArray: Array<Array<FloatArray>>): Bitmap {
        val height = floatArray.size
        val width = floatArray[0].size

        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565) // Use less memory

            // Process in chunks
            val chunkSize = 100
            val pixels = IntArray(width * chunkSize)

            for (startY in 0 until height step chunkSize) {
                val endY = minOf(startY + chunkSize, height)
                val currentHeight = endY - startY

                // Convert float values to pixels
                for (y in 0 until currentHeight) {
                    for (x in 0 until width) {
                        val arrayY = startY + y
                        val channels = floatArray[arrayY][x].size

                        val pixel = when {
                            channels >= 3 -> {
                                val r = (floatArray[arrayY][x][0] * 255f).toInt().coerceIn(0, 255)
                                val g = (floatArray[arrayY][x][1] * 255f).toInt().coerceIn(0, 255)
                                val b = (floatArray[arrayY][x][2] * 255f).toInt().coerceIn(0, 255)
                                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            }
                            else -> {
                                val gray = (floatArray[arrayY][x][0] * 255f).toInt().coerceIn(0, 255)
                                (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                            }
                        }
                        pixels[y * width + x] = pixel
                    }
                }

                // Set pixels for this chunk
                bitmap.setPixels(pixels, 0, width, 0, startY, width, currentHeight)
            }

            bitmap
        } catch (e: OutOfMemoryError) {
            println("❌ OOM during bitmap creation")
            // Return a small placeholder bitmap
            Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        }
    }
}