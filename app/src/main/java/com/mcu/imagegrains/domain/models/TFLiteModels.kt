package com.mcu.imagegrains.domain.models

data class InputTensorInfo(
    val index: Int,
    val shape: IntArray,
    val dataType: DataType,
    val quantizationParams: QuantizationParams?
)

data class OutputTensorInfo(
    val index: Int,
    val shape: IntArray,
    val dataType: DataType,
    val quantizationParams: QuantizationParams?
)

data class QuantizationParams(
    val scale: Float,
    val zeroPoint: Int
)

enum class DataType {
    FLOAT32, UINT8, INT8
}