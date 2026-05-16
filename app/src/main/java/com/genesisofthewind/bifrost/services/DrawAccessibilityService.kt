package com.genesisofthewind.bifrost.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.genesisofthewind.bifrost.BifrostDebug
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.engine.DrawEngine
import com.genesisofthewind.bifrost.engine.GestureStep
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
        val commandName = command.javaClass.simpleName
        BifrostDebug.record("Selected command: $commandName")
        val steps = drawEngine.createGestureStepsForCommand(command)
        if (steps.isEmpty()) {
            BifrostDebug.record("Gesture action stopped")
            return
        }

        dispatchStep(commandName, steps, 0)
    }

    private fun dispatchStep(commandName: String, steps: List<GestureStep>, index: Int) {
        val step = steps[index]
        BifrostDebug.record("Dispatching ${step.name}: ${step.coordinates}")
        val dispatched = dispatchGesture(step.gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
                BifrostDebug.record("Gesture completed: ${step.name}")
                val nextIndex = index + 1
                if (nextIndex < steps.size) {
                    BifrostDebug.record("Queueing next $commandName stroke: ${nextIndex + 1}/${steps.size}")
                    dispatchStep(commandName, steps, nextIndex)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                BifrostDebug.record("Gesture cancelled: ${step.name}")
            }
        }, null)
        if (!dispatched) {
            BifrostDebug.record("Gesture dispatch failed: ${step.name}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        BifrostDebug.setAccessibilityRuntimeReady(false)
        BifrostDebug.record("Accessibility service destroyed")
    }
}
