package com.mcu.imagegrains.presentation.session_detail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcu.imagegrains.data.local.GrainDatabase
import com.mcu.imagegrains.data.local.GrainSession
import com.mcu.imagegrains.domain.models.GrainStatistics
import com.mcu.imagegrains.domain.models.ScaleCalibration
import com.mcu.imagegrains.domain.repository.GrainRepository
import com.mcu.imagegrains.utils.GrainHistogram
import com.mcu.imagegrains.utils.GrainHistogramData
import com.mcu.imagegrains.utils.ImageUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun SessionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { GrainDatabase.getDatabase(context) }
    val repository = remember { GrainRepository(database.grainSessionDao()) }
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<GrainSession?>(null) }
    var statistics by remember { mutableStateOf<GrainStatistics?>(null) }
    var scaleCalibration by remember { mutableStateOf<ScaleCalibration?>(null) }
    var histogramData by remember { mutableStateOf<GrainHistogramData?>(null) }
    var histogramBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        try {
            session = repository.getSession(sessionId)
            session?.let { s ->
                statistics = repository.parseStatistics(s)
                scaleCalibration = repository.parseScaleCalibration(s)
                histogramData = repository.parseHistogramData(s)
                originalImageBitmap = ImageUtils.loadImageFromPath(s.imagePath)

                histogramData?.let { hData ->
                    histogramBitmap = GrainHistogram.createHistogramBitmap(
                        data = hData,
                        width = 800,
                        height = 600,
                        showGrainClassification = true,
                        showECDFCurves = true
                    )
                }
            }
        } catch (e: Exception) {
            println("❌ Failed to load session: ${e.message}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Text(
                text = session?.name ?: "Loading...",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = { showDeleteDialog = true }
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Session")
            }
        }

        session?.let { s ->
            LazyColumn {
                // Session info
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Session Information",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                text = "Created: ${SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(s.timestamp))}",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            statistics?.let { stats ->
                                Text(
                                    text = "Grain count: ${stats.count}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Original image
                item {
                    originalImageBitmap?.let { bitmap ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Original Image",
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Original grain image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }

                // Statistics - same as Results Overview Screen
                item {
                    statistics?.let { stats ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Grain Size Distribution",
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatisticItem(
                                        label = "D16",
                                        value = "%.2f".format(stats.d16),
                                        unit = scaleCalibration?.unit ?: " "
                                    )
                                    StatisticItem(
                                        label = "D50 (Median)",
                                        value = "%.2f".format(stats.d50),
                                        unit = scaleCalibration?.unit ?: " "
                                    )
                                    StatisticItem(
                                        label = "D84",
                                        value = "%.2f".format(stats.d84),
                                        unit = scaleCalibration?.unit ?: " "
                                    )
                                }
                            }
                        }
                    }
                }

                // Histogram
                item {
                    histogramBitmap?.let { bitmap ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Grain Size Distribution",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Grain size histogram",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(400.dp),
                                    contentScale = ContentScale.Fit
                                )

                                // Legend
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    LegendItem(color = androidx.compose.ui.graphics.Color(0, 102, 255), label = "Major axis lengths")
                                    LegendItem(color = androidx.compose.ui.graphics.Color(255, 179, 128), label = "Minor axis lengths")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Session") },
            text = {
                Text("Are you sure you want to delete this session? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        session?.let { s ->
                            scope.launch {
                                try {
                                    repository.deleteSession(s)
                                    onNavigateBack()
                                } catch (e: Exception) {
                                    println("❌ Failed to delete session: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatisticItem(
    label: String,
    value: String,
    unit: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}