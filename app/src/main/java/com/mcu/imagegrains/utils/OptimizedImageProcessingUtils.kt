package com.mcu.imagegrains.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object OptimizedImageProcessingUtils {

    // Maximum image dimensions to prevent OOM
    private const val MAX_IMAGE_WIDTH = 1024
    private const val MAX_IMAGE_HEIGHT = 1024

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

}