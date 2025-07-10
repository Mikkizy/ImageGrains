package com.mcu.imagegrains.presentation.semantic_seg

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcu.imagegrains.utils.VisualizationUtils
import com.mcu.imagegrains.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemanticSegmentationVisualization(
    originalImage: Array<Array<FloatArray>>?,
    imagePred: Array<Array<FloatArray>>?,
    coords: Array<IntArray>?,
    modifier: Modifier = Modifier,
    onSaveImage: ((Bitmap) -> Unit)? = null,
    onShareImage: ((Bitmap) -> Unit)? = null
) {
    var visualizationMode by remember { mutableStateOf(VisualizationMode.PREDICTION_WITH_COORDS) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Generate visualization bitmap when data changes
    LaunchedEffect(originalImage, imagePred, coords, visualizationMode) {
        if (imagePred != null && coords != null) {
            currentBitmap = when (visualizationMode) {
                VisualizationMode.PREDICTION_WITH_COORDS -> {
                    VisualizationUtils.createSemanticSegmentationVisualization(
                        imagePred = imagePred,
                        coords = coords,
                        dotColor = Color.Black,
                        dotRadius = 4f
                    )
                }
                VisualizationMode.COLOR_CODED -> {
                    VisualizationUtils.createColorCodedSemanticVisualization(
                        imagePred = imagePred,
                        coords = coords,
                        dotColor = Color.Yellow,
                        dotRadius = 4f
                    )
                }
                VisualizationMode.SIDE_BY_SIDE -> {
                    if (originalImage != null) {
                        VisualizationUtils.createSideBySideVisualization(
                            originalImage = originalImage,
                            imagePred = imagePred,
                            coords = coords
                        )
                    } else {
                        VisualizationUtils.createSemanticSegmentationVisualization(
                            imagePred = imagePred,
                            coords = coords
                        )
                    }
                }
                VisualizationMode.PREDICTION_ONLY -> {
                    VisualizationUtils.convertPredictionToBitmap(imagePred)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar with controls
        TopAppBar(
            title = {
                Text(
                    "Semantic Segmentation Result",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            actions = {
                // Save button
                currentBitmap?.let { bitmap ->
                    IconButton(onClick = { onSaveImage?.invoke(bitmap) }) {
                        Icon(painter = painterResource(R.drawable.ic_download), contentDescription = "Save")
                    }

                    IconButton(onClick = { onShareImage?.invoke(bitmap) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            }
        )

        // Visualization mode selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Visualization Mode:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VisualizationMode.values().forEach { mode ->
                        FilterChip(
                            onClick = { visualizationMode = mode },
                            label = {
                                Text(
                                    text = mode.displayName,
                                    fontSize = 8.sp
                                )
                            },
                            selected = visualizationMode == mode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Statistics card
        if (coords != null && imagePred != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatisticItem(
                        label = "Image Size",
                        value = "${imagePred[0].size} × ${imagePred.size}"
                    )
                    StatisticItem(
                        label = "Coordinates",
                        value = "${coords.size}"
                    )
                    StatisticItem(
                        label = "Channels",
                        value = "${imagePred[0][0].size}"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main visualization
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                currentBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Semantic Segmentation Result",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } ?: run {
                    if (imagePred == null) {
                        Text(
                            text = "No prediction data available",
                            modifier = Modifier.padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
            }
        }

        // Legend
        if (visualizationMode == VisualizationMode.COLOR_CODED) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Class Legend:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LegendItem("Background", Color.Black)
                    LegendItem("Grains", Color.Green)
                    LegendItem("Boundaries", Color.Red)
                    LegendItem("Coordinate Points", Color.Yellow)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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

@Composable
private fun LegendItem(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 12.sp
        )
    }
}

enum class VisualizationMode(val displayName: String) {
    PREDICTION_WITH_COORDS("Prediction + Coords"),
    COLOR_CODED("Color Coded"),
    SIDE_BY_SIDE("Side by Side"),
    PREDICTION_ONLY("Prediction Only")
}

@Preview
@Composable
private fun SemanticTagPreview() {
    SemanticSegmentationVisualization(
        originalImage = null,
        imagePred = null,
        coords = null,
        modifier = Modifier,
        onSaveImage = null,
        onShareImage = null
    )
}