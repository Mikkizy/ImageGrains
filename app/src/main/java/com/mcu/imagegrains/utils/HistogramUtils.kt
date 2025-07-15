package com.mcu.imagegrains.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import com.mcu.imagegrains.domain.models.ScaledGrainData
import java.util.Locale
import kotlin.math.*
import androidx.core.graphics.withRotation

data class HistogramData(
    val bins: List<Double>,
    val majorAxisCounts: List<Int>,
    val minorAxisCounts: List<Int>,
    val phiScale: List<Double>,
    val grainSizeClasses: List<GrainSizeClass>,
    val maxCount: Int,
    val actualXLimits: Pair<Double, Double>,
    val countInterval: Int,
    val majorAxisECDF: List<Pair<Double, Double>>, // Added: (phi, ecdf_value)
    val minorAxisECDF: List<Pair<Double, Double>>  // Added: (phi, ecdf_value)
)

data class GrainSizeClass(
    val name: String,
    val minPhi: Double,
    val maxPhi: Double,
    val centerPhi: Double,
    val color: Int = android.graphics.Color.LTGRAY
)

object HistogramUtils {

    private val grainSizeClasses = mapOf(
        "very fine silt" to Triple(7.0, 8.0, Color.Cyan.copy(alpha = 0.4f).toArgb()),
        "fine silt" to Triple(6.0, 7.0, Color.Blue.copy(alpha = 0.4f).toArgb()),
        "medium silt" to Triple(5.0, 6.0, Color.Green.copy(alpha = 0.4f).toArgb()),
        "coarse silt" to Triple(4.0, 5.0, Color.Yellow.copy(alpha = 0.4f).toArgb()),
        "very fine sand" to Triple(3.0, 4.0, Color(0xFFFFE4B5).toArgb()), // Moccasin
        "fine sand" to Triple(2.0, 3.0, Color(0xFFFFA500).copy(alpha = 0.6f).toArgb()), // Orange
        "medium sand" to Triple(1.0, 2.0, Color(0xFFDDA0DD).copy(alpha = 0.6f).toArgb()), // Plum
        "coarse sand" to Triple(0.0, 1.0, Color(0xFF98FB98).copy(alpha = 0.6f).toArgb()), // Pale green
        "very coarse sand" to Triple(-1.0, 0.0, Color(0xFF87CEEB).copy(alpha = 0.6f).toArgb()), // Sky blue
        "granule" to Triple(-2.0, -1.0, Color(0xFFFFA500).copy(alpha = 0.6f).toArgb()), // Orange
        "pebble" to Triple(-6.0, -2.0, Color(0xFF808080).copy(alpha = 0.6f).toArgb()), // Gray
        "cobble" to Triple(-8.0, -6.0, Color(0xFF696969).copy(alpha = 0.6f).toArgb()), // Dim gray
        "boulder" to Triple(-12.0, -8.0, Color(0xFF2F4F4F).copy(alpha = 0.6f).toArgb()) // Dark slate gray
    )

    /**
     * Calculate Empirical Cumulative Distribution Function (ECDF)
     */
    /**
     * Calculate ECDF with detailed debugging
     */
    private fun calculateECDF(values: List<Double>, axisName: String): List<Pair<Double, Double>> {
        val sortedValues = values.sorted()
        val ecdf = mutableListOf<Pair<Double, Double>>()

        println("🔍 Calculating ECDF for $axisName:")
        println("   Raw values (first 10): ${values.take(10).map { "%.2f".format(it) }}")
        println("   Sorted values (first 10): ${sortedValues.take(10).map { "%.2f".format(it) }}")
        println("   Sorted values (last 10): ${sortedValues.takeLast(10).map { "%.2f".format(it) }}")
        println("   Count: ${sortedValues.size}")
        println("   Min: ${"%.2f".format(sortedValues.first())} mm")
        println("   Max: ${"%.2f".format(sortedValues.last())} mm")
        println("   Median: ${"%.2f".format(sortedValues[sortedValues.size/2])} mm")

        for (i in sortedValues.indices) {
            val sizeMM = sortedValues[i]
            val phi = -log2(sizeMM)
            val cumulativeProb = (i + 1).toDouble() / sortedValues.size
            ecdf.add(Pair(phi, cumulativeProb))

            // Debug key points
            if (i == 0 || i == sortedValues.size/2 || i == sortedValues.size-1) {
                println("   Point ${i+1}: size=${"%.2f".format(sizeMM)}mm, phi=${"%.2f".format(phi)}, ECDF=${"%.3f".format(cumulativeProb)}")
            }
        }

        return ecdf
    }

