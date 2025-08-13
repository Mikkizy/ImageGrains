package com.mcu.imagegrains.domain

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import org.locationtech.jts.precision.GeometryPrecisionReducer
import kotlin.math.*

/**
 * Geometry optimizer to fix precision issues and improve performance
 */
class GeometryOptimizer {
    private val precisionReducer = GeometryPrecisionReducer(PrecisionModel(100.0))

    /**
     * Optimize polygon to fix geometry errors and improve performance
     */
    fun optimizePolygon(polygon: Polygon): Polygon? {
        return try {
            var optimized = polygon

            optimized = reducePrecision(optimized)

            optimized = fixGeometry(optimized)

            optimized = simplifyIfNeeded(optimized)

            if (optimized.isValid && !optimized.isEmpty && optimized.area > 10) {
                optimized
            } else {
                null
            }

        } catch (e: Exception) {
            println("Error optimizing polygon: ${e.message}")
            null
        }
    }

    /**
     * Reduce coordinate precision to avoid floating-point errors
     */
    private fun reducePrecision(polygon: Polygon): Polygon {
        return try {
            val reduced = precisionReducer.reduce(polygon)
            reduced as? Polygon ?: polygon
        } catch (_: Exception) {
            polygon
        }
    }

    /**
     * Fix invalid geometry using JTS GeometryFixer
     */
    private fun fixGeometry(polygon: Polygon): Polygon {
        return try {
            if (!polygon.isValid) {
                val fixed = GeometryFixer.fix(polygon)
                when (fixed) {
                    is Polygon -> fixed
                    is MultiPolygon -> {
                        // Return largest polygon from multipolygon
                        var largest = polygon
                        var maxArea = 0.0

                        for (i in 0 until fixed.numGeometries) {
                            val geom = fixed.getGeometryN(i)
                            if (geom is Polygon && geom.area > maxArea) {
                                maxArea = geom.area
                                largest = geom
                            }
                        }
                        largest
                    }
                    else -> polygon
                }
            } else {
                polygon
            }
        } catch (_: Exception) {
            polygon
        }
    }

    /**
     * Simplify polygon if it has too many vertices
     */
    private fun simplifyIfNeeded(polygon: Polygon): Polygon {
        return try {
            val numVertices = polygon.numPoints

            if (numVertices > 500) {
                // Simplify using Douglas-Peucker algorithm
                val tolerance = max(1.0, sqrt(polygon.area) / 100.0)
                val simplified = DouglasPeuckerSimplifier.simplify(polygon, tolerance)

                if (simplified is Polygon && simplified.isValid && simplified.area > polygon.area * 0.8) {
                    simplified
                } else {
                    polygon
                }
            } else {
                polygon
            }
        } catch (_: Exception) {
            polygon
        }
    }

}