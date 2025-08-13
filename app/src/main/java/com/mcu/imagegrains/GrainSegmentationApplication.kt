package com.mcu.imagegrains

import android.app.Application

class GrainSegmentationApplication : Application() {

    companion object {
        lateinit var instance: GrainSegmentationApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Configure memory management
        configureMemorySettings()
    }

    private fun configureMemorySettings() {
        // Suggest aggressive garbage collection
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "1")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        println("⚠️ Low memory warning - forcing garbage collection")
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        println("⚠️ Memory trim level: $level")

        when (level) {
            TRIM_MEMORY_RUNNING_MODERATE,
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                System.gc()
            }
        }
    }
}