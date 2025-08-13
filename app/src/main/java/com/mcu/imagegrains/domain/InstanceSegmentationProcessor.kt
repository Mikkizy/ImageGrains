package com.mcu.imagegrains.domain

import android.graphics.Bitmap
import com.mcu.imagegrains.domain.models.GrainProperties
import org.locationtech.jts.geom.Polygon

data class InstanceSegmentationResult(
    val allGrains: List<Polygon>,
    val labelsOut: Array<IntArray>,
    val maskAll: Array<IntArray>,
    val grainData: List<GrainProperties>,
    val processingStats: ProcessingStats
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InstanceSegmentationResult

        if (allGrains != other.allGrains) return false
        if (!labelsOut.contentDeepEquals(other.labelsOut)) return false
        if (!maskAll.contentDeepEquals(other.maskAll)) return false
        if (grainData != other.grainData) return false
        if (processingStats != other.processingStats) return false

        return true
    }

    override fun hashCode(): Int {
        var result = allGrains.hashCode()
        result = 31 * result + labelsOut.contentDeepHashCode()
        result = 31 * result + maskAll.contentDeepHashCode()
        result = 31 * result + grainData.hashCode()
        result = 31 * result + processingStats.hashCode()
        return result
    }
}

data class CompleteSegmentationResult(
    val initialResult: InstanceSegmentationResult,
    val finalGrains: List<Polygon>,
    val finalLabels: Array<IntArray>,
    val finalMask: Array<IntArray>,
    val finalGrainData: List<GrainProperties>,
    val finalVisualization: Bitmap
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompleteSegmentationResult

        if (initialResult != other.initialResult) return false
        if (finalGrains != other.finalGrains) return false
        if (!finalLabels.contentDeepEquals(other.finalLabels)) return false
        if (!finalMask.contentDeepEquals(other.finalMask)) return false
        if (finalGrainData != other.finalGrainData) return false
        if (finalVisualization != other.finalVisualization) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initialResult.hashCode()
        result = 31 * result + finalGrains.hashCode()
        result = 31 * result + finalLabels.contentDeepHashCode()
        result = 31 * result + finalMask.contentDeepHashCode()
        result = 31 * result + finalGrainData.hashCode()
        result = 31 * result + finalVisualization.hashCode()
        return result
    }
}

data class ProcessingStats(
    val totalCoordinates: Int,
    val successfulCoordinates: Int,
    val finalGrainCount: Int,
    val processingTimeMs: Long
)