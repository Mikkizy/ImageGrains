package com.mcu.imagegrains.domain.models

data class ScaleCalibration(
    val pixelLength: Double,  // Length in pixels
    val realLength: Double,   // Real length in chosen units
    val unit: String          // Unit name (e.g., "cm", "mm", "inches")
) {
    val unitsPerPixel: Double get() = realLength / pixelLength
}

data class ScaledGrainProperties(
    val label: Int,
    val area: Double,           // Scaled area
    val centroidX: Double,      // Scaled coordinates
    val centroidY: Double,
    val majorAxisLength: Double, // Scaled lengths
    val minorAxisLength: Double,
    val orientation: Double,     // Not scaled
    val perimeter: Double,       // Scaled perimeter
    val maxIntensity: Double,    // Not scaled
    val meanIntensity: Double,
    val minIntensity: Double
)

data class ScaledGrainData(
    val scaledGrains: List<ScaledGrainProperties>,
    val scaleCalibration: ScaleCalibration,
    val statistics: GrainStatistics
)

data class GrainStatistics(
    val count: Int,
    val areaMean: Double,
    val areaStd: Double,
    val areaMin: Double,
    val areaQ25: Double,
    val areaQ50: Double,
    val areaQ75: Double,
    val areaMax: Double,
    val majorAxisMean: Double,
    val majorAxisStd: Double,
    val majorAxisMin: Double,
    val majorAxisMax: Double,
    val minorAxisMean: Double,
    val minorAxisStd: Double,
    val minorAxisMin: Double,
    val minorAxisMax: Double
)