package com.mcu.imagegrains.presentation.instance_seg

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcu.imagegrains.domain.InstanceSegmentationProcessor
import com.mcu.imagegrains.domain.InstanceSegmentationResult
import com.mcu.imagegrains.domain.models.LabelingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstanceSegmentationViewModel : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _result = MutableStateFlow<InstanceSegmentationResult?>(null)
    val result: StateFlow<InstanceSegmentationResult?> = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var processor: InstanceSegmentationProcessor? = null

    fun initializeModels(
        context: Context,
        encoderPath: String = "mobile_sam_encoder.onnx",
        predictorPath: String = "mobile_sam_onnx_version.onnx"
    ) {
        viewModelScope.launch {
            try {
                processor = InstanceSegmentationProcessor(context)
                val success = processor?.initialize() ?: false

                if (!success) {
                    _error.value = "Failed to initialize ONNX models"
                }
            } catch (e: Exception) {
                _error.value = "Error initializing models: ${e.message}"
            }
        }
    }

    fun performInstanceSegmentation(
        originalBitmap: Bitmap,
        predictionArray: Array<Array<FloatArray>>,
        labelingResult: LabelingResult,
        minArea: Int = 400
    ) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                _error.value = null
                _progress.value = 0f

                val result = processor?.performInstanceSegmentation(
                    originalBitmap = originalBitmap,
                    predictionArray = predictionArray,
                    labelingResult = labelingResult,
                    minArea = minArea,
                    progressCallback = { progress ->
                        _progress.value = progress
                    }
                )

                _result.value = result

                if (result == null) {
                    _error.value = "Failed to perform instance segmentation"
                }

            } catch (e: Exception) {
                _error.value = "Instance segmentation error: ${e.message}"
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        processor?.close()
    }
}