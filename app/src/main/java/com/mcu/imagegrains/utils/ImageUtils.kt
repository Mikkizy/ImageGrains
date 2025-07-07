package com.mcu.imagegrains.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import java.io.File
import kotlin.math.sqrt
import androidx.core.graphics.scale
import java.io.FileOutputStream
import java.io.IOException

object ImageUtils {
    /**
     * Compress image to maximum 5 megapixels
     */
    fun compressImageTo5MP(context: Context, uri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            val compressedBitmap = compressToMaxPixels(originalBitmap, 5_000_000)
            val correctedBitmap = correctImageOrientation(context, uri, compressedBitmap)

            // Save compressed image
            val file = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
            if (!saveImageToFile(correctedBitmap, file)) {
                Toast.makeText(context, "Failed to save compressed image", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Image compressed successfully", Toast.LENGTH_SHORT).show()
            }

            // Clean up
            if (compressedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            if (correctedBitmap != compressedBitmap) {
                compressedBitmap.recycle()
            }

            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Compress bitmap to maximum specified pixels
     */
    private fun compressToMaxPixels(bitmap: Bitmap, maxPixels: Int): Bitmap {
        val currentPixels = bitmap.width * bitmap.height

        if (currentPixels <= maxPixels) {
            return bitmap
        }

        val scaleFactor = sqrt(maxPixels.toDouble() / currentPixels.toDouble()).toFloat()
        val newWidth = (bitmap.width * scaleFactor).toInt()
        val newHeight = (bitmap.height * scaleFactor).toInt()

        return bitmap.scale(newWidth, newHeight)
    }

    /**
     * Correct image orientation based on EXIF data
     */
    private fun correctImageOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * Rotate bitmap by specified degrees
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Save bitmap to file
     */
    private fun saveImageToFile(bitmap: Bitmap, file: File): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Create file for camera capture
     */
    fun createImageFile(context: Context): File {
        val imageFileName = "GRAIN_${System.currentTimeMillis()}"
        val storageDir = File(context.getExternalFilesDir(null), "Pictures")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, "$imageFileName.jpg")
    }
}