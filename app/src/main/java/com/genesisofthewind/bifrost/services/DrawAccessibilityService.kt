package com.genesisofthewind.bifrost.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.engine.DrawEngine
import com.genesisofthewind.bifrost.engine.ShapeCommand

class DrawAccessibilityService : AccessibilityService() {

    private lateinit var drawEngine: DrawEngine

    companion object {
        private var instance: DrawAccessibilityService? = null
        fun getInstance() = instance

        const val ACTION_DRAW = "com.genesisofthewind.bifrost.ACTION_DRAW"
        const val EXTRA_COMMAND = "extra_command"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        drawEngine = DrawEngine(CalibrationStore(this))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("DrawService", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun executeCommand(command: ShapeCommand) {
        val gesture = drawEngine.createGestureForCommand(command)
        if (gesture != null) {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    super.onCompleted(gestureDescription)
                    Log.d("DrawService", "Gesture completed")
                }
            }, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