    /**
     * Apply area weighting to grain sizes (like Python function)
     */
    private fun getAreaWeightedDistribution(grainSizes: List<Double>, areas: List<Double>): List<Double> {
        if (areas.isEmpty()) return grainSizes

        // 🎯 SAFETY CHECK: Ensure lists have same size
        if (grainSizes.size != areas.size) {
            println("⚠️ Warning: grainSizes.size=${grainSizes.size} != areas.size=${areas.size}")
            println("   Using minimum size to avoid crash")
            val minSize = minOf(grainSizes.size, areas.size)
            return getAreaWeightedDistribution(grainSizes.take(minSize), areas.take(minSize))
        }

        val meanArea = areas.average()
        val areaWeightedGrainSizes = mutableListOf<Double>()

        println("🔍 Area weighting debug:")
        println("   Mean area: ${"%.2f".format(meanArea)}")
        println("   Processing ${grainSizes.size} grains")

        // 🎯 SAFE ITERATION: Use indices instead of separate loops
        for (i in grainSizes.indices) {
            try {
                val grainSize = grainSizes[i]
                val area = areas[i]
                val weight = maxOf(1, (area / (0.5 * meanArea)).toInt()) // Ensure at least 1

                // Debug extreme weights
                if (weight > 100) {
                    println("   ⚠️ Large weight: grain $i, area=${"%.2f".format(area)}, weight=$weight")
                }

                repeat(weight) {
                    areaWeightedGrainSizes.add(grainSize)
                }

            } catch (e: Exception) {
                println("   ❌ Error at index $i: ${e.message}")
                // Add the grain at least once to avoid losing data
                areaWeightedGrainSizes.add(grainSizes[i])
            }
        }

        println("   Original count: ${grainSizes.size}, Weighted count: ${areaWeightedGrainSizes.size}")
        return areaWeightedGrainSizes
    }

