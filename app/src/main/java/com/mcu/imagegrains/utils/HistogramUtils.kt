package com.mcu.imagegrains.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
    val maxCount: Int
)

data class GrainSizeClass(
    val name: String,
    val minPhi: Double,
    val maxPhi: Double,
    val centerPhi: Double
)

object HistogramUtils {

    private val grainSizeClasses = mapOf(
        "very fine silt" to Pair(7.0, 8.0),
        "fine silt" to Pair(6.0, 7.0),
        "medium silt" to Pair(5.0, 6.0),
        "coarse silt" to Pair(4.0, 5.0),
        "very fine sand" to Pair(3.0, 4.0),
        "fine sand" to Pair(2.0, 3.0),
        "medium sand" to Pair(1.0, 2.0),
        "coarse sand" to Pair(0.0, 1.0),
        "very coarse sand" to Pair(-1.0, 0.0),
        "granule" to Pair(-2.0, -1.0),
        "pebble" to Pair(-6.0, -2.0),
        "cobble" to Pair(-8.0, -6.0),
        "boulder" to Pair(-12.0, -8.0)
    )

    /**
     * Create histogram data from grain measurements
     */
    fun createHistogramData(
        grainData: ScaledGrainData,
        binSize: Double = 0.4,
        xlimits: Pair<Double, Double>? = null,
        convertToMillimeters: Boolean = true
    ): HistogramData {

        // Convert to millimeters if needed
        val conversionFactor = when {
            convertToMillimeters && grainData.scaleCalibration.unit == "cm" -> 10.0
            convertToMillimeters && grainData.scaleCalibration.unit == "inches" -> 25.4
            convertToMillimeters && grainData.scaleCalibration.unit == "meters" -> 1000.0
            else -> 1.0
        }

        val majorAxisLengths = grainData.scaledGrains.map { it.majorAxisLength * conversionFactor }
        val minorAxisLengths = grainData.scaledGrains.map { it.minorAxisLength * conversionFactor }

        // Convert to phi scale
        val phiMajor = majorAxisLengths.map { -log2(it) }
        val phiMinor = minorAxisLengths.map { -log2(it) }

        // Determine phi limits
        val (phiMin, phiMax) = if (xlimits != null) {
            val phiMaxCalc = ceil(-log2(xlimits.first)).toInt()
            val phiMinCalc = floor(-log2(xlimits.second)).toInt()
            Pair(phiMinCalc.toDouble(), phiMaxCalc.toDouble())
        } else {
            val allPhi = phiMajor + phiMinor
            val phiMaxCalc = ceil(allPhi.maxOrNull() ?: 0.0)
            val phiMinCalc = floor(allPhi.minOrNull() ?: 0.0)
            Pair(phiMinCalc, phiMaxCalc)
        }

        // Create bins
        val numBins = ((phiMax - phiMin) / binSize).toInt()
        val bins = (0..numBins).map { phiMin + it * binSize }

        // Calculate histograms
        val majorCounts = calculateHistogram(phiMajor, bins)
        val minorCounts = calculateHistogram(phiMinor, bins)

        // Find matching grain size classes
        val matchingClasses = findMatchingGrainSizeClasses(phiMin, phiMax)

        val maxCount = maxOf(majorCounts.maxOrNull() ?: 0, minorCounts.maxOrNull() ?: 0)

        return HistogramData(
            bins = bins,
            majorAxisCounts = majorCounts,
            minorAxisCounts = minorCounts,
            phiScale = bins,
            grainSizeClasses = matchingClasses,
            maxCount = maxCount
        )
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

    private fun findMatchingGrainSizeClasses(phiMin: Double, phiMax: Double): List<GrainSizeClass> {
        val matchingClasses = mutableListOf<GrainSizeClass>()

        for ((name, bounds) in grainSizeClasses) {
            val (lowerBound, upperBound) = bounds

            if (lowerBound < phiMax && upperBound > phiMin) {
                matchingClasses.add(
                    GrainSizeClass(
                        name = name,
                        minPhi = lowerBound,
                        maxPhi = upperBound,
                        centerPhi = (lowerBound + upperBound) / 2.0
                    )
                )
            }
        }

        return matchingClasses.sortedBy { it.minPhi }
    }

    /**
     * Create histogram bitmap visualization
     */
    fun createHistogramBitmap(
        histogramData: HistogramData,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(android.graphics.Color.WHITE)

        val padding = 80f
        val plotWidth = width - 2 * padding
        val plotHeight = height - 2 * padding

        // Paints
        val majorAxisPaint = Paint().apply {
            color = Color.Blue.copy(alpha = 0.5f).toArgb()
            style = Paint.Style.FILL
        }

        val minorAxisPaint = Paint().apply {
            color = Color.Red.copy(alpha = 0.5f).toArgb()
            style = Paint.Style.FILL
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

        val classPaint = Paint().apply {
            color = android.graphics.Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        // Draw axes
        canvas.drawLine(padding, height - padding, width - padding, height - padding, axisPaint) // X-axis
        canvas.drawLine(padding, padding, padding, height - padding, axisPaint) // Y-axis

        if (histogramData.bins.size > 1 && histogramData.maxCount > 0) {
            val binWidth = plotWidth / (histogramData.bins.size - 1)
            val phiRange = histogramData.bins.last() - histogramData.bins.first()

            // Draw histogram bars
            for (i in 0 until histogramData.majorAxisCounts.size) {
                val x = padding + i * binWidth
                val majorHeight = (histogramData.majorAxisCounts[i].toFloat() / histogramData.maxCount) * plotHeight
                val minorHeight = (histogramData.minorAxisCounts[i].toFloat() / histogramData.maxCount) * plotHeight

                // Major axis bars
                canvas.drawRect(
                    x,
                    height - padding - majorHeight,
                    x + binWidth * 0.8f,
                    height - padding,
                    majorAxisPaint
                )

                // Minor axis bars
                canvas.drawRect(
                    x,
                    height - padding - minorHeight,
                    x + binWidth * 0.8f,
                    height - padding,
                    minorAxisPaint
                )
            }

            // Draw grain size class boundaries
            for (grainClass in histogramData.grainSizeClasses) {
                val xPos = padding + ((grainClass.minPhi - histogramData.bins.first()) / phiRange) * plotWidth
                canvas.drawLine(xPos.toFloat(), padding, xPos.toFloat(), height - padding, classPaint)

                // Draw class labels (rotated)
                canvas.save()
                canvas.rotate(-90f, xPos.toFloat() + 20f, height - padding - 20f)
                canvas.drawText(grainClass.name, xPos.toFloat() + 20f, height - padding - 20f, textPaint)
                canvas.restore()
            }

            // Draw scale labels
            val numLabels = 6
            for (i in 0..numLabels) {
                val phi = histogramData.bins.first() + i * phiRange / numLabels
                val mm = 2.0.pow(-phi)
                val x = padding + i * plotWidth / numLabels

                canvas.drawText(
                    String.format(locale = Locale.getDefault(),"%.1f", mm),
                    x - 20f,
                    height - padding + 30f,
                    textPaint
                )
            }
        }

        // Draw labels
        canvas.drawText("Grain axis length (mm)", width / 2f - 80f, height - 20f, textPaint)

        canvas.withRotation(-90f, 30f, height / 2f) {
            drawText("Count", 30f, height / 2f, textPaint)
        }

        return bitmap
    }
}