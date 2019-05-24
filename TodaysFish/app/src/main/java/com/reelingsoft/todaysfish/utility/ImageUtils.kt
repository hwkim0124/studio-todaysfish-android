package com.reelingsoft.todaysfish.utility

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import kotlin.math.abs
import kotlin.math.max


object ImageUtils {

    // Always prefer the native implementation if available.
    private var useNativeConversion = false

    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
    // are normalized to eight bits.
    private val kMaxChannelValue:Int = 262143


    init {
        try {
            System.loadLibrary("image_utils")
        } catch (e: UnsatisfiedLinkError) {
            Timber.w("Native library not found, native RGB -> YUV conversion may be unavailable.")
        }
    }


    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     */
    fun saveBitmap(bitmap: Bitmap) {
        ImageUtils.saveBitmap(bitmap, "preview.png")
    }


    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     * @param filename The location to save the bitmap to.
     */
    fun saveBitmap(bitmap: Bitmap, filename: String) {
        val root = Environment.getExternalStorageDirectory().absolutePath + File.separator + "todaysfish"
        Timber.i("Saving ${bitmap.width} x ${bitmap.height} to $root")

        val myDir = File(root)
        if (!myDir.mkdirs()) {
            Timber.i("Make dir failed!")
        }

        val file = File(myDir, filename)
        if (file.exists()) {
            file.delete()
        }

        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            Timber.e("Exception!")
        }
    }


    /**
     * Utility method to compute the allocated size in bytes of a YUV420SP image
     * of the given dimensions.
     */
    fun getYUVByteSize(width: Int, height: Int): Int {
        // The luminance plane requires 1 byte per pixel.
        val ySize = width * height

        // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
        // Each 2x2 block takes 2 bytes to encode, one each for U and V.
        val uvSize = ((width + 1) / 2) * ((height + 1) / 2) * 2

        return ySize + uvSize
    }


    fun convertYUV420ToARGB8888(
        yData: ByteArray,
        uData: ByteArray,
        vData: ByteArray,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int,
        output: IntArray
    ) {
        if (useNativeConversion) {

        }

        var yp = 0
        for (j in 0 until height) {
            val pY = yRowStride * j
            val pUV = uvRowStride * (j shr 1)

            for (i in 0 until width) {
                val uvOffset = pUV + (i shr 1) * uvPixelStride
                val y = 0xff and yData[pY + i].toInt()
                val u = 0xff and uData[uvOffset].toInt()
                val v = 0xff and vData[uvOffset].toInt()
                output[yp++] = YUV2RGB(y, u, v)
            }
        }
    }

    fun convertYUV420SPToARGB8888(input: ByteArray, width: Int, height: Int, output: IntArray) {
        if (useNativeConversion) {

        }

        // Java implementation of YUV420SP to ARGB8888 converting
        val frameSize = width * height
        var yp = 0

        for (j in 0 until height) {
            var uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0

            for (i in 0 until width) {
                val data = input[yp].toInt()
                val y = (0xff and data)
                if ((i and 1) == 0) {
                    v = 0xff and input[uvp++].toInt()
                    u = 0xff and input[uvp++].toInt()
                }
                output[yp++] = YUV2RGB(y, u, v)
            }
        }
    }


    fun YUV2RGB(y: Int, u: Int, v: Int): Int {
        // Adjust and check YUV values
        val y2 = if ((y - 16) < 0) 0 else (y - 16)
        val u2 = u - 128
        val v2 = v - 128

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);
        val y1192 = 1192 * y2
        var r: Int = (y1192 + 1634 * v2)
        var g: Int = (y1192 - 833 * v2 - 400 * u2)
        var b: Int = (y1192 + 2066 * u2)

        // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
        r = if (r > kMaxChannelValue) kMaxChannelValue else r
        g = if (g > kMaxChannelValue) kMaxChannelValue else g
        b = if (b > kMaxChannelValue) kMaxChannelValue else b

        r = if (r > 0) r else 0
        g = if (g > 0) g else 0
        b = if (b > 0) b else 0

        r = ((r shl 6) and 0xff0000)
        g = ((g shr 2) and 0xff00)
        b = ((b shr 10) and 0xff)
        return 0xff000000.toInt() or r or g or b
    }


    fun getTransformationMatrix(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int,
                                applyRotation: Int, maintainAspectRatio: Boolean): Matrix {
        val matrix = Matrix()

        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                Timber.w("Rotation of $applyRotation % 90 != 0")
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)

            // Rotate around origin.
            matrix.postRotate(applyRotation.toFloat())
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        val transpose = (abs(applyRotation) + 90) % 180 == 0

        val inWidth = if (transpose) srcHeight else srcWidth
        val inHeight = if (transpose) srcWidth else srcHeight

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            val scaleFactorX = dstWidth / inWidth.toFloat()
            val scaleFactorY = dstHeight / inHeight.toFloat()

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                val scaleFactor = max(scaleFactorX, scaleFactorY)
                matrix.postScale(scaleFactor, scaleFactor)
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY)
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }

        return matrix
    }
}