package com.mcu.imagegrains.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class GrainHistogramData(
    val actualXLimits: Pair<Double,Double>,
    val bins: List<Double>,
    val majorCounts: List<Int>,
    val minorCounts: List<Int>,
    val grainClasses: List<OptGrainSizeClass>,
    val phiMin: Double,
    val phiMax: Double,
    val maxCount: Int,
    val majorEcdf: List<Pair<Double, Double>>,  // (phi, 1–F(phi))
    val minorEcdf: List<Pair<Double, Double>>   // (phi, 1–F(phi))
)

data class OptGrainSizeClass(
    val name: String,
    val minPhi: Double,
    val maxPhi: Double
)

object GrainHistogram {

    private val classDefs: Map<String, Pair<Double,Double>> = mapOf(
        "very fine silt"   to (7.0 to 8.0),
        "fine silt"        to (6.0 to 7.0),
        "medium silt"      to (5.0 to 6.0),
        "coarse silt"      to (4.0 to 5.0),
        "very fine sand"   to (3.0 to 4.0),
        "fine sand"        to (2.0 to 3.0),
        "medium sand"      to (1.0 to 2.0),
        "coarse sand"      to (0.0 to 1.0),
        "very coarse sand" to (-1.0 to 0.0),
        "granule"          to (-2.0 to -1.0),
        "pebble"           to (-6.0 to -2.0),
        "cobble"           to (-8.0 to -6.0),
        "boulder"          to (-12.0 to -8.0)
    )

    /** exactly your Python area‐weighting logic */
    fun getAreaWeightedDistribution(
        grainSizes: List<Double>,
        areas: List<Double>
    ): List<Double> {
        if (areas.isEmpty() || grainSizes.size != areas.size) return grainSizes
        val meanArea = areas.average()
        val out = mutableListOf<Double>()
        for ((g, a) in grainSizes.zip(areas)) {
            val weight = max(1, (a / (0.5 * meanArea)).toInt())
            repeat(weight) { out += g }
        }
        return out
    }

    /** find the classes whose phi‐ranges overlap [phiMin,phiMax] */
    fun findGrainSizeClasses(phiMin: Double, phiMax: Double): List<OptGrainSizeClass> {
        return classDefs.mapNotNull { (name, bounds) ->
            val (lo, hi) = bounds
            if (lo < phiMax && hi > phiMin) OptGrainSizeClass(name, lo, hi) else null
        }.sortedBy { it.minPhi }
    }

    /** build histogram counts, phi‐bins, ECDF (1–F) and class list */
    fun createHistogramData(
        major: List<Double>,
        minor: List<Double>,
        areas: List<Double> = emptyList(),
        binSize: Double = 0.1,
        xLimits: Pair<Double, Double>? = null
    ): GrainHistogramData {
        // apply area weighting if requested
        val mj = if (areas.isNotEmpty()) getAreaWeightedDistribution(major, areas) else major
        val mn = if (areas.isNotEmpty()) getAreaWeightedDistribution(minor, areas) else minor

        // convert to phi
        val phiMaj = mj.map { -log2(it) }
        val phiMin = mn.map { -log2(it) }

        // determine phi‐range
        val (phiMax, phiMinVal) = if (xLimits != null) {
            val (x0, x1) = xLimits
            ceil(-log2(x0)) to floor(-log2(x1))
        } else {
            ceil(max(phiMaj.maxOrNull()!!, phiMin.maxOrNull()!!)) to
                    floor(min(phiMaj.minOrNull()!!, phiMin.minOrNull()!!))
        }
        val phiLow = phiMinVal

        // build bins
        val bins = generateSequence(phiLow) { it + binSize }
            .takeWhile { it <= phiMax }
            .toMutableList()
            .apply { add(phiMax) }

        // histogram counts
        fun hist(vals: List<Double>): List<Int> {
            val cnt = MutableList(bins.size - 1) { 0 }
            for (v in vals) {
                for (i in 0 until cnt.size) {
                    if (v >= bins[i] && v < bins[i+1]) {
                        cnt[i]++
                        break
                    }
                }
            }
            return cnt
        }
        val majCnt = hist(phiMaj)
        val minCnt = hist(phiMin)
        val maxCount = max(majCnt.maxOrNull() ?: 0, minCnt.maxOrNull() ?: 0)

        // ECDF (1–F): for each sorted phi, 1 - (i+1)/N
        fun complementEcdf(vals: List<Double>): List<Pair<Double,Double>> {
            val sorted = vals.sorted()
            val n = sorted.size
            return sorted.mapIndexed { i, phi ->
                phi to (1.0 - (i+1).toDouble()/n)
            }
        }
        val ecdfMaj = complementEcdf(phiMaj)
        val ecdfMin = complementEcdf(phiMin)

        val classes = findGrainSizeClasses(phiLow, phiMax)

        val actualXLimits = xLimits ?: run {
            val all = major + minor
            Pair(all.minOrNull()!!, all.maxOrNull()!!)
        }

        return GrainHistogramData(
            bins        = bins,
            majorCounts = majCnt,
            minorCounts = minCnt,
            grainClasses= classes,
            phiMin      = phiLow,
            phiMax      = phiMax,
            maxCount    = maxCount,
            majorEcdf   = ecdfMaj,
            minorEcdf   = ecdfMin,
            actualXLimits   = actualXLimits
        )
    }

