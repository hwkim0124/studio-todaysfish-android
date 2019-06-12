package com.reelingsoft.todaysfish.activity

import android.graphics.*
import android.media.ImageReader
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.util.TypedValue
import com.reelingsoft.todaysfish.R
import com.reelingsoft.todaysfish.customview.OverlayView
import com.reelingsoft.todaysfish.tflite.Detector
import com.reelingsoft.todaysfish.tflite.MultiBoxTracker
import com.reelingsoft.todaysfish.tflite.TFLiteDetectorModel
import com.reelingsoft.todaysfish.utility.ImageUtils
import com.reelingsoft.todaysfish.widget.BorderedText
import kotlinx.android.synthetic.main.activity_preview.*
import timber.log.Timber
import java.io.IOException

class PreviewActivity : CameraActivity(), ImageReader.OnImageAvailableListener {

    private var timestamp: Long = 0
    private var lastProcessingTimeMs: Long = 0
    private var computingDetection = false

    private lateinit var borderedText: BorderedText
    private lateinit var tracker: MultiBoxTracker
    private lateinit var detector: Detector

    private var sensorOrientation: Int = 0

    private lateinit var rgbFrameBitmap: Bitmap
    private lateinit var croppedBitmap: Bitmap
    private lateinit var cropCopyBitmap: Bitmap

    private lateinit var frameToCropTransform: Matrix
    private lateinit var cropToFrameTransform: Matrix

    private var luminanceCopy: ByteArray? = null


    /*
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
    }
    */

    override fun onPreviewSizeChosen(size: Size, rotation: Int) {
        val textSizePix = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP,
            resources.displayMetrics
        )

        borderedText = BorderedText(textSizePix)
        borderedText.setTypeface(Typeface.MONOSPACE)

        tracker = MultiBoxTracker(this)

        try {
            detector = TFLiteDetectorModel.createDetector(
                assets,
                DETECTOR_MODEL_FILE
            )

        } catch (e: IOException) {
            e.printStackTrace()
            Timber.e("Exception initializing classifier!")
            finish()
        }

        previewWidth = size.width
        previewHeight = size.height

        sensorOrientation = rotation - getScrrenOrientation()
        Timber.i("Camera orientation relative to screen canvas: $sensorOrientation")

        val targetWidth = MODEL_INPUT_WIDTH
        val targetHeight = MODEL_INPUT_HEIGHT

        Timber.i("Initializing at preview size $previewWidth x $previewHeight")
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        frameToCropTransform = ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            targetWidth, targetHeight,
            sensorOrientation, MAINTAIN_ASPECT
        )

        cropToFrameTransform = Matrix()
        frameToCropTransform.invert(cropToFrameTransform)

        overlay_tracking.addCallback(object: OverlayView.DrawCallback{
            override fun drawCallback(canvas: Canvas) {
                tracker.draw(canvas)
            }
        })
    }


    override fun processImage() {
        val currTimestamp = ++timestamp
        val originalLuminance = getLuminance()!!

        tracker.onFrame(
            previewWidth,
            previewHeight,
            surfaceWidth,
            surfaceHeight,
            getLuminanceStride(),
            sensorOrientation,
            originalLuminance,
            timestamp
        )
        overlay_tracking.postInvalidate()

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage()
            return
        }

        computingDetection = true
        Timber.i("Preparing image $currTimestamp for detection in bg thread.")

        val startTime = SystemClock.uptimeMillis()
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight)
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
        Timber.i("Image processing time: $lastProcessingTimeMs")

        if (luminanceCopy == null) {
            luminanceCopy = ByteArray(originalLuminance.size)
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.size)
        readyForNextImage()

        val canvas = Canvas(croppedBitmap)
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null)

        // For examining the actual TF input.
        if (false) { // SAVE_PREVIEW_BITMAP) {
            if (currTimestamp.rem(20) == 0L) {
                ImageUtils.saveBitmap(croppedBitmap)
            }
        }

        runInBackground(
            Runnable {
                if (true) {
                    Timber.i("Running detection on image $currTimestamp")
                    val startTime = SystemClock.uptimeMillis()
                    val results = detector.recognizeImage(croppedBitmap)
                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
                    Timber.i("Inference time: $lastProcessingTimeMs")

                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap)
                    val canvas2 = Canvas(cropCopyBitmap)
                    val paint = Paint()
                    paint.color = Color.RED
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.0f

                    val mappedResults = mutableListOf<Detector.Recognition>()

                    for (r in results) {
                        val location = r.location
                        if (location != null && r.confidence!! > DETECTOR_MINIMUM_CONFIDENCE) {
                            canvas2.drawRect(location, paint)
                            cropToFrameTransform.mapRect(location)

                            r.location = location
                            mappedResults.add(r)
                        }
                    }

                    tracker.trackResults(mappedResults, luminanceCopy!!, currTimestamp)
                    overlay_tracking.postInvalidate()

                    computingDetection = false
                }

                runOnUiThread {

                }
            }
        )
    }


    override fun getLayoutId(): Int {
        return R.layout.activity_preview
    }


    override fun getDesiredPreviewFrameSize(): Size {
        return Size(DESIRED_PREVIEW_WIDTH, DESIRED_PREVIEW_HEIGHT)
    }


    companion object {
        private const val MODEL_INPUT_WIDTH = 320
        private const val MODEL_INPUT_HEIGHT = 240
        private const val DETECTOR_MODEL_FILE = "shufflenet.tflite"
        private const val DETECTOR_MINIMUM_CONFIDENCE = 0.5f
        private const val DESIRED_PREVIEW_WIDTH = 640
        private const val DESIRED_PREVIEW_HEIGHT = 480
        private const val SAVE_PREVIEW_BITMAP = false
        private const val MAINTAIN_ASPECT = false
        private const val TEXT_SIZE_DIP = 10.0f
    }
}
