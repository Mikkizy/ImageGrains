package com.mcu.imagegrains.domain.models

data class InputTensorInfo(
    val index: Int,
    val shape: IntArray,
    val dataType: DataType,
    val quantizationParams: QuantizationParams?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InputTensorInfo

        if (index != other.index) return false
        if (!shape.contentEquals(other.shape)) return false
        if (dataType != other.dataType) return false
        if (quantizationParams != other.quantizationParams) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + shape.contentHashCode()
        result = 31 * result + dataType.hashCode()
        result = 31 * result + (quantizationParams?.hashCode() ?: 0)
        return result
    }
}

data class OutputTensorInfo(
    val index: Int,
    val shape: IntArray,
    val dataType: DataType,
    val quantizationParams: QuantizationParams?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OutputTensorInfo

        if (index != other.index) return false
        if (!shape.contentEquals(other.shape)) return false
        if (dataType != other.dataType) return false
        if (quantizationParams != other.quantizationParams) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + shape.contentHashCode()
        result = 31 * result + dataType.hashCode()
        result = 31 * result + (quantizationParams?.hashCode() ?: 0)
        return result
    }
}

data class QuantizationParams(
    val scale: Float,
    val zeroPoint: Int
)

enum class DataType {
    FLOAT32, UINT8, INT8
}