    /** draw exactly like your Python plt version */
    fun createHistogramBitmap(
        data: GrainHistogramData,
        width: Int,
        height: Int,
        showGrainClassification: Boolean = true,
        showECDFCurves: Boolean = true
    ): Bitmap {
        val bmp = createBitmap(width, height)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFFFFFFF.toInt())

        // padding & plot area
        val pad = 80f
        val plotW = width  - 2*pad
        val plotH = height - 2*pad

        // paints
        val barMaj     = Paint().apply { style = Paint.Style.FILL;  color = 0x800000FF.toInt() }
        val barMin     = Paint().apply { style = Paint.Style.FILL;  color = 0x80FF7F00.toInt() }
        val ecdfMaj    = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xFF0000FF.toInt() }
        val ecdfMin    = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xFFFF7F00.toInt() }
        val axisPaint  = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFF000000.toInt() }
        val textPaint  = Paint().apply { isAntiAlias = true; textSize = 24f; color = 0xFF000000.toInt() }
        val smallText  = Paint().apply { isAntiAlias = true; textSize = 20f; color = 0xFF000000.toInt() }
        val bgClass    = Paint().apply { style = Paint.Style.FILL; color = 0x30CCCCCC.toInt() }

        // φ‐range
        val φ_hi = data.phiMax
        val φ_lo = data.phiMin
        val φ_span = φ_hi - φ_lo

        // φ→x (reversed so φ_hi is left, φ_lo is right)
        fun mapX(phi: Double) =
            pad + ((φ_hi - phi)/φ_span * plotW).toFloat()

        // 1) draw grain‐class backgrounds
        if (showGrainClassification) {
            data.grainClasses.forEach { cls ->
                val x0 = mapX(cls.maxPhi)
                val x1 = mapX(cls.minPhi)
                canvas.drawRect(x0, pad, x1, height - pad, bgClass)
                canvas.drawLine(x0, pad, x0, height - pad, axisPaint)
                // label
                val labelY = height - pad + 40f
                canvas.save()
                canvas.rotate(-90f, (x0+x1)/2, labelY)
                canvas.drawText(cls.name, (x0+x1)/2 - smallText.measureText(cls.name)/2, labelY, smallText)
                canvas.restore()
            }
        }

        // 2) axes
        canvas.drawLine(pad, pad, pad, height - pad, axisPaint)         // left Y
        canvas.drawLine(pad, height - pad, width - pad, height - pad, axisPaint) // bottom X

        // 3) histogram bars
        for (i in data.majorCounts.indices) {
            val left  = mapX(data.bins[i+1])
            val right = mapX(data.bins[i])
            val hMaj  = data.majorCounts[i].toFloat() / data.maxCount * plotH
            val hMin  = data.minorCounts[i].toFloat() / data.maxCount * plotH

            canvas.drawRect(left, height - pad - hMaj, right, height - pad, barMaj)
            canvas.drawRect(left, height - pad - hMin, right, height - pad, barMin)
        }

        // 4) ECDFs
        if (showECDFCurves) {
            val pathMaj = Path().apply {
                data.majorEcdf.forEachIndexed { idx, (phi, p) ->
                    val x = mapX(phi)
                    val y = height - pad - (p.toFloat()*plotH)
                    if (idx==0) moveTo(x,y) else lineTo(x,y)
                }
            }
            canvas.drawPath(pathMaj, ecdfMaj)

            val pathMin = Path().apply {
                data.minorEcdf.forEachIndexed { idx, (phi, p) ->
                    val x = mapX(phi)
                    val y = height - pad - (p.toFloat()*plotH)
                    if (idx==0) moveTo(x,y) else lineTo(x,y)
                }
            }
            canvas.drawPath(pathMin, ecdfMin)
        }

        // 5) left‐axis count ticks & labels
        val maxC = data.maxCount
        val step = when {
            maxC <= 5   -> 1
            maxC <= 10  -> 2
            maxC <= 25  -> 5
            maxC <= 50  -> 10
            maxC <= 100 -> 20
            else        -> ((maxC/10)/5 + 1)*5
        }
        val steps = data.maxCount / step
        for (i in 0..steps) {
            val count = i * step
            val y = height - pad - (count.toFloat()/data.maxCount * plotH)
            // tick
            canvas.drawLine(pad - 10f, y, pad, y, axisPaint)
            // label
            canvas.drawText(count.toString(), pad - 50f, y + 8f, smallText)
        }

        // 6) right‐axis cumulative‐probability ticks & labels
        for (i in 0..5) {
            val p = i / 5.0f
            val y = height - pad - (p * plotH)
            canvas.drawLine(width - pad, y, width - pad + 10f, y, axisPaint)
            canvas.drawText(String.format("%.1f", p), width - pad + 20f, y + 8f, smallText)
        }

        // 7) bottom‐axis φ‐scale (top of plot)
        for (cls in data.grainClasses) {
            // draw tick at each class boundary
            listOf(cls.minPhi, cls.maxPhi).distinct().forEach { φ ->
                val x = mapX(φ)
                canvas.drawLine(x, pad, x, pad - 10f, axisPaint)
                canvas.drawText(
                    "%.0f".format(φ),
                    x - smallText.measureText("%.0f".format(φ))/2,
                    pad - 15f,
                    smallText
                )
            }
        }
        // label
        canvas.drawText("phi scale", width/2f - textPaint.measureText("phi scale")/2, pad-40f, textPaint)

        // 8) bottom‐axis grain‐size labels (mm), 6 evenly spaced in φ→mm
        for (i in 0..6) {
            val φ = data.phiMax - i*(data.phiMax - data.phiMin)/6
            val mm = 2.0.pow(-φ)
            val x  = mapX(φ)
            val lbl = if (mm<10) "%.1f".format(mm) else "%.0f".format(mm)
            canvas.drawText(lbl, x - smallText.measureText(lbl)/2, height - pad + 30f, smallText)
        }
        canvas.drawText("grain axis length (mm)",
            width/2f - textPaint.measureText("grain axis length (mm)")/2,
            height - 10f, textPaint)

        // 9) side‐labels
        canvas.save();  canvas.rotate(-90f, 20f, height/2f);  canvas.drawText("count", 20f, height/2f, textPaint);  canvas.restore()
        canvas.save();  canvas.rotate(90f, width - 20f, height/2f); canvas.drawText("cum. prob.", width - 20f, height/2f, textPaint); canvas.restore()

        return bmp
    }
}
