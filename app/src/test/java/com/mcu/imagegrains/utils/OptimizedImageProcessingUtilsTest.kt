package com.mcu.imagegrains.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OptimizedImageProcessingUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockUri: Uri
    private lateinit var mockInputStream: InputStream
    private lateinit var mockBitmap: Bitmap

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockContext = mockk()
        mockContentResolver = mockk()
        mockUri = mockk()
        mockInputStream = mockk()
        mockBitmap = mockk(relaxed = true)

        every { mockContext.contentResolver } returns mockContentResolver
        every { mockBitmap.width } returns 100
        every { mockBitmap.height } returns 100

        // Mock static methods
        mockkStatic(BitmapFactory::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `loadBitmapFromUri should return null when input stream is null`() {
        // Arrange
        every { mockContentResolver.openInputStream(mockUri) } returns null

        // Act
        val result = OptimizedImageProcessingUtils.loadBitmapFromUri(mockContext, mockUri)

        // Assert
        assertNull(result)
    }

    @Test
    fun `loadBitmapFromUri should handle exception and return null`() {
        // Arrange
        every { mockContentResolver.openInputStream(mockUri) } throws RuntimeException("Test exception")

        // Act
        val result = OptimizedImageProcessingUtils.loadBitmapFromUri(mockContext, mockUri)

        // Assert
        assertNull(result)
    }

    @Test
    fun `getImageDimensions should return null on exception`() {
        // Arrange
        every { mockContentResolver.openInputStream(mockUri) } throws RuntimeException("Test exception")

        // Act
        val result = OptimizedImageProcessingUtils.getImageDimensions(mockContext, mockUri)

        // Assert
        assertNull(result)
    }
}