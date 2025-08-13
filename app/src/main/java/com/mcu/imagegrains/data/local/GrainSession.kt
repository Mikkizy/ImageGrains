package com.mcu.imagegrains.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "grain_sessions")
data class GrainSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String,
    val scaleCalibration: String, // JSON string of ScaleCalibration
    val grainData: String, // JSON string of ScaledGrainData
    val statistics: String, // JSON string of GrainStatistics
    val histogramData: String // JSON string of GrainHistogramData
)
