package com.mcu.imagegrains.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.sqrt
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale

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

    suspend fun saveBitmapToGallery( // Renamed for clarity
        context: Context,
        bitmap: Bitmap,
        displayNamePrefix: String = "GrainSegImage"
    ): Boolean = withContext(Dispatchers.IO) {
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val filename = "$displayNamePrefix-$name.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29+ (Android 10+)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "GrainSegImages")
                put(MediaStore.MediaColumns.IS_PENDING, 1) // Mark as pending until written
            } else {

                // For < API 29, if you were writing to public storage (requires WRITE_EXTERNAL_STORAGE):
                // val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                // val image = File(imagesDir, "GrainSegImages/$filename")
                // if (!image.parentFile.exists()) image.parentFile.mkdirs()
                // put(MediaStore.MediaColumns.DATA, image.absolutePath)
                // For now, the focus is the Q+ error. We'll stick to the Q+ path for MediaStore direct insert.
            }
        }

        var imageUri: Uri? = null
        try {
            imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (imageUri == null) {
                // Log.e("SaveBitmap", "Failed to create new MediaStore record.")
                return@withContext false
            }

            context.contentResolver.openOutputStream(imageUri)?.use { outputStream: OutputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)) {
                    // Log.e("SaveBitmap", "Failed to save bitmap.")
                    // If saving failed, you might want to delete the pending MediaStore entry
                    context.contentResolver.delete(imageUri, null, null)
                    return@withContext false
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0) // Mark as not pending
                context.contentResolver.update(imageUri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // If an error occurs, and we have a URI, delete the incomplete MediaStore entry
            imageUri?.let { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (deleteEx: Exception) {
                    // Log.e("SaveBitmap", "Error deleting MediaStore entry after failure: $deleteEx")
                }
            }
            false
        }
    }



    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap
    ) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "shared_segmentation_result.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            withContext(Dispatchers.Main) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, "Share Segmentation Result"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}