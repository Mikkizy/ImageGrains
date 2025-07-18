package com.mcu.imagegrains.presentation.instance_seg

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcu.imagegrains.R
import com.mcu.imagegrains.presentation.SharedSegmentationViewModel
import com.mcu.imagegrains.utils.ImageUtils
import com.mcu.imagegrains.utils.VisualizationUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceSegmentationScreen(
    goBack: () -> Unit,
    navigateToScaleCalibration: () -> Unit,
    navigateToResultOverview: () -> Unit,
    sharedViewModel: SharedSegmentationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isProcessing by sharedViewModel.isProcessing.collectAsStateWithLifecycle()
    val progress by sharedViewModel.progress.collectAsStateWithLifecycle()
    val error by sharedViewModel.error.collectAsStateWithLifecycle()
    val instanceResult by sharedViewModel.instanceResult.collectAsStateWithLifecycle()
    val scaleCalibration by sharedViewModel.scaleCalibration.collectAsStateWithLifecycle()

    // Keep screen awake while processing
    DisposableEffect(isProcessing) {
        val window = (context as Activity).window
        if (isProcessing) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            println("🔋 Screen kept awake for segmentation")
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            println("🔋 Screen wake lock released")
        }

        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        if (instanceResult == null && !isProcessing) {
            sharedViewModel.performInstanceSegmentation()
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
                    "Instance Segmentation",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = { goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (instanceResult != null) {
                    instanceResult!!.finalVisualization.let { bitmap ->
                        IconButton(onClick = {
                            scope.launch {
                                val success = ImageUtils.saveBitmapToGallery(context, bitmap, "MaskedImage")
                                if (success) {
                                    Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(painter = painterResource(R.drawable.ic_download), contentDescription = "Save")
                        }

                        IconButton(onClick = {
                            scope.launch {
                                ImageUtils.shareBitmap(context, bitmap)
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
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
                        text = "Processing instance segmentation...",
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
                            sharedViewModel.performInstanceSegmentation()
                        }
                    ) {
                        Text("Retry")
                    }
                }
            }

            instanceResult != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Results summary
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Segmentation Complete",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatisticItem(
                                    label = "Final Grains",
                                    value = "${instanceResult!!.finalGrains.size}"
                                )
                                StatisticItem(
                                    label = "Processing Time",
                                    value = VisualizationUtils.convertMillisecondsToTime(instanceResult!!.initialResult.processingStats.processingTimeMs)
                                )
                                StatisticItem(
                                    label = "Scale",
                                    value = if (scaleCalibration != null) "✓" else "Not set"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Visualization
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Instance Segmentation Result",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Image(
                                bitmap = instanceResult!!.finalVisualization.asImageBitmap(),
                                contentDescription = "Instance segmentation result",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (scaleCalibration == null) {
                            OutlinedButton(
                                onClick = { navigateToScaleCalibration() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Set Scale")
                            }
                        }

                        Button(
                            onClick = { navigateToResultOverview() },
                            modifier = Modifier.weight(1f),
                            enabled = scaleCalibration != null
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Results")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}