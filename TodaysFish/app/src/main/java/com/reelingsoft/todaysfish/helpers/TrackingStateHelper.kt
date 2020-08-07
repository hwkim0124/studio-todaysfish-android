package com.reelingsoft.todaysfish.helpers


import android.app.Activity
import android.view.WindowManager
import com.google.ar.core.Camera
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState

/** Gets human readibly tracking failure reasons and suggested actions.  */
class TrackingStateHelper(private val activity: Activity) {

    private var previousTrackingState: TrackingState? = null

    /** Keep the screen unlocked while tracking, but allow it to lock when tracking stops.  */
    fun updateKeepScreenOnFlag(trackingState: TrackingState) {
        if (trackingState == previousTrackingState) {
            return
        }

        previousTrackingState = trackingState
        when (trackingState) {
            TrackingState.PAUSED, TrackingState.STOPPED -> activity.runOnUiThread {
                activity.window.clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
            TrackingState.TRACKING -> activity.runOnUiThread { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    companion object {
        private val INSUFFICIENT_FEATURES_MESSAGE =
            "Can't find anything. Aim device at a surface with more texture or color."
        private val EXCESSIVE_MOTION_MESSAGE = "Moving too fast. Slow down."
        private val INSUFFICIENT_LIGHT_MESSAGE = "Too dark. Try moving to a well-lit area."
        private val BAD_STATE_MESSAGE =
            "Tracking lost due to bad internal state. Please try restarting the AR experience."

        fun getTrackingFailureReasonString(camera: Camera): String {
            val reason = camera.trackingFailureReason
            when (reason) {
                TrackingFailureReason.NONE -> return ""
                TrackingFailureReason.BAD_STATE -> return BAD_STATE_MESSAGE
                TrackingFailureReason.INSUFFICIENT_LIGHT -> return INSUFFICIENT_LIGHT_MESSAGE
                TrackingFailureReason.EXCESSIVE_MOTION -> return EXCESSIVE_MOTION_MESSAGE
                TrackingFailureReason.INSUFFICIENT_FEATURES -> return INSUFFICIENT_FEATURES_MESSAGE
            }
            return "Unknown tracking failure reason: $reason"
        }
    }
}
