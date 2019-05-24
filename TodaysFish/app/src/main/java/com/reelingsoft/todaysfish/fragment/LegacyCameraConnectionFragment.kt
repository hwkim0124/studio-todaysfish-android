package com.reelingsoft.todaysfish.fragment


import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Size
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import com.reelingsoft.todaysfish.R
import com.reelingsoft.todaysfish.customview.AutoFitTextureView
import com.reelingsoft.todaysfish.utility.ImageUtils
import java.io.IOException


// If the derived class has a primary constructor, the base class must be initialized
// right there, using the parameters of the primary constructor.
class LegacyCameraConnectionFragment : Fragment() {

    lateinit var camera: Camera
    lateinit var previewCallback: Camera.PreviewCallback
    lateinit var textureView: AutoFitTextureView
    lateinit var desiredSize: Size

    /** The layout identifier to inflate for this Fragment. */
    private var layoutId = 0


    fun setup(callback: Camera.PreviewCallback, layoutId: Int, desiredSize: Size) {
        previewCallback = callback
        this.layoutId = layoutId
        this.desiredSize = desiredSize
    }


    private val surfaceTextureListener = object: TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            val index = getCameraId()
            if (index < 0) {
                return
            }
            camera = Camera.open(index)

            try {
                val parameters = camera.parameters
                val focusModes = parameters.supportedFocusModes
                focusModes?.apply {
                    if (this.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                    }
                }

                val sizes = Array(parameters.supportedPreviewSizes.size) {
                    Size(parameters.supportedPreviewSizes[it].width, parameters.supportedPreviewSizes[it].height)
                }

                val previewSize: Size = CameraConnectionFragment.chooseOptimalSize(
                    sizes, desiredSize.width, desiredSize.height
                )
                parameters.setPreviewSize(previewSize.width, previewSize.height)
                camera.setDisplayOrientation(90)
                camera.parameters = parameters
                camera.setPreviewTexture(surface)
            }
            catch (e: IOException) {
                camera.release()
            }

            camera.setPreviewCallbackWithBuffer(previewCallback)
            val size = camera.parameters.previewSize
            camera.addCallbackBuffer(ByteArray(ImageUtils.getYUVByteSize(size.width, size.height)))

            textureView.setAspectRatio(size.width, size.height)
            camera.startPreview()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(layoutId, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.texture)
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }


    override fun onResume() {
        super.onResume()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable) {
            camera.startPreview()
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }


    override fun onPause() {
        stopCamera()
        super.onPause()
    }


    fun stopCamera() {
        camera.apply {
            this.stopPreview()
            this.setPreviewCallback(null)
            this.release()
        }
    }


    fun getCameraId(): Int {
        val ci = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, ci)
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i
            }
        }
        return -1 // No camera found
    }
}