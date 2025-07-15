package com.mcu.imagegrains.presentation.photo_display

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mcu.imagegrains.presentation.SharedSegmentationViewModel
import com.mcu.imagegrains.utils.OptimizedImageProcessingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDisplayScreen(
    onBackClicked: () -> Unit,
    photoUriString: String,
    sharedViewModel: SharedSegmentationViewModel,
    navigateToSegmentation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoUri = remember { Uri.decode(photoUriString).toUri() }

    // State management for image loading
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingImage by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    var originalImageSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Load image with memory optimization - NO RECYCLING
    LaunchedEffect(photoUri) {
        isLoadingImage = true
        imageError = null

        scope.launch {
            try {
                // Don't clean up previous bitmap here - let ViewModel handle it

                val bitmap = withContext(Dispatchers.IO) {
                    // First get original dimensions
                    val originalDimensions = OptimizedImageProcessingUtils.getImageDimensions(context, photoUri)
                    originalImageSize = originalDimensions

                    // Load optimized bitmap
                    OptimizedImageProcessingUtils.loadBitmapFromUri(
                        context = context,
                        uri = photoUri,
                        maxWidth = 1024,
                        maxHeight = 1024
                    )
                }

                if (bitmap != null) {
                    selectedBitmap = bitmap
                    // SET THE ORIGINAL IMAGE IN SHARED VIEW MODEL - ViewModel will handle copying
                    sharedViewModel.setOriginalImage(photoUri, bitmap)

                    println("✅ Image loaded successfully: ${bitmap.width}x${bitmap.height}")

                    // Log memory usage
                    val runtime = Runtime.getRuntime()
                    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                    println("📊 Memory usage: ${usedMemory / 1024 / 1024}MB / ${runtime.totalMemory() / 1024 / 1024}MB")
                } else {
                    imageError = "Failed to load image. The image might be corrupted."
                }
            } catch (e: OutOfMemoryError) {
                imageError = "Image is too large for processing. Please try a smaller image."
                println("❌ OutOfMemoryError: ${e.message}")
                System.gc()
            } catch (e: Exception) {
                imageError = "Error loading image: ${e.message}"
                println("❌ Error: ${e.message}")
            } finally {
                isLoadingImage = false
            }
        }
    }

    // Don't recycle bitmap on dispose - let ViewModel handle lifecycle
    // DisposableEffect removed to prevent premature recycling

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(bottom = 16.dp)
    ) {
        TopAppBar(
            title = { Text("Photo Display") },
            navigationIcon = {
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // Image display area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoadingImage -> {
                    // Loading state
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Optimizing image...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                imageError != null -> {
                    // Error state with retry
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error loading image",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = imageError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    imageError = null
                                    // Retry loading without recycling
                                    scope.launch {
                                        isLoadingImage = true
                                        try {
                                            val bitmap = withContext(Dispatchers.IO) {
                                                OptimizedImageProcessingUtils.loadBitmapFromUri(
                                                    context = context,
                                                    uri = photoUri,
                                                    maxWidth = 1024,
                                                    maxHeight = 1024
                                                )
                                            }

                                            if (bitmap != null) {
                                                selectedBitmap = bitmap
                                                sharedViewModel.setOriginalImage(photoUri, bitmap)
                                            } else {
                                                imageError = "Failed to load image on retry."
                                            }
                                        } catch (e: Exception) {
                                            imageError = "Retry failed: ${e.message}"
                                        } finally {
                                            isLoadingImage = false
                                        }
                                    }
                                }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                else -> {
                    // Normal image display using Coil (doesn't interfere with our bitmap)
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Captured photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Image information
        if (selectedBitmap != null || originalImageSize != null || isLoadingImage) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Image Information",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (selectedBitmap != null && !selectedBitmap!!.isRecycled) {
                        Text(
                            text = "Optimized size: ${selectedBitmap!!.width} × ${selectedBitmap!!.height}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        // Memory usage (debug info)
                        val runtime = Runtime.getRuntime()
                        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                        Text(
                            text = "Memory: ${usedMemory / 1024 / 1024}MB / ${runtime.maxMemory() / 1024 / 1024}MB",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    // Don't recycle here - let ViewModel handle it
                    onBackClicked()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Retake")
            }

            Button(
                onClick = {
                    navigateToSegmentation()
                },
                modifier = Modifier.weight(1f),
                enabled = selectedBitmap != null && !selectedBitmap!!.isRecycled && !isLoadingImage
            ) {
                Text("Analyze Grains")
            }
        }
    }
}

/*
@Preview
@Composable
private fun PhotoDisplayScreenPreview() {
    PhotoDisplayScreen(
        {},
        photoUriString = "",
    )
}*/
