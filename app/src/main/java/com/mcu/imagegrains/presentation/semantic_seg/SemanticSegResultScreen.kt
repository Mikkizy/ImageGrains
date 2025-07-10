package com.mcu.imagegrains.presentation.semantic_seg

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mcu.imagegrains.presentation.SharedSegmentationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemanticSegmentationResultScreen(
    goBack: () -> Unit,
    navigateToScaleCalibration: () -> Unit,
    sharedViewModel: SharedSegmentationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSnackbar by remember { mutableStateOf<String?>(null) }
    val isProcessing by sharedViewModel.isProcessing.collectAsState()
    val progress by sharedViewModel.progress.collectAsState()
    val error by sharedViewModel.error.collectAsState()
    val semanticResult by sharedViewModel.semanticResult.collectAsState()
    val labelingResult by sharedViewModel.labelingResult.collectAsState()

    // Start processing when screen loads
    LaunchedEffect(Unit) {
        if (semanticResult == null && !isProcessing) {
            sharedViewModel.performSemanticSegmentation(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    "Semantic Segmentation",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = goBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        when {
            isProcessing -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(64.dp),
                        color = ProgressIndicatorDefaults.circularColor,
                        strokeWidth = ProgressIndicatorDefaults.CircularStrokeWidth,
                        trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
                        strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Processing semantic segmentation...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${(progress * 100).toInt()}% complete",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            sharedViewModel.clearError()
                            sharedViewModel.performSemanticSegmentation(context)
                        }
                    ) {
                        Text("Retry")
                    }
                }
            }

            semanticResult != null && labelingResult != null -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    SemanticSegmentationVisualization(
                        originalImage = semanticResult!!.originalArray,
                        imagePred = semanticResult!!.predictionArray,
                        coords = labelingResult!!.allCoords,
                        modifier = Modifier.weight(1f),
                        onSaveImage = { bitmap ->
                            scope.launch {
                                val success = saveBitmapToGallery(context, bitmap)
                                showSnackbar = if (success) {
                                    "Image saved to gallery"
                                } else {
                                    "Failed to save image"
                                }
                                if (success) {
                                    Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onShareImage = { bitmap ->
                            scope.launch {
                                shareBitmap(context, bitmap)
                            }
                        }
                    )

                    // Navigation button
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { goBack() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Back to Photo")
                            }

                            Button(
                                onClick = { navigateToScaleCalibration() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Set Scale")
                            }
                        }
                    }
                }
            }
        }
    }

    // Snackbar for feedback
    showSnackbar?.let { message ->
        LaunchedEffect(message) {
            showSnackbar = null
        }

        SnackbarHost(
            hostState = remember { SnackbarHostState() }.apply {
                LaunchedEffect(message) {
                    showSnackbar(message)
                }
            }
        )
    }
}

private suspend fun saveBitmapToGallery( // Renamed for clarity
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



private suspend fun shareBitmap(
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