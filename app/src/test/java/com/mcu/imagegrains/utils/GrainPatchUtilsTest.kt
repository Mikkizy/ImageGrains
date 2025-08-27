package com.mcu.imagegrains.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import io.mockk.*
import kotlin.test.*

@ExperimentalCoroutinesApi
class GrainPatchUtilsTest {

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
    fun `getGrainsFromPolygons should handle empty input`() = runTest {
        // Arrange
        val emptyGrains = emptyList<Polygon>()
        val imageWidth = 100
        val imageHeight = 100

        // Act
        val (filteredGrains, rasterized, mask) = GrainPatchUtils.getGrainsFromPolygons(
            emptyGrains, imageWidth, imageHeight
        )

        // Assert
        assertTrue(filteredGrains.isEmpty())
        assertEquals(imageHeight, rasterized.size)
        assertEquals(imageWidth, rasterized[0].size)
        assertEquals(imageHeight, mask.size)
        assertEquals(imageWidth, mask[0].size)

        // Check all arrays are initialized to 0
        assertEquals(0, rasterized[0][0])
        assertEquals(0, mask[0][0])
    }

    @Test
    fun `getGrainsFromPolygons should handle single grain`() = runTest {
        // Arrange
        val singleGrain = listOf(createRectanglePolygon(10.0, 10.0, 20.0, 20.0))
        val imageWidth = 100
        val imageHeight = 100

        // Mock SpatialIndexingUtils to return no overlaps
        mockkObject(SpatialIndexingUtils)
        every {
            SpatialIndexingUtils.findOverlappingPolygons(any(), any())
        } returns emptyList()

        // Act
        val (filteredGrains, rasterized, _) = GrainPatchUtils.getGrainsFromPolygons(
            singleGrain, imageWidth, imageHeight
        )

        // Assert
        assertEquals(1, filteredGrains.size)
        assertEquals(singleGrain[0], filteredGrains[0])

        // Verify arrays are created
        assertEquals(imageHeight, rasterized.size)
        assertEquals(imageWidth, rasterized[0].size)

        unmockkObject(SpatialIndexingUtils)
    }

    @Test
    fun `getGrainsFromPolygons should remove overlapping grains correctly`() = runTest {
        // Arrange
        val largeGrain = createRectanglePolygon(0.0, 0.0, 20.0, 20.0) // Area = 400
        val smallGrain = createRectanglePolygon(5.0, 5.0, 10.0, 10.0) // Area = 100
        val grains = listOf(largeGrain, smallGrain)

        val imageWidth = 100
        val imageHeight = 100

        // Mock SpatialIndexingUtils to return overlap between grains
        mockkObject(SpatialIndexingUtils)
        every {
            SpatialIndexingUtils.findOverlappingPolygons(any(), any())
        } returns listOf(setOf(0, 1)) // Both grains overlap

        // Act
        val (filteredGrains, _, _) = GrainPatchUtils.getGrainsFromPolygons(
            grains, imageWidth, imageHeight
        )

        // Assert
        assertEquals(1, filteredGrains.size) // Only one grain should remain
        assertEquals(largeGrain, filteredGrains[0]) // Larger grain should be kept

        unmockkObject(SpatialIndexingUtils)
    }

    @Test
    fun `getGrainsFromPolygons should handle multiple non-overlapping grains`() = runTest {
        // Arrange
        val grain1 = createRectanglePolygon(0.0, 0.0, 10.0, 10.0)
        val grain2 = createRectanglePolygon(50.0, 50.0, 10.0, 10.0)
        val grain3 = createRectanglePolygon(20.0, 80.0, 10.0, 10.0)
        val grains = listOf(grain1, grain2, grain3)

        val imageWidth = 100
        val imageHeight = 100

        // Mock SpatialIndexingUtils to return no overlaps
        mockkObject(SpatialIndexingUtils)
        every {
            SpatialIndexingUtils.findOverlappingPolygons(any(), any())
        } returns emptyList()

        // Act
        val (filteredGrains, _, _) = GrainPatchUtils.getGrainsFromPolygons(
            grains, imageWidth, imageHeight
        )

        // Assert
        assertEquals(3, filteredGrains.size) // All grains should remain
        assertEquals(grains, filteredGrains)

        unmockkObject(SpatialIndexingUtils)
    }

