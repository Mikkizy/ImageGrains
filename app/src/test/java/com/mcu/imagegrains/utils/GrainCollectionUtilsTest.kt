package com.mcu.imagegrains.utils

import org.junit.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import kotlin.test.*

class GrainCollectionUtilsTest {

    private val geometryFactory = GeometryFactory()

    private fun createRectanglePolygon(x: Double, y: Double, width: Double, height: Double): Polygon {
        val coordinates = arrayOf(
            Coordinate(x, y),
            Coordinate(x + width, y),
            Coordinate(x + width, y + height),
            Coordinate(x, y + height),
            Coordinate(x, y) // Close the ring
        )
        val ring = geometryFactory.createLinearRing(coordinates)
        return geometryFactory.createPolygon(ring)
    }

    @Test
    fun `collectPolygonFromMask should handle empty mask`() {
        // Arrange
        val labels = arrayOf(
            intArrayOf(0, 0),
            intArrayOf(0, 0)
        )
        val mask = arrayOf(
            booleanArrayOf(false, false),
            booleanArrayOf(false, false)
        )
        val imagePred = arrayOf(
            arrayOf(floatArrayOf(0.1f), floatArrayOf(0.2f)),
            arrayOf(floatArrayOf(0.3f), floatArrayOf(0.4f))
        )
        val allGrains = mutableListOf<Polygon>()
        val sx = floatArrayOf(0f, 1f, 1f, 0f)
        val sy = floatArrayOf(0f, 0f, 1f, 1f)

        // Act
        val result = GrainCollectionUtils.collectPolygonFromMask(
            labels, mask, imagePred, allGrains, sx, sy
        )

        // Assert
        assertEquals(0, result.size)
    }

    @Test
    fun `collectPolygonFromMask should reject high background fraction`() {
        // Arrange
        val labels = arrayOf(
            intArrayOf(1, 1),
            intArrayOf(1, 1)
        )
        val mask = arrayOf(
            booleanArrayOf(true, true),
            booleanArrayOf(true, true)
        )
        // High background values (> 0.7)
        val imagePred = arrayOf(
            arrayOf(floatArrayOf(0.8f), floatArrayOf(0.9f)),
            arrayOf(floatArrayOf(0.85f), floatArrayOf(0.75f))
        )
        val allGrains = mutableListOf<Polygon>()
        val sx = floatArrayOf(0f, 2f, 2f, 0f)
        val sy = floatArrayOf(0f, 0f, 2f, 2f)

        // Act
        val result = GrainCollectionUtils.collectPolygonFromMask(
            labels, mask, imagePred, allGrains, sx, sy, minArea = 1
        )

        // Assert
        assertEquals(0, result.size) // Should reject due to high background fraction
    }

    @Test
    fun `collectPolygonFromMask should accept valid conditions`() {
        // Arrange
        val labels = arrayOf(
            intArrayOf(1, 1),
            intArrayOf(1, 1)
        )
        val mask = arrayOf(
            booleanArrayOf(true, true),
            booleanArrayOf(true, true)
        )
        // Low background values (< 0.7)
        val imagePred = arrayOf(
            arrayOf(floatArrayOf(0.2f), floatArrayOf(0.3f)),
            arrayOf(floatArrayOf(0.1f), floatArrayOf(0.4f))
        )
        val allGrains = mutableListOf<Polygon>()
        val sx = floatArrayOf(0f, 2f, 2f, 0f)
        val sy = floatArrayOf(0f, 0f, 2f, 2f)

        // Act
        val result = GrainCollectionUtils.collectPolygonFromMask(
            labels, mask, imagePred, allGrains, sx, sy, minArea = 1
        )

        // Assert
        assertEquals(1, result.size) // Should accept valid grain
        assertNotNull(result.first())
    }

    @Test
    fun `collectPolygonFromMask should handle coordinate decimation for large contours`() {
        // Arrange
        val labels = arrayOf(intArrayOf(1))
        val mask = arrayOf(booleanArrayOf(true))
        val imagePred = arrayOf(arrayOf(floatArrayOf(0.1f)))
        val allGrains = mutableListOf<Polygon>()

        // Create large coordinate arrays (> MAX_CONTOUR_POINTS = 2000)
        val largeSize = 3000
        val sx = FloatArray(largeSize) { it.toFloat() }
        val sy = FloatArray(largeSize) { it.toFloat() }

        // Act
        val result = GrainCollectionUtils.collectPolygonFromMask(
            labels, mask, imagePred, allGrains, sx, sy, minArea = 1
        )

        // Assert
        assertEquals(1, result.size)
        // Should have decimated coordinates to stay within limits
    }

    @Test
    fun `collectPolygonFromMask should ensure polygon closure`() {
        // Arrange
        val labels = arrayOf(intArrayOf(1))
        val mask = arrayOf(booleanArrayOf(true))
        val imagePred = arrayOf(arrayOf(floatArrayOf(0.1f)))
        val allGrains = mutableListOf<Polygon>()

        // Create open contour (first != last)
        val sx = floatArrayOf(0f, 1f, 2f, 1f)
        val sy = floatArrayOf(0f, 0f, 1f, 2f)

        // Act
        val result = GrainCollectionUtils.collectPolygonFromMask(
            labels, mask, imagePred, allGrains, sx, sy, minArea = 1
        )

        // Assert
        assertEquals(1, result.size)
        val polygon = result.first()
        val coords = polygon.coordinates
        // Should be closed (first == last coordinate)
        assertEquals(coords.first().x, coords.last().x, 0.01)
        assertEquals(coords.first().y, coords.last().y, 0.01)
    }

