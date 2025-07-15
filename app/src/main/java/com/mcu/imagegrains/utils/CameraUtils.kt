package com.mcu.imagegrains.utils

import android.content.Context
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.core.content.ContextCompat
import java.io.File

object CameraUtils {

    /**
     * Capture image with 2000x2000 resolution
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
                                2000,
                                2000
                            ), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                }
                setResolutionSelector(resolutionSelectorBuilder.build())
                setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            }
            .build()
    }
}