package com.mcu.imagegrains.utils

import org.junit.Test
import kotlin.math.*
import kotlin.test.*

class DBSCANUtilsTest {

    @Test
    fun `dbscan should handle empty input`() {
        // Arrange
        val points = emptyArray<DoubleArray>()
        val eps = 1.0
        val minSamples = 2

        // Act
        val result = DBSCANUtils.dbscan(points, eps, minSamples)

        // Assert
        assertEquals(0, result.labels.size)
        assertEquals(0, result.numClusters)
    }

    @Test
    fun `dbscan should handle single point`() {
        // Arrange
        val points = arrayOf(doubleArrayOf(1.0, 1.0))
        val eps = 1.0
        val minSamples = 2

        // Act
        val result = DBSCANUtils.dbscan(points, eps, minSamples)

        // Assert
        assertEquals(1, result.labels.size)
        assertEquals(-1, result.labels[0]) // Should be noise (not enough neighbors)
        assertEquals(0, result.numClusters)
    }

    @Test
    fun `dbscan should create single cluster for close points`() {
        // Arrange
        val points = arrayOf(
            doubleArrayOf(1.0, 1.0),
            doubleArrayOf(1.1, 1.1),
            doubleArrayOf(1.2, 1.0),
            doubleArrayOf(1.0, 1.2)
        )
        val eps = 0.5
        val minSamples = 3

        // Act
        val result = DBSCANUtils.dbscan(points, eps, minSamples)

        // Assert
        assertEquals(4, result.labels.size)
        assertEquals(1, result.numClusters)

        // All points should be in the same cluster (cluster 0)
        assertTrue(result.labels.all { it == 0 })
    }

    @Test
    fun `dbscan should identify noise points`() {
        // Arrange
        val points = arrayOf(
            doubleArrayOf(1.0, 1.0),
            doubleArrayOf(1.1, 1.1),
            doubleArrayOf(10.0, 10.0) // Isolated point
        )
        val eps = 0.5
        val minSamples = 2

        // Act
        val result = DBSCANUtils.dbscan(points, eps, minSamples)

        // Assert
        assertEquals(3, result.labels.size)
        assertEquals(1, result.numClusters)

        // First two points should form a cluster
        assertEquals(0, result.labels[0])
        assertEquals(0, result.labels[1])

        // Third point should be noise
        assertEquals(-1, result.labels[2])
    }

    @Test
    fun `dbscan should create multiple clusters`() {
        // Arrange
        val points = arrayOf(
            // Cluster 1
            doubleArrayOf(1.0, 1.0),
            doubleArrayOf(1.1, 1.1),
            doubleArrayOf(1.2, 1.0),

            // Cluster 2
            doubleArrayOf(5.0, 5.0),
            doubleArrayOf(5.1, 5.1),
            doubleArrayOf(5.0, 5.2)
        )
        val eps = 0.5
        val minSamples = 2

        // Act
        val result = DBSCANUtils.dbscan(points, eps, minSamples)

        // Assert
        assertEquals(6, result.labels.size)
        assertEquals(2, result.numClusters)

        // Check that we have two distinct clusters
        val uniqueLabels = result.labels.filter { it >= 0 }.toSet()
        assertEquals(2, uniqueLabels.size)
        assertTrue(uniqueLabels.contains(0))
        assertTrue(uniqueLabels.contains(1))
    }

    @Test
    fun `dbscan should handle border points correctly`() {
        // Arrange - Create a line of points where middle points connect two dense regions
        val points = arrayOf(
            // Dense region 1
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.1, 0.0),
            doubleArrayOf(0.0, 0.1),
            doubleArrayOf(0.1, 0.1),

            // Bridge point
            doubleArrayOf(1.0, 0.0),

            // Dense region 2
            doubleArrayOf(2.0, 0.0),
            doubleArrayOf(2.1, 0.0),
            doubleArrayOf(2.0, 0.1),
            doubleArrayOf(2.1, 0.1)
        )
        val eps = 1.1 // Large enough to connect regions through bridge
        val minSamples = 3

        // Act
        val result = DBSCANUtils.dbscan(points, eps, minSamples)

        // Assert
        assertEquals(9, result.labels.size)

        // Should form clusters - exact clustering depends on order of processing
        assertTrue(result.numClusters >= 1)

