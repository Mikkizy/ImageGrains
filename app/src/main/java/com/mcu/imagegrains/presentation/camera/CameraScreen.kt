package com.mcu.imagegrains.presentation.camera

import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mcu.imagegrains.R
import com.mcu.imagegrains.utils.CameraUtils
import com.mcu.imagegrains.utils.CameraUtils.detectZoomGestures
import com.mcu.imagegrains.utils.ImageUtils
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBackClicked: () -> Unit,
    navigateToPhotoDisplay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Zoom state
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10.0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Camera Preview with custom zoom gestures
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageCaptureUseCase = CameraUtils.getImageCaptureUseCase()
                    imageCapture = imageCaptureUseCase

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCaptureUseCase
                        )

                        // Get zoom capabilities and camera control
                        camera?.let { cam ->
                            cameraControl = cam.cameraControl
                            val cameraInfo = cam.cameraInfo
                            minZoomRatio = cameraInfo.zoomState.value?.minZoomRatio ?: 1.0f
                            maxZoomRatio = cameraInfo.zoomState.value?.maxZoomRatio ?: 10.0f
                            zoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 1.0f

                            println("📸 Camera zoom capabilities: min=$minZoomRatio, max=$maxZoomRatio")
                        }

                        println("📸 ImageCapture resolution: ${imageCaptureUseCase.resolutionInfo?.resolution}")
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Use our custom detectZoomGestures function
                    detectZoomGestures { zoom ->
                        val newZoomRatio = (zoomRatio * zoom).coerceIn(minZoomRatio, maxZoomRatio)
                        if (newZoomRatio != zoomRatio) {
                            zoomRatio = newZoomRatio
                            cameraControl?.setZoomRatio(newZoomRatio)
                        }
                    }
                }
        )

        // Top Bar
        TopAppBar(
            title = { Text("Take Photo") },
            navigationIcon = {
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        // Zoom Controls (Left side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom In with smooth animation
            SmallFloatingActionButton(
                onClick = {
                    val newZoomRatio = min(zoomRatio + 0.5f, maxZoomRatio)
                    if (newZoomRatio != zoomRatio && cameraControl != null) {
                        scope.launch {
                            // Use animateZoomTo for smooth transition
                            CameraUtils.animateZoomTo(
                                cameraControl = cameraControl!!,
                                targetZoom = newZoomRatio,
                                currentZoom = zoomRatio
                            )
                            zoomRatio = newZoomRatio
                        }
                    }
                },
                containerColor = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Level Indicator with description
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${String.format(Locale.getDefault(),"%.1f", zoomRatio)}x",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CameraUtils.getZoomDescription(zoomRatio),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 8.sp
                    )
                }
            }

            // Zoom Out with smooth animation
            SmallFloatingActionButton(
                onClick = {
                    val newZoomRatio = max(zoomRatio - 0.5f, minZoomRatio)
                    if (newZoomRatio != zoomRatio && cameraControl != null) {
                        scope.launch {
                            // Use animateZoomTo for smooth transition
                            CameraUtils.animateZoomTo(
                                cameraControl = cameraControl!!,
                                targetZoom = newZoomRatio,
                                currentZoom = zoomRatio
                            )
                            zoomRatio = newZoomRatio
                        }
                    }
                },
                containerColor = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White
            ) {
                Icon(painter = painterResource(R.drawable.ic_remove), contentDescription = "Zoom Out")
            }

            // Reset Zoom with smooth animation
            if (zoomRatio != 1.0f) {
                SmallFloatingActionButton(
                    onClick = {
                        if (cameraControl != null) {
                            scope.launch {
                                // Use animateZoomTo for smooth reset
                                CameraUtils.animateZoomTo(
                                    cameraControl = cameraControl!!,
                                    targetZoom = 1.0f,
                                    currentZoom = zoomRatio
                                )
                                zoomRatio = 1.0f
                            }
                        }
                    },
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom")
                }
            }
        }

        // Camera Info (Top Right) - Enhanced with zoom description
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Resolution indicator
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Text(
                    text = imageCapture?.resolutionInfo?.resolution?.let { resolution ->
                        "${resolution.width} × ${resolution.height}"
                    } ?: "Loading...",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Current zoom mode indicator
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "📸 ${CameraUtils.getZoomDescription(zoomRatio)} Mode",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Range: ${String.format(Locale.getDefault(), "%.1f", minZoomRatio)}x - ${String.format(Locale.getDefault(),"%.1f", maxZoomRatio)}x",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 8.sp
                    )
                }
            }
        }

        // Capture Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    imageCapture?.let { capture ->
                        isCapturing = true
                        val photoFile = ImageUtils.createImageFile(context)

                        CameraUtils.captureImage(
                            context = context,
                            imageCapture = capture,
                            outputFile = photoFile,
                            onImageCaptured = { success ->
                                isCapturing = false
                                if (success) {
                                    val photoUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile
                                    )
                                    navigateToPhotoDisplay("${Uri.encode(photoUri.toString())}")
                                }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.photo_camera),
                        contentDescription = "Capture",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }
        }

        // Enhanced Zoom Instructions with current mode
        /*if (zoomRatio <= 1.2f) { // Show instructions when near default zoom
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "🔍 Zoom Controls",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Pinch to zoom smoothly",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "• Use +/- for precise control",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "• Reset button returns to ${String.format("%.1f", minZoomRatio)}x",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Perfect for grain detail capture! 🌾",
                        color = Color.Green.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }*/
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun CameraScreenPreview() {
    CameraScreen(
        {}, {}
    )
}