    @Test
    fun `getGrainsFromPolygons should handle progress callback`() = runTest {
        // Arrange
        val grains = listOf(createRectanglePolygon(10.0, 10.0, 20.0, 20.0))
        val imageWidth = 50
        val imageHeight = 50

        val progressValues = mutableListOf<Float>()
        val progressCallback: (Float) -> Unit = { progress ->
            progressValues.add(progress)
        }

        // Mock SpatialIndexingUtils
        mockkObject(SpatialIndexingUtils)
        every {
            SpatialIndexingUtils.findOverlappingPolygons(any(), any())
        } returns emptyList()

        // Act
        GrainPatchUtils.getGrainsFromPolygons(
            grains, imageWidth, imageHeight, progressCallback
        )

        // Assert
        assertTrue(progressValues.isNotEmpty())
        assertTrue(progressValues.contains(1.0f)) // Should reach 100%
        assertTrue(progressValues.first() >= 0.0f) // Should start with valid progress
        assertTrue(progressValues.last() == 1.0f) // Should end at 100%

        unmockkObject(SpatialIndexingUtils)
    }

    @Test
    fun `getGrainsFromPolygons should handle exception gracefully`() = runTest {
        // Arrange
        val grains = listOf(createRectanglePolygon(10.0, 10.0, 20.0, 20.0))
        val imageWidth = 50
        val imageHeight = 50

        // Mock SpatialIndexingUtils to throw exception
        mockkObject(SpatialIndexingUtils)
        every {
            SpatialIndexingUtils.findOverlappingPolygons(any(), any())
        } throws RuntimeException("Test exception")

        // Act
        val (filteredGrains, rasterized, _) = GrainPatchUtils.getGrainsFromPolygons(
            grains, imageWidth, imageHeight
        )

        // Assert - Should fallback to original grains
        assertEquals(1, filteredGrains.size)
        assertEquals(grains[0], filteredGrains[0])

        // Arrays should still be created
        assertEquals(imageHeight, rasterized.size)
        assertEquals(imageWidth, rasterized[0].size)

        unmockkObject(SpatialIndexingUtils)
    }

    @Test
    fun `getGrainsFromPolygons should filter invalid polygons`() = runTest {
        // Arrange
        val validGrain = createRectanglePolygon(10.0, 10.0, 20.0, 20.0)
        val invalidGrain = mockk<Polygon>()
        every { invalidGrain.isValid } returns false
        every { invalidGrain.isEmpty } returns false

        val grains = listOf(validGrain, invalidGrain)
        val imageWidth = 100
        val imageHeight = 100

        // Mock SpatialIndexingUtils
        mockkObject(SpatialIndexingUtils)
        every {
            SpatialIndexingUtils.findOverlappingPolygons(any(), any())
        } returns emptyList()

        // Act
        val (filteredGrains, _, _) = GrainPatchUtils.getGrainsFromPolygons(
            grains, imageWidth, imageHeight
        )

        // Assert
        assertEquals(1, filteredGrains.size) // Only valid grain should remain
        assertEquals(validGrain, filteredGrains[0])

        unmockkObject(SpatialIndexingUtils)
    }

    @Test
    fun `getGrainsFromPolygons should handle complex overlapping groups`() = runTest {
        // Arrange
        val grain1 = createRectanglePolygon(0.0, 0.0, 20.0, 20.0)   // Area = 400
        val grain2 = createRectanglePolygon(5.0, 5.0, 10.0, 10.0)   // Area = 100 (overlaps with 1)
        val grain3 = createRectanglePolygon(10.0, 10.0, 15.0, 15.0) // Area = 225 (overlaps with 1 and 2)
        val grain4 = createRectanglePolygon(50.0, 50.0, 10.0, 10.0) // Area = 100 (no overlap)

        val grains = listOf(grain1, grain2, grain3, grain4)
        val imageWidth = 100
        val imageHeight = 100

        // Mock overlapping group (grains 0, 1, 2 overlap)
        mockkObject(SpatialIndexingUtils)
        every {
            SpatialIndexingUtils.findOverlappingPolygons(any(), any())
        } returns listOf(setOf(0, 1, 2))

        // Act
        val (filteredGrains, _, _) = GrainPatchUtils.getGrainsFromPolygons(
            grains, imageWidth, imageHeight
        )

        // Assert
        assertEquals(2, filteredGrains.size) // Should keep largest from group + isolated grain
        assertTrue(filteredGrains.contains(grain1)) // Largest in overlapping group
        assertTrue(filteredGrains.contains(grain4)) // Isolated grain

        unmockkObject(SpatialIndexingUtils)
    }
}