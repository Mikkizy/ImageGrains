package com.mcu.imagegrains.presentation.semantic_seg

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.navigation.NavController
import com.mcu.imagegrains.presentation.SharedSegmentationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
        modifier = modifier.fillMaxSize()
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
                                val success = saveBitmapToFile(context, bitmap, "semantic_segmentation_result.jpg")
                                showSnackbar = if (success) {
                                    "Image saved to gallery"
                                } else {
                                    "Failed to save image"
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

private suspend fun saveBitmapToFile(
    context: Context,
    bitmap: Bitmap,
    filename: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val file = File(context.getExternalFilesDir("Images"), filename)
        file.parentFile?.mkdirs()

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        // Add to gallery
        /*val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = Uri.fromFile(file)
        context.sendBroadcast(intent)*/


        MediaScannerConnection.scanFile(context, arrayOf(file.toString()),
            null, null)

        /*val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GrainSegImages")
            }
        }


        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)*/

        true
    } catch (e: Exception) {
        e.printStackTrace()
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