package com.mcu.imagegrains.utils

import java.util.*

object WatershedUtils {

    /**
     * Watershed segmentation algorithm
     */
    fun watershed(
        image: Array<DoubleArray>,
        markers: Array<IntArray>,
        mask: Array<BooleanArray>
    ): Array<IntArray> {
        val height = image.size
        val width = image[0].size
        val labels = Array(height) { IntArray(width) }

        // Copy markers to labels
        for (i in 0 until height) {
            for (j in 0 until width) {
                if (markers[i][j] > 0 && mask[i][j]) {
                    labels[i][j] = markers[i][j]
                }
            }
        }

        // Priority queue for watershed flooding
        val queue = PriorityQueue<WatershedPixel> { a, b -> a.value.compareTo(b.value) }

        // Add border pixels of marked regions to queue
        val neighbors = arrayOf(
            intArrayOf(-1, 0), intArrayOf(1, 0),
            intArrayOf(0, -1), intArrayOf(0, 1)
        )

        for (i in 0 until height) {
            for (j in 0 until width) {
                if (labels[i][j] > 0) {
                    for (neighbor in neighbors) {
                        val ni = i + neighbor[0]
                        val nj = j + neighbor[1]

                        if (ni >= 0 && ni < height && nj >= 0 && nj < width &&
                            labels[ni][nj] == 0 && mask[ni][nj]) {
                            queue.add(WatershedPixel(ni, nj, image[ni][nj], labels[i][j]))
                        }
                    }
                }
            }
        }

        // Watershed flooding
        while (queue.isNotEmpty()) {
            val pixel = queue.poll()
            val i = pixel.i
            val j = pixel.j

            if (labels[i][j] != 0) continue // Already labeled

            labels[i][j] = pixel.label

            // Add unlabeled neighbors to queue
            for (neighbor in neighbors) {
                val ni = i + neighbor[0]
                val nj = j + neighbor[1]

                if (ni >= 0 && ni < height && nj >= 0 && nj < width &&
                    labels[ni][nj] == 0 && mask[ni][nj]) {
                    queue.add(WatershedPixel(ni, nj, image[ni][nj], pixel.label))
                }
            }
        }

        return labels
    }

    private data class WatershedPixel(
        val i: Int,
        val j: Int,
        val value: Double,
        val label: Int
    )
}