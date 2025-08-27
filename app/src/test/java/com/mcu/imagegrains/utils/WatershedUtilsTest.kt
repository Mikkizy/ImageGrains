package com.mcu.imagegrains.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class WatershedUtilsTest {

    @Test
    fun `watershed should handle simple 3x3 case with single marker`() {
        // Arrange
        val image = arrayOf(
            doubleArrayOf(1.0, 2.0, 3.0),
            doubleArrayOf(4.0, 5.0, 6.0),
            doubleArrayOf(7.0, 8.0, 9.0)
        )

        val markers = arrayOf(
            intArrayOf(0, 0, 0),
            intArrayOf(0, 1, 0),  // Center marked as region 1
            intArrayOf(0, 0, 0)
        )

        val mask = arrayOf(
            booleanArrayOf(true, true, true),
            booleanArrayOf(true, true, true),
            booleanArrayOf(true, true, true)
        )

        // Act
        val result = WatershedUtils.watershed(image, markers, mask)

        // Assert
        assertEquals(1, result[1][1]) // Center should keep its label

        // Neighbors should be labeled based on flooding
        assertEquals(1, result[0][1]) // Top neighbor
        assertEquals(1, result[1][0]) // Left neighbor
        assertEquals(1, result[1][2]) // Right neighbor
        assertEquals(1, result[2][1]) // Bottom neighbor
    }

    @Test
    fun `watershed should handle two separate markers`() {
        // Arrange
        val image = arrayOf(
            doubleArrayOf(1.0, 5.0, 1.0),
            doubleArrayOf(5.0, 9.0, 5.0),
            doubleArrayOf(1.0, 5.0, 1.0)
        )

        val markers = arrayOf(
            intArrayOf(1, 0, 2),  // Two markers at corners
            intArrayOf(0, 0, 0),
            intArrayOf(0, 0, 0)
        )

        val mask = arrayOf(
            booleanArrayOf(true, true, true),
            booleanArrayOf(true, true, true),
            booleanArrayOf(true, true, true)
        )

        // Act
        val result = WatershedUtils.watershed(image, markers, mask)

        // Assert
        assertEquals(1, result[0][0]) // First marker preserved
        assertEquals(2, result[0][2]) // Second marker preserved

        // Check that regions grow from markers
        assertEquals(1, result[1][0]) // Should be labeled by marker 1
        assertEquals(2, result[1][2]) // Should be labeled by marker 2
    }

    @Test
    fun `watershed should respect mask boundaries`() {
        // Arrange
        val image = arrayOf(
            doubleArrayOf(1.0, 2.0, 3.0),
            doubleArrayOf(4.0, 5.0, 6.0),
            doubleArrayOf(7.0, 8.0, 9.0)
        )

        val markers = arrayOf(
            intArrayOf(0, 0, 0),
            intArrayOf(0, 1, 0),
            intArrayOf(0, 0, 0)
        )

        val mask = arrayOf(
            booleanArrayOf(false, true, false),  // Only middle column is valid
            booleanArrayOf(false, true, false),
            booleanArrayOf(false, true, false)
        )

        // Act
        val result = WatershedUtils.watershed(image, markers, mask)

        // Assert
        assertEquals(1, result[1][1]) // Center marker preserved
        assertEquals(1, result[0][1]) // Top neighbor in mask
        assertEquals(1, result[2][1]) // Bottom neighbor in mask

        // Masked areas should remain unlabeled
        assertEquals(0, result[0][0])
        assertEquals(0, result[0][2])
        assertEquals(0, result[1][0])
        assertEquals(0, result[1][2])
    }

    @Test
    fun `watershed should handle empty markers array`() {
        // Arrange
        val image = arrayOf(
            doubleArrayOf(1.0, 2.0),
            doubleArrayOf(3.0, 4.0)
        )

        val markers = arrayOf(
            intArrayOf(0, 0),  // No markers
            intArrayOf(0, 0)
        )

        val mask = arrayOf(
            booleanArrayOf(true, true),
            booleanArrayOf(true, true)
        )

        // Act
        val result = WatershedUtils.watershed(image, markers, mask)

        // Assert - Should remain all zeros since no markers
        assertContentEquals(intArrayOf(0, 0), result[0])
        assertContentEquals(intArrayOf(0, 0), result[1])
    }

    @Test
    fun `watershed should handle single pixel case`() {
        // Arrange
        val image = arrayOf(doubleArrayOf(5.0))
        val markers = arrayOf(intArrayOf(1))
        val mask = arrayOf(booleanArrayOf(true))

        // Act
        val result = WatershedUtils.watershed(image, markers, mask)

        // Assert
        assertEquals(1, result[0][0])
    }

    @Test
    fun `watershed should preserve original marker values`() {
        // Arrange
        val image = arrayOf(
            doubleArrayOf(1.0, 2.0, 3.0),
            doubleArrayOf(4.0, 5.0, 6.0)
        )

        val markers = arrayOf(
            intArrayOf(5, 0, 10),  // Different marker values
            intArrayOf(0, 0, 0)
        )

        val mask = arrayOf(
            booleanArrayOf(true, true, true),
            booleanArrayOf(true, true, true)
        )

        // Act
        val result = WatershedUtils.watershed(image, markers, mask)

        // Assert
        assertEquals(5, result[0][0])   // Original marker value preserved
        assertEquals(10, result[0][2])  // Original marker value preserved
        assertEquals(5, result[0][1])   // Should be labeled by lower intensity neighbor
    }
}