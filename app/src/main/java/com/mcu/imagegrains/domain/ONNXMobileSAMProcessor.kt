package com.mcu.imagegrains.domain

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import com.mcu.imagegrains.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.*

class ONNXMobileSAMProcessor(
    private val context: Context
) {
    private var encoderSession: OrtSession? = null
    private var predictorSession: OrtSession? = null
    private var ortEnvironment: OrtEnvironment? = null

    // Image processing info
    private var cachedEmbeddings: OnnxTensor? = null
    private var cachedImageHash: Int? = null
    private var originalImageShape: Pair<Int, Int>? = null
    private val targetLength = 1024 // SAM's standard

    // SAM normalization constants
    private val meanValues = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val stdValues = floatArrayOf(0.229f, 0.224f, 0.225f)

    data class EmbeddingResult(
        val embeddings: OnnxTensor,
        val originalShape: Pair<Int, Int>
    )

    /**
     * Initialize ONNX sessions
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()

            // Create session options
            val sessionOptions = OrtSession.SessionOptions().apply {
                // Use NNAPI if available on Android
                try {
                    addNnapi()
                    println("✅ Using NNAPI for ONNX inference")
                } catch (e: Exception) {
                    println("📱 NNAPI not available, using CPU")
                }
            }

            // Load models from assets
            //val encoderBytes = loadModelFromAssets(encoderModelPath)
            //val predictorBytes = loadModelFromAssets(predictorModelPath)

            val encoderBytes = loadModel(R.raw.mobile_sam_encoder)
            val predictorBytes = loadModel(R.raw.mobile_sam_onnx_version)

            encoderSession = ortEnvironment!!.createSession(encoderBytes, sessionOptions)
            predictorSession = ortEnvironment!!.createSession(predictorBytes, sessionOptions)

            println("✅ ONNX MobileSAM models loaded successfully")
            println("Encoder inputs: ${encoderSession!!.inputNames}")
            println("Predictor inputs: ${predictorSession!!.inputNames}")

            true
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Failed to initialize ONNX models: ${e.message}")
            false
        }
    }

    private fun loadModelFromAssets(modelPath: String): ByteArray {
        return context.assets.open(modelPath).use { inputStream ->
            inputStream.readBytes()
        }
    }

    private fun loadModel(modelPath: Int): ByteArray {
        return context.resources.openRawResource(modelPath).readBytes()
    }

    /**
     * Apply SAM's coordinate transformation
     */
    private fun applyCoordsTransform(
        coords: FloatArray, // [batch, num_points, 2]
        originalSize: Pair<Int, Int>
    ): FloatArray {
        val (oldH, oldW) = originalSize
        val scale = targetLength.toFloat() / max(oldH, oldW)

        val transformedCoords = FloatArray(coords.size)
        for (i in coords.indices step 2) {
            transformedCoords[i] = coords[i] * scale     // x coordinate
            transformedCoords[i + 1] = coords[i + 1] * scale // y coordinate
        }

        println("Coordinate transform: ${originalSize} -> scale=${scale} -> transformed")
        return transformedCoords
    }

    /**
     * Preprocess image exactly like SAM
     */
    private fun preprocessImageSAMStyle(bitmap: Bitmap): Pair<FloatArray, Pair<Int, Int>> {
        val originalShape = Pair(bitmap.height, bitmap.width)
        this.originalImageShape = originalShape

        val (oldH, oldW) = originalShape
        val scale = targetLength.toFloat() / max(oldH, oldW)
        val newH = (oldH * scale).toInt()
        val newW = (oldW * scale).toInt()

        println("SAM preprocessing: ${oldW}x${oldH} -> scale=${scale} -> ${newW}x${newH}")

        // Resize maintaining aspect ratio
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)

        // Create 1024x1024 padded image
        val paddedBitmap = Bitmap.createBitmap(targetLength, targetLength, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(paddedBitmap)
        canvas.drawBitmap(resizedBitmap, 0f, 0f, null)

        // Convert to float array with SAM normalization
        val inputArray = FloatArray(3 * targetLength * targetLength)
        var index = 0

        for (c in 0 until 3) {
            for (h in 0 until targetLength) {
                for (w in 0 until targetLength) {
                    val pixel = paddedBitmap.getPixel(w, h)
                    val channelValue = when (c) {
                        0 -> ((pixel shr 16) and 0xFF) / 255f // Red
                        1 -> ((pixel shr 8) and 0xFF) / 255f  // Green
                        else -> (pixel and 0xFF) / 255f       // Blue
                    }

                    // Apply SAM normalization
                    val normalizedValue = (channelValue - meanValues[c]) / stdValues[c]
                    inputArray[index++] = normalizedValue
                }
            }
        }

        return Pair(inputArray, originalShape)
    }

    /**
     * Get image embeddings with caching
     */
    suspend fun getImageEmbeddings(bitmap: Bitmap): EmbeddingResult? = withContext(Dispatchers.IO) {
        try {
            val imageHash = bitmap.hashCode()

            // Check cache
            if (cachedEmbeddings != null && cachedImageHash == imageHash) {
                println("Using cached image embeddings")
                return@withContext EmbeddingResult(cachedEmbeddings!!, originalImageShape!!)
            }

            println("Generating image embeddings with SAM-style preprocessing...")

            val (inputArray, originalShape) = preprocessImageSAMStyle(bitmap)

            // Create input tensor
            val inputShape = longArrayOf(1, 3, targetLength.toLong(), targetLength.toLong())
            val inputTensor = OnnxTensor.createTensor(
                ortEnvironment!!,
                FloatBuffer.wrap(inputArray),
                inputShape
            )

            // Run encoder
            val inputs = mapOf("input_image" to inputTensor)
            val outputs = encoderSession!!.run(inputs)

            val embeddings = outputs.get("image_embeddings").get() as OnnxTensor

            // Cache results
            cachedEmbeddings = embeddings
            cachedImageHash = imageHash
            originalImageShape = originalShape

            println("✅ Generated embeddings shape: ${embeddings.info.shape.contentToString()}")

            EmbeddingResult(embeddings, originalShape)

        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Failed to get embeddings: ${e.message}")
            null
        }
    }

    /**
     * Predict mask using SAM coordinate transformation
     */
    suspend fun predictMaskWithSAMCoords(
        x: Int,
        y: Int,
        embeddings: OnnxTensor
    ): Array<BooleanArray>? = withContext(Dispatchers.Default) {

        try {
            val originalShape = originalImageShape
            if (originalShape == null) {
                println("❌ Original image shape not set")
                return@withContext null
            }

            // Prepare point input like the sample code
            val inputPoint = floatArrayOf(x.toFloat(), y.toFloat())
            val inputLabel = floatArrayOf(1f) // Positive point

            // Add batch dimension and padding point
            val coordsArray = floatArrayOf(
                x.toFloat(), y.toFloat(),  // Input point
                0f, 0f                     // Padding point
            )
            val labelsArray = floatArrayOf(1f, -1f) // Positive + padding label

            // Apply SAM coordinate transformation
            val transformedCoords = applyCoordsTransform(coordsArray, originalShape)

            // Create tensors
            val coordsShape = longArrayOf(1, 2, 2) // [batch, num_points, 2]
            val labelsShape = longArrayOf(1, 2)    // [batch, num_points]

            val coordsTensor = OnnxTensor.createTensor(
                ortEnvironment!!,
                FloatBuffer.wrap(transformedCoords),
                coordsShape
            )

            val labelsTensor = OnnxTensor.createTensor(
                ortEnvironment!!,
                FloatBuffer.wrap(labelsArray),
                labelsShape
            )

            // Prepare other inputs
            val maskInputShape = longArrayOf(1, 1, 256, 256)
            val maskInputArray = FloatArray(1 * 1 * 256 * 256) { 0f }
            val maskInputTensor = OnnxTensor.createTensor(
                ortEnvironment!!,
                FloatBuffer.wrap(maskInputArray),
                maskInputShape
            )

            val hasMaskInputTensor = OnnxTensor.createTensor(
                ortEnvironment!!,
                FloatBuffer.wrap(floatArrayOf(0f)),
                longArrayOf(1)
            )

            val origImSizeTensor = OnnxTensor.createTensor(
                ortEnvironment!!,
                FloatBuffer.wrap(floatArrayOf(originalShape.first.toFloat(), originalShape.second.toFloat())),
                longArrayOf(2)
            )

            // Package inputs
            val inputs = mapOf(
                "image_embeddings" to embeddings,
                "point_coords" to coordsTensor,
                "point_labels" to labelsTensor,
                "mask_input" to maskInputTensor,
                "has_mask_input" to hasMaskInputTensor,
                "orig_im_size" to origImSizeTensor
            )

            // Run inference
            val outputs = predictorSession!!.run(inputs)
            val masks = outputs.get(0).value as OnnxTensor

            // Convert to boolean array
            val maskShape = masks.info.shape
            val height = maskShape[2].toInt()
            val width = maskShape[3].toInt()

            val maskData = masks.floatBuffer
            val booleanMask = Array(height) { BooleanArray(width) }

            for (h in 0 until height) {
                for (w in 0 until width) {
                    val value = maskData.get(h * width + w)
                    booleanMask[h][w] = value > 0f // SAM's default threshold
                }
            }

            // Clean up tensors
            coordsTensor.close()
            labelsTensor.close()
            maskInputTensor.close()
            hasMaskInputTensor.close()
            origImSizeTensor.close()

            booleanMask

        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ SAM prediction failed for point ($x, $y): ${e.message}")
            null
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        try {
            cachedEmbeddings?.close()
            encoderSession?.close()
            predictorSession?.close()
            ortEnvironment?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}