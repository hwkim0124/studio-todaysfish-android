package com.reelingsoft.todaysfish.customview

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View


/** A simple View providing a render callback to other classes. */
class OverlayView: View {

    private val callbacks = mutableListOf<DrawCallback>()

    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    fun addCallback(callback: DrawCallback) {
        callbacks.add(callback)
    }

    @Synchronized
    override fun draw(canvas: Canvas) {
        for (callback in callbacks) {
            callback.drawCallback(canvas)
        }
        // super.draw(canvas)
    }

    /** Interface defining the callback for client classes. */
    interface DrawCallback {
        fun drawCallback(canvas: Canvas)
    }
}