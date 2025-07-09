package com.mcu.imagegrains.domain.models

data class GrainProperties(
    val label: Int,
    val area: Double,
    val centroidX: Double,
    val centroidY: Double,
    val majorAxisLength: Double = 0.0,
    val minorAxisLength: Double = 0.0,
    val orientation: Double = 0.0,
    val perimeter: Double = 0.0,
    val maxIntensity: Double = 0.0,
    val meanIntensity: Double = 0.0,
    val minIntensity: Double = 0.0
)

data class LabelingResult(
    val labelsSimple: Array<IntArray>,
    val allCoords: Array<IntArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LabelingResult

        if (!labelsSimple.contentDeepEquals(other.labelsSimple)) return false
        if (!allCoords.contentDeepEquals(other.allCoords)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = labelsSimple.contentDeepHashCode()
        result = 31 * result + allCoords.contentDeepHashCode()
        return result
    }
}