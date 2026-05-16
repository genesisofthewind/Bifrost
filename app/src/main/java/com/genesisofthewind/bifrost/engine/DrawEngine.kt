package com.genesisofthewind.bifrost.engine

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.genesisofthewind.bifrost.data.CalibrationStore

class DrawEngine(private val calibrationStore: CalibrationStore) {

    fun createGestureForCommand(command: ShapeCommand): GestureDescription? {
        val (tlX, tlY) = calibrationStore.getTopLeft()
        val (brX, brY) = calibrationStore.getBottomRight()
        
        val width = brX - tlX
        val height = brY - tlY

        return when (command) {
            is ShapeCommand.Tap -> {
                val path = Path()
                path.moveTo(command.x, command.y)
                path.lineTo(command.x + 1f, command.y + 1f)
                createStroke(path, command.durationMs.coerceAtLeast(50L))
            }
            is ShapeCommand.Line -> {
                val path = Path()
                path.moveTo(command.startX, command.startY)
                path.lineTo(command.endX, command.endY)
                createStroke(path, command.durationMs.coerceAtLeast(50L))
            }
            is ShapeCommand.SafeTestGesture -> {
                val safeY = tlY + (height * 0.8f)
                val startX = tlX + (width * 0.1f)
                val endX = tlX + (width * 0.2f)
                val path = Path()
                path.moveTo(startX, safeY)
                path.lineTo(endX, safeY)
                createStroke(path, 180)
            }
            is ShapeCommand.CalibratedLine -> {
                val values = calibrationStore.getValues()
                val path = Path()
                path.moveTo(values.startX, values.startY)
                path.lineTo(values.endX, values.endY)
                createStroke(path, values.durationMs)
            }
            is ShapeCommand.CalibratedDiagonal -> {
                val values = calibrationStore.getValues()
                val path = Path()
                path.moveTo(values.topLeftX, values.topLeftY)
                path.lineTo(values.bottomRightX, values.bottomRightY)
                createStroke(path, values.durationMs)
            }
            is ShapeCommand.CalibratedSmallSquare -> {
                val values = calibrationStore.getValues()
                val width = values.bottomRightX - values.topLeftX
                val height = values.bottomRightY - values.topLeftY
                val size = minOf(width, height) * 0.2f
                val left = values.topLeftX + (width - size) / 2f
                val top = values.topLeftY + (height - size) / 2f
                val path = Path()
                path.moveTo(left, top)
                path.lineTo(left + size, top)
                path.lineTo(left + size, top + size)
                path.lineTo(left, top + size)
                path.lineTo(left, top)
                createStroke(path, values.durationMs.coerceAtLeast(300L))
            }
            is ShapeCommand.CalibratedXShape -> {
                val values = calibrationStore.getValues()
                val pathA = Path()
                pathA.moveTo(values.topLeftX, values.topLeftY)
                pathA.lineTo(values.bottomRightX, values.bottomRightY)
                val pathB = Path()
                pathB.moveTo(values.bottomRightX, values.topLeftY)
                pathB.lineTo(values.topLeftX, values.bottomRightY)
                createGesture(
                    listOf(
                        StrokeSpec(pathA, 0L, values.durationMs),
                        StrokeSpec(pathB, values.durationMs + 120L, values.durationMs)
                    )
                )
            }
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

    private fun createGesture(strokes: List<StrokeSpec>): GestureDescription {
        val builder = GestureDescription.Builder()
        strokes.forEach { stroke ->
            builder.addStroke(GestureDescription.StrokeDescription(stroke.path, stroke.startTimeMs, stroke.durationMs.coerceAtLeast(50L)))
        }
        return builder.build()
    }

    private data class StrokeSpec(
        val path: Path,
        val startTimeMs: Long,
        val durationMs: Long
    )
}
