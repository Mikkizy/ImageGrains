package com.mcu.imagegrains.utils

import android.content.Context
import android.widget.Toast
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.core.content.ContextCompat
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
import java.io.File
import java.util.concurrent.Executor
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockImageCapture: ImageCapture
    private lateinit var mockOutputFile: File
    private lateinit var mockCameraControl: CameraControl
    private lateinit var mockExecutor: Executor

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockContext = mockk()
        mockImageCapture = mockk()
        mockOutputFile = mockk()
        mockCameraControl = mockk()
        mockExecutor = mockk()

        // Mock static methods
        mockkStatic(ContextCompat::class)
        mockkStatic(Toast::class)

        every { ContextCompat.getMainExecutor(any()) } returns mockExecutor
        every { mockExecutor.execute(any()) } answers {
            firstArg<Runnable>().run()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `animateZoomTo should set zoom directly when steps is 1 or less`() = runTest {
        // Arrange
        val targetZoom = 2.0f
        val currentZoom = 1.0f
        every { mockCameraControl.setZoomRatio(any()) } returns mockk()

        // Act
        CameraUtils.animateZoomTo(mockCameraControl, targetZoom, currentZoom, 1)

        // Assert
        verify(exactly = 1) { mockCameraControl.setZoomRatio(targetZoom) }
    }

    @Test
    fun `animateZoomTo should animate zoom in steps when steps is greater than 1`() = runTest {
        // Arrange
        val targetZoom = 3.0f
        val currentZoom = 1.0f
        val steps = 4
        every { mockCameraControl.setZoomRatio(any()) } returns mockk()

        // Act
        CameraUtils.animateZoomTo(mockCameraControl, targetZoom, currentZoom, steps)

        // Assert
        verify(exactly = steps) { mockCameraControl.setZoomRatio(any()) }

        // Verify the zoom values are incremental
        val capturedZoomValues = mutableListOf<Float>()
        verify { mockCameraControl.setZoomRatio(capture(capturedZoomValues)) }

        // Should be 4 calls with values: 1.5, 2.0, 2.5, 3.0
        assertEquals(steps, capturedZoomValues.size)
        assertEquals(1.5f, capturedZoomValues[0], 0.01f)
        assertEquals(2.0f, capturedZoomValues[1], 0.01f)
        assertEquals(2.5f, capturedZoomValues[2], 0.01f)
        assertEquals(3.0f, capturedZoomValues[3], 0.01f)
    }

    @Test
    fun `animateZoomTo should handle negative zoom direction`() = runTest {
        // Arrange
        val targetZoom = 1.0f
        val currentZoom = 3.0f
        val steps = 2
        every { mockCameraControl.setZoomRatio(any()) } returns mockk()

        // Act
        CameraUtils.animateZoomTo(mockCameraControl, targetZoom, currentZoom, steps)

        // Assert
        verify(exactly = steps) { mockCameraControl.setZoomRatio(any()) }

        val capturedZoomValues = mutableListOf<Float>()
        verify { mockCameraControl.setZoomRatio(capture(capturedZoomValues)) }

        // Should decrease zoom: 2.0, 1.0
        assertEquals(2.0f, capturedZoomValues[0], 0.01f)
        assertEquals(1.0f, capturedZoomValues[1], 0.01f)
    }

    @Test
    fun `getZoomDescription should return Wide for zoom less than 1_5`() {
        // Test cases for Wide range
        assertEquals("Wide", CameraUtils.getZoomDescription(0.5f))
        assertEquals("Wide", CameraUtils.getZoomDescription(1.0f))
        assertEquals("Wide", CameraUtils.getZoomDescription(1.4f))
    }

    @Test
    fun `getZoomDescription should return Normal for zoom between 1_5 and 3_0`() {
        // Test cases for Normal range
        assertEquals("Normal", CameraUtils.getZoomDescription(1.5f))
        assertEquals("Normal", CameraUtils.getZoomDescription(2.0f))
        assertEquals("Normal", CameraUtils.getZoomDescription(2.9f))
    }

    @Test
    fun `getZoomDescription should return Close for zoom between 3_0 and 6_0`() {
        // Test cases for Close range
        assertEquals("Close", CameraUtils.getZoomDescription(3.0f))
        assertEquals("Close", CameraUtils.getZoomDescription(4.5f))
        assertEquals("Close", CameraUtils.getZoomDescription(5.9f))
    }

    @Test
    fun `getZoomDescription should return Macro for zoom 6_0 and above`() {
        // Test cases for Macro range
        assertEquals("Macro", CameraUtils.getZoomDescription(6.0f))
        assertEquals("Macro", CameraUtils.getZoomDescription(8.0f))
        assertEquals("Macro", CameraUtils.getZoomDescription(10.0f))
    }

    @Test
    fun `getZoomDescription should handle boundary values correctly`() {
        // Test exact boundary values
        assertEquals("Wide", CameraUtils.getZoomDescription(1.49f))
        assertEquals("Normal", CameraUtils.getZoomDescription(1.5f))
        assertEquals("Normal", CameraUtils.getZoomDescription(2.99f))
        assertEquals("Close", CameraUtils.getZoomDescription(3.0f))
        assertEquals("Close", CameraUtils.getZoomDescription(5.99f))
        assertEquals("Macro", CameraUtils.getZoomDescription(6.0f))
    }

    @Test
    fun `getZoomDescription should handle edge cases`() {
        // Test edge cases
        assertEquals("Wide", CameraUtils.getZoomDescription(0.0f))
        assertEquals("Wide", CameraUtils.getZoomDescription(Float.MIN_VALUE))
        assertEquals("Macro", CameraUtils.getZoomDescription(Float.MAX_VALUE))
    }
}