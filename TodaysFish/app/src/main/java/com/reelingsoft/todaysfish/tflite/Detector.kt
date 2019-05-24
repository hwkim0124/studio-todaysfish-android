package com.reelingsoft.todaysfish.tflite

import android.graphics.Bitmap
import android.graphics.RectF


interface Detector {

    fun recognizeImage(bitmap: Bitmap): List<Recognition>
    fun enableStatLogging(debug: Boolean)
    fun getStatString(): String
    fun close()
    fun setNumThreads(numThreads: Int)
    fun setUseNNAPI(isChecked: Boolean)

    // An immutable result returned by a Detector describing what was recognized.
    data class Recognition(
        var id: String?,
        var title: String?,
        var confidence: Float?,
        var location: RectF?
    ) {

        override fun toString(): String {
            var text = ""
            if (id != null) {
                text += "[$id] "
            }
            if (title != null) {
                text += "$title "
            }
            if (confidence != null) {
                text += "(%.1f%%) ".format(confidence)
            }
            if (location != null) {
                text += location
            }
            return text.trim()
        }
    }
}