package com.reelingsoft.todaysfish.tflite

import android.content.Context
import android.graphics.*
import android.util.TypedValue
import com.reelingsoft.todaysfish.utility.ImageUtils
import com.reelingsoft.todaysfish.widget.BorderedText
import timber.log.Timber
import java.util.*
import kotlin.math.min


class MultiBoxTracker(context: Context) {

    private val trackedObjects = mutableListOf<TrackedRecognition>()
    private val screenRects = mutableListOf<Pair<Float, RectF>>()

    private lateinit var borderedText: BorderedText
    private val boxPaint = Paint()

    private var frameToCanvasMatrix: Matrix? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private var sensorOrientation = 0
    private var initialized = false

    private lateinit var availableColors: Queue<Int>
    private var textSizePix = 0.0f

    private val COLORS = intArrayOf(
        Color.BLUE,
        Color.RED,
        Color.GREEN,
        Color.YELLOW,
        Color.CYAN,
        Color.MAGENTA,
        Color.WHITE,
        Color.parseColor("#55FF55"),
        Color.parseColor("#FFA500"),
        Color.parseColor("#FF8888"),
        Color.parseColor("#AAAAFF"),
        Color.parseColor("#FFFFAA"),
        Color.parseColor("#55AAAA"),
        Color.parseColor("#AA33AA"),
        Color.parseColor("#0D0068")
    )

    init {
        availableColors = ArrayDeque<Int>()
        for (color in COLORS) {
            availableColors.add(color)
        }

        boxPaint.color = Color.RED
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 10.0f
        boxPaint.strokeCap = Paint.Cap.ROUND
        boxPaint.strokeJoin = Paint.Join.ROUND
        boxPaint.strokeMiter = 100.0f

        textSizePix = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP,
            context.resources.displayMetrics
        )

        borderedText = BorderedText(textSizePix)
    }


    @Synchronized
    fun onFrame(width: Int, height: Int, viewWidth: Int, viewHeight: Int, rowStride: Int, orientation: Int, frame: ByteArray, timestamp: Long) {
        frameWidth = width
        frameHeight = height
        surfaceWidth = viewWidth
        surfaceHeight = viewHeight
        sensorOrientation = orientation
        initialized = true
    }


    @Synchronized
    fun draw(canvas: Canvas) {
        if (frameWidth == 0 || frameHeight == 0) {
            return
        }

        val rotated = sensorOrientation % 180 == 90
        val multiplier = min(
            surfaceHeight / (if (rotated) frameWidth else frameHeight).toFloat(),
            surfaceWidth / (if (rotated) frameHeight else frameWidth).toFloat()
        )

        val matrix = ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (multiplier * (if (rotated) frameHeight else frameWidth)).toInt(),
            (multiplier * (if (rotated) frameWidth else frameHeight)).toInt(),
            sensorOrientation,
            false
        )
        matrix.postTranslate((canvas.width - surfaceWidth)/2.0f, (canvas.height-surfaceHeight)/2.0f)
        frameToCanvasMatrix = matrix

        for (obj in trackedObjects) {
            val location = RectF(obj.location)
            frameToCanvasMatrix!!.mapRect(location)
            boxPaint.color = obj.color

            val cornerSize = 1.0f
            canvas.drawRoundRect(location, cornerSize, cornerSize, boxPaint)

            val labelText = "${obj.title} ${obj.confidence*100}"
            borderedText.drawText(
                canvas,
                location.left + cornerSize, location.top,
                labelText,
                boxPaint
            )

            canvas.drawLine(100.0f, 200.0f, 1000.0f, 200.0f, boxPaint)

            Timber.d(
                "Bordered text: $location"
            )
        }
    }


    @Synchronized
    fun trackResults(
        results: List<Detector.Recognition>,
        frame: ByteArray,
        timestamp: Long
    ) {
        Timber.i("Processing ${results.size} from $timestamp")
        processResults(results, frame, timestamp)
    }


    private fun processResults(
        results: List<Detector.Recognition>,
        frame: ByteArray,
        timestamp: Long
    ) {
        val matrix = getFrameToCanvasMatrix()
        matrix ?: return

        val rectsToTrack = mutableListOf<Pair<Float, Detector.Recognition>>()
        val rgbFrameToScreen = Matrix(matrix)

        screenRects.clear()
        for (r in results) {
            if (r.location == null) {
                continue
            }

            val detectFrameRect = RectF(r.location)
            val detectScreenRect = RectF()
            rgbFrameToScreen.mapRect(detectScreenRect, detectFrameRect)

            Timber.d(
                "Result frame: ${r.location} mapped to screen: $detectScreenRect"
            )
            screenRects.add(Pair<Float, RectF>(r.confidence!!, detectScreenRect))

            if (detectFrameRect.width() < DETECT_MIN_SIZE || detectFrameRect.height() < DETECT_MIN_SIZE) {
                Timber.d("Degenerate rectangle! $detectFrameRect")
                continue
            }

            rectsToTrack.add(Pair<Float, Detector.Recognition>(r.confidence!!, r))
        }

        if (rectsToTrack.isEmpty()) {
            Timber.d("Nothing to track, aborting.")
            return
        }

        trackedObjects.clear()
        for (r in rectsToTrack) {
            val recog = TrackedRecognition()
            recog.confidence = r.first
            recog.location = r.second.location!!
            recog.title = r.second.title!!
            recog.color = COLORS[trackedObjects.size]
            trackedObjects.add(recog)

            if (trackedObjects.size >= COLORS.size) {
                break
            }
        }
    }


    private fun getFrameToCanvasMatrix(): Matrix? {
        return frameToCanvasMatrix
    }


    companion object {
        const val TEXT_SIZE_DIP = 18.0f
        const val DETECT_MIN_SIZE = 16.0f
    }


    class TrackedRecognition {
        var location = RectF(0.0f, 0.0f, 0.0f, 0.0f)
        var confidence = 0.0f
        var color = Color.RED
        var title = ""
    }
}