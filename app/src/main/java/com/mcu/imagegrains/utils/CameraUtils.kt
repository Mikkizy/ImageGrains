package com.mcu.imagegrains.utils

import android.content.Context
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

object CameraUtils {

    /**
     * Capture image with 1000x1000 resolution
     */
    fun captureImage(
        imageCapture: ImageCapture,
        outputFile: File,
        context: Context,
        onImageCaptured: (Boolean) -> Unit
    ) {
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onImageCaptured(true)
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(context, "Photo capture succeeded", Toast.LENGTH_SHORT).show()
                    Log.d("CameraUtils", msg)
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    onImageCaptured(false)
                }
            }
        )
    }

    /**
     * Get ImageCapture use case configured for 2000x2000
     */
    fun getImageCaptureUseCase(): ImageCapture {
        return ImageCapture.Builder()
            .apply {
                //setTargetResolution(Size(2000, 2000))
                val resolutionSelectorBuilder = ResolutionSelector.Builder().apply {
                    setResolutionStrategy(
                        ResolutionStrategy(
                            Size(
                                1000,
                                1000
                            ), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                }
                setResolutionSelector(resolutionSelectorBuilder.build())
                setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            }
            .build()
    }

    suspend fun PointerInputScope.detectZoomGestures(
        onZoom: (zoom: Float) -> Unit
    ) {
        detectTransformGestures(
            panZoomLock = true
        ) { centroid, pan, zoom, rotation ->
            // Only process significant zoom changes to avoid jitter
            // This prevents accidental zoom triggers from small finger movements
            if (abs(zoom - 1f) > 0.02f) {
                onZoom(zoom)
            }
        }
    }

    /**
     * Smoothly animate zoom to target ratio
     */
    suspend fun animateZoomTo(
        cameraControl: CameraControl,
        targetZoom: Float,
        currentZoom: Float,
        steps: Int = 8
    ) {
        if (steps <= 1) {
            cameraControl.setZoomRatio(targetZoom)
            return
        }

        val stepSize = (targetZoom - currentZoom) / steps

        for (i in 1..steps) {
            val nextZoom = currentZoom + (stepSize * i)
            cameraControl.setZoomRatio(nextZoom)
            delay(16) // ~60fps animation
        }
    }

    /**
     * Get zoom level description for UI
     */
    fun getZoomDescription(zoomRatio: Float): String {
        return when {
            zoomRatio < 1.5f -> "Wide"
            zoomRatio < 3.0f -> "Normal"
            zoomRatio < 6.0f -> "Close"
            else -> "Macro"
        }
    }
}