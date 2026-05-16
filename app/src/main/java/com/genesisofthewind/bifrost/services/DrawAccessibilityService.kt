package com.genesisofthewind.bifrost.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.genesisofthewind.bifrost.BifrostDebug
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
        BifrostDebug.setAccessibilityRuntimeReady(true)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        BifrostDebug.setAccessibilityRuntimeReady(true)
        BifrostDebug.record("Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        BifrostDebug.setAccessibilityRuntimeReady(false)
        BifrostDebug.record("Accessibility service unbound")
        instance = null
        return super.onUnbind(intent)
    }

    fun executeCommand(command: ShapeCommand) {
        val gesture = drawEngine.createGestureForCommand(command)
        if (gesture != null) {
            BifrostDebug.record("Dispatching gesture: ${command::class.simpleName}")
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    super.onCompleted(gestureDescription)
                    BifrostDebug.record("Gesture completed: ${command::class.simpleName}")
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    super.onCancelled(gestureDescription)
                    BifrostDebug.record("Gesture cancelled: ${command::class.simpleName}")
                }
            }, null)
            if (!dispatched) {
                BifrostDebug.record("Gesture dispatch failed: ${command::class.simpleName}")
            }
        } else {
            BifrostDebug.record("Gesture action stopped")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        BifrostDebug.setAccessibilityRuntimeReady(false)
        BifrostDebug.record("Accessibility service destroyed")
    }
}
