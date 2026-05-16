package com.genesisofthewind.bifrost.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.genesisofthewind.bifrost.BifrostDebug
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.engine.DrawEngine
import com.genesisofthewind.bifrost.engine.ShapeCommand
import com.genesisofthewind.bifrost.engine.StrokePlan
import com.genesisofthewind.bifrost.engine.StrokeSpec

class DrawAccessibilityService : AccessibilityService() {

    private lateinit var drawEngine: DrawEngine
    private val mainHandler = Handler(Looper.getMainLooper())

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
        val plan = drawEngine.createStrokePlanForCommand(command)
        BifrostDebug.setStrokePreview(plan.debugLines)
        plan.debugLines.forEach { BifrostDebug.record(it) }
        if (plan.strokes.isEmpty()) {
            BifrostDebug.record("Gesture action stopped")
            return
        }

        dispatchStroke(plan, 0)
    }

    private fun dispatchStroke(plan: StrokePlan, index: Int) {
        val stroke = plan.strokes[index]
        BifrostDebug.record("Dispatching ${plan.commandName} stroke ${index + 1}/${plan.strokes.size}: ${stroke.describe(index + 1)}")
        val dispatched = dispatchGesture(createGesture(stroke), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
                BifrostDebug.record("Gesture completed: ${plan.commandName} stroke ${index + 1}/${plan.strokes.size}")
                val nextIndex = index + 1
                if (nextIndex < plan.strokes.size) {
                    BifrostDebug.record("Waiting ${stroke.delayAfterMs}ms before next stroke")
                    mainHandler.postDelayed({
                        dispatchStroke(plan, nextIndex)
                    }, stroke.delayAfterMs)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                BifrostDebug.record("Gesture cancelled: ${plan.commandName} stroke ${index + 1}/${plan.strokes.size}")
            }
        }, null)
        if (!dispatched) {
            BifrostDebug.record("Gesture dispatch failed: ${plan.commandName} stroke ${index + 1}/${plan.strokes.size}")
        } else {
            BifrostDebug.record("Gesture dispatch accepted: ${plan.commandName} stroke ${index + 1}/${plan.strokes.size}")
        }
    }

    private fun createGesture(stroke: StrokeSpec): GestureDescription {
        val path = Path().apply {
            moveTo(stroke.startX, stroke.startY)
            lineTo(stroke.endX, stroke.endY)
        }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, stroke.durationMs.coerceAtLeast(50L)))
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        BifrostDebug.setAccessibilityRuntimeReady(false)
        BifrostDebug.record("Accessibility service destroyed")
    }
}
