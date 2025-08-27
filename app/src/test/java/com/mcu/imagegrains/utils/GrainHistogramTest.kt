package com.mcu.imagegrains.utils

import io.mockk.MockKAnnotations
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.log2
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GrainHistogramTest {

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic("androidx.core.graphics.BitmapKt")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getAreaWeightedDistribution should handle empty areas`() {
        // Arrange
        val grainSizes = listOf(1.0, 2.0, 3.0)
        val areas = emptyList<Double>()

        // Act
        val result = GrainHistogram.getAreaWeightedDistribution(grainSizes, areas)

        // Assert
        assertEquals(grainSizes, result)
    }

    @Test
    fun `getAreaWeightedDistribution should handle mismatched sizes`() {
        // Arrange
        val grainSizes = listOf(1.0, 2.0, 3.0)
        val areas = listOf(10.0, 20.0) // Different size

        // Act
        val result = GrainHistogram.getAreaWeightedDistribution(grainSizes, areas)

        // Assert
        assertEquals(grainSizes, result)
    }

    @Test
    fun `getAreaWeightedDistribution should apply area weighting correctly`() {
        // Arrange
        val grainSizes = listOf(1.0, 2.0)
        val areas = listOf(10.0, 40.0) // Mean = 25.0, so weights are 1 and 3

        // Act
        val result = GrainHistogram.getAreaWeightedDistribution(grainSizes, areas)

        // Assert
        assertEquals(4, result.size) // 1 + 3 = 4 total elements
        assertEquals(1, result.count { it == 1.0 }) // First grain appears once
        assertEquals(3, result.count { it == 2.0 }) // Second grain appears 3 times
    }

    @Test
    fun `findGrainSizeClasses should return classes within phi range`() {
        // Arrange
        val phiMin = 1.5
        val phiMax = 3.5

        // Act
        val result = GrainHistogram.findGrainSizeClasses(phiMin, phiMax)

        // Assert
        assertTrue(result.isNotEmpty())

        // Should include "medium sand" (1.0 to 2.0), "fine sand" (2.0 to 3.0), "very fine sand" (3.0 to 4.0)
        val classNames = result.map { it.name }
        assertTrue(classNames.contains("medium sand"))
        assertTrue(classNames.contains("fine sand"))
        assertTrue(classNames.contains("very fine sand"))

        // Should be sorted by minPhi
        assertEquals(result.sortedBy { it.minPhi }, result)
    }

    @Test
    fun `findGrainSizeClasses should return empty list for no overlap`() {
        // Arrange
        val phiMin = 10.0
        val phiMax = 15.0 // Outside all defined ranges

        // Act
        val result = GrainHistogram.findGrainSizeClasses(phiMin, phiMax)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `createHistogramData should handle basic grain size data`() {
        // Arrange
        val major = listOf(2.0, 4.0, 8.0) // phi = 1.0, 2.0, 3.0
        val minor = listOf(1.0, 2.0, 4.0) // phi = 0.0, 1.0, 2.0

        // Act
        val result = GrainHistogram.createHistogramData(major, minor)

        // Assert
        assertNotNull(result)
        assertTrue(result.bins.isNotEmpty())
        assertEquals(result.majorCounts.size, result.bins.size - 1)
        assertEquals(result.minorCounts.size, result.bins.size - 1)
        assertTrue(result.maxCount >= 0)
        assertTrue(result.grainClasses.isNotEmpty())
    }

    @Test
    fun `createHistogramData should apply area weighting when provided`() {
        // Arrange
        val major = listOf(2.0, 4.0)
        val minor = listOf(1.0, 2.0)
        val areas = listOf(10.0, 40.0) // Should weight the second grain more

        // Act
        val resultWithAreas = GrainHistogram.createHistogramData(major, minor, areas)
        val resultWithoutAreas = GrainHistogram.createHistogramData(major, minor)

        // Assert
        // With area weighting, total counts should be higher
        val totalWithAreas = resultWithAreas.majorCounts.sum() + resultWithAreas.minorCounts.sum()
        val totalWithoutAreas = resultWithoutAreas.majorCounts.sum() + resultWithoutAreas.minorCounts.sum()
        assertTrue(totalWithAreas > totalWithoutAreas)
    }

    @Test
    fun `createHistogramData should respect custom x limits`() {
        // Arrange
        val major = listOf(1.0, 2.0, 4.0, 8.0)
        val minor = listOf(0.5, 1.0, 2.0, 4.0)
        val xLimits = 0.5 to 4.0

        // Act
        val result = GrainHistogram.createHistogramData(major, minor, xLimits = xLimits)

        // Assert
        assertEquals(xLimits, result.actualXLimits)

        // Phi range should be based on x limits
        val expectedPhiMax = kotlin.math.ceil(-log2(0.5)) // ceil(-(-1)) = ceil(1) = 1
        val expectedPhiMin = kotlin.math.floor(-log2(4.0)) // floor(-2) = -2
        assertTrue(result.phiMax <= expectedPhiMax + 0.1) // Allow small tolerance
        assertTrue(result.phiMin >= expectedPhiMin - 0.1)
    }

    @Test
    fun `createHistogramData should generate correct ECDF`() {
        // Arrange
        val major = listOf(1.0, 2.0, 4.0) // phi = 0.0, 1.0, 2.0
        val minor = listOf(2.0, 4.0, 8.0) // phi = 1.0, 2.0, 3.0

        // Act
        val result = GrainHistogram.createHistogramData(major, minor)

        // Assert
        assertEquals(3, result.majorEcdf.size)
        assertEquals(3, result.minorEcdf.size)

        // ECDF should be sorted by phi
        val majorPhis = result.majorEcdf.map { it.first }
        assertEquals(majorPhis.sorted(), majorPhis)

        // ECDF probabilities should decrease (1-F(phi))
        val majorProbs = result.majorEcdf.map { it.second }
        assertTrue(majorProbs[0] >= majorProbs[1])
        assertTrue(majorProbs[1] >= majorProbs[2])
    }

    @Test
    fun `OptGrainSizeClass should store correct properties`() {
        // Arrange & Act
        val grainClass = OptGrainSizeClass("medium sand", 1.0, 2.0)

        // Assert
        assertEquals("medium sand", grainClass.name)
        assertEquals(1.0, grainClass.minPhi)
        assertEquals(2.0, grainClass.maxPhi)
    }

    @Test
    fun `GrainHistogramData should store all required properties`() {
        // Arrange & Act
        val data = GrainHistogramData(
            actualXLimits = 1.0 to 4.0,
            bins = listOf(0.0, 1.0, 2.0),
            majorCounts = listOf(5, 3),
            minorCounts = listOf(2, 4),
            grainClasses = listOf(OptGrainSizeClass("test", 1.0, 2.0)),
            phiMin = 0.0,
            phiMax = 2.0,
            maxCount = 5,
            majorEcdf = listOf(0.0 to 1.0),
            minorEcdf = listOf(0.0 to 1.0)
        )

        // Assert
        assertEquals(1.0 to 4.0, data.actualXLimits)
        assertEquals(listOf(0.0, 1.0, 2.0), data.bins)
        assertEquals(listOf(5, 3), data.majorCounts)
        assertEquals(listOf(2, 4), data.minorCounts)
        assertEquals(1, data.grainClasses.size)
        assertEquals(0.0, data.phiMin)
        assertEquals(2.0, data.phiMax)
        assertEquals(5, data.maxCount)
        assertEquals(1, data.majorEcdf.size)
        assertEquals(1, data.minorEcdf.size)
    }
}