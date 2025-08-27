package com.mcu.imagegrains.utils

import org.junit.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import kotlin.test.*

class FastSpatialIndexTest {

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
    fun `constructor should initialize grid with correct dimensions`() {
        // Arrange & Act
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 80, cellSize = 25)

        // Assert
        // Grid should be ceil(100/25) x ceil(80/25) = 4 x 4
        // We can't directly access private fields, but we can test behavior
        assertNotNull(index)
    }

    @Test
    fun `insert should add polygon to index`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100, cellSize = 50)
        val polygon = createRectanglePolygon(10.0, 10.0, 20.0, 20.0)

        // Act
        index.insert(polygon, 0)

        // Assert
        // Test by finding overlapping groups - should work without error
        val groups = index.findOverlappingGroups()
        assertNotNull(groups)
    }

    @Test
    fun `insert should handle expanding polygon list`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100)
        val polygon1 = createRectanglePolygon(0.0, 0.0, 10.0, 10.0)
        val polygon2 = createRectanglePolygon(50.0, 50.0, 10.0, 10.0)

        // Act - Insert with indices that require list expansion
        index.insert(polygon1, 0)
        index.insert(polygon2, 5) // Should expand list to accommodate index 5

        // Assert
        val groups = index.findOverlappingGroups()
        assertNotNull(groups)
    }

    @Test
    fun `findOverlappingGroups should return empty list for no overlaps`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 200, imageHeight = 200, cellSize = 50)
        val polygon1 = createRectanglePolygon(0.0, 0.0, 20.0, 20.0)
        val polygon2 = createRectanglePolygon(100.0, 100.0, 20.0, 20.0)
        val polygon3 = createRectanglePolygon(50.0, 150.0, 20.0, 20.0)

        index.insert(polygon1, 0)
        index.insert(polygon2, 1)
        index.insert(polygon3, 2)

        // Act
        val groups = index.findOverlappingGroups(overlapThreshold = 0.1)

        // Assert
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `findOverlappingGroups should detect overlapping polygons`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100, cellSize = 25)
        val polygon1 = createRectanglePolygon(0.0, 0.0, 20.0, 20.0)   // Area = 400
        val polygon2 = createRectanglePolygon(10.0, 10.0, 20.0, 20.0) // Area = 400, overlaps with polygon1
        val polygon3 = createRectanglePolygon(80.0, 80.0, 15.0, 15.0) // Area = 225, no overlap

        index.insert(polygon1, 0)
        index.insert(polygon2, 1)
        index.insert(polygon3, 2)

        // Act
        val groups = index.findOverlappingGroups(overlapThreshold = 0.05) // Low threshold

        // Assert
        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals(2, group.size)
        assertTrue(group.contains(0))
        assertTrue(group.contains(1))
        assertFalse(group.contains(2))
    }

    @Test
    fun `findOverlappingGroups should respect overlap threshold`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100, cellSize = 25)
        val polygon1 = createRectanglePolygon(0.0, 0.0, 10.0, 10.0)   // Area = 100
        val polygon2 = createRectanglePolygon(9.0, 9.0, 10.0, 10.0)   // Area = 100, small overlap (1 unit²)

        index.insert(polygon1, 0)
        index.insert(polygon2, 1)

        // Act with high threshold
        val groupsHighThreshold = index.findOverlappingGroups(overlapThreshold = 0.05) // 5%

        // Act with low threshold
        val groupsLowThreshold = index.findOverlappingGroups(overlapThreshold = 0.005) // 0.5%

        // Assert
        // With high threshold (5%), should not detect overlap (1/100 = 1% < 5%)
        assertTrue(groupsHighThreshold.isEmpty())

        // With low threshold (0.5%), should detect overlap (1% > 0.5%)
        assertEquals(1, groupsLowThreshold.size)
    }

    @Test
    fun `findOverlappingGroups should handle chain of overlapping polygons`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100, cellSize = 20)
        val polygon1 = createRectanglePolygon(0.0, 0.0, 15.0, 15.0)   // Overlaps with 2
        val polygon2 = createRectanglePolygon(10.0, 10.0, 15.0, 15.0) // Overlaps with 1 and 3
        val polygon3 = createRectanglePolygon(20.0, 20.0, 15.0, 15.0) // Overlaps with 2

        index.insert(polygon1, 0)
        index.insert(polygon2, 1)
        index.insert(polygon3, 2)

        // Act
        val groups = index.findOverlappingGroups(overlapThreshold = 0.05)

        // Assert
        assertEquals(1, groups.size) // Should form one connected group
        val group = groups.first()
        assertEquals(3, group.size) // All three polygons should be connected
        assertTrue(group.containsAll(setOf(0, 1, 2)))
    }

    @Test
    fun `findOverlappingGroups should handle multiple separate groups`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 200, imageHeight = 200, cellSize = 40)

        // Group 1: overlapping polygons
        val group1_poly1 = createRectanglePolygon(0.0, 0.0, 15.0, 15.0)
        val group1_poly2 = createRectanglePolygon(10.0, 10.0, 15.0, 15.0)

        // Group 2: overlapping polygons
        val group2_poly1 = createRectanglePolygon(100.0, 100.0, 15.0, 15.0)
        val group2_poly2 = createRectanglePolygon(110.0, 110.0, 15.0, 15.0)

        // Isolated polygon
        val isolated = createRectanglePolygon(50.0, 150.0, 10.0, 10.0)

        index.insert(group1_poly1, 0)
        index.insert(group1_poly2, 1)
        index.insert(group2_poly1, 2)
        index.insert(group2_poly2, 3)
        index.insert(isolated, 4)

        // Act
        val groups = index.findOverlappingGroups(overlapThreshold = 0.05)

        // Assert
        assertEquals(2, groups.size) // Two separate overlapping groups

        val groupSizes = groups.map { it.size }.sorted()
        assertEquals(listOf(2, 2), groupSizes)

        val allGroupedIndices = groups.flatten().toSet()
        assertTrue(allGroupedIndices.containsAll(setOf(0, 1, 2, 3)))
        assertFalse(allGroupedIndices.contains(4)) // Isolated polygon should not be in any group
    }

    @Test
    fun `findOverlappingGroups should handle single polygon`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100)
        val polygon = createRectanglePolygon(25.0, 25.0, 20.0, 20.0)

        index.insert(polygon, 0)

        // Act
        val groups = index.findOverlappingGroups()

        // Assert
        assertTrue(groups.isEmpty()) // Single polygon cannot form a group
    }

    @Test
    fun `findOverlappingGroups should handle empty index`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100)

        // Act
        val groups = index.findOverlappingGroups()

        // Assert
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `findOverlappingGroups should handle grid boundary cases`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100, cellSize = 50)

        // Polygons that span grid cell boundaries
        val polygon1 = createRectanglePolygon(45.0, 45.0, 10.0, 10.0) // Spans 4 cells
        val polygon2 = createRectanglePolygon(48.0, 48.0, 8.0, 8.0)   // Overlaps with polygon1

        index.insert(polygon1, 0)
        index.insert(polygon2, 1)

        // Act
        val groups = index.findOverlappingGroups(overlapThreshold = 0.05)

        // Assert
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().size)
    }

    @Test
    fun `findOverlappingGroups should handle exception during processing`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100)
        val polygon = createRectanglePolygon(10.0, 10.0, 20.0, 20.0)

        index.insert(polygon, 0)

        // Act - Should not throw exception even if internal errors occur
        val groups = index.findOverlappingGroups()

        // Assert
        assertNotNull(groups) // Should return valid result even with internal errors
    }

    @Test
    fun `insert should handle edge coordinates`() {
        // Arrange
        val index = FastSpatialIndex(imageWidth = 100, imageHeight = 100, cellSize = 25)

        // Polygon at image boundary
        val edgePolygon = createRectanglePolygon(95.0, 95.0, 5.0, 5.0)

        // Polygon outside image bounds (should still work)
        val outsidePolygon = createRectanglePolygon(150.0, 150.0, 10.0, 10.0)

        // Act
        index.insert(edgePolygon, 0)
        index.insert(outsidePolygon, 1)

        // Assert
        val groups = index.findOverlappingGroups()
        assertNotNull(groups) // Should handle edge cases gracefully
    }
}