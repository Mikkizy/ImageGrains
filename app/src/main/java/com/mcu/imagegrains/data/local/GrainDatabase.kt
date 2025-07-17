package com.mcu.imagegrains.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Database(
    entities = [GrainSession::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GrainDatabase : RoomDatabase() {
    abstract fun grainSessionDao(): GrainSessionDao

    companion object {
        @Volatile
        private var INSTANCE: GrainDatabase? = null

        fun getDatabase(context: Context): GrainDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GrainDatabase::class.java,
                    "grain_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        return Gson().fromJson(value, object : TypeToken<List<String>>() {}.type)
    }

    @TypeConverter
    fun fromListString(list: List<String>): String {
        return Gson().toJson(list)
    }
}