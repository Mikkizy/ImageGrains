package com.mcu.imagegrains.utils

import kotlin.math.*

object DBSCANUtils {

    data class DBSCANResult(
        val labels: IntArray,
        val numClusters: Int
    )

    /**
     * DBSCAN clustering algorithm
     */
    fun dbscan(
        points: Array<DoubleArray>,
        eps: Double,
        minSamples: Int
    ): DBSCANResult {
        val n = points.size
        val labels = IntArray(n) { -2 } // -2: unclassified, -1: noise, >=0: cluster
        var clusterId = 0

        for (i in 0 until n) {
            if (labels[i] != -2) continue // Already classified

            val neighbors = findNeighbors(points, i, eps)

            if (neighbors.size < minSamples) {
                labels[i] = -1 // Mark as noise
            } else {
                expandCluster(points, i, neighbors, clusterId, eps, minSamples, labels)
                clusterId++
            }
        }

        return DBSCANResult(labels, clusterId)
    }

    private fun findNeighbors(
        points: Array<DoubleArray>,
        pointIndex: Int,
        eps: Double
    ): MutableList<Int> {
        val neighbors = mutableListOf<Int>()
        val point = points[pointIndex]

        for (i in points.indices) {
            if (euclideanDistance(point, points[i]) <= eps) {
                neighbors.add(i)
            }
        }

        return neighbors
    }

    private fun expandCluster(
        points: Array<DoubleArray>,
        pointIndex: Int,
        neighbors: MutableList<Int>,
        clusterId: Int,
        eps: Double,
        minSamples: Int,
        labels: IntArray
    ) {
        labels[pointIndex] = clusterId
        var i = 0

        while (i < neighbors.size) {
            val neighborIndex = neighbors[i]

            if (labels[neighborIndex] == -1) {
                labels[neighborIndex] = clusterId // Change noise to border point
            } else if (labels[neighborIndex] == -2) {
                labels[neighborIndex] = clusterId

                val neighborNeighbors = findNeighbors(points, neighborIndex, eps)
                if (neighborNeighbors.size >= minSamples) {
                    // Merge neighbors
                    for (newNeighbor in neighborNeighbors) {
                        if (!neighbors.contains(newNeighbor)) {
                            neighbors.add(newNeighbor)
                        }
                    }
                }
            }
            i++
        }
    }

    private fun euclideanDistance(point1: DoubleArray, point2: DoubleArray): Double {
        var sum = 0.0
        for (i in point1.indices) {
            val diff = point1[i] - point2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}