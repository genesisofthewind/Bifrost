package com.genesisofthewind.bifrost.engine

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.data.CalibrationValues
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class GestureStep(
    val name: String,
    val coordinates: String,
    val gesture: GestureDescription
)

class DrawEngine(private val calibrationStore: CalibrationStore) {

    fun createGestureForCommand(command: ShapeCommand): GestureDescription? {
        return createGestureStepsForCommand(command).firstOrNull()?.gesture
    }

    fun createGestureStepsForCommand(command: ShapeCommand): List<GestureStep> {
        val (tlX, tlY) = calibrationStore.getTopLeft()
        val (brX, brY) = calibrationStore.getBottomRight()
        
        val width = brX - tlX
        val height = brY - tlY

        return when (command) {
            is ShapeCommand.Tap -> {
                val path = Path()
                path.moveTo(command.x, command.y)
                path.lineTo(command.x + 1f, command.y + 1f)
                listOf(
                    GestureStep(
                        "Tap",
                        "tap ${command.x},${command.y} duration=${command.durationMs}",
                        createStroke(path, command.durationMs.coerceAtLeast(50L))
                    )
                )
            }
            is ShapeCommand.Line -> {
                val path = Path()
                path.moveTo(command.startX, command.startY)
                path.lineTo(command.endX, command.endY)
                listOf(
                    GestureStep(
                        "Line",
                        "line ${command.startX},${command.startY} -> ${command.endX},${command.endY} duration=${command.durationMs}",
                        createStroke(path, command.durationMs.coerceAtLeast(50L))
                    )
                )
            }
            is ShapeCommand.SafeTestGesture -> {
                val safeY = tlY + (height * 0.8f)
                val startX = tlX + (width * 0.1f)
                val endX = tlX + (width * 0.2f)
                val path = Path()
                path.moveTo(startX, safeY)
                path.lineTo(endX, safeY)
                listOf(
                    GestureStep(
                        "SafeTestGesture",
                        "safe line $startX,$safeY -> $endX,$safeY duration=180",
                        createStroke(path, 180)
                    )
                )
            }
            is ShapeCommand.CalibratedLine -> {
                val values = calibrationStore.getValues()
                val path = Path()
                path.moveTo(values.startX, values.startY)
                path.lineTo(values.endX, values.endY)
                listOf(
                    GestureStep(
                        "CalibratedLine",
                        "line ${values.startX},${values.startY} -> ${values.endX},${values.endY} duration=${values.durationMs}",
                        createStroke(path, values.durationMs)
                    )
                )
            }
            is ShapeCommand.CalibratedDiagonal -> {
                val values = calibrationStore.getValues()
                val bounds = insetBounds(values)
                val path = Path()
                path.moveTo(bounds.left, bounds.top)
                path.lineTo(bounds.right, bounds.bottom)
                listOf(
                    GestureStep(
                        "CalibratedDiagonal",
                        "diagonal ${bounds.left},${bounds.top} -> ${bounds.right},${bounds.bottom} duration=${values.durationMs.coerceAtLeast(400L)}",
                        createStroke(path, values.durationMs.coerceAtLeast(400L))
                    )
                )
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
                listOf(
                    GestureStep(
                        "CalibratedSmallSquare",
                        "square left=$left top=$top size=$size duration=${values.durationMs.coerceAtLeast(300L)}",
                        createStroke(path, values.durationMs.coerceAtLeast(300L))
                    )
                )
            }
            is ShapeCommand.CalibratedXShape -> {
                val values = calibrationStore.getValues()
                val bounds = insetBounds(values)
                val pathA = Path()
                pathA.moveTo(bounds.left, bounds.top)
                pathA.lineTo(bounds.right, bounds.bottom)
                val pathB = Path()
                pathB.moveTo(bounds.right, bounds.top)
                pathB.lineTo(bounds.left, bounds.bottom)
                val duration = values.durationMs.coerceAtLeast(400L)
                listOf(
                    GestureStep(
                        "CalibratedXShape stroke 1",
                        "x stroke 1 ${bounds.left},${bounds.top} -> ${bounds.right},${bounds.bottom} duration=$duration",
                        createStroke(pathA, duration)
                    ),
                    GestureStep(
                        "CalibratedXShape stroke 2",
                        "x stroke 2 ${bounds.right},${bounds.top} -> ${bounds.left},${bounds.bottom} duration=$duration",
                        createStroke(pathB, duration)
                    )
                )
            }
            is ShapeCommand.TestLine -> {
                val path = Path()
                path.moveTo(tlX, tlY)
                path.lineTo(brX, brY)
                listOf(
                    GestureStep(
                        "TestLine",
                        "test line $tlX,$tlY -> $brX,$brY duration=500",
                        createStroke(path, 500)
                    )
                )
            }
            is ShapeCommand.TestSquare -> {
                val path = Path()
                path.moveTo(tlX, tlY)
                path.lineTo(tlX + width, tlY)
                path.lineTo(tlX + width, tlY + height)
                path.lineTo(tlX, tlY + height)
                path.lineTo(tlX, tlY)
                listOf(
                    GestureStep(
                        "TestSquare",
                        "test square left=$tlX top=$tlY width=$width height=$height duration=1000",
                        createStroke(path, 1000)
                    )
                )
            }
            is ShapeCommand.Stop -> emptyList()
        }
    }

    private fun createStroke(path: Path, duration: Long): GestureDescription {
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    private fun insetBounds(values: CalibrationValues): InsetBounds {
        val left = min(values.topLeftX, values.bottomRightX)
        val right = max(values.topLeftX, values.bottomRightX)
        val top = min(values.topLeftY, values.bottomRightY)
        val bottom = max(values.topLeftY, values.bottomRightY)
        val width = max(1f, abs(right - left))
        val height = max(1f, abs(bottom - top))
        val margin = min(width, height).coerceAtMost(120f) * 0.08f
        return InsetBounds(
            left = left + margin,
            top = top + margin,
            right = right - margin,
            bottom = bottom - margin
        )
    }

    private data class InsetBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}
