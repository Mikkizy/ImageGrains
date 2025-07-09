package com.mcu.imagegrains.presentation.scale_calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mcu.imagegrains.domain.models.ScaleCalibration
import com.mcu.imagegrains.presentation.SharedSegmentationViewModel
import java.util.Locale
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleCalibrationScreen(
    goBack: () -> Unit,
    navigateToInstanceSegmentation: () -> Unit,
    sharedViewModel: SharedSegmentationViewModel,
    modifier: Modifier = Modifier
) {
    val originalBitmap by sharedViewModel.originalBitmap.collectAsState()

    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var endPoint by remember { mutableStateOf<Offset?>(null) }
    var realLength by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("cm") }
    var showUnitMenu by remember { mutableStateOf(false) }

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

    Column(
        modifier = modifier.fillMaxSize()
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

        // Instructions
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
                    text = "Scale Bar Calibration",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "1. Draw a line across the scale bar in your image\n" +
                            "2. Enter the real length of the scale bar\n" +
                            "3. Select the appropriate unit\n" +
                            "4. Tap ✓ to confirm",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                if (pixelLength > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Line length: ${String.format(locale = Locale.getDefault(),"%.1f", pixelLength)} pixels",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

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
                originalBitmap?.let { bitmap ->
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        startPoint = offset
                                        endPoint = offset
                                    },
                                    onDrag = { _, dragAmount ->
                                        endPoint = endPoint?.plus(dragAmount)
                                    },
                                    onDragEnd = { },
                                    onDragCancel = { }
                                )
                            }
                    ) {
                        // Draw image
                        val imagePaint = Paint().asFrameworkPaint()
                        drawContext.canvas.nativeCanvas.drawBitmap(
                            bitmap.asImageBitmap().asAndroidBitmap(),
                            null,
                            androidx.compose.ui.geometry.Rect(
                                Offset.Zero,
                                Offset(size.width, size.height)
                            ).toAndroidRectF(),
                            imagePaint
                        )

                        // Draw line
                        if (startPoint != null && endPoint != null) {
                            drawLine(
                                color = Color.Red,
                                start = startPoint!!,
                                end = endPoint!!,
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Draw start and end circles
                            drawCircle(
                                color = Color.Red,
                                radius = 6.dp.toPx(),
                                center = startPoint!!
                            )
                            drawCircle(
                                color = Color.Red,
                                radius = 6.dp.toPx(),
                                center = endPoint!!
                            )
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
                        text = "Scale: ${String.format(locale = Locale.getDefault(), format ="%.6f", unitsPerPixel)} $selectedUnit/pixel",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.geometry.Rect.toAndroidRectF(): android.graphics.RectF {
    return android.graphics.RectF(left, top, right, bottom)
}