package com.mcu.imagegrains.presentation.multiple_sessions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.mcu.imagegrains.data.local.GrainDatabase
import com.mcu.imagegrains.data.local.GrainSession
import com.mcu.imagegrains.domain.models.GrainStatistics
import com.mcu.imagegrains.domain.repository.GrainRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow


@Composable
fun MultiSessionComparisonScreen(
    sessionIds: List<String>,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { GrainDatabase.getDatabase(context) }
    val repository = remember { GrainRepository(database.grainSessionDao()) }

    var sessions by remember { mutableStateOf<List<GrainSession>>(emptyList()) }
    var comparisonBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(sessionIds) {
        try {
            sessions = repository.getSessions(sessionIds)

            // Create comparison chart
            if (sessions.isNotEmpty()) {
                comparisonBitmap = createMultiSessionComparisonChart(
                    sessions = sessions,
                    repository = repository,
                    width = 800,
                    height = 600
                )
            }
        } catch (e: Exception) {
            println("❌ Failed to load sessions: ${e.message}")
        } finally {
            isLoading = false
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
                text = "Compare Sessions (${sessions.size})",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.width(48.dp)) // Balance the back button
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                // Comparison chart
                item {
                    comparisonBitmap?.let { bitmap ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ECDF Comparison",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Multiple sessions ECDF comparison",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(400.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }

                // Session summaries
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Session Summaries",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            sessions.forEachIndexed { index, session ->
                                val statistics = try {
                                    repository.parseStatistics(session)
                                } catch (_: Exception) {
                                    null
                                }
                                val scaleUnit = repository.parseScaleCalibration(session).unit

                                SessionComparisonRow(
                                    session = session,
                                    statistics = statistics,
                                    colorIndex = index,
                                    scaleUnit = scaleUnit
                                )

                                if (index < sessions.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        thickness = DividerDefaults.Thickness,
                                        color = DividerDefaults.color
                                    )
                                }
                            }
                        }
                    }
                }

                // Statistics comparison table
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Statistics Comparison",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            StatisticsComparisonTable(sessions, repository)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionComparisonRow(
    session: GrainSession,
    scaleUnit: String,
    statistics: GrainStatistics?,
    colorIndex: Int
) {
    val colors = listOf(
        Color.Blue, Color.Red, Color(0xFF00C853), Color(0xFFFF9800),
        Color(0xFF9C27B0), Color(0xFF795548), Color(0xFF607D8B), Color(0xFFE91E63)
    )
    val lineColor = colors[colorIndex % colors.size]

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(lineColor, CircleShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    .format(Date(session.timestamp)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        statistics?.let { stats ->
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${stats.count} grains",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "D50: ${"%.2f".format(stats.d50)} $scaleUnit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatisticsComparisonTable(
    sessions: List<GrainSession>,
    repository: GrainRepository
) {
    // Create table with statistics
    val statistics = sessions.map { session ->
        try {
            repository.parseStatistics(session)
        } catch (_: Exception) {
            null
        }
    }

    Column {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            Text(
                text = "Session",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(2f)
            )
            Text(
                text = "Count",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "D16",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "D50",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "D84",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }

        // Data rows
        sessions.forEachIndexed { index, session ->
            val stats = statistics[index]

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stats?.count?.toString() ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stats?.let { "%.2f".format(it.d16) } ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stats?.let { "%.2f".format(it.d50) } ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stats?.let { "%.2f".format(it.d84) } ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }

            if (index < sessions.size - 1) {
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
        }
    }
}

/**
 * Create overlapping ECDF comparison chart
 */
fun createMultiSessionComparisonChart(
    sessions: List<GrainSession>,
    repository: GrainRepository,
    width: Int,
    height: Int
): Bitmap {
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFFFFFFFF.toInt())

    val pad = 80f
    val plotW = width - 2 * pad
    val plotH = height - 2 * pad

    val colors = listOf(
        0xFF0000FF.toInt(), 0xFFFF0000.toInt(), 0xFF00C853.toInt(), 0xFFFF9800.toInt(),
        0xFF9C27B0.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt(), 0xFFE91E63.toInt()
    )

    val strokeStyles = listOf(
        DashPathEffect(floatArrayOf(10f, 0f), 0f), // Solid
        DashPathEffect(floatArrayOf(10f, 5f), 0f), // Dashed
        DashPathEffect(floatArrayOf(5f, 5f), 0f),  // Dotted
        DashPathEffect(floatArrayOf(15f, 5f, 5f, 5f), 0f) // Dash-dot
    )

    // Parse all histogram data to find common phi range
    val allHistogramData = sessions.mapNotNull { session ->
        try {
            repository.parseHistogramData(session)
        } catch (_: Exception) {
            null
        }
    }

    if (allHistogramData.isEmpty()) return bitmap

    val globalPhiMin = allHistogramData.minOf { it.phiMin }
    val globalPhiMax = allHistogramData.maxOf { it.phiMax }
    val phiSpan = globalPhiMax - globalPhiMin

    // Function to map phi to x coordinate
    fun mapX(phi: Double): Float = pad + ((globalPhiMax - phi) / phiSpan * plotW).toFloat()

    // Draw axes
    val axisPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF000000.toInt()
    }
    canvas.drawLine(pad, pad, pad, height - pad, axisPaint)
    canvas.drawLine(pad, height - pad, width - pad, height - pad, axisPaint)

    // Draw ECDF curves
    sessions.forEachIndexed { index, session ->
        val histogramData = try {
            repository.parseHistogramData(session)
        } catch (_: Exception) {
            return@forEachIndexed
        }

        val color = colors[index % colors.size]
        val strokeStyle = strokeStyles[index % strokeStyles.size]

        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            this.color = color
            pathEffect = strokeStyle
            isAntiAlias = true
        }

        // Draw major axis ECDF
        val majorPath = Path()
        histogramData.majorEcdf.forEachIndexed { i, (phi, ecdf) ->
            val x = mapX(phi)
            val y = height - pad - (ecdf.toFloat() * plotH)
            if (i == 0) majorPath.moveTo(x, y) else majorPath.lineTo(x, y)
        }
        canvas.drawPath(majorPath, paint)

        // Draw minor axis ECDF with thinner line
        val minorPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            this.color = color
            pathEffect = strokeStyle
            isAntiAlias = true
        }

        val minorPath = Path()
        histogramData.minorEcdf.forEachIndexed { i, (phi, ecdf) ->
            val x = mapX(phi)
            val y = height - pad - (ecdf.toFloat() * plotH)
            if (i == 0) minorPath.moveTo(x, y) else minorPath.lineTo(x, y)
        }
        canvas.drawPath(minorPath, minorPaint)
    }

    // Draw labels and ticks
    val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 24f
        color = 0xFF000000.toInt()
    }

    val smallText = Paint().apply {
        isAntiAlias = true
        textSize = 20f
        color = 0xFF000000.toInt()
    }

    // Y-axis labels (cumulative probability)
    for (i in 0..5) {
        val p = i / 5.0f
        val y = height - pad - (p * plotH)
        canvas.drawLine(pad - 10f, y, pad, y, axisPaint)
        canvas.drawText(String.format(Locale.getDefault(),"%.1f", p), pad - 50f, y + 8f, smallText)
    }

    // X-axis labels (phi scale)
    for (i in 0..6) {
        val phi = globalPhiMax - i * (globalPhiMax - globalPhiMin) / 6
        val mm = 2.0.pow(-phi)
        val x = mapX(phi)
        canvas.drawLine(x, height - pad, x, height - pad + 10f, axisPaint)
        val label = if (mm < 10) "%.1f".format(mm) else "%.0f".format(mm)
        canvas.drawText(label, x - smallText.measureText(label)/2, height - pad + 30f, smallText)
    }

    // Axis titles
    canvas.drawText("grain axis length (mm)", width/2f - textPaint.measureText("grain axis length (mm)")/2, height - 10f, textPaint)

    canvas.save()
    canvas.rotate(-90f, 20f, height/2f)
    canvas.drawText("cumulative probability", 20f, height/2f, textPaint)
    canvas.restore()

    return bitmap
}