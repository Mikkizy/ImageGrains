package com.mcu.imagegrains.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GrainSessionDao {
    @Query("SELECT * FROM grain_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<GrainSession>>

    @Query("SELECT * FROM grain_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): GrainSession?

    @Query("SELECT * FROM grain_sessions WHERE id IN (:ids)")
    suspend fun getSessionsByIds(ids: List<String>): List<GrainSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: GrainSession)

    @Delete
    suspend fun deleteSession(session: GrainSession)

    @Query("DELETE FROM grain_sessions WHERE id IN (:ids)")
    suspend fun deleteSessionsByIds(ids: List<String>)
}