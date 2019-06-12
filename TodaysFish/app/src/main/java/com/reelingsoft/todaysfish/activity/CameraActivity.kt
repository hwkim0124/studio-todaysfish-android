package com.reelingsoft.todaysfish.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.reelingsoft.todaysfish.R
import com.reelingsoft.todaysfish.fragment.CameraConnectionFragment
import com.reelingsoft.todaysfish.fragment.LegacyCameraConnectionFragment
import com.reelingsoft.todaysfish.utility.ImageUtils
import kotlinx.android.synthetic.main.activity_camera.*
import timber.log.Timber
import java.lang.Exception


open class CameraActivity: AppCompatActivity(),
    ImageReader.OnImageAvailableListener,
    android.hardware.Camera.PreviewCallback
{
    private var useCamera2API = true
    private var isProcessingFrame = false

    private var rgbBytes: IntArray? = null
    private var yuvBytes: Array<ByteArray?> = Array(3) { null }
    private var yRowStride: Int = 0
    protected var previewHeight: Int = 0
    protected var previewWidth: Int = 0
    protected var surfaceHeight: Int = 0
    protected var surfaceWidth: Int = 0

    private var imageConverter: Runnable? = null
    private var postInferenceCallback: Runnable? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_camera)

        if (!hasPermission2()) {
            requestPermission2()
        }

        if (hasPermission()) {
            setFragment()
        } else {
            requestPermission()
        }
    }


    /** Callback for android.hardware.Camera API */
    override fun onPreviewFrame(data: ByteArray?, camera: android.hardware.Camera?) {
        if (isProcessingFrame) {
            Timber.w("Dropping frame!")
            return
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                val previewSize = camera!!.parameters.previewSize
                previewHeight = previewSize.height
                previewWidth = previewSize.width
                rgbBytes = IntArray(previewWidth * previewHeight)
                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
            }
        } catch (e: Exception) {
            Timber.e("Exception occurred!")
            return
        }

        isProcessingFrame = true
        yuvBytes[0] = data
        yRowStride = previewWidth

        imageConverter = object: Runnable {
            override fun run() {
                data ?: return
                rgbBytes ?: return
                ImageUtils.convertYUV420SPToARGB8888(data, previewWidth, previewHeight, rgbBytes!!)
            }
        }

        postInferenceCallback = object: Runnable {
            override fun run() {
                camera?.addCallbackBuffer(data)
                isProcessingFrame = false
            }
        }

        processImage()
    }


    /** Callback for Camera2 API */
    override fun onImageAvailable(reader: ImageReader?) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }

        if (rgbBytes == null) {
            rgbBytes = IntArray(previewHeight * previewWidth)
        }

        try {
            val image = reader?.acquireLatestImage()
            image ?: return

            if (isProcessingFrame) {
                Timber.w("Dropping frame!")
                image.close()
                return
            }

            isProcessingFrame = true
            Trace.beginSection("imageAvailable")
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            imageConverter = object : Runnable {
                override fun run() {
                    ImageUtils.convertYUV420ToARGB8888(
                        yuvBytes[0]!!,
                        yuvBytes[1]!!,
                        yuvBytes[2]!!,
                        previewWidth,
                        previewHeight,
                        yRowStride,
                        uvRowStride,
                        uvPixelStride,
                        rgbBytes!!
                    )
                }
            }

            postInferenceCallback = object : Runnable {
                override fun run() {
                    image.close()
                    isProcessingFrame = false
                }
            }

            processImage()
        } catch (e: Exception) {
            Timber.e("Exception occurred!")
            Trace.endSection()
            return
        }
        Trace.endSection()
    }


    @Synchronized
    override fun onStart() {
        Timber.d("onStart $this")
        super.onStart()
    }


    @Synchronized
    override fun onResume() {
        Timber.d("onResume $this")
        super.onResume()

        backgroundThread = HandlerThread("inference")
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.looper)
    }


    @Synchronized
    override fun onPause() {
        Timber.d("onPause $this")

        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Timber.e("InterruptedException occurred!")
        }
        super.onPause()
    }


    @Synchronized
    override fun onStop() {
        Timber.d("onStop $this")
        super.onStop()
    }


    @Synchronized
    override fun onDestroy() {
        Timber.d("onDestroy $this")
        super.onDestroy()
    }


    @Synchronized
    protected fun runInBackground(r: Runnable) {
        if (backgroundHandler != null) {
            backgroundHandler!!.post(r)
        }
    }


    protected fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in 0 until planes.size) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                Timber.d("Initializing buffer $i at size ${buffer.capacity()}")
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i])
        }
    }


    protected fun setFragment() {
        val cameraId = chooseCamera()

        var fragment: Fragment

        if (useCamera2API) {
            val previewSize = getDesiredPreviewFrameSize()
            val camera2Fragment =
                    CameraConnectionFragment.newInstance(
                        object: CameraConnectionFragment.ConnectionCallback {
                            override fun onPreviewSizeChosen2(size: Size, cameraRotation: Int) {
                                previewHeight = size.height
                                previewWidth = size.width
                                onPreviewSizeChosen(size, cameraRotation)
                            }

                            override fun onSurfaceSizeChanged(size: Size) {
                                surfaceWidth = size.width
                                surfaceHeight = size.height
                            }
                        },
                        this,
                        getLayoutId(),
                        previewSize)

            camera2Fragment.setCamera(cameraId)
            fragment = camera2Fragment
        }
        else {
            val legacyFragment = LegacyCameraConnectionFragment()
            legacyFragment.setup(this, getLayoutId(), getDesiredPreviewFrameSize())
            fragment = legacyFragment
        }

        supportFragmentManager.beginTransaction().replace(R.id.frame_container, fragment).commit()
    }


    private fun chooseCamera(): String {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == null || facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                map ?: continue

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API =
                    (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                        || isHardwareLevelSupported(characteristics,
                            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)

                Timber.i("Camera API level2: $useCamera2API")
                return cameraId
            }
        } catch (e: CameraAccessException) {
            Timber.e("Not allowed to access camera")
        }
        return ""
    }


    // Returns true if the device supports the required hardware level, or better.
    private fun isHardwareLevelSupported(
        characteristics: CameraCharacteristics, requiredLevel: Int
    ): Boolean {
        val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel!!
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                setFragment()
            }
            else {
                requestPermission()
            }
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(this,
                    "Camera permission is required",
                    Toast.LENGTH_LONG).show()
            }
            requestPermissions(arrayOf(PERMISSION_CAMERA), PERMISSIONS_REQUEST)
        }
    }

    private fun requestPermission2() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
                Toast.makeText(this,
                    "Camera permission is required",
                    Toast.LENGTH_LONG).show()
            }
            requestPermissions(arrayOf(PERMISSION_STORAGE), PERMISSIONS_REQUEST)
        }
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            return true
        }
    }

    private fun hasPermission2(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            return true
        }
    }

    protected fun readyForNextImage() {
        postInferenceCallback?.run()
    }


    protected fun getScrrenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }


    protected fun getRgbBytes(): IntArray? {
        imageConverter?.run()
        return rgbBytes
    }


    protected fun getLuminance(): ByteArray? {
        return yuvBytes[0]
    }


    protected fun getLuminanceStride(): Int {
        return yRowStride
    }


    protected open fun processImage() {}
    protected open fun onPreviewSizeChosen(size: Size, rotation: Int) {}
    protected open fun getLayoutId(): Int { return 0 }
    protected open fun getDesiredPreviewFrameSize(): Size { return Size(0,0 )}

    companion object {
        const val PERMISSIONS_REQUEST = 1000
        const val PERMISSION_CAMERA = Manifest.permission.CAMERA
        const val PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
}