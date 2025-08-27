package com.mcu.imagegrains.utils

import org.junit.Test
import kotlin.math.roundToLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageAnalysisUtilsTest {

    @Test
    fun `labelConnectedComponents should handle empty binary image`() {
        // Arrange
        val binaryImage = arrayOf(
            booleanArrayOf(false, false),
            booleanArrayOf(false, false)
        )

        // Act
        val (labels, componentCount) = ImageAnalysisUtils.labelConnectedComponents(binaryImage)

        // Assert
        assertEquals(0, componentCount)
        assertEquals(0, labels[0][0])
        assertEquals(0, labels[1][1])
    }

    @Test
    fun `labelConnectedComponents should label single component with 4-connectivity`() {
        // Arrange
        val binaryImage = arrayOf(
            booleanArrayOf(true, true, false),
            booleanArrayOf(true, false, false),
            booleanArrayOf(false, false, true)
        )

        // Act
        val (labels, componentCount) = ImageAnalysisUtils.labelConnectedComponents(binaryImage, 1)

        // Assert
        assertEquals(2, componentCount) // Two separate components

        // First component (connected L-shape)
        assertEquals(1, labels[0][0])
        assertEquals(1, labels[0][1])
        assertEquals(1, labels[1][0])

        // Second component (isolated pixel)
        assertEquals(2, labels[2][2])

        // Background pixels
        assertEquals(0, labels[0][2])
        assertEquals(0, labels[1][1])
    }

    @Test
    fun `labelConnectedComponents should label with 8-connectivity`() {
        // Arrange
        val binaryImage = arrayOf(
            booleanArrayOf(true, false, true),
            booleanArrayOf(false, true, false),
            booleanArrayOf(true, false, true)
        )

        // Act
        val (labels8, componentCount8) = ImageAnalysisUtils.labelConnectedComponents(binaryImage, 8)
        val (_, componentCount4) = ImageAnalysisUtils.labelConnectedComponents(binaryImage, 1)

        // Assert
        assertEquals(1, componentCount8) // All connected with 8-connectivity
        assertEquals(5, componentCount4) // Separate components with 4-connectivity

        // With 8-connectivity, all true pixels should have same label
        assertEquals(labels8[0][0], labels8[1][1])
        assertEquals(labels8[1][1], labels8[2][2])
    }

    @Test
    fun `calculateRegionProperties should handle single pixel region`() {
        // Arrange
        val labeledImage = arrayOf(
            intArrayOf(0, 0, 0),
            intArrayOf(0, 1, 0),
            intArrayOf(0, 0, 0)
        )

        // Act
        val properties = ImageAnalysisUtils.calculateRegionProperties(labeledImage)

        // Assert
        assertEquals(1, properties.size)

        val grain = properties[0]
        assertEquals(1, grain.label)
        assertEquals(1.0, grain.area)
        assertEquals(1.0, grain.centroidX) // Column index
        assertEquals(1.0, grain.centroidY) // Row index
        assertEquals(4.0, grain.perimeter) // Single pixel has 4 boundary edges
    }

    @Test
    fun `calculateRegionProperties should calculate correct properties for rectangular region`() {
        // Arrange
        val labeledImage = arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 0, 0, 0)
        )

        // Act
        val properties = ImageAnalysisUtils.calculateRegionProperties(labeledImage)

        // Assert
        assertEquals(1, properties.size)

        val grain = properties[0]
        assertEquals(1, grain.label)
        assertEquals(4.0, grain.area) // 2x2 rectangle
        assertEquals(1.5, grain.centroidX) // Average of columns 1,1,2,2
        assertEquals(1.5, grain.centroidY) // Average of rows 1,1,2,2
        assertEquals(8.0, grain.perimeter) // Rectangle perimeter
    }

    @Test
    fun `calculateRegionProperties should handle intensity image`() {
        // Arrange
        val labeledImage = arrayOf(
            intArrayOf(0, 1),
            intArrayOf(1, 0)
        )

        val intensityImage = arrayOf(
            arrayOf(
                floatArrayOf(0.0f, 0.0f, 0.0f), // Black
                floatArrayOf(1.0f, 1.0f, 1.0f)  // White
            ),
            arrayOf(
                floatArrayOf(0.5f, 0.5f, 0.5f), // Gray
                floatArrayOf(0.0f, 0.0f, 0.0f)  // Black
            )
        )

        // Act
        val properties = ImageAnalysisUtils.calculateRegionProperties(labeledImage, intensityImage)

        // Assert
        assertEquals(1, properties.size)

        val grain = properties[0]
        // Grain contains pixels at (0,1) and (1,0)
        // Intensities: white (1.0) and gray (0.5)
        assertEquals(1.0, grain.maxIntensity.roundToLong().toDouble())
        assertEquals(0.75, grain.meanIntensity, 0.01) // (1.0 + 0.5) / 2
    }

    @Test
    fun `distanceTransformEDT should calculate correct distances`() {
        // Arrange
        val binaryImage = arrayOf(
            booleanArrayOf(false, false, false),
            booleanArrayOf(false, true, false),
            booleanArrayOf(false, false, false)
        )

        // Act
        val distance = ImageAnalysisUtils.distanceTransformEDT(binaryImage)

        // Assert
        // Background pixels should have distance 0
        assertEquals(0.0, distance[0][0])
        assertEquals(0.0, distance[0][1])
        assertEquals(0.0, distance[2][2])

        // Center pixel should have distance 1 (minimum distance to background)
        assertEquals(1.0, distance[1][1])
    }

    @Test
    fun `distanceTransformEDT should handle larger regions`() {
        // Arrange
        val binaryImage = arrayOf(
            booleanArrayOf(false, false, false, false, false),
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(false, false, false, false, false)
        )

        // Act
        val distance = ImageAnalysisUtils.distanceTransformEDT(binaryImage)

        // Assert
        // Corners of the true region should have distance 1
        assertEquals(1.0, distance[1][1])
        assertEquals(1.0, distance[1][3])
        assertEquals(1.0, distance[3][1])
        assertEquals(1.0, distance[3][3])

        // Center should have higher distance
        assertTrue(distance[2][2] > 1.0)
    }

    @Test
    fun `findLocalMaxima should identify peaks in distance transform`() {
        // Arrange
        val distance = arrayOf(
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(1.0, 2.0, 1.0),
            doubleArrayOf(0.0, 1.0, 0.0)
        )

        val mask = arrayOf(
            booleanArrayOf(true, true, true),
            booleanArrayOf(true, true, true),
            booleanArrayOf(true, true, true)
        )

        // Act
        val maxima = ImageAnalysisUtils.findLocalMaxima(distance, mask, 3)

        // Assert
        assertEquals(1, maxima.size)
        assertEquals(Pair(1, 1), maxima[0]) // Center pixel is local maximum
    }

    @Test
    fun `findLocalMaxima should respect mask boundaries`() {
        // Arrange
        val distance = arrayOf(
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(1.0, 2.0, 1.0),
            doubleArrayOf(0.0, 1.0, 0.0)
        )

        val mask = arrayOf(
            booleanArrayOf(true, true, true),
            booleanArrayOf(true, false, true), // Center masked out
            booleanArrayOf(true, true, true)
        )

        // Act
        val maxima = ImageAnalysisUtils.findLocalMaxima(distance, mask, 3)

        // Assert
        assertEquals(0, maxima.size) // No maxima should be found due to mask
    }

    @Test
    fun `calculateRegionProperties should handle multiple regions`() {
        // Arrange
        val labeledImage = arrayOf(
            intArrayOf(1, 1, 0, 2),
            intArrayOf(0, 0, 0, 2),
            intArrayOf(3, 0, 0, 0)
        )

        // Act
        val properties = ImageAnalysisUtils.calculateRegionProperties(labeledImage)

        // Assert
        assertEquals(3, properties.size)

        // Check each region has correct label and area
        val grainsByLabel = properties.associateBy { it.label }

        assertEquals(2.0, grainsByLabel[1]?.area) // Region 1 has 2 pixels
        assertEquals(2.0, grainsByLabel[2]?.area) // Region 2 has 2 pixels
        assertEquals(1.0, grainsByLabel[3]?.area) // Region 3 has 1 pixel
    }
}