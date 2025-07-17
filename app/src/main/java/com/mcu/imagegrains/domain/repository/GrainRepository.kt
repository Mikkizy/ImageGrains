package com.mcu.imagegrains.domain.repository

import com.google.gson.Gson
import com.mcu.imagegrains.data.local.GrainSession
import com.mcu.imagegrains.data.local.GrainSessionDao
import com.mcu.imagegrains.domain.models.GrainStatistics
import com.mcu.imagegrains.domain.models.ScaleCalibration
import com.mcu.imagegrains.domain.models.ScaledGrainData
import com.mcu.imagegrains.utils.GrainHistogramData
import com.mcu.imagegrains.utils.ImageUtils

class GrainRepository(private val dao: GrainSessionDao) {

    fun getAllSessions() = dao.getAllSessions()

    suspend fun saveSession(
        name: String,
        imagePath: String,
        scaleCalibration: ScaleCalibration,
        grainData: ScaledGrainData,
        statistics: GrainStatistics,
        histogramData: GrainHistogramData
    ): String {
        val session = GrainSession(
            name = name,
            imagePath = imagePath,
            scaleCalibration = Gson().toJson(scaleCalibration),
            grainData = Gson().toJson(grainData),
            statistics = Gson().toJson(statistics),
            histogramData = Gson().toJson(histogramData)
        )
        dao.insertSession(session)
        return session.id
    }

    suspend fun getSession(id: String): GrainSession? {
        return dao.getSessionById(id)
    }

    suspend fun getSessions(ids: List<String>): List<GrainSession> {
        return dao.getSessionsByIds(ids)
    }

    suspend fun deleteSession(session: GrainSession) {
        ImageUtils.deleteImage(session.imagePath)
        dao.deleteSession(session)
    }

    suspend fun deleteSessions(ids: List<String>) {
        val sessions = dao.getSessionsByIds(ids)
        sessions.forEach { session ->
            ImageUtils.deleteImage(session.imagePath)
        }
        dao.deleteSessionsByIds(ids)
    }

    // Helper functions to deserialize data
    fun parseStatistics(session: GrainSession): GrainStatistics {
        return Gson().fromJson(session.statistics, GrainStatistics::class.java)
    }

    fun parseHistogramData(session: GrainSession): GrainHistogramData {
        return Gson().fromJson(session.histogramData, GrainHistogramData::class.java)
    }

    fun parseGrainData(session: GrainSession): ScaledGrainData {
        return Gson().fromJson(session.grainData, ScaledGrainData::class.java)
    }

    fun parseScaleCalibration(session: GrainSession): ScaleCalibration {
        return Gson().fromJson(session.scaleCalibration, ScaleCalibration::class.java)
    }
}