    /**
     * Create histogram data with ECDF curves like Python version
     */
    fun createHistogramData(
        grainData: ScaledGrainData,
        binSize: Double = 0.1, // Smaller bin size like Python (0.1)
        xlimits: Pair<Double, Double>? = null,
        convertToMillimeters: Boolean = true,
        useDataBasedLimits: Boolean = true,
        useAreaWeighting: Boolean = false // New: apply area weighting
    ): HistogramData {

        println("🔍 Scale calibration unit: ${grainData.scaleCalibration.unit}")

        val conversionFactor = when {
            convertToMillimeters && grainData.scaleCalibration.unit == "cm" -> 10.0
            convertToMillimeters && grainData.scaleCalibration.unit == "inches" -> 25.4
            convertToMillimeters && grainData.scaleCalibration.unit == "meters" -> 1000.0
            convertToMillimeters && grainData.scaleCalibration.unit == "mm" -> 1.0
            else -> 1.0
        }

        var majorAxisLengths = grainData.scaledGrains.map { it.majorAxisLength * conversionFactor }
        var minorAxisLengths = grainData.scaledGrains.map { it.minorAxisLength * conversionFactor }

        println("🔍 Before area weighting:")
        println("   Major axis count: ${majorAxisLengths.size}")
        println("   Minor axis count: ${minorAxisLengths.size}")
        println("   Grain data count: ${grainData.scaledGrains.size}")

        // 🎯 FIXED: Apply area weighting with proper error handling
        if (useAreaWeighting) {
            try {
                // Convert areas to mm² using squared conversion factor
                val areas = grainData.scaledGrains.map { it.area * conversionFactor * conversionFactor }

                println("🔍 Area weighting debug:")
                println("   Areas count: ${areas.size}")
                println("   Major lengths count: ${majorAxisLengths.size}")
                println("   Minor lengths count: ${minorAxisLengths.size}")

                // Ensure all lists have the same size
                val minCount = minOf(majorAxisLengths.size, minorAxisLengths.size, areas.size)
                if (minCount < majorAxisLengths.size) {
                    println("   ⚠️ Trimming to minimum count: $minCount")
                    majorAxisLengths = majorAxisLengths.take(minCount)
                    minorAxisLengths = minorAxisLengths.take(minCount)
                }

                majorAxisLengths = getAreaWeightedDistribution(majorAxisLengths, areas.take(minCount))
                minorAxisLengths = getAreaWeightedDistribution(minorAxisLengths, areas.take(minCount))

                println("🔍 After area weighting:")
                println("   Major axis count: ${majorAxisLengths.size}")
                println("   Minor axis count: ${minorAxisLengths.size}")

            } catch (e: Exception) {
                println("❌ Area weighting failed: ${e.message}")
                println("   Continuing without area weighting...")
                // Continue without area weighting if it fails
            }
        }

        // Calculate ECDF
        val majorAxisECDF = calculateECDFFromMM(majorAxisLengths, "Major Axis")
        val minorAxisECDF = calculateECDFFromMM(minorAxisLengths, "Minor Axis")

        // Test specific size points
        val testSizes = listOf(2.0, 3.0, 4.0, 5.0)
        println("🔍 ECDF comparison at test points:")
        for (testSize in testSizes) {
            val majorECDFAtSize = majorAxisLengths.count { it <= testSize }.toDouble() / majorAxisLengths.size
            val minorECDFAtSize = minorAxisLengths.count { it <= testSize }.toDouble() / minorAxisLengths.size

            println("   At ${testSize}mm: Major ECDF=${"%.3f".format(majorECDFAtSize)}, Minor ECDF=${"%.3f".format(minorECDFAtSize)}")

            if (minorECDFAtSize > majorECDFAtSize) {
                println("     ✅ Correct: Minor ECDF > Major ECDF (orange should be above blue)")
            } else {
                println("     ❌ Wrong: Major ECDF > Minor ECDF")
            }
        }

        // 🎯 AREA WEIGHTING (like Python)
        if (useAreaWeighting) {
            val areas = grainData.scaledGrains.map { it.area * conversionFactor * conversionFactor }
            majorAxisLengths = getAreaWeightedDistribution(majorAxisLengths, areas)
            minorAxisLengths = getAreaWeightedDistribution(minorAxisLengths, areas)
        }

        // Smart x-axis limit
        val actualXLimits = when {
            xlimits != null -> xlimits
            useDataBasedLimits -> {
                val maxMajorAxis = grainData.statistics.majorAxisMax * conversionFactor
                val minMajorAxis = grainData.statistics.majorAxisMin * conversionFactor

                val dataRange = maxMajorAxis - minMajorAxis
                val padding = dataRange * 0.1

                Pair(maxOf(0.1, minMajorAxis - padding), maxMajorAxis + padding)
            }
            else -> {
                val allLengths = majorAxisLengths + minorAxisLengths
                Pair(allLengths.minOrNull() ?: 0.1, allLengths.maxOrNull() ?: 10.0)
            }
        }

        // Convert to phi scale
        val phiMajor = majorAxisLengths.map { -log2(it) }
        val phiMinor = minorAxisLengths.map { -log2(it) }

        val phiMax = ceil(-log2(actualXLimits.first)).toInt().toDouble()
        val phiMin = floor(-log2(actualXLimits.second)).toInt().toDouble()

        val bins = mutableListOf<Double>()
        var currentBin = phiMin
        while (currentBin < phiMax) {
            bins.add(currentBin)
            currentBin += binSize
        }
        bins.add(phiMax)

        val majorCounts = calculateHistogram(phiMajor, bins)
        val minorCounts = calculateHistogram(phiMinor, bins)
        val matchingClasses = findMatchingGrainSizeClasses(phiMin, phiMax)
        val maxCount = maxOf(majorCounts.maxOrNull() ?: 0, minorCounts.maxOrNull() ?: 0)
        val countInterval = calculateOptimalCountInterval(maxCount)

        return HistogramData(
            bins = bins,
            majorAxisCounts = majorCounts,
            minorAxisCounts = minorCounts,
            phiScale = bins,
            grainSizeClasses = matchingClasses,
            maxCount = maxCount,
            actualXLimits = actualXLimits,
            countInterval = countInterval,
            majorAxisECDF = majorAxisECDF,
            minorAxisECDF = minorAxisECDF
        )
    }

