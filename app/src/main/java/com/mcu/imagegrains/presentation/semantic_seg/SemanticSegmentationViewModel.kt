package com.mcu.imagegrains.presentation.semantic_seg

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcu.imagegrains.domain.GrainLabelingProcessor
import com.mcu.imagegrains.domain.SemanticSegmentationProcessor
import com.mcu.imagegrains.domain.models.LabelingResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SemanticSegmentationViewModel : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _result = MutableStateFlow<Bitmap?>(null)
    val result: StateFlow<Bitmap?> = _result.asStateFlow()

    private val _labelingResult = MutableStateFlow<LabelingResult?>(null)
    val labelingResult: StateFlow<LabelingResult?> = _labelingResult.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var processor: SemanticSegmentationProcessor? = null
    private val labelingProcessor = GrainLabelingProcessor()


    fun initializeModel(context: Context, modelPath: String = "seg_model_android_gpu_float16.tflite") {
        viewModelScope.launch {
            try {
                processor = SemanticSegmentationProcessor(context, modelPath)
                val success = processor?.initialize() ?: false

                if (!success) {
                    _error.value = "Failed to initialize semantic segmentation model"
                }
            } catch (e: Exception) {
                _error.value = "Error initializing model: ${e.message}"
            }
        }
    }

    fun processImage(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                _error.value = null
                _progress.value = 0f

                val result = processor?.predictImage(imageUri) { progress ->
                    _progress.value = progress
                }

                _result.value = result

                if (result == null) {
                    _error.value = "Failed to process image"
                }

            } catch (e: Exception) {
                _error.value = "Processing error: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun labelGrains(
        image: Array<Array<FloatArray>>,
        imagePred: Array<Array<FloatArray>>,
        dbsMaxDist: Double = 20.0
    ) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true
                _error.value = null

                val result = labelingProcessor.labelGrains(image, imagePred, dbsMaxDist)
                _labelingResult.value = result

            } catch (e: Exception) {
                _error.value = "Labeling error: ${e.message}"
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