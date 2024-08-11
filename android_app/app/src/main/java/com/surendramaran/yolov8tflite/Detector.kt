package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {

    private var interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    fun setup() {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options().apply {
            numThreads = 8  // Experimente com 2, 4, 8, etc.
        }
        options.numThreads = 4
        interpreter = Interpreter(model, options)

        val inputShape = interpreter?.getInputTensor(0)?.shape() ?: return
        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return

        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]
        numChannel = outputShape[1]
        numElements = outputShape[2]

        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String? = reader.readLine()
            while (line != null && line != "") {
                labels.add(line)
                line = reader.readLine()
            }

            reader.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    fun detect(frame: Bitmap) {
        interpreter ?: return
        if (tensorWidth == 0) return
        if (tensorHeight == 0) return
        if (numChannel == 0) return
        if (numElements == 0) return

        val inferenceTime = SystemClock.uptimeMillis()

        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer

        val output = TensorBuffer.createFixedSize(intArrayOf(1 , numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter?.run(imageBuffer, output.buffer)

        val detectedLabels = detectLabels(output.floatArray)
        val elapsedTime = SystemClock.uptimeMillis() - inferenceTime

        if (detectedLabels.isNullOrEmpty()) {
            detectorListener.onEmptyDetect()
            return
        }

        // Exibir os labels detectados no console
        detectedLabels.forEach { label ->
            Log.d("Gesture Detection", "Detected: $label")
        }

        detectorListener.onDetect(detectedLabels, elapsedTime)
    }

    private fun detectLabels(array: FloatArray) : List<String> {

        val labelsDetected = mutableListOf<String>()

        for (c in 0 until numElements) {
            var maxConf = -1.0f
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            while (j < numChannel){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                val clsName = labels[maxIdx]
                labelsDetected.add(clsName)
            }
        }

        return labelsDetected
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(labels: List<String>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.6F
    }
}