    private fun calculateOptimalCountInterval(maxCount: Int): Int {
        return when {
            maxCount <= 5 -> 1
            maxCount <= 10 -> 2
            maxCount <= 25 -> 5
            maxCount <= 50 -> 10
            maxCount <= 100 -> 20
            else -> ((maxCount / 10) / 5 + 1) * 5
        }
    }

    private fun calculateHistogram(values: List<Double>, bins: List<Double>): List<Int> {
        val counts = MutableList(bins.size - 1) { 0 }

        for (value in values) {
            for (i in 0 until bins.size - 1) {
                if (value >= bins[i] && value < bins[i + 1]) {
                    counts[i]++
                    break
                }
            }
        }

        return counts
    }

    /**
     * Calculate ECDF from millimeter values and convert to phi for plotting
     */
    private fun calculateECDFFromMM(values: List<Double>, axisName: String): List<Pair<Double, Double>> {
        val phiValues = values.map { -log2(it) }
        val sortedPhiValues = phiValues.sorted() // Sort phi ascending (small grains to large grains)

        val ecdf = mutableListOf<Pair<Double, Double>>()

        println("🔍 Calculating Standard ECDF for $axisName:")

        // Create standard ECDF (0 to 1) - NO REVERSAL
        for (i in sortedPhiValues.indices) {
            val phi = sortedPhiValues[i]
            val cumulativeProb = (i + 1).toDouble() / sortedPhiValues.size
            ecdf.add(Pair(phi, cumulativeProb))

            if (i < 3 || i == sortedPhiValues.size - 1) {
                val originalSize = 2.0.pow(-phi)
                println("   Point ${i+1}: phi=${"%.2f".format(phi)} (${"%.1f".format(originalSize)}mm) → ECDF=${"%.3f".format(cumulativeProb)}")
            }
        }

        return ecdf
    }

    private fun findMatchingGrainSizeClasses(phiMin: Double, phiMax: Double): List<GrainSizeClass> {
        val matchingClasses = mutableListOf<GrainSizeClass>()

        for ((name, bounds) in grainSizeClasses) {
            val (lowerBound, upperBound, color) = bounds

            if (lowerBound < phiMax && upperBound > phiMin) {
                matchingClasses.add(
                    GrainSizeClass(
                        name = name,
                        minPhi = lowerBound,
                        maxPhi = upperBound,
                        centerPhi = (lowerBound + upperBound) / 2.0,
                        color = color
                    )
                )
            }
        }

        return matchingClasses.sortedBy { it.minPhi }
    }

    /**
     * Create histogram with ECDF curves exactly like Python version
     */
    fun createHistogramBitmap(
        histogramData: HistogramData,
        width: Int,
        height: Int,
        showGrainClassification: Boolean = true,
        showECDFCurves: Boolean = true
    ): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(android.graphics.Color.WHITE)

