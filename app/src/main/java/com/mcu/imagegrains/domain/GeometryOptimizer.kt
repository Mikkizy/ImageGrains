package com.mcu.imagegrains.utils

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import org.locationtech.jts.operation.buffer.BufferOp
import org.locationtech.jts.operation.buffer.BufferParameters
import org.locationtech.jts.precision.GeometryPrecisionReducer
import kotlin.math.*

/**
 * Geometry optimizer to fix precision issues and improve performance
 */
class GeometryOptimizer {
    private val geometryFactory = GeometryFactory(PrecisionModel(1000.0)) // Reduced precision
    private val precisionReducer = GeometryPrecisionReducer(PrecisionModel(100.0))

    /**
     * Optimize polygon to fix geometry errors and improve performance
     */
    fun optimizePolygon(polygon: Polygon): Polygon? {
        return try {
            var optimized = polygon

            // Step 1: Reduce precision to avoid floating point errors
            optimized = reducePrecision(optimized)

            // Step 2: Fix invalid geometry
            optimized = fixGeometry(optimized)

            // Step 3: Simplify if too complex
            optimized = simplifyIfNeeded(optimized)

            // Step 4: Final validation
            if (optimized.isValid && !optimized.isEmpty && optimized.area > 10) {
                optimized
            } else {
                null
            }

        } catch (e: Exception) {
            println("⚠️ Error optimizing polygon: ${e.message}")
            null
        }
    }

    /**
     * Reduce coordinate precision to avoid floating-point errors
     */
    private fun reducePrecision(polygon: Polygon): Polygon {
        return try {
            val reduced = precisionReducer.reduce(polygon)
            if (reduced is Polygon) reduced else polygon
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            polygon
        }
    }

    /**
     * Create a robust buffer operation that handles edge cases
     */
    /*fun createRobustBuffer(polygon: Polygon, distance: Double): Geometry? {
        return try {
            val bufferOp = BufferOp(polygon)
            bufferOp.setEndCapStyle(BufferParameters.CAP_ROUND)
            bufferOp.joinStyle = BufferParameters.JOIN_ROUND
            bufferOp.getResultGeometry(distance)
        } catch (e: Exception) {
            println("⚠️ Error creating buffer: ${e.message}")
            polygon.buffer(distance) // Fallback to simple buffer
        }
    }*/

    /**
     * Snap coordinates to integer grid to reduce precision issues
     */
    fun snapToGrid(coordinate: Coordinate, gridSize: Double = 1.0): Coordinate {
        return Coordinate(
            round(coordinate.x / gridSize) * gridSize,
            round(coordinate.y / gridSize) * gridSize
        )
    }

    /**
     * Create polygon with snapped coordinates
     */
    fun createSnappedPolygon(coordinates: Array<Coordinate>): Polygon? {
        return try {
            val snappedCoords = coordinates.map { snapToGrid(it) }.toTypedArray()

            // Ensure polygon is closed
            if (snappedCoords.isNotEmpty()) {
                val finalCoords = if (snappedCoords.first() != snappedCoords.last()) {
                    snappedCoords + snappedCoords.first()
                } else {
                    snappedCoords
                }

                if (finalCoords.size >= 4) { // Minimum for valid polygon
                    val polygon = geometryFactory.createPolygon(finalCoords)
                    optimizePolygon(polygon)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("⚠️ Error creating snapped polygon: ${e.message}")
            null
        }
    }
}