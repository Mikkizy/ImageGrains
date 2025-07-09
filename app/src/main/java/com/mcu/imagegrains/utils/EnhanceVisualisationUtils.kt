package com.mcu.imagegrains.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.locationtech.jts.geom.Polygon
import kotlin.math.*
import kotlin.random.Random
import androidx.core.graphics.createBitmap

object EnhancedVisualizationUtils {

    /**
     * Create visualization with colorful grains (equivalent to plot_image_w_colorful_grains)
     */
    fun createColorfulGrainsVisualization(
        originalBitmap: Bitmap,
        allGrains: List<Polygon>,
        colorPalette: List<Color> = generateColorPalette(100),
        plotImage: Boolean = true,
        imageAlpha: Float = 1.0f
    ): Bitmap {

        val width = originalBitmap.width
        val height = originalBitmap.height
        val resultBitmap = createBitmap(width, height)
        val canvas = Canvas(resultBitmap)

        // Draw original image if requested
        if (plotImage) {
            val imagePaint = Paint().apply {
                alpha = (imageAlpha * 255).toInt()
            }
            canvas.drawBitmap(originalBitmap, 0f, 0f, imagePaint)
        }

        // Draw each grain with random colors
        allGrains.forEachIndexed { index, grain ->
            val color = colorPalette[index % colorPalette.size]

            // Fill grain
            val fillPaint = Paint().apply {
                this.color = color.copy(alpha = 0.5f).toArgb()
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            // Outline grain
            val outlinePaint = Paint().apply {
                this.color = Color.Black.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }

            // Convert JTS polygon to Android Path
            val path = convertPolygonToPath(grain)
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, outlinePaint)
        }

        return resultBitmap
    }

    /**
     * Create visualization with grain axes and centroids
     */
    fun createGrainAxesAndCentroidsVisualization(
        baseBitmap: Bitmap,
        allGrains: List<Polygon>,
        labels: Array<IntArray>,
        lineWidth: Float = 1f,
        markerSize: Float = 10f
    ): Bitmap {

        val resultBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // Calculate region properties
        val grainProperties = ImageAnalysisUtils.calculateRegionProperties(labels)

        // Paint for axes
        val axisPaint = Paint().apply {
            color = Color.Black.toArgb()
            strokeWidth = lineWidth
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // Paint for centroids
        val centroidPaint = Paint().apply {
            color = Color.Black.toArgb()
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw axes and centroids for each grain
        grainProperties.forEachIndexed { index, grain ->
            if (index < allGrains.size) {
                val x0 = grain.centroidX.toFloat()
                val y0 = grain.centroidY.toFloat()
                val orientation = grain.orientation.toFloat()

                // Calculate axis endpoints
                val minorAxisHalf = (grain.minorAxisLength * 0.5).toFloat()
                val majorAxisHalf = (grain.majorAxisLength * 0.5).toFloat()

                // Minor axis
                val x1 = x0 + cos(orientation) * minorAxisHalf
                val y1 = y0 - sin(orientation) * minorAxisHalf

                // Major axis
                val x2 = x0 - sin(orientation) * majorAxisHalf
                val y2 = y0 - cos(orientation) * majorAxisHalf

                // Draw axes
                canvas.drawLine(x0, y0, x1, y1, axisPaint)
                canvas.drawLine(x0, y0, x2, y2, axisPaint)

                // Draw centroid
                canvas.drawCircle(x0, y0, markerSize / 2, centroidPaint)
            }
        }

        return resultBitmap
    }

    /**
     * Create complete grain analysis visualization
     */
    fun createCompleteGrainVisualization(
        originalBitmap: Bitmap,
        allGrains: List<Polygon>,
        labels: Array<IntArray>,
        maskAll: Array<IntArray>
    ): Bitmap {

        // Step 1: Create colorful grains visualization
        val colorfulBitmap = createColorfulGrainsVisualization(
            originalBitmap = originalBitmap,
            allGrains = allGrains,
            plotImage = true,
            imageAlpha = 1.0f
        )

        // Step 2: Add axes and centroids
        val finalBitmap = createGrainAxesAndCentroidsVisualization(
            baseBitmap = colorfulBitmap,
            allGrains = allGrains,
            labels = labels,
            lineWidth = 1f,
            markerSize = 10f
        )

        return finalBitmap
    }

    /**
     * Convert JTS Polygon to Android Path
     */
    private fun convertPolygonToPath(polygon: Polygon): android.graphics.Path {
        val path = android.graphics.Path()
        val coordinates = polygon.exteriorRing.coordinates

        if (coordinates.isNotEmpty()) {
            path.moveTo(coordinates[0].x.toFloat(), coordinates[0].y.toFloat())

            for (i in 1 until coordinates.size) {
                path.lineTo(coordinates[i].x.toFloat(), coordinates[i].y.toFloat())
            }

            path.close()
        }

        return path
    }

    /**
     * Generate a color palette
     */
    private fun generateColorPalette(size: Int): List<Color> {
        val colors = mutableListOf<Color>()
        val random = Random(42) // Fixed seed for reproducible colors

        // Predefined nice colors
        val baseColors = listOf(
            Color(0xFF2E86AB), Color(0xFFA23B72), Color(0xFFF18F01),
            Color(0xFFC73E1D), Color(0xFF592F0F), Color(0xFF6A994E),
            Color(0xFF386641), Color(0xFFBC4749), Color(0xFFF2E8CF),
            Color(0xFFA7C957), Color(0xFF219EBC), Color(0xFF8ECAE6),
            Color(0xFFFFB3BA), Color(0xFFFFDFBA), Color(0xFFBAFFC9),
            Color(0xFFBAE1FF), Color(0xFFE2BAFF), Color(0xFFFFBAED)
        )

        repeat(size) { index ->
            if (index < baseColors.size) {
                colors.add(baseColors[index])
            } else {
                // Generate random colors
                val hue = random.nextFloat() * 360f
                val saturation = 0.5f + random.nextFloat() * 0.5f
                val lightness = 0.4f + random.nextFloat() * 0.4f
                colors.add(Color.hsl(hue, saturation, lightness))
            }
        }

        return colors
    }
}