        val padding = 120f
        val plotWidth = width - 2 * padding
        val plotHeight = height - 2 * padding

        // 🎨 PAINT DEFINITIONS
        val majorAxisPaint = Paint().apply {
            color = Color.Blue.copy(alpha = 0.7f).toArgb() // Tab:blue like Python
            style = Paint.Style.FILL
        }

        val minorAxisPaint = Paint().apply {
            color = Color(0xFF1f77b4).copy(alpha = 0.7f).toArgb() // Matplotlib default blue (overlapping)
            style = Paint.Style.FILL
        }

        val majorECDFPaint = Paint().apply {
            color = Color.Blue.toArgb() // tab:blue
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        val minorECDFPaint = Paint().apply {
            color = Color(0xFFFF7F0E).toArgb() // tab:orange
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        val axisPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }

        val smallTextPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
            isAntiAlias = true
        }

        val boundaryPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        // 🎨 DRAW GRAIN SIZE CLASS BACKGROUNDS (like Python)
        if (showGrainClassification) {
            val phiRange = histogramData.bins.last() - histogramData.bins.first()

            for (grainClass in histogramData.grainSizeClasses) {
                val classStartX = padding + ((grainClass.minPhi - histogramData.bins.first()) / phiRange) * plotWidth
                val classEndX = padding + ((grainClass.maxPhi - histogramData.bins.first()) / phiRange) * plotWidth

                val classPaint = Paint().apply {
                    color = grainClass.color
                    style = Paint.Style.FILL
                }

                canvas.drawRect(
                    classStartX.toFloat(),
                    padding,
                    classEndX.toFloat(),
                    height - padding,
                    classPaint
                )

                // Class boundary lines
                canvas.drawLine(classStartX.toFloat(), padding, classStartX.toFloat(), height - padding, boundaryPaint)

                // Class labels (rotated)
                val labelY = height - padding + 60f
                canvas.save()
                canvas.rotate(-90f, (classStartX + classEndX).toFloat() / 2, labelY)
                canvas.drawText(
                    grainClass.name,
                    (classStartX + classEndX).toFloat() / 2 - textPaint.measureText(grainClass.name) / 2,
                    labelY,
                    smallTextPaint
                )
                canvas.restore()
            }
        }

        // Draw main axes
        canvas.drawLine(padding, height - padding, width - padding, height - padding, axisPaint) // X-axis
        canvas.drawLine(padding, padding, padding, height - padding, axisPaint) // Y-axis

