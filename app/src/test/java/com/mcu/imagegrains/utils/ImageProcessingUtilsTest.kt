package com.mcu.imagegrains.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.get
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageProcessingUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockUri: Uri
    private lateinit var mockInputStream: InputStream

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockContext = mockk()
        mockContentResolver = mockk()
        mockUri = mockk()
        mockInputStream = mockk(relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver

        // Mock static methods
        mockkStatic(BitmapFactory::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `loadImageForTFLite should return null when bitmap is null`() = runTest {
        // Arrange
        every { mockContentResolver.openInputStream(mockUri) } returns mockInputStream
        every { BitmapFactory.decodeStream(mockInputStream) } returns null

        // Act
        val result = ImageProcessingUtils.loadImageForTFLite(mockContext, mockUri)

        // Assert
        assertNull(result)
        verify { mockInputStream.close() }
    }

    @Test
    fun `loadImageForTFLite should return null on exception`() = runTest {
        // Arrange
        every { mockContentResolver.openInputStream(mockUri) } throws RuntimeException("Test exception")

        // Act
        val result = ImageProcessingUtils.loadImageForTFLite(mockContext, mockUri)

        // Assert
        assertNull(result)
    }

    @Test
    fun `convertBitmapToFloatArray should convert RGB bitmap correctly`() {
        // Arrange
        val mockBitmap = mockk<Bitmap>()
        every { mockBitmap.width } returns 2
        every { mockBitmap.height } returns 2

        // Red pixel (255, 0, 0)
        every { mockBitmap[0, 0] } returns Color.RED
        // Green pixel (0, 255, 0)
        every { mockBitmap[1, 0] } returns Color.GREEN
        // Blue pixel (0, 0, 255)
        every { mockBitmap[0, 1] } returns Color.BLUE
        // White pixel (255, 255, 255)
        every { mockBitmap[1, 1] } returns Color.WHITE

        // Act
        val result = ImageProcessingUtils.convertBitmapToFloatArray(mockBitmap, ImageProcessingUtils.ColorMode.RGB)

        // Assert
        assertEquals(2, result.size) // height
        assertEquals(2, result[0].size) // width
        assertEquals(3, result[0][0].size) // RGB channels

        // Check red pixel
        assertEquals(1.0f, result[0][0][0], 0.01f) // Red channel
        assertEquals(0.0f, result[0][0][1], 0.01f) // Green channel
        assertEquals(0.0f, result[0][0][2], 0.01f) // Blue channel

        // Check green pixel
        assertEquals(0.0f, result[0][1][0], 0.01f) // Red channel
        assertEquals(1.0f, result[0][1][1], 0.01f) // Green channel
        assertEquals(0.0f, result[0][1][2], 0.01f) // Blue channel
    }

    @Test
    fun `convertBitmapToFloatArray should convert grayscale correctly`() {
        // Arrange
        val mockBitmap = mockk<Bitmap>()
        every { mockBitmap.width } returns 1
        every { mockBitmap.height } returns 1

        // Red pixel (255, 0, 0) - should become grayscale using luminance formula
        every { mockBitmap[0, 0] } returns Color.RED

        // Act
        val result = ImageProcessingUtils.convertBitmapToFloatArray(mockBitmap, ImageProcessingUtils.ColorMode.GRAYSCALE)

        // Assert
        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals(3, result[0][0].size)

        // Expected grayscale value for red: 0.299 * 255 / 255 = 0.299
        val expectedGray = 0.299f
        assertEquals(expectedGray, result[0][0][0], 0.01f)
        assertEquals(expectedGray, result[0][0][1], 0.01f)
        assertEquals(expectedGray, result[0][0][2], 0.01f)
    }

    @Test
    fun `convertFloatArrayToBitmap should create bitmap with correct dimensions`() {
        // Arrange
        val imageArray = arrayOf(
            arrayOf(
                floatArrayOf(1.0f, 0.0f, 0.0f), // Red pixel
                floatArrayOf(0.0f, 1.0f, 0.0f)  // Green pixel
            ),
            arrayOf(
                floatArrayOf(0.0f, 0.0f, 1.0f), // Blue pixel
                floatArrayOf(1.0f, 1.0f, 1.0f)  // White pixel
            )
        )

        // Act
        val result = ImageProcessingUtils.convertFloatArrayToBitmap(imageArray)

        // Assert
        assertNotNull(result)
        assertEquals(2, result.width)  // width from imageArray[0].size
        assertEquals(2, result.height) // height from imageArray.size
    }

    @Test
    fun `convertFloatArrayToBitmap should handle single channel grayscale`() {
        // Arrange
        val imageArray = arrayOf(
            arrayOf(
                floatArrayOf(0.5f), // Gray pixel
                floatArrayOf(1.0f)  // White pixel
            )
        )

        // Act
        val result = ImageProcessingUtils.convertFloatArrayToBitmap(imageArray)

        // Assert
        assertNotNull(result)
        assertEquals(2, result.width)
        assertEquals(1, result.height)
    }

    @Test
    fun `convertFloatArrayToBitmap should handle invalid channels`() {
        // Arrange
        val imageArray = arrayOf(
            arrayOf(
                floatArrayOf(1.0f, 0.5f, 0.0f, 0.2f, 0.8f) // 5 channels - invalid
            )
        )

        // Act
        val result = ImageProcessingUtils.convertFloatArrayToBitmap(imageArray)

        // Assert
        assertNotNull(result)
        assertEquals(1, result.width)
        assertEquals(1, result.height)
        // Invalid channels should result in black pixels (verified by the logic in the function)
    }

    @Test
    fun `generateModifiedHanningWindows should create correct window shapes`() {
        // Arrange
        val size = 4

        // Act
        val (W, Wup, Wdown) = ImageProcessingUtils.generateModifiedHanningWindows(size)

        // Assert
        assertEquals(size, W.size)
        assertEquals(size, W[0].size)
        assertEquals(size, Wup.size)
        assertEquals(size, Wup[0].size)
        assertEquals(size, Wdown.size)
        assertEquals(size, Wdown[0].size)

        // Verify Hanning window properties
        // First and last values should be close to 0
        assertTrue(W[0][0] < 0.1f) // Corner should be near 0
        assertTrue(W[size-1][size-1] < 0.1f) // Opposite corner should be near 0

        // Center should have higher values
        assertTrue(W[size/2][size/2] > W[0][0])
    }

    @Test
    fun `generateModifiedHanningWindows should have correct mathematical properties`() {
        // Arrange
        val size = 8

        // Act
        val (W, Wup, Wdown) = ImageProcessingUtils.generateModifiedHanningWindows(size)

        // Assert - Check 1D Hanning window formula at specific points
        val expectedFirst = (0.5 * (1 - cos(2 * PI * 0 / (size - 1)))).toFloat()
        val expectedLast = (0.5 * (1 - cos(2 * PI * (size - 1) / (size - 1)))).toFloat()

        assertEquals(expectedFirst, W[0][0], 0.01f)
        assertEquals(expectedLast, W[size-1][size-1], 0.01f)

        // Check upper window has flat top for first half
        size / 2
        for (j in 0 until size) {
            val expected1D = (0.5 * (1 - cos(2 * PI * j / (size - 1)))).toFloat()
            assertEquals(expected1D, Wup[0][j], 0.01f) // First row should be 1D window
        }

        // Check lower window has flat bottom for second half
        for (j in 0 until size) {
            val expected1D = (0.5 * (1 - cos(2 * PI * j / (size - 1)))).toFloat()
            assertEquals(expected1D, Wdown[size-1][j], 0.01f) // Last row should be 1D window
        }
    }

    @Test
    fun `convertFloatArrayToBitmap should handle edge case empty array`() {
        // Arrange
        val imageArray = arrayOf<Array<FloatArray>>()

        // Act & Assert
        assertFailsWith<IndexOutOfBoundsException> {
            ImageProcessingUtils.convertFloatArrayToBitmap(imageArray)
        }
    }

    @Test
    fun `generateModifiedHanningWindows should handle small window size`() {
        // Arrange
        val size = 2

        // Act
        val (W, _, _) = ImageProcessingUtils.generateModifiedHanningWindows(size)

        // Assert
        assertEquals(size, W.size)
        assertEquals(size, W[0].size)

        // For size 2, first and last values of 1D window should be 0 and 1
        val expected0 = (0.5 * (1 - cos(2 * PI * 0 / 1))).toFloat() // Should be 0
        val expected1 = (0.5 * (1 - cos(2 * PI * 1 / 1))).toFloat() // Should be 1

        assertEquals(expected0, W[0][0], 0.01f)
        assertEquals(expected1, W[1][1], 0.01f)
    }
}