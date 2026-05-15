package com.thor.drawbridge.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.thor.drawbridge.data.CalibrationStore

class DrawEngine(private val calibrationStore: CalibrationStore) {

    fun createGestureForCommand(command: ShapeCommand): GestureDescription? {
        val (tlX, tlY) = calibrationStore.getTopLeft()
        val (brX, brY) = calibrationStore.getBottomRight()
        
        val width = brX - tlX
        val height = brY - tlY

        return when (command) {
            is ShapeCommand.TestLine -> {
                val path = Path()
                path.moveTo(tlX, tlY)
                path.lineTo(brX, brY)
                createStroke(path, 500)
            }
            is ShapeCommand.TestSquare -> {
                val path = Path()
                path.moveTo(tlX, tlY)
                path.lineTo(tlX + width, tlY)
                path.lineTo(tlX + width, tlY + height)
                path.lineTo(tlX, tlY + height)
                path.lineTo(tlX, tlY)
                createStroke(path, 1000)
            }
            is ShapeCommand.Stop -> null
        }
    }

    private fun createStroke(path: Path, duration: Long): GestureDescription {
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }
}
