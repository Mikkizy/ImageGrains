package com.mcu.imagegrains.utils

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree
import org.apache.lucene.spatial.query.SpatialArgs
import org.apache.lucene.spatial.query.SpatialOperation
import org.apache.lucene.store.RAMDirectory
import org.locationtech.jts.geom.Polygon
import org.locationtech.spatial4j.context.SpatialContext
import org.locationtech.spatial4j.shape.ShapeFactory

class SpatialPolygonIndex {

    private val spatialContext: SpatialContext = SpatialContext.GEO
    private val shapeFactory: ShapeFactory = spatialContext.shapeFactory
    private val grid: SpatialPrefixTree = GeohashPrefixTree(spatialContext, 11)
    private val strategy = RecursivePrefixTreeStrategy(grid, "geom")

    private val directory = RAMDirectory()
    private val analyzer = StandardAnalyzer()
    private val indexWriterConfig = IndexWriterConfig(analyzer)
    private var indexWriter: IndexWriter? = null
    private var indexSearcher: IndexSearcher? = null

    private val polygons = mutableListOf<Polygon>()

    init {
        indexWriter = IndexWriter(directory, indexWriterConfig)
    }

    /**
     * Add polygon to spatial index
     */
    fun addPolygon(polygon: Polygon, id: Int) {
        polygons.add(polygon)

        // Convert JTS polygon to spatial4j shape
        val envelope = polygon.envelopeInternal
        val rectangle = shapeFactory.rect(
            envelope.minX, envelope.maxX,
            envelope.minY, envelope.maxY
        )

        // Create document for indexing
        val document = Document().apply {
            add(StringField("id", id.toString(), Field.Store.YES))
            val indexableFields = strategy.createIndexableFields(rectangle)
            for (field in indexableFields) {
                add(field)
            }
        }

        indexWriter?.addDocument(document)
    }

    /**
     * Finalize index for searching
     */
    fun finalizeIndex() {
        indexWriter?.close()
        val reader = DirectoryReader.open(directory)
        indexSearcher = IndexSearcher(reader)
    }

    /**
     * Find all polygons that potentially intersect with the given polygon
     */
    fun findIntersectingPolygons(queryPolygon: Polygon): List<Int> {
        val searcher = indexSearcher ?: return emptyList()

        // Convert query polygon to spatial4j shape
        val envelope = queryPolygon.envelopeInternal
        val queryShape = shapeFactory.rect(
            envelope.minX, envelope.maxX,
            envelope.minY, envelope.maxY
        )

        // Create spatial query
        val spatialArgs = SpatialArgs(SpatialOperation.Intersects, queryShape)
        val query = strategy.makeQuery(spatialArgs)

        // Search
        val topDocs = searcher.search(query, polygons.size)
        val results = mutableListOf<Int>()

        for (scoreDoc in topDocs.scoreDocs) {
            val doc = searcher.doc(scoreDoc.doc)
            val id = doc.get("id").toInt()
            results.add(id)
        }

        return results
    }

    /**
     * Get polygon by ID
     */
    fun getPolygon(id: Int): Polygon? {
        return if (id >= 0 && id < polygons.size) polygons[id] else null
    }

    /**
     * Get total number of indexed polygons
     */
    fun size(): Int = polygons.size

