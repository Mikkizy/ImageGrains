package com.mcu.imagegrains.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mcu.imagegrains.domain.models.ScaledGrainData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CSVExportUtils {

    /**
     * Export grain data to CSV file
     */
    suspend fun exportGrainDataToCSV(
        context: Context,
        grainData: ScaledGrainData,
        fileName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val csvFileName = fileName ?: "grain_analysis_$timestamp.csv"

            val file = File(context.getExternalFilesDir("exports"), csvFileName)
            file.parentFile?.mkdirs()

            FileWriter(file).use { writer ->
                // Write header
                writer.append("label,area,centroid_x,centroid_y,major_axis_length,minor_axis_length,orientation,perimeter,max_intensity,mean_intensity,min_intensity\n")

                // Write data
                grainData.scaledGrains.forEach { grain ->
                    writer.append("${grain.label},")
                    writer.append("${grain.area},")
                    writer.append("${grain.centroidX},")
                    writer.append("${grain.centroidY},")
                    writer.append("${grain.majorAxisLength},")
                    writer.append("${grain.minorAxisLength},")
                    writer.append("${grain.orientation},")
                    writer.append("${grain.perimeter},")
                    writer.append("${grain.maxIntensity},")
                    writer.append("${grain.meanIntensity},")
                    writer.append("${grain.minIntensity}\n")
                }

                // Write metadata
                writer.append("\n# Metadata\n")
                writer.append("# Scale: ${grainData.scaleCalibration.unitsPerPixel} ${grainData.scaleCalibration.unit}/pixel\n")
                writer.append("# Real length: ${grainData.scaleCalibration.realLength} ${grainData.scaleCalibration.unit}\n")
                writer.append("# Pixel length: ${grainData.scaleCalibration.pixelLength} pixels\n")
                writer.append("# Export date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            }

            // Return file URI
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Share CSV file
     */
    suspend fun shareCSVFile(
        context: Context,
        grainData: ScaledGrainData
    ) = withContext(Dispatchers.Main) {
        val uri = withContext(Dispatchers.IO) {
            exportGrainDataToCSV(context, grainData)
        }

        if (uri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Grain Analysis Results")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Grain Data"))
        }
    }
}