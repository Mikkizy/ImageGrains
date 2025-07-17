package com.mcu.imagegrains.presentation

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcu.imagegrains.domain.CompleteSegmentationResult
import com.mcu.imagegrains.domain.GrainLabelingProcessor
import com.mcu.imagegrains.domain.InstanceSegmentationProcessor
import com.mcu.imagegrains.domain.OptimizedInstanceSegmentationProcessor
import com.mcu.imagegrains.domain.OptimizedProcessingStats
import com.mcu.imagegrains.domain.SemanticSegmentationProcessor
import com.mcu.imagegrains.domain.SemanticSegmentationResult
import com.mcu.imagegrains.domain.models.GrainProperties
import com.mcu.imagegrains.domain.models.GrainStatistics
import com.mcu.imagegrains.domain.models.LabelingResult
import com.mcu.imagegrains.domain.models.ScaleCalibration
import com.mcu.imagegrains.domain.models.ScaledGrainData
import com.mcu.imagegrains.domain.models.ScaledGrainProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedSegmentationViewModel : ViewModel() {

    // Original image data
    private val _originalImageUri = MutableStateFlow<Uri?>(null)
    val originalImageUri: StateFlow<Uri?> = _originalImageUri.asStateFlow()

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    // Keep track of bitmap copies to prevent recycling issues
    private var bitmapCopies = mutableListOf<Bitmap>()

    // Semantic segmentation results
    private val _semanticResult = MutableStateFlow<SemanticSegmentationResult?>(null)
    val semanticResult: StateFlow<SemanticSegmentationResult?> = _semanticResult.asStateFlow()

    private val _labelingResult = MutableStateFlow<LabelingResult?>(null)
    val labelingResult: StateFlow<LabelingResult?> = _labelingResult.asStateFlow()

    // Scale calibration
    private val _scaleCalibration = MutableStateFlow<ScaleCalibration?>(null)
    val scaleCalibration: StateFlow<ScaleCalibration?> = _scaleCalibration.asStateFlow()

    // Instance segmentation results
    private val _instanceResult = MutableStateFlow<CompleteSegmentationResult?>(null)
    val instanceResult: StateFlow<CompleteSegmentationResult?> = _instanceResult.asStateFlow()

    // Final grain data with scaling applied
    private val _scaledGrainData = MutableStateFlow<ScaledGrainData?>(null)
    val scaledGrainData: StateFlow<ScaledGrainData?> = _scaledGrainData.asStateFlow()

    // Processing states
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Processors
    private var semanticProcessor: SemanticSegmentationProcessor? = null
    private var instanceProcessor: OptimizedInstanceSegmentationProcessor? = null
    private val labelingProcessor = GrainLabelingProcessor()

    fun initializeModels(context: Context) {
        viewModelScope.launch {
            try {
                _isProcessing.value = true

                // Initialize semantic segmentation model
                semanticProcessor = SemanticSegmentationProcessor(
                    context,
                    "seg_model_android_gpu_float16.tflite"
                )
                val semanticInit = semanticProcessor?.initialize() ?: false

                // Initialize instance segmentation models
                instanceProcessor = OptimizedInstanceSegmentationProcessor(
                    context
                )
                val instanceInit = instanceProcessor?.initialize() ?: false

                if (!semanticInit || !instanceInit) {
                    _error.value = "Failed to initialize models"
                }

            } catch (e: Exception) {
                _error.value = "Model initialization error: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Set original image and create a copy for safe use across screens
     */
    fun setOriginalImage(uri: Uri, bitmap: Bitmap) {
        // Clean up previous bitmaps
        cleanupPreviousBitmaps()

        _originalImageUri.value = uri

        // Create a copy of the bitmap to avoid recycling issues
        val bitmapCopy = createBitmapCopy(bitmap)
        _originalBitmap.value = bitmapCopy

        if (bitmapCopy != null) {
            bitmapCopies.add(bitmapCopy)
            println("✅ Original image set with safe copy: ${bitmapCopy.width}x${bitmapCopy.height}")
        }
    }

    /**
     * Get a safe copy of the original bitmap for use in UI
     */
    fun getSafeBitmapCopy(): Bitmap? {
        val originalBitmap = _originalBitmap.value
        return if (originalBitmap != null && !originalBitmap.isRecycled) {
            createBitmapCopy(originalBitmap)?.also { copy ->
                bitmapCopies.add(copy)
            }
        } else {
            null
        }
    }

    /**
     * Create a safe copy of a bitmap
     */
    private fun createBitmapCopy(original: Bitmap): Bitmap? {
        return try {
            if (original.isRecycled) {
                println("⚠️ Cannot copy recycled bitmap")
                return null
            }

            val copy = original.copy(original.config ?: Bitmap.Config.ARGB_8888, false)
            println("✅ Created bitmap copy: ${copy.width}x${copy.height}")
            copy
        } catch (e: Exception) {
            println("❌ Error creating bitmap copy: ${e.message}")
            null
        }
    }

    /**
     * Clean up previous bitmap copies
     */
    private fun cleanupPreviousBitmaps() {
        bitmapCopies.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        bitmapCopies.clear()

        // Don't recycle the current original bitmap as it might be in use
        // Let the caller handle its lifecycle
    }

    fun performSemanticSegmentation(context: Context) {
        val uri = _originalImageUri.value ?: return

        viewModelScope.launch {
            try {
                _isProcessing.value = true
                _error.value = null
                _progress.value = 0f

                // Semantic segmentation
                val segResult = semanticProcessor?.predictImageComplete(uri) { progress ->
                    _progress.value = progress * 0.7f
                }

                if (segResult == null) {
                    _error.value = "Semantic segmentation failed"
                    return@launch
                }

                _semanticResult.value = segResult
                _progress.value = 0.7f

                // Grain labeling
                val labelResult = labelingProcessor.labelGrains(
                    image = segResult.originalArray,
                    imagePred = segResult.predictionArray,
                    dbsMaxDist = 20.0
                )

                _labelingResult.value = labelResult
                _progress.value = 1.0f

            } catch (e: Exception) {
                _error.value = "Segmentation error: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun setScaleCalibration(calibration: ScaleCalibration) {
        _scaleCalibration.value = calibration
    }

    fun performInstanceSegmentation() {
        val bitmap = _originalBitmap.value ?: return
        val semanticResult = _semanticResult.value ?: return
        val labelingResult = _labelingResult.value ?: return

        viewModelScope.launch {
            try {
                _isProcessing.value = true
                _error.value = null
                _progress.value = 0f

                val result = instanceProcessor?.performCompleteInstanceSegmentation(
                    originalBitmap = bitmap,
                    predictionArray = semanticResult.predictionArray,
                    labelingResult = labelingResult,
                    minArea = 400
                ) { progress -> _progress.value = progress }

                _instanceResult.value = result

                // Apply scaling if available
                val scaleCalibration = _scaleCalibration.value
                if (result != null && scaleCalibration != null) {
                    val scaledData = applyScalingToGrainData(result.finalGrainData, scaleCalibration)
                    _scaledGrainData.value = scaledData
                }

            } catch (e: Exception) {
                _error.value = "Instance segmentation error: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun applyScalingToGrainData(
        grainData: List<GrainProperties>,
        scaleCalibration: ScaleCalibration
    ): ScaledGrainData {
        val unitsPerPixel = scaleCalibration.realLength / scaleCalibration.pixelLength

        val scaledGrains = grainData.map { grain ->
            ScaledGrainProperties(
                label = grain.label,
                area = grain.area * (unitsPerPixel * unitsPerPixel), // Area scales by square
                centroidX = grain.centroidX * unitsPerPixel,
                centroidY = grain.centroidY * unitsPerPixel,
                majorAxisLength = grain.majorAxisLength * unitsPerPixel,
                minorAxisLength = grain.minorAxisLength * unitsPerPixel,
                orientation = grain.orientation, // Orientation doesn't scale
                perimeter = grain.perimeter * unitsPerPixel,
                maxIntensity = grain.maxIntensity, // Intensities don't scale
                meanIntensity = grain.meanIntensity,
                minIntensity = grain.minIntensity
            )
        }

        val statistics = calculateGrainStatistics(scaledGrains)

        return ScaledGrainData(
            scaledGrains = scaledGrains,
            scaleCalibration = scaleCalibration,
            statistics = statistics
        )
    }

    private fun calculateGrainStatistics(grains: List<ScaledGrainProperties>): GrainStatistics {
        if (grains.isEmpty()) {
            return GrainStatistics(
                0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0
            )
        }

        val areas = grains.map { it.area }
        val majorAxes = grains.map { it.majorAxisLength }
        val minorAxes = grains.map { it.minorAxisLength }
        val perimeters = grains.map { it.perimeter }
        val allSizes = (majorAxes + minorAxes)

        return GrainStatistics(
            count = grains.size,
            areaMean = areas.average(),
            areaStd = calculateStandardDeviation(areas),
            areaMin = areas.minOrNull() ?: 0.0,
            areaQ25 = calculatePercentile(areas, 25.0),
            areaQ50 = calculatePercentile(areas, 50.0),
            areaQ75 = calculatePercentile(areas, 75.0),
            areaMax = areas.maxOrNull() ?: 0.0,
            majorAxisMean = majorAxes.average(),
            majorAxisStd = calculateStandardDeviation(majorAxes),
            majorAxisMin = majorAxes.minOrNull() ?: 0.0,
            majorAxisMax = majorAxes.maxOrNull() ?: 0.0,
            minorAxisMean = minorAxes.average(),
            minorAxisStd = calculateStandardDeviation(minorAxes),
            minorAxisMin = minorAxes.minOrNull() ?: 0.0,
            minorAxisMax = minorAxes.maxOrNull() ?: 0.0,
            d16 = calculatePercentile(allSizes, 16.0),
            d50 = calculatePercentile(allSizes, 50.0),
            d84 = calculatePercentile(allSizes, 84.0),
            majorAxisD16 = calculatePercentile(majorAxes, 16.0),
            majorAxisD50 = calculatePercentile(majorAxes, 50.0),
            majorAxisD84 = calculatePercentile(majorAxes, 84.0),
            minorAxisD16 = calculatePercentile(minorAxes, 16.0),
            minorAxisD50 = calculatePercentile(minorAxes, 50.0),
            minorAxisD84 = calculatePercentile(minorAxes, 84.0)
        )
    }

    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }

    private fun calculatePercentile(values: List<Double>, percentile: Double): Double {
        val sorted = values.sorted()
        val index = (percentile / 100.0) * (sorted.size - 1)
        val lower = kotlin.math.floor(index).toInt()
        val upper = kotlin.math.ceil(index).toInt()

        return if (lower == upper) {
            sorted[lower]
        } else {
            val weight = index - lower
            sorted[lower] * (1 - weight) + sorted[upper] * weight
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearResults() {
        _semanticResult.value = null
        _instanceResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        semanticProcessor?.close()
        instanceProcessor?.close()
        // Clean up all bitmap copies
        cleanupPreviousBitmaps()

        // Clean up original bitmap
        _originalBitmap.value?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }

        println("🔄 SharedSegmentationViewModel cleared, bitmaps recycled")
    }
}