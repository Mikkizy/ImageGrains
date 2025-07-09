package com.mcu.imagegrains.presentation.result_overview

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mcu.imagegrains.R
import com.mcu.imagegrains.domain.models.GrainStatistics
import com.mcu.imagegrains.domain.models.ScaledGrainData
import com.mcu.imagegrains.presentation.SharedSegmentationViewModel
import com.mcu.imagegrains.utils.CSVExportUtils
import com.mcu.imagegrains.utils.HistogramUtils
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsOverviewScreen(
    goBack: () -> Unit,
    goToHome: () -> Unit,
    sharedViewModel: SharedSegmentationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scaledGrainData by sharedViewModel.scaledGrainData.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var histogramBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Generate histogram when data is available
    LaunchedEffect(scaledGrainData) {
        scaledGrainData?.let { data ->
            val histogramData = HistogramUtils.createHistogramData(
                grainData = data,
                binSize = 0.4,
                xlimits = Pair(2.0, 128.0), // Default limits, can be made configurable
                convertToMillimeters = true
            )

            histogramBitmap = HistogramUtils.createHistogramBitmap(
                histogramData = histogramData,
                width = 800,
                height = 600
            )
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    "Results Overview",
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
                scaledGrainData?.let { data ->
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(painter = painterResource(R.drawable.ic_download), contentDescription = "Export CSV")
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                CSVExportUtils.shareCSVFile(context, data)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share CSV")
                    }
                }
            }
        )

        scaledGrainData?.let { data ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Card
                item {
                    SummaryCard(data)
                }

                // Statistics Card
                item {
                    StatisticsCard(data.statistics)
                }

                // Histogram Card
                item {
                    histogramBitmap?.let { bitmap ->
                        HistogramCard(bitmap)
                    }
                }

                // Scale Information Card
                item {
                    ScaleInformationCard(data)
                }

                // Export Actions Card
                item {
                    ExportActionsCard(
                        data = data,
                        onExportCSV = { showExportDialog = true },
                        onShareCSV = {
                            scope.launch {
                                CSVExportUtils.shareCSVFile(context, data)
                            }
                        }
                    )
                }

                item {
                    Button(
                        onClick = {
                            goToHome()
                        }
                    ) {
                        Text("Go to Home")
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No results available",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Complete the segmentation process first",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            goToHome()
                        }
                    ) {
                        Text("Go to Home")
                    }
                }
            }
        }
    }

    // Export Dialog
    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { fileName ->
                scope.launch {
                    scaledGrainData?.let { data ->
                        CSVExportUtils.exportGrainDataToCSV(context, data, fileName)
                    }
                    showExportDialog = false
                }
            }
        )
    }
}

@Composable
private fun SummaryCard(data: ScaledGrainData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Grain Analysis Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem("Total Grains", "${data.scaledGrains.size}")
                SummaryItem("Scale Unit", data.scaleCalibration.unit)
                SummaryItem("Resolution", "${String.format("%.4f", data.scaleCalibration.unitsPerPixel)} ${data.scaleCalibration.unit}/px")
            }
        }
    }
}

@Composable
private fun StatisticsCard(statistics: GrainStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Statistical Summary",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Area Statistics
            StatisticSection(
                title = "Area",
                statistics = listOf(
                    "Count" to "${statistics.count}",
                    "Mean" to String.format("%.2f", statistics.areaMean),
                    "Std" to String.format("%.2f", statistics.areaStd),
                    "Min" to String.format("%.2f", statistics.areaMin),
                    "25%" to String.format("%.2f", statistics.areaQ25),
                    "50%" to String.format("%.2f", statistics.areaQ50),
                    "75%" to String.format("%.2f", statistics.areaQ75),
                    "Max" to String.format("%.2f", statistics.areaMax)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Major Axis Statistics
            StatisticSection(
                title = "Major Axis Length",
                statistics = listOf(
                    "Mean" to String.format(locale = Locale.getDefault(),"%.2f", statistics.majorAxisMean),
                    "Std" to String.format(locale = Locale.getDefault(),"%.2f", statistics.majorAxisStd),
                    "Min" to String.format(locale = Locale.getDefault(),"%.2f", statistics.majorAxisMin),
                    "Max" to String.format(locale = Locale.getDefault(),"%.2f", statistics.majorAxisMax)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Minor Axis Statistics
            StatisticSection(
                title = "Minor Axis Length",
                statistics = listOf(
                    "Mean" to String.format(locale = Locale.getDefault(),"%.2f", statistics.minorAxisMean),
                    "Std" to String.format(locale = Locale.getDefault(),"%.2f", statistics.minorAxisStd),
                    "Min" to String.format(locale = Locale.getDefault(),"%.2f", statistics.minorAxisMin),
                    "Max" to String.format(locale = Locale.getDefault(),"%.2f", statistics.minorAxisMax)
                )
            )
        }
    }
}

@Composable
private fun StatisticSection(
    title: String,
    statistics: List<Pair<String, String>>
) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(statistics) { (label, value) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(60.dp)
                ) {
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistogramCard(histogramBitmap: android.graphics.Bitmap) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Grain Size Distribution",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Image(
                bitmap = histogramBitmap.asImageBitmap(),
                contentDescription = "Grain size histogram",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Blue: Major axis lengths\n• Orange: Minor axis lengths\n• Gray lines: Grain size class boundaries",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ScaleInformationCard(data: ScaledGrainData) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Scale Calibration",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Real Length",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${data.scaleCalibration.realLength} ${data.scaleCalibration.unit}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column {
                    Text(
                        text = "Pixel Length",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${String.format("%.1f", data.scaleCalibration.pixelLength)} px",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column {
                    Text(
                        text = "Scale Factor",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = String.format(locale = Locale.getDefault(),"%.6f", data.scaleCalibration.unitsPerPixel),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportActionsCard(
    data: ScaledGrainData,
    onExportCSV: () -> Unit,
    onShareCSV: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Export Options",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExportCSV,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_download), contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export CSV")
                }

                Button(
                    onClick = onShareCSV,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share CSV")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "CSV includes all grain measurements with applied scaling (${data.scaledGrains.size} grains)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SummaryItem(
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
private fun ExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export CSV") },
        text = {
            Column {
                Text("Enter a filename for the CSV export:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Filename") },
                    placeholder = { Text("grain_analysis.csv") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(fileName.ifEmpty { null } ?: "grain_analysis.csv") }
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}