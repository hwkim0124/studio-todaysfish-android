package com.reelingsoft.todaysfish.tflite

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Trace
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.FileInputStream
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class TFLiteDetectorModel : Detector {

    var inputWidth: Int = INPUT_WIDTH
    var inputHeight: Int = INPUT_HEIGHT
    var inputSize: Int = (INPUT_WIDTH * INPUT_HEIGHT)
    var isModelQuantized: Boolean = true

    // Kotlin convention that if the last parameter of a function accepts a function,
    // a lambda expression that is passed as the corresponding argument can be placed
    // outside the parentheses.
    private val outputLocations = Array(NUM_DETECTIONS) {FloatArray(4)}
    private val outputClasses = FloatArray(NUM_DETECTIONS)
    private val outputScores = FloatArray(NUM_DETECTIONS)

    var tfLite: Interpreter? = null
    lateinit var imageData: ByteBuffer
    lateinit var intValues: IntArray

    override fun recognizeImage(bitmap: Bitmap): List<Detector.Recognition> {
        Trace.beginSection("recognizeImage")

        Trace.beginSection("preprocessBitmap")
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        imageData.rewind()
        for (i in 0 until inputHeight) {
            for (j in 0 until inputWidth) {
                val pixel = intValues[i * inputWidth + j]
                if (isModelQuantized) {
                    imageData.put(((pixel shr 16) and 0xFF).toByte())
                    imageData.put(((pixel shr 8) and 0xFF).toByte())
                    imageData.put((pixel and 0xFF).toByte())
                }
                else {
                    imageData.putFloat((((pixel shr 16) and 0xFF) - IMAGE_MEAN) / IMAGE_STDEV)
                    imageData.putFloat((((pixel shr 8) and 0xFF) - IMAGE_MEAN) / IMAGE_STDEV)
                    imageData.putFloat(((pixel and 0xFF) - IMAGE_MEAN) / IMAGE_STDEV)
                }
            }
        }
        Trace.endSection()

        Trace.beginSection("feed")
        val inputArray = arrayOf({imageData})
        val outputMap = mutableMapOf<Int, Any>()
        outputMap[0] = outputLocations
        outputMap[1] = outputClasses
        outputMap[2] = outputScores
        Trace.endSection()

        Trace.beginSection("run")
        tfLite?.runForMultipleInputsOutputs(inputArray, outputMap)
        Trace.endSection()

        val recognitions = arrayListOf<Detector.Recognition>()
        for (i in 0 until NUM_DETECTIONS) {
            val rect = RectF(
                outputLocations[i][1] * inputWidth,
                outputLocations[i][0] * inputHeight,
                outputLocations[i][3] * inputWidth,
                outputLocations[i][2] * inputHeight
            )
            recognitions.add(
                Detector.Recognition(
                    i.toString(),
                    ClassLabels.getClassName(outputClasses[i].toInt()),
                    outputScores[i], rect)
            )
        }
        Trace.endSection()

        return recognitions
    }


    override fun enableStatLogging(debug: Boolean) {}
    override fun getStatString(): String { return "" }
    override fun close() {}

    override fun setNumThreads(numThreads: Int) {
        tfLite?.apply { setNumThreads(numThreads) }
    }

    override fun setUseNNAPI(isChecked: Boolean) {
        tfLite?.apply { setUseNNAPI(isChecked) }
    }

    companion object {
        const val INPUT_WIDTH = 320
        const val INPUT_HEIGHT = 256
        const val NUM_DETECTIONS = 5
        const val NUM_THREADS = 4
        const val IMAGE_MEAN = 127.5f // 128.0f
        const val IMAGE_STDEV = 127.5f // 128.0f

        // Memory-map the model file in Assets.
        fun loadModelFile(assets: AssetManager, fileName: String): MappedByteBuffer {
            val fileDescriptor = assets.openFd(fileName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLen = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLen)
        }

        // Initialize a native TensorFlow session for classifying images.
        fun createDetector(
            assetManager: AssetManager,
            modelFileName: String,
            inputWidth: Int = INPUT_WIDTH,
            inputHeight: Int = INPUT_HEIGHT,
            isQuantized: Boolean = true
        ): Detector {
            val model = TFLiteDetectorModel()
            model.inputWidth = inputWidth
            model.inputHeight = inputHeight
            model.inputSize = inputHeight * inputWidth
            model.isModelQuantized = isQuantized

            try {
                val modelFile = loadModelFile(assetManager, modelFileName)
                model.tfLite = Interpreter(modelFile)
                Timber.d("TFLite Detector model loaded, file: $modelFileName")
            } catch (e: Exception){
                throw RuntimeException()
            }

            Timber.d("Model input dimension: $inputHeight, $inputWidth")
            Timber.d("Model Quantized: $isQuantized")

            val numBytesPerChannel = if (isQuantized) 1 else 4
            model.imageData = ByteBuffer.allocateDirect(1 * inputHeight * inputWidth * numBytesPerChannel)
            model.imageData.order(ByteOrder.nativeOrder())
            model.intValues = IntArray(inputWidth * inputHeight)
            return model
        }
    }
}