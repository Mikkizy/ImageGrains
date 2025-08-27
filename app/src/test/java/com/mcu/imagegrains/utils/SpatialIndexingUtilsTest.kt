package com.mcu.imagegrains.utils

import org.junit.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SpatialIndexingUtilsTest {

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
    fun `findOverlappingPolygons should return empty list for empty input`() {
        // Arrange
        val polygons = emptyList<Polygon>()

        // Act
        val result = SpatialIndexingUtils.findOverlappingPolygons(polygons)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findOverlappingPolygons should return empty list for single polygon`() {
        // Arrange
        val polygon = createRectanglePolygon(0.0, 0.0, 10.0, 10.0)
        val polygons = listOf(polygon)

        // Act
        val result = SpatialIndexingUtils.findOverlappingPolygons(polygons)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findOverlappingPolygons should detect overlapping polygons`() {
        // Arrange
        val polygon1 = createRectanglePolygon(0.0, 0.0, 10.0, 10.0)  // (0,0) to (10,10)
        val polygon2 = createRectanglePolygon(5.0, 5.0, 10.0, 10.0)  // (5,5) to (15,15) - overlaps with polygon1
        val polygon3 = createRectanglePolygon(20.0, 20.0, 10.0, 10.0) // (20,20) to (30,30) - no overlap

        val polygons = listOf(polygon1, polygon2, polygon3)

        // Act
        val result = SpatialIndexingUtils.findOverlappingPolygons(polygons, 0.1)

        // Assert
        assertEquals(1, result.size) // One overlapping group
        assertTrue(result[0].contains(0)) // Contains polygon1 index
        assertTrue(result[0].contains(1)) // Contains polygon2 index
        assertFalse(result[0].contains(2)) // Does not contain polygon3 index
    }

    @Test
    fun `findOverlappingPolygons should handle non-overlapping polygons`() {
        // Arrange
        val polygon1 = createRectanglePolygon(0.0, 0.0, 10.0, 10.0)   // (0,0) to (10,10)
        val polygon2 = createRectanglePolygon(20.0, 20.0, 10.0, 10.0) // (20,20) to (30,30)
        val polygon3 = createRectanglePolygon(40.0, 40.0, 10.0, 10.0) // (40,40) to (50,50)

        val polygons = listOf(polygon1, polygon2, polygon3)

        // Act
        val result = SpatialIndexingUtils.findOverlappingPolygons(polygons)

        // Assert
        assertTrue(result.isEmpty()) // No overlapping groups
    }

    @Test
    fun `findOverlappingPolygons should handle chain of overlapping polygons`() {
        // Arrange
        val polygon1 = createRectanglePolygon(0.0, 0.0, 10.0, 10.0)   // (0,0) to (10,10)
        val polygon2 = createRectanglePolygon(5.0, 5.0, 10.0, 10.0)   // (5,5) to (15,15) - overlaps with 1
        val polygon3 = createRectanglePolygon(10.0, 10.0, 10.0, 10.0) // (10,10) to (20,20) - overlaps with 2

        val polygons = listOf(polygon1, polygon2, polygon3)

        // Act
        val result = SpatialIndexingUtils.findOverlappingPolygons(polygons, 0.01) // Low threshold

        // Assert
        assertEquals(1, result.size) // One connected group
        assertEquals(3, result[0].size) // All three polygons should be connected
        assertTrue(result[0].containsAll(setOf(0, 1, 2)))
    }

    @Test
    fun `findOverlappingPolygons should respect overlap threshold`() {
        // Arrange - Create polygons with minimal overlap
        val polygon1 = createRectanglePolygon(0.0, 0.0, 10.0, 10.0)   // Area = 100
        val polygon2 = createRectanglePolygon(9.0, 9.0, 10.0, 10.0)   // Area = 100, overlap area = 1
        // Overlap percentage = 1/100 = 0.01 = 1%

        val polygons = listOf(polygon1, polygon2)

        // Act with high threshold
        val resultHighThreshold = SpatialIndexingUtils.findOverlappingPolygons(polygons, 0.05) // 5%

        // Act with low threshold
        val resultLowThreshold = SpatialIndexingUtils.findOverlappingPolygons(polygons, 0.005) // 0.5%

        // Assert
        assertTrue(resultHighThreshold.isEmpty()) // Should not detect overlap with high threshold
        assertEquals(1, resultLowThreshold.size) // Should detect overlap with low threshold
    }

    @Test
    fun `findOverlappingPolygons should handle multiple separate groups`() {
        // Arrange
        val group1_poly1 = createRectanglePolygon(0.0, 0.0, 10.0, 10.0)
        val group1_poly2 = createRectanglePolygon(5.0, 5.0, 10.0, 10.0)

        val group2_poly1 = createRectanglePolygon(50.0, 50.0, 10.0, 10.0)
        val group2_poly2 = createRectanglePolygon(55.0, 55.0, 10.0, 10.0)

        val isolatedPolygon = createRectanglePolygon(100.0, 100.0, 10.0, 10.0)

        val polygons = listOf(group1_poly1, group1_poly2, group2_poly1, group2_poly2, isolatedPolygon)

        // Act
        val result = SpatialIndexingUtils.findOverlappingPolygons(polygons, 0.1)

        // Assert
        assertEquals(2, result.size) // Two overlapping groups

        // Verify groups contain correct polygon indices
        val flattenedResult = result.flatten().toSet()
        assertTrue(flattenedResult.containsAll(setOf(0, 1, 2, 3)))
        assertFalse(flattenedResult.contains(4)) // Isolated polygon should not be in any group
    }
}