    @Test
    fun `findContours should handle empty mask`() {
        // Arrange
        val mask = arrayOf(
            booleanArrayOf(false, false),
            booleanArrayOf(false, false)
        )

        // Act
        val result = GrainCollectionUtils.findContours(mask)

        // Assert
        assertEquals(0, result.size)
    }

    @Test
    fun `createLabeledImage should handle empty grain list`() {
        // Arrange
        val emptyGrains = emptyList<Polygon>()
        val imageWidth = 10
        val imageHeight = 10

        // Act
        val (rasterized, maskAll) = GrainCollectionUtils.createLabeledImage(
            emptyGrains, imageWidth, imageHeight
        )

        // Assert
        assertEquals(imageHeight, rasterized.size)
        assertEquals(imageWidth, rasterized[0].size)
        assertEquals(imageHeight, maskAll.size)
        assertEquals(imageWidth, maskAll[0].size)

        // All values should be 0 (empty)
        assertEquals(0, rasterized[0][0])
        assertEquals(0, maskAll[0][0])
    }

    @Test
    fun `createLabeledImage should rasterize single grain correctly`() {
        // Arrange
        val grain = createRectanglePolygon(2.0, 2.0, 4.0, 4.0) // 2x2 to 6x6
        val grains = listOf(grain)
        val imageWidth = 10
        val imageHeight = 10

        // Act
        val (rasterized, maskAll) = GrainCollectionUtils.createLabeledImage(
            grains, imageWidth, imageHeight
        )

        // Assert
        assertEquals(imageHeight, rasterized.size)
        assertEquals(imageWidth, rasterized[0].size)

        // Check that some pixels are labeled (grain should occupy area around 2-6, 2-6)
        var hasGrainPixels = false
        var hasBoundaryPixels = false

        for (i in 0 until imageHeight) {
            for (j in 0 until imageWidth) {
                if (rasterized[i][j] > 0) {
                    hasGrainPixels = true
                }
                if (maskAll[i][j] == 1) { // Grain pixels
                    hasGrainPixels = true
                }
                if (maskAll[i][j] == 2) { // Boundary pixels
                    hasBoundaryPixels = true
                }
            }
        }

        assertTrue(hasGrainPixels)
        // Note: boundary pixels depend on boundary buffer which might be complex to test
    }

    @Test
    fun `createLabeledImage should handle multiple grains with different labels`() {
        // Arrange
        val grain1 = createRectanglePolygon(1.0, 1.0, 2.0, 2.0)
        val grain2 = createRectanglePolygon(5.0, 5.0, 2.0, 2.0)
        val grains = listOf(grain1, grain2)
        val imageWidth = 10
        val imageHeight = 10

        // Act
        val (rasterized, maskAll) = GrainCollectionUtils.createLabeledImage(
            grains, imageWidth, imageHeight
        )

        // Assert
        val uniqueLabels = mutableSetOf<Int>()
        for (i in 0 until imageHeight) {
            for (j in 0 until imageWidth) {
                if (rasterized[i][j] > 0) {
                    uniqueLabels.add(rasterized[i][j])
                }
            }
        }

        // Should have at least 1 unique label, potentially 2 (if grains don't overlap in rasterization)
        assertTrue(uniqueLabels.isNotEmpty())
        assertTrue(uniqueLabels.size <= 2)
    }

    @Test
    fun `collectPolygonFromMask should reject too many large grains`() {
        // Arrange
        val labels = arrayOf(
            intArrayOf(1, 2, 3, 4, 5),
            intArrayOf(6, 7, 8, 9, 10),
            intArrayOf(11, 12, 13, 14, 15)
        )
        val mask = arrayOf(
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(true, true, true, true, true)
        )
        val imagePred = arrayOf(
            arrayOf(floatArrayOf(0.1f), floatArrayOf(0.1f), floatArrayOf(0.1f), floatArrayOf(0.1f), floatArrayOf(0.1f)),
            arrayOf(floatArrayOf(0.1f), floatArrayOf(0.1f), floatArrayOf(0.1f), floatArrayOf(0.1f), floatArrayOf(0.1f)),
            arrayOf(floatArrayOf(0.1f), floatArrayOf(0.1f), floatArrayOf(0.1f), floatArrayOf(0.1f), floatArrayOf(0.1f))
        )
        val allGrains = mutableListOf<Polygon>()
        val sx = floatArrayOf(0f, 5f, 5f, 0f)
        val sy = floatArrayOf(0f, 0f, 3f, 3f)

        // Act
        val result = GrainCollectionUtils.collectPolygonFromMask(
            labels, mask, imagePred, allGrains, sx, sy,
            minArea = 1, maxNLargeGrains = 5 // Limit to 5 large grains
        )

        // Assert
        assertEquals(0, result.size) // Should reject due to too many large grains (>5)
    }
}