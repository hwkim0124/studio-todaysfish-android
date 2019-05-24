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

    private lateinit var frameToCanvasMatrix: Matrix
    private var frameWidth = 0
    private var frameHeight = 0
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
    fun onFrame(width: Int, height: Int, rowStride: Int, orientation: Int, frame: ByteArray, timestamp: Long) {
        frameWidth = width
        frameHeight = height
        sensorOrientation = orientation
        initialized = true
    }


    @Synchronized
    fun draw(canvas: Canvas) {
        val rotated = sensorOrientation % 180 == 90
        val multiplier = min(
            canvas.height / (if (rotated) frameWidth else frameHeight),
            canvas.width / (if (rotated) frameHeight else frameWidth)
        )

        frameToCanvasMatrix = ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (multiplier * (if (rotated) frameHeight else frameWidth)),
            (multiplier * (if (rotated) frameWidth else frameHeight)),
            sensorOrientation,
            false
        )

        for (obj in trackedObjects) {
            getFrameToCanvasMatrix().mapRect(obj.location)
            boxPaint.color = obj.color

            val cornerSize = 1.0f
            canvas.drawRoundRect(obj.location, cornerSize, cornerSize, boxPaint)

            val labelText = "${obj.title} ${obj.confidence*100}"
            borderedText.drawText(
                canvas,
                obj.location.left + cornerSize, obj.location.top,
                labelText,
                boxPaint
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
        val rectsToTrack = mutableListOf<Pair<Float, Detector.Recognition>>()
        val rgbFrameToScreen = Matrix(getFrameToCanvasMatrix())

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


    private fun getFrameToCanvasMatrix(): Matrix {
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