    /**
     * Close and cleanup resources
     */
    fun close() {
        try {
            indexWriter?.close()
            indexSearcher?.indexReader?.close()
            directory.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

object SpatialIndexingUtils {

    /**
     * Find overlapping polygons using spatial indexing (equivalent to Python's rtree)
     */
    fun findOverlappingPolygons(
        polygons: List<Polygon>,
        minOverlap: Double = 0.4,
        progressCallback: (Float) -> Unit = {}
    ): List<Pair<Int, Int>> {

        if (polygons.isEmpty()) return emptyList()

        println("🔄 Building spatial index for ${polygons.size} polygons...")

        // Build spatial index
        val spatialIndex = SpatialPolygonIndex()

        polygons.forEachIndexed { index, polygon ->
            spatialIndex.addPolygon(polygon, index)
        }
        spatialIndex.finalizeIndex()

        println("✅ Spatial index built successfully")

        val overlappingPairs = mutableListOf<Pair<Int, Int>>()
        val processedPairs = mutableSetOf<Pair<Int, Int>>()

        // Find overlapping polygons using spatial index
        polygons.forEachIndexed { i, polygon1 ->

            // Use spatial index to find potential intersections
            val candidateIds = spatialIndex.findIntersectingPolygons(polygon1)

            for (j in candidateIds) {
                if (i >= j) continue // Avoid duplicates and self-comparison

                val pairKey = if (i < j) Pair(i, j) else Pair(j, i)
                if (processedPairs.contains(pairKey)) continue

                val polygon2 = spatialIndex.getPolygon(j) ?: continue

                try {
                    // Fix invalid geometries
                    val fixedPoly1 = if (!polygon1.isValid) {
                        polygon1.buffer(0.0)
                    } else {
                        polygon1
                    }

                    val fixedPoly2 = if (!polygon2.isValid) {
                        polygon2.buffer(0.0)
                    } else {
                        polygon2
                    }

                    // Check actual intersection
                    if (fixedPoly1.intersects(fixedPoly2)) {
                        val intersection = fixedPoly1.intersection(fixedPoly2)
                        val intersectionArea = intersection.area
                        val minArea = minOf(fixedPoly1.area, fixedPoly2.area)

                        if (intersectionArea > minOverlap * minArea) {
                            overlappingPairs.add(pairKey)
                        }
                    }

                    processedPairs.add(pairKey)

                } catch (e: Exception) {
                    println("❌ Error checking intersection between polygons $i and $j: ${e.message}")
                }
            }

            // Update progress
            if (i % 100 == 0) {
                val progress = i.toFloat() / polygons.size
                progressCallback(progress)
            }
        }

        spatialIndex.close()

        println("✅ Found ${overlappingPairs.size} overlapping polygon pairs")
        return overlappingPairs
    }

    /**
     * Create spatial grid for efficient neighborhood queries
     */
    fun createSpatialGrid(
        polygons: List<Polygon>,
        gridSize: Double = 100.0
    ): SpatialGrid {

        if (polygons.isEmpty()) {
            return SpatialGrid(0.0, 0.0, 0.0, 0.0, gridSize)
        }

        // Calculate bounds
        var minX = Double.MAX_VALUE
        var maxX = Double.MIN_VALUE
        var minY = Double.MAX_VALUE
        var maxY = Double.MIN_VALUE

        for (polygon in polygons) {
            val envelope = polygon.envelopeInternal
            minX = minOf(minX, envelope.minX)
            maxX = maxOf(maxX, envelope.maxX)
            minY = minOf(minY, envelope.minY)
            maxY = maxOf(maxY, envelope.maxY)
        }

        val grid = SpatialGrid(minX, minY, maxX, maxY, gridSize)

        // Add polygons to grid
        polygons.forEachIndexed { index, polygon ->
            grid.addPolygon(polygon, index)
        }

        return grid
    }
}

/**
 * Simple spatial grid for fast neighborhood queries
 */
class SpatialGrid(
    private val minX: Double,
    private val minY: Double,
    private val maxX: Double,
    private val maxY: Double,
    private val cellSize: Double
) {
    private val numCols = ((maxX - minX) / cellSize).toInt() + 1
    private val numRows = ((maxY - minY) / cellSize).toInt() + 1
    private val grid = Array(numRows) { Array(numCols) { mutableListOf<Int>() } }

    fun addPolygon(polygon: Polygon, id: Int) {
        val envelope = polygon.envelopeInternal

        val startCol = ((envelope.minX - minX) / cellSize).toInt().coerceAtLeast(0)
        val endCol = ((envelope.maxX - minX) / cellSize).toInt().coerceAtMost(numCols - 1)
        val startRow = ((envelope.minY - minY) / cellSize).toInt().coerceAtLeast(0)
        val endRow = ((envelope.maxY - minY) / cellSize).toInt().coerceAtMost(numRows - 1)

        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                grid[row][col].add(id)
            }
        }
    }

    fun getNeighbors(polygon: Polygon): List<Int> {
        val envelope = polygon.envelopeInternal
        val neighbors = mutableSetOf<Int>()

        val startCol = ((envelope.minX - minX) / cellSize).toInt().coerceAtLeast(0)
        val endCol = ((envelope.maxX - minX) / cellSize).toInt().coerceAtMost(numCols - 1)
        val startRow = ((envelope.minY - minY) / cellSize).toInt().coerceAtLeast(0)
        val endRow = ((envelope.maxY - minY) / cellSize).toInt().coerceAtMost(numRows - 1)

        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                neighbors.addAll(grid[row][col])
            }
        }

        return neighbors.toList()
    }
}