        if (histogramData.bins.size > 1 && histogramData.maxCount > 0) {
            val phiRange = histogramData.bins.last() - histogramData.bins.first()

            // 📊 DRAW HISTOGRAM BARS (overlapping like Python)
            for (i in 0 until histogramData.majorAxisCounts.size) {
                val phiBinStart = histogramData.bins[i]
                val phiBinEnd = histogramData.bins[i + 1]

                val xStart = padding + ((phiBinStart - histogramData.bins.first()) / phiRange) * plotWidth
                val xEnd = padding + ((phiBinEnd - histogramData.bins.first()) / phiRange) * plotWidth

                val majorHeight = (histogramData.majorAxisCounts[i].toFloat() / histogramData.maxCount) * plotHeight
                val minorHeight = (histogramData.minorAxisCounts[i].toFloat() / histogramData.maxCount) * plotHeight

                // Major axis bars (blue, behind)
                canvas.drawRect(
                    xStart.toFloat(),
                    height - padding - majorHeight,
                    xEnd.toFloat(),
                    height - padding,
                    majorAxisPaint
                )

                // Minor axis bars (overlapping, in front)
                canvas.drawRect(
                    xStart.toFloat(),
                    height - padding - minorHeight,
                    xEnd.toFloat(),
                    height - padding,
                    minorAxisPaint
                )
            }

            // 📈 DRAW ECDF CURVES
            if (showECDFCurves && histogramData.majorAxisECDF.isNotEmpty()) {

                // Major axis ECDF (blue line)
                val majorPath = Path()
                var firstPoint = true
                for ((phi, ecdf) in histogramData.majorAxisECDF) {
                    val x = padding + ((phi - histogramData.bins.first()) / phiRange) * plotWidth
                    val y = height - padding - (ecdf * plotHeight) // 🎯 FIXED

                    if (firstPoint) {
                        majorPath.moveTo(x.toFloat(), y.toFloat())
                        firstPoint = false
                    } else {
                        majorPath.lineTo(x.toFloat(), y.toFloat())
                    }
                }
                canvas.drawPath(majorPath, majorECDFPaint)

                // Minor axis ECDF (orange line)
                val minorPath = Path()
                firstPoint = true
                for ((phi, ecdf) in histogramData.minorAxisECDF) {
                    val x = padding + ((phi - histogramData.bins.first()) / phiRange) * plotWidth
                    val y = height - padding - (ecdf * plotHeight) // 🎯 FIXED

                    if (firstPoint) {
                        minorPath.moveTo(x.toFloat(), y.toFloat())
                        firstPoint = false
                    } else {
                        minorPath.lineTo(x.toFloat(), y.toFloat())
                    }
                }
                canvas.drawPath(minorPath, minorECDFPaint)
            }

            // 📏 X-AXIS LABELS (grain axis length in mm)
            val (minSize, maxSize) = histogramData.actualXLimits
            val sizeRange = maxSize - minSize

            val numXLabels = 8
            for (i in 0..numXLabels) {
                val size = minSize + i * sizeRange / numXLabels
                val x = padding + i * plotWidth / numXLabels

                val label = when {
                    size < 1.0 -> String.format(Locale.getDefault(), "%.1f", size)
                    size < 10.0 -> String.format(Locale.getDefault(), "%.0f", size)
                    else -> String.format(Locale.getDefault(), "%.0f", size)
                }

                canvas.drawText(
                    label,
                    x - textPaint.measureText(label) / 2,
                    height - padding + 30f,
                    smallTextPaint
                )
            }

            // 📏 Y-AXIS LABELS (count - left side)
            val yAxisSteps = (histogramData.maxCount / histogramData.countInterval) + 1
            for (i in 0..yAxisSteps) {
                val count = i * histogramData.countInterval
                if (count <= histogramData.maxCount) {
                    val y = height - padding - (count.toFloat() / histogramData.maxCount) * plotHeight

                    canvas.drawText(
                        count.toString(),
                        padding - 60f,
                        y + 8f,
                        smallTextPaint
                    )
                }
            }

            // 📏 Y-AXIS LABELS (ECDF - right side)
            if (showECDFCurves) {
                for (i in 0..5) { // 0.0, 0.2, 0.4, 0.6, 0.8, 1.0
                    val ecdfValue = i * 0.2
                    val y = padding + (1.0 - ecdfValue) * plotHeight

                    val label = String.format(Locale.getDefault(), "%.1f", ecdfValue)
                    canvas.drawText(
                        label,
                        width - padding + 20f,
                        y.toFloat() + 8f,
                        smallTextPaint
                    )
                }
            }
        }

        // 🏷️ AXIS LABELS
        val xLabelText = "grain axis length (mm)"
        canvas.drawText(xLabelText, width / 2f - textPaint.measureText(xLabelText) / 2, height - 20f, textPaint)

        canvas.withRotation(-90f, 30f, height / 2f) {
            drawText("count", 30f, height / 2f, textPaint)
        }

        if (showECDFCurves) {
            canvas.withRotation(90f, width - 30f, height / 2f) {
                drawText("cumulative probability", width - 30f, height / 2f, textPaint)
            }
        }

        return bitmap
    }

    // Extension function for number formatting
    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}