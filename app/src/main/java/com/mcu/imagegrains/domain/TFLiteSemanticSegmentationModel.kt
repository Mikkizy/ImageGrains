package com.mcu.imagegrains.domain

import android.content.Context
import com.mcu.imagegrains.domain.models.DataType
import com.mcu.imagegrains.domain.models.InputTensorInfo
import com.mcu.imagegrains.domain.models.OutputTensorInfo
import com.mcu.imagegrains.domain.models.QuantizationParams
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

class TFLiteSemanticSegmentationModel(
    private val context: Context,
    private val modelPath: String
) {
    private var interpreter: Interpreter? = null
    private var inputDetails: InputTensorInfo? = null
    private var outputDetails: OutputTensorInfo? = null

    /**
     * Initialize the TFLite model
     */
    fun initialize(): Boolean {
        return try {
            // Load model
            val modelBuffer = loadModelFile(modelPath)

            // Create interpreter options
            val options = Interpreter.Options().apply {
                setNumThreads(4) // Use multiple threads for better performance

                // Try to use GPU delegate if available
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice

                    addDelegate(GpuDelegate(delegateOptions))
                    println("✅ Using GPU delegate for TFLite")
                } else {
                    println("📱 Using CPU for TFLite (GPU not available)")
                }
            }

            // Create interpreter
            interpreter = Interpreter(modelBuffer, options)

            // Get input and output details
            extractTensorDetails()

            println("✅ TFLite Semantic Segmentation Model initialized successfully")
            println("Input details: ${inputDetails}")
            println("Output details: ${outputDetails}")

            true
        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Failed to initialize TFLite model: ${e.message}")
            false
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        return retFile
    }

    private fun extractTensorDetails() {
        val interpreter = this.interpreter ?: throw IllegalStateException("Interpreter not initialized")

        // Input details
        val inputTensor = interpreter.getInputTensor(0)
        inputDetails = InputTensorInfo(
            index = 0,
            shape = inputTensor.shape(),
            dataType = when (inputTensor.dataType()) {
                org.tensorflow.lite.DataType.FLOAT32 -> DataType.FLOAT32
                org.tensorflow.lite.DataType.UINT8 -> DataType.UINT8
                org.tensorflow.lite.DataType.INT8 -> DataType.INT8
                else -> DataType.FLOAT32
            },
            quantizationParams = if (inputTensor.quantizationParams().scale != 0f) {
                QuantizationParams(
                    scale = inputTensor.quantizationParams().scale,
                    zeroPoint = inputTensor.quantizationParams().zeroPoint
                )
            } else null
        )

        // Output details
        val outputTensor = interpreter.getOutputTensor(0)
        outputDetails = OutputTensorInfo(
            index = 0,
            shape = outputTensor.shape(),
            dataType = when (outputTensor.dataType()) {
                org.tensorflow.lite.DataType.FLOAT32 -> DataType.FLOAT32
                org.tensorflow.lite.DataType.UINT8 -> DataType.UINT8
                org.tensorflow.lite.DataType.INT8 -> DataType.INT8
                else -> DataType.FLOAT32
            },
            quantizationParams = if (outputTensor.quantizationParams().scale != 0f) {
                QuantizationParams(
                    scale = outputTensor.quantizationParams().scale,
                    zeroPoint = outputTensor.quantizationParams().zeroPoint
                )
            } else null
        )
    }

    /**
     * Predict single image tile using TFLite model
     */
    fun predictImageTile(imageTile: Array<Array<FloatArray>>): Array<Array<FloatArray>>? {
        val interpreter = this.interpreter ?: return null
        val inputDetails = this.inputDetails ?: return null
        val outputDetails = this.outputDetails ?: return null

        try {
            // Validate input
            if (imageTile.size == 0 || imageTile[0].size == 0 || imageTile[0][0].size != 3) {
                throw IllegalArgumentException("Input image tile must be 3D with 3 channels")
            }

            val height = imageTile.size
            val width = imageTile[0].size
            val channels = imageTile[0][0].size

            // Prepare input buffer
            val inputBuffer = prepareInputBuffer(imageTile, inputDetails)

            // Prepare output buffer
            val outputShape = outputDetails.shape
            val outputBuffer = when (outputDetails.dataType) {
                DataType.FLOAT32 -> ByteBuffer.allocateDirect(4 * outputShape.fold(1, Int::times))
                DataType.UINT8, DataType.INT8 -> ByteBuffer.allocateDirect(outputShape.fold(1, Int::times))
            }.apply {
                order(ByteOrder.nativeOrder())
            }

            // Run inference
            interpreter.run(inputBuffer, outputBuffer)

            // Convert output buffer to array
            return convertOutputBufferToArray(outputBuffer, outputDetails)

        } catch (e: Exception) {
            e.printStackTrace()
            println("❌ Failed to predict image tile: ${e.message}")
            return null
        }
    }

    private fun prepareInputBuffer(
        imageTile: Array<Array<FloatArray>>,
        inputDetails: InputTensorInfo
    ): ByteBuffer {
        val height = imageTile.size
        val width = imageTile[0].size
        val channels = imageTile[0][0].size

        val buffer = when (inputDetails.dataType) {
            DataType.FLOAT32 -> ByteBuffer.allocateDirect(4 * 1 * height * width * channels)
            DataType.UINT8, DataType.INT8 -> ByteBuffer.allocateDirect(1 * height * width * channels)
        }.apply {
            order(ByteOrder.nativeOrder())
        }

        // Fill buffer based on data type
        when (inputDetails.dataType) {
            DataType.FLOAT32 -> {
                for (h in 0 until height) {
                    for (w in 0 until width) {
                        for (c in 0 until channels) {
                            buffer.putFloat(imageTile[h][w][c])
                        }
                    }
                }
            }
            DataType.UINT8, DataType.INT8 -> {
                val quantParams = inputDetails.quantizationParams
                if (quantParams != null) {
                    for (h in 0 until height) {
                        for (w in 0 until width) {
                            for (c in 0 until channels) {
                                val quantized = (imageTile[h][w][c] / quantParams.scale + quantParams.zeroPoint)
                                    .roundToInt()
                                    .coerceIn(0, 255)
                                buffer.put(quantized.toByte())
                            }
                        }
                    }
                } else {
                    // Fallback: scale to 0-255 range
                    for (h in 0 until height) {
                        for (w in 0 until width) {
                            for (c in 0 until channels) {
                                val scaled = (imageTile[h][w][c] * 255f).roundToInt().coerceIn(0, 255)
                                buffer.put(scaled.toByte())
                            }
                        }
                    }
                }
            }
        }

        buffer.rewind()
        return buffer
    }

    private fun convertOutputBufferToArray(
        outputBuffer: ByteBuffer,
        outputDetails: OutputTensorInfo
    ): Array<Array<FloatArray>> {
        outputBuffer.rewind()

        val shape = outputDetails.shape
        val height = shape[1]
        val width = shape[2]
        val channels = shape[3]

        val result = Array(height) { Array(width) { FloatArray(channels) } }

        when (outputDetails.dataType) {
            DataType.FLOAT32 -> {
                for (h in 0 until height) {
                    for (w in 0 until width) {
                        for (c in 0 until channels) {
                            result[h][w][c] = outputBuffer.float
                        }
                    }
                }
            }
            DataType.UINT8, DataType.INT8 -> {
                val quantParams = outputDetails.quantizationParams
                if (quantParams != null) {
                    for (h in 0 until height) {
                        for (w in 0 until width) {
                            for (c in 0 until channels) {
                                val quantizedValue = outputBuffer.get().toInt() and 0xFF
                                result[h][w][c] = quantParams.scale * (quantizedValue - quantParams.zeroPoint)
                            }
                        }
                    }
                } else {
                    // Fallback: normalize to 0-1 range
                    for (h in 0 until height) {
                        for (w in 0 until width) {
                            for (c in 0 until channels) {
                                val value = outputBuffer.get().toInt() and 0xFF
                                result[h][w][c] = value / 255f
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Clean up resources
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}