        // No point should remain unclassified
        assertFalse(result.labels.contains(-2))
    }

    @Test
    fun `dbscan should respect minSamples parameter`() {
        // Arrange
        val points = arrayOf(
            doubleArrayOf(1.0, 1.0),
            doubleArrayOf(1.1, 1.1),
            doubleArrayOf(1.2, 1.0)
        )
        val eps = 0.5

        // Act with different minSamples values
        val resultMinSamples2 = DBSCANUtils.dbscan(points, eps, 2)
        val resultMinSamples4 = DBSCANUtils.dbscan(points, eps, 4)

        // Assert
        // With minSamples=2, should form cluster(s)
        assertTrue(resultMinSamples2.numClusters >= 1)

        // With minSamples=4, not enough points to form any cluster
        assertEquals(0, resultMinSamples4.numClusters)
        assertTrue(resultMinSamples4.labels.all { it == -1 }) // All should be noise
    }

    @Test
    fun `dbscan should respect eps parameter`() {
        // Arrange
        val points = arrayOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(2.0, 0.0),
            doubleArrayOf(4.0, 0.0)
        )
        val minSamples = 2

        // Act with different eps values
        val resultSmallEps = DBSCANUtils.dbscan(points, 1.0, minSamples) // Small eps
        val resultLargeEps = DBSCANUtils.dbscan(points, 3.0, minSamples) // Large eps

        // Assert
        // With small eps, points should be separate (noise)
        assertEquals(0, resultSmallEps.numClusters)

        // With large eps, points should form one cluster
        assertEquals(1, resultLargeEps.numClusters)
        assertTrue(resultLargeEps.labels.all { it == 0 })
    }

    @Test
    fun `dbscan should handle 3D points`() {
        // Arrange
        val points = arrayOf(
            doubleArrayOf(1.0, 1.0, 1.0),
            doubleArrayOf(1.1, 1.1, 1.1),
            doubleArrayOf(1.0, 1.2, 1.0),
            doubleArrayOf(1.1, 1.0, 1.2)
        )
        val eps = 0.5
        val minSamples = 2

        // Act
        val result = DBSCANUtils.dbscan(points, eps, minSamples)

        // Assert
        assertEquals(4, result.labels.size)
        assertTrue(result.numClusters >= 1)

        // Should handle 3D distance calculations correctly
        assertFalse(result.labels.contains(-2)) // No unclassified points
    }

    @Test
    fun `dbscan should handle higher dimensional points`() {
        // Arrange - 5D points
        val points = arrayOf(
            doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0),
            doubleArrayOf(1.1, 1.1, 1.1, 1.1, 1.1),
            doubleArrayOf(1.0, 1.0, 1.2, 1.0, 1.0)
        )
        val eps = 0.5
        val minSamples = 2

        // Act
        val result = DBSCANUtils.dbscan(points, eps, minSamples)

        // Assert
        assertEquals(3, result.labels.size)
        // Should handle high-dimensional distance calculations
        assertNotNull(result)
    }

    @Test
    fun `DBSCANResult equals should work correctly`() {
        // Arrange
        val labels1 = intArrayOf(0, 0, 1, -1)
        val labels2 = intArrayOf(0, 0, 1, -1)
        val labels3 = intArrayOf(0, 1, 1, -1)

        val result1 = DBSCANUtils.DBSCANResult(labels1, 2)
        val result2 = DBSCANUtils.DBSCANResult(labels2, 2)
        val result3 = DBSCANUtils.DBSCANResult(labels3, 2)
        val result4 = DBSCANUtils.DBSCANResult(labels1, 3)

        // Act & Assert
        assertEquals(result1, result2) // Same labels and cluster count
        assertNotEquals(result1, result3) // Different labels
        assertNotEquals(result1, result4) // Different cluster count
    }

    @Test
    fun `DBSCANResult hashCode should be consistent`() {
        // Arrange
        val labels = intArrayOf(0, 0, 1, -1)
        val result1 = DBSCANUtils.DBSCANResult(labels, 2)
        val result2 = DBSCANUtils.DBSCANResult(labels.copyOf(), 2)

        // Act & Assert
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun `dbscan should handle edge case with zero eps`() {
        // Arrange
        val points = arrayOf(
            doubleArrayOf(1.0, 1.0),
            doubleArrayOf(1.0, 1.0), // Exact duplicate
            doubleArrayOf(2.0, 2.0)
        )
        val eps = 0.0 // Only exact matches
        val minSamples = 2

        // Act
        val result = DBSCANUtils.dbscan(points, eps, minSamples)

        // Assert
        assertEquals(3, result.labels.size)
        assertEquals(1, result.numClusters) // Only the duplicate points should cluster

        // First two points should form a cluster
        assertEquals(result.labels[0], result.labels[1])
        assertTrue(result.labels[0] >= 0)

        // Third point should be noise
        assertEquals(-1, result.labels[2])
    }
}