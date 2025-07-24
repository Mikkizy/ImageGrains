package com.mcu.imagegrains.presentation.scale_calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.mcu.imagegrains.R
import com.mcu.imagegrains.domain.models.ScaleCalibration
import com.mcu.imagegrains.presentation.SharedSegmentationViewModel
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleCalibrationScreen(
    goBack: () -> Unit,
    navigateToInstanceSegmentation: () -> Unit,
    sharedViewModel: SharedSegmentationViewModel,
    modifier: Modifier = Modifier
) {
    // Get safe bitmap copy from ViewModel
    val safeBitmap = remember { sharedViewModel.getSafeBitmapCopy() }

    // Line drawing state
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var endPoint by remember { mutableStateOf<Offset?>(null) }
    var realLength by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("cm") }
    var showUnitMenu by remember { mutableStateOf(false) }

    // Mode selection
    var drawingMode by remember { mutableStateOf(DrawingMode.TWO_POINT) }

    // Zoom state for zoom mode
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Two-point mode state
    var firstPoint by remember { mutableStateOf<Offset?>(null) }
    var hasFirstPoint by remember { mutableStateOf(false) }

    // Zoom mode drawing state
    var isDrawingMode by remember { mutableStateOf(false) }

    val units = listOf("mm", "cm", "inches", "meters")
    val density = LocalDensity.current

    // Calculate line length in pixels
    val pixelLength = remember(startPoint, endPoint) {
        if (startPoint != null && endPoint != null) {
            val dx = endPoint!!.x - startPoint!!.x
            val dy = endPoint!!.y - startPoint!!.y
            sqrt(dx * dx + dy * dy).toDouble()
        } else 0.0
    }

    // Reset drawing when mode changes
    fun resetDrawing() {
        startPoint = null
        endPoint = null
        firstPoint = null
        hasFirstPoint = false
        isDrawingMode = false
    }

    // Reset zoom
    fun resetZoom() {
        zoomLevel = 1.0f
        panOffset = Offset.Zero
        isDrawingMode = false
    }

    // Convert screen coordinates to image coordinates for zoom mode
    fun screenToImageCoordinates(screenOffset: Offset, canvasSize: androidx.compose.ui.geometry.Size): Offset {
        if (drawingMode != DrawingMode.ZOOM_DRAW || zoomLevel <= 1.0f) {
            return screenOffset
        }

        // Calculate the image position accounting for zoom and pan
        val centerX = canvasSize.width / 2
        val centerY = canvasSize.height / 2

        // Reverse the transformation applied during drawing
        val imageX = (screenOffset.x - centerX - panOffset.x) / zoomLevel + centerX
        val imageY = (screenOffset.y - centerY - panOffset.y) / zoomLevel + centerY

        return Offset(imageX, imageY)
    }

    // Convert image coordinates back to screen coordinates for drawing
    fun imageToScreenCoordinates(imageOffset: Offset, canvasSize: androidx.compose.ui.geometry.Size): Offset {
        if (drawingMode != DrawingMode.ZOOM_DRAW || zoomLevel <= 1.0f) {
            return imageOffset
        }

        val centerX = canvasSize.width / 2
        val centerY = canvasSize.height / 2

        val screenX = (imageOffset.x - centerX) * zoomLevel + centerX + panOffset.x
        val screenY = (imageOffset.y - centerY) * zoomLevel + centerY + panOffset.y

        return Offset(screenX, screenY)
    }

    // Cleanup safe bitmap when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            safeBitmap?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                    println("🔄 Scale calibration bitmap cleaned up")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    "Scale Calibration",
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
                IconButton(
                    onClick = {
                        val realValue = realLength.toDoubleOrNull()
                        if (realValue != null && realValue > 0 && pixelLength > 0) {
                            val calibration = ScaleCalibration(
                                pixelLength = pixelLength,
                                realLength = realValue,
                                unit = selectedUnit
                            )
                            sharedViewModel.setScaleCalibration(calibration)
                            navigateToInstanceSegmentation()
                        }
                    },
                    enabled = realLength.toDoubleOrNull() != null && pixelLength > 0
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Confirm")
                }
            }
        )

        // Mode Selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Drawing Mode",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        modifier = Modifier.weight(0.3f),
                        selected = drawingMode == DrawingMode.TWO_POINT,
                        onClick = {
                            drawingMode = DrawingMode.TWO_POINT
                            resetDrawing()
                            resetZoom()
                        },
                        label = { Text("📍 Two Points") }
                    )

                    FilterChip(
                        modifier = Modifier.weight(0.3f),
                        selected = drawingMode == DrawingMode.DRAG_LINE,
                        onClick = {
                            drawingMode = DrawingMode.DRAG_LINE
                            resetDrawing()
                            resetZoom()
                        },
                        label = { Text("✏️ Draw Line") }
                    )

                    FilterChip(
                        modifier = Modifier.weight(0.3f),
                        selected = drawingMode == DrawingMode.ZOOM_DRAW,
                        onClick = {
                            drawingMode = DrawingMode.ZOOM_DRAW
                            resetDrawing()
                        },
                        label = { Text("🔍 Zoom & Draw") }
                    )
                }
            }
        }

        // Instructions with fixed height
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp) // Fixed height to prevent resizing
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = when (drawingMode) {
                        DrawingMode.TWO_POINT -> "Two-Point Mode"
                        DrawingMode.DRAG_LINE -> "Drag Line Mode"
                        DrawingMode.ZOOM_DRAW -> if (isDrawingMode) "Drawing Mode (Zoom Locked)" else "Zoom & Draw Mode"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable content area for instructions
                Column(
                    modifier = Modifier.weight(1f) // Takes remaining space
                ) {
                    Text(
                        text = when (drawingMode) {
                            DrawingMode.TWO_POINT ->
                                "1. Tap first edge of scale bar\n" +
                                        "2. Tap second edge of scale bar\n" +
                                        "3. A straight line will connect the points"
                            DrawingMode.DRAG_LINE ->
                                "1. Drag across the scale bar\n" +
                                        "2. Line follows your finger movement\n" +
                                        "3. Release to finish drawing"
                            DrawingMode.ZOOM_DRAW ->
                                if (isDrawingMode) {
                                    "✏️ Now drag to draw the line on the zoomed image\n" +
                                            "Tap 'Zoom Mode' to adjust zoom/pan again"
                                } else {
                                    "1. Pinch to zoom into scale bar\n" +
                                            "2. Pan to position the scale bar\n" +
                                            "3. Tap 'Draw Mode' to draw precise line"
                                }
                        } + "\n\nEnter real length and confirm ✓",
                        fontSize = 13.sp, // Slightly smaller to fit better
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 16.sp // Tighter line spacing
                    )
                }

                // Status section at bottom - always visible
                Column {
                    if (drawingMode == DrawingMode.TWO_POINT && hasFirstPoint) {
                        Text(
                            text = "✅ First point set. Tap second point.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (pixelLength > 0) {
                        Text(
                            text = "📏 Line: ${String.format(Locale.getDefault(),"%.1f", pixelLength)}px",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Image with line drawing
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (safeBitmap != null && !safeBitmap.isRecycled) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(drawingMode, isDrawingMode, zoomLevel) {
                                when (drawingMode) {
                                    DrawingMode.TWO_POINT -> {
                                        detectTapGestures { offset ->
                                            if (!hasFirstPoint) {
                                                firstPoint = offset
                                                startPoint = offset
                                                hasFirstPoint = true
                                            } else {
                                                endPoint = offset
                                            }
                                        }
                                    }
                                    DrawingMode.DRAG_LINE -> {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                startPoint = offset
                                                endPoint = offset
                                                hasFirstPoint = false
                                            },
                                            onDrag = { _, dragAmount ->
                                                endPoint = endPoint?.plus(dragAmount)
                                            }
                                        )
                                    }
                                    DrawingMode.ZOOM_DRAW -> {
                                        if (isDrawingMode) {
                                            // Only handle drawing when in drawing mode
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    val imageOffset = screenToImageCoordinates(offset, size.toSize())
                                                    startPoint = imageOffset
                                                    endPoint = imageOffset
                                                    hasFirstPoint = false
                                                },
                                                onDrag = { _, dragAmount ->
                                                    // Apply drag in image coordinates
                                                    val scaledDrag = dragAmount / zoomLevel
                                                    endPoint = endPoint?.plus(scaledDrag)
                                                }
                                            )
                                        } else {
                                            // Handle zoom and pan when not in drawing mode
                                            detectTransformGestures(
                                                onGesture = { centroid, pan, zoom, _ ->
                                                    // Handle zoom
                                                    val newZoom = (zoomLevel * zoom).coerceIn(1.0f, 5.0f)
                                                    if (newZoom != zoomLevel) {
                                                        zoomLevel = newZoom
                                                    }

                                                    // Handle pan
                                                    if (zoomLevel > 1.0f) {
                                                        val maxPanX = size.width * (zoomLevel - 1) / 2
                                                        val maxPanY = size.height * (zoomLevel - 1) / 2

                                                        panOffset = Offset(
                                                            (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                                            (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                    ) {
                        // Draw image with zoom and pan
                        try {
                            val imagePaint = Paint().asFrameworkPaint()

                            if (drawingMode == DrawingMode.ZOOM_DRAW && zoomLevel > 1.0f) {
                                // Apply zoom and pan transformations
                                val scaledWidth = size.width * zoomLevel
                                val scaledHeight = size.height * zoomLevel

                                val left = size.center.x - scaledWidth/2 + panOffset.x
                                val top = size.center.y - scaledHeight/2 + panOffset.y

                                // Use android.graphics.RectF for zoomed drawing
                                drawContext.canvas.nativeCanvas.drawBitmap(
                                    safeBitmap,
                                    null,
                                    android.graphics.RectF(
                                        left, top,
                                        left + scaledWidth,
                                        top + scaledHeight
                                    ),
                                    imagePaint
                                )
                            } else {
                                // Normal drawing - use Compose Rect
                                drawContext.canvas.nativeCanvas.drawBitmap(
                                    safeBitmap,
                                    null,
                                    androidx.compose.ui.geometry.Rect(
                                        Offset.Zero,
                                        Offset(size.width, size.height)
                                    ).toAndroidRectF(),
                                    imagePaint
                                )
                            }

                            // Draw line - convert coordinates for zoomed mode
                            if (startPoint != null && endPoint != null) {
                                val displayStartPoint = if (drawingMode == DrawingMode.ZOOM_DRAW && zoomLevel > 1.0f) {
                                    imageToScreenCoordinates(startPoint!!, size)
                                } else {
                                    startPoint!!
                                }

                                val displayEndPoint = if (drawingMode == DrawingMode.ZOOM_DRAW && zoomLevel > 1.0f) {
                                    imageToScreenCoordinates(endPoint!!, size)
                                } else {
                                    endPoint!!
                                }

                                drawLine(
                                    color = Color.Red,
                                    start = displayStartPoint,
                                    end = displayEndPoint,
                                    strokeWidth = 4.dp.toPx(),
                                    cap = StrokeCap.Round
                                )

                                // Draw start and end circles
                                val circleRadius = 8.dp.toPx()
                                drawCircle(
                                    color = Color.Red,
                                    radius = circleRadius,
                                    center = displayStartPoint
                                )
                                drawCircle(
                                    color = Color.Red,
                                    radius = circleRadius,
                                    center = displayEndPoint
                                )

                                // Draw length text
                                if (pixelLength > 0) {
                                    val midPoint = Offset(
                                        (displayStartPoint.x + displayEndPoint.x) / 2,
                                        (displayStartPoint.y + displayEndPoint.y) / 2
                                    )

                                    val textPaint = android.graphics.Paint().apply {
                                        color = Color.White.toArgb()
                                        textSize = 14.sp.toPx()
                                        isAntiAlias = true
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    }

                                    val backgroundPaint = android.graphics.Paint().apply {
                                        color = Color.Black.copy(alpha = 0.7f).toArgb()
                                    }

                                    val text = "${String.format(Locale.getDefault(), "%.0f", pixelLength)}px"
                                    val textBounds = android.graphics.Rect()
                                    textPaint.getTextBounds(text, 0, text.length, textBounds)

                                    // Draw background
                                    drawContext.canvas.nativeCanvas.drawRect(
                                        midPoint.x - textBounds.width()/2 - 6.dp.toPx(),
                                        midPoint.y - textBounds.height() - 4.dp.toPx(),
                                        midPoint.x + textBounds.width()/2 + 6.dp.toPx(),
                                        midPoint.y + 4.dp.toPx(),
                                        backgroundPaint
                                    )

                                    // Draw text
                                    drawContext.canvas.nativeCanvas.drawText(
                                        text,
                                        midPoint.x,
                                        midPoint.y,
                                        textPaint
                                    )
                                }
                            }

                            // Draw first point indicator for two-point mode
                            if (drawingMode == DrawingMode.TWO_POINT && firstPoint != null && hasFirstPoint) {
                                drawCircle(
                                    color = Color.Blue,
                                    radius = 10.dp.toPx(),
                                    center = firstPoint!!
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 5.dp.toPx(),
                                    center = firstPoint!!
                                )
                            }

                        } catch (e: Exception) {
                            println("❌ Error drawing bitmap: ${e.message}")
                            // Draw error placeholder
                            drawRect(
                                color = Color.LightGray,
                                size = size
                            )
                        }
                    }

                    // Zoom controls for zoom mode
                    if (drawingMode == DrawingMode.ZOOM_DRAW) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    zoomLevel = min(5.0f, zoomLevel * 1.2f)
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom In")
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${String.format("%.1f", zoomLevel)}x",
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            SmallFloatingActionButton(
                                onClick = {
                                    zoomLevel = max(1.0f, zoomLevel / 1.2f)
                                    if (zoomLevel == 1.0f) panOffset = Offset.Zero
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_remove), contentDescription = "Zoom Out")
                            }

                            // Mode toggle button
                            SmallFloatingActionButton(
                                onClick = {
                                    isDrawingMode = !isDrawingMode
                                },
                                containerColor = if (isDrawingMode)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(
                                    if (isDrawingMode) Icons.Default.Add else Icons.Default.Edit,
                                    contentDescription = if (isDrawingMode) "Zoom Mode" else "Draw Mode"
                                )
                            }

                            if (zoomLevel > 1.0f || startPoint != null) {
                                SmallFloatingActionButton(
                                    onClick = {
                                        resetZoom()
                                        resetDrawing()
                                    },
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                                }
                            }
                        }
                    }

                    // Clear line button for other modes
                    if (drawingMode != DrawingMode.ZOOM_DRAW && (startPoint != null || hasFirstPoint)) {
                        Button(
                            onClick = { resetDrawing() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text("Clear Line")
                        }
                    }

                } else {
                    // Show error if bitmap is not available
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Image not available",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { goBack() }
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            }
        }

        // Input controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = realLength,
                        onValueChange = { realLength = it },
                        label = { Text("Real Length") },
                        placeholder = { Text("e.g., 10.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Unit selector
                    Box {
                        OutlinedButton(
                            onClick = { showUnitMenu = true },
                            modifier = Modifier.width(100.dp)
                        ) {
                            Text(selectedUnit)
                        }

                        DropdownMenu(
                            expanded = showUnitMenu,
                            onDismissRequest = { showUnitMenu = false }
                        ) {
                            units.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit) },
                                    onClick = {
                                        selectedUnit = unit
                                        showUnitMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (pixelLength > 0 && realLength.toDoubleOrNull() != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val unitsPerPixel = realLength.toDouble() / pixelLength
                    Text(
                        text = "Scale: ${String.format(Locale.getDefault(), "%.6f", unitsPerPixel)} $selectedUnit/pixel",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

enum class DrawingMode {
    TWO_POINT,     // Tap two points, draw straight line
    DRAG_LINE,     // Original drag-to-draw behavior
    ZOOM_DRAW      // Zoom in then draw
}

private fun androidx.compose.ui.geometry.Rect.toAndroidRectF(): android.graphics.RectF {
    return android.graphics.RectF(left, top, right, bottom)
}