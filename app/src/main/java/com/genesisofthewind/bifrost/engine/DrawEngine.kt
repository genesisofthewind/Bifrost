package com.genesisofthewind.bifrost.engine

import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.data.CalibrationValues
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class StrokeSpec(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long,
    val delayAfterMs: Long = 140L,
    val directionLabel: String = "custom",
    val segmented: Boolean = false,
    val segmentIndex: Int = 1,
    val segmentCount: Int = 1
) {
    fun describe(index: Int): String {
        val mode = if (segmented) "segmented" else "normal"
        return "stroke $index: $mode $directionLabel segment=$segmentIndex/$segmentCount $startX,$startY -> $endX,$endY duration=$durationMs delayAfter=$delayAfterMs"
    }
}

data class StrokePlan(
    val commandName: String,
    val debugLines: List<String>,
    val strokes: List<StrokeSpec>
)

class DrawEngine(private val calibrationStore: CalibrationStore) {

    fun createStrokePlanForCommand(command: ShapeCommand): StrokePlan {
        val values = calibrationStore.getValues()
        val legacyTopLeft = calibrationStore.getTopLeft()
        val legacyBottomRight = calibrationStore.getBottomRight()
        val commandName = command.javaClass.simpleName

        val strokes = when (command) {
            is ShapeCommand.Tap -> listOf(
                StrokeSpec(command.x, command.y, command.x + 1f, command.y + 1f, command.durationMs.coerceAtLeast(50L), 0L)
            )
            is ShapeCommand.Line -> listOf(
                StrokeSpec(command.startX, command.startY, command.endX, command.endY, command.durationMs.coerceAtLeast(50L), 0L)
            )
            is ShapeCommand.SafeTestGesture -> {
                val bounds = Bounds.fromLegacy(legacyTopLeft, legacyBottomRight)
                val y = bounds.top + bounds.height * 0.8f
                listOf(StrokeSpec(bounds.left + bounds.width * 0.1f, y, bounds.left + bounds.width * 0.2f, y, 180L, 0L))
            }
            is ShapeCommand.CalibratedLine -> {
                val bounds = Bounds.fromValues(values)
                val inset = bounds.inset()
                val y = inset.top + inset.height / 2f
                listOf(StrokeSpec(inset.left, y, inset.right, y, values.durationMs.coerceAtLeast(300L), 0L))
            }
            is ShapeCommand.CalibratedDiagonal -> {
                diagonalStroke(Bounds.fromValues(values).inset(), DiagonalDirection.TopLeftToBottomRight, values.durationMs.coerceAtLeast(400L), 0L)
            }
            is ShapeCommand.DiagonalTopLeftToBottomRight -> {
                diagonalStroke(Bounds.fromValues(values).inset(), DiagonalDirection.TopLeftToBottomRight, values.durationMs.coerceAtLeast(400L), 0L)
            }
            is ShapeCommand.DiagonalTopRightToBottomLeft -> {
                diagonalStroke(Bounds.fromValues(values).inset(), DiagonalDirection.TopRightToBottomLeft, values.durationMs.coerceAtLeast(400L), 0L)
            }
            is ShapeCommand.DiagonalBottomLeftToTopRight -> {
                diagonalStroke(Bounds.fromValues(values).inset(), DiagonalDirection.BottomLeftToTopRight, values.durationMs.coerceAtLeast(400L), 0L)
            }
            is ShapeCommand.DiagonalBottomRightToTopLeft -> {
                diagonalStroke(Bounds.fromValues(values).inset(), DiagonalDirection.BottomRightToTopLeft, values.durationMs.coerceAtLeast(400L), 0L)
            }
            is ShapeCommand.SegmentedDiagonal -> {
                segmentedDiagonal(Bounds.fromValues(values).inset(), DiagonalDirection.TopLeftToBottomRight, values.durationMs.coerceAtLeast(600L), 8)
            }
            is ShapeCommand.SegmentedTopLeftToBottomRight -> {
                segmentedDiagonal(Bounds.fromValues(values).inset(), DiagonalDirection.TopLeftToBottomRight, values.durationMs.coerceAtLeast(720L), 10)
            }
            is ShapeCommand.SegmentedTopRightToBottomLeft -> {
                segmentedDiagonal(Bounds.fromValues(values).inset(), DiagonalDirection.TopRightToBottomLeft, values.durationMs.coerceAtLeast(720L), 10)
            }
            is ShapeCommand.CalibratedSmallSquare -> {
                val inset = Bounds.fromValues(values).inset()
                val size = min(inset.width, inset.height) * 0.2f
                val left = inset.left + (inset.width - size) / 2f
                val top = inset.top + (inset.height - size) / 2f
                val right = left + size
                val bottom = top + size
                val sideDuration = max(90L, values.durationMs.coerceAtLeast(360L) / 4L)
                listOf(
                    StrokeSpec(left, top, right, top, sideDuration),
                    StrokeSpec(right, top, right, bottom, sideDuration),
                    StrokeSpec(right, bottom, left, bottom, sideDuration),
                    StrokeSpec(left, bottom, left, top, sideDuration, 0L)
                )
            }
            is ShapeCommand.CalibratedXShape -> {
                val inset = Bounds.fromValues(values).inset()
                val duration = values.durationMs.coerceAtLeast(400L)
                diagonalStroke(inset, DiagonalDirection.TopRightToBottomLeft, duration, 260L) +
                    segmentedDiagonal(inset, DiagonalDirection.TopLeftToBottomRight, duration.coerceAtLeast(720L), 10)
            }
            is ShapeCommand.ReverseXShape -> {
                val inset = Bounds.fromValues(values).inset()
                val duration = values.durationMs.coerceAtLeast(400L)
                segmentedDiagonal(inset, DiagonalDirection.TopLeftToBottomRight, duration.coerceAtLeast(720L), 10, finalDelayAfterMs = 260L) +
                    diagonalStroke(inset, DiagonalDirection.TopRightToBottomLeft, duration, 0L)
            }
            is ShapeCommand.SegmentedXShape -> {
                val inset = Bounds.fromValues(values).inset()
                val duration = values.durationMs.coerceAtLeast(600L)
                segmentedDiagonal(inset, DiagonalDirection.TopLeftToBottomRight, duration, 10, finalDelayAfterMs = 260L) +
                    segmentedDiagonal(inset, DiagonalDirection.TopRightToBottomLeft, duration, 10)
            }
            is ShapeCommand.TestLine -> {
                val bounds = Bounds.fromLegacy(legacyTopLeft, legacyBottomRight)
                listOf(StrokeSpec(bounds.left, bounds.top, bounds.right, bounds.bottom, 500L, 0L))
            }
            is ShapeCommand.TestSquare -> {
                val bounds = Bounds.fromLegacy(legacyTopLeft, legacyBottomRight)
                listOf(
                    StrokeSpec(bounds.left, bounds.top, bounds.right, bounds.top, 250L),
                    StrokeSpec(bounds.right, bounds.top, bounds.right, bounds.bottom, 250L),
                    StrokeSpec(bounds.right, bounds.bottom, bounds.left, bounds.bottom, 250L),
                    StrokeSpec(bounds.left, bounds.bottom, bounds.left, bounds.top, 250L, 0L)
                )
            }
            is ShapeCommand.Stop -> emptyList()
        }

        val bounds = Bounds.fromValues(values)
        val inset = bounds.inset()
        val debugLines = buildList {
            add("command: $commandName")
            add("canvas bounds: left=${bounds.left}, top=${bounds.top}, right=${bounds.right}, bottom=${bounds.bottom}")
            add("safety inset: ${bounds.safetyInset}")
            add("inset bounds: left=${inset.left}, top=${inset.top}, right=${inset.right}, bottom=${inset.bottom}")
            strokes.forEachIndexed { index, stroke -> add(stroke.describe(index + 1)) }
        }

        return StrokePlan(commandName, debugLines, strokes)
    }

    private fun diagonalStroke(
        bounds: Bounds,
        direction: DiagonalDirection,
        durationMs: Long,
        delayAfterMs: Long
    ): List<StrokeSpec> {
        val (start, end) = direction.points(bounds)
        return listOf(StrokeSpec(start.first, start.second, end.first, end.second, durationMs, delayAfterMs, direction.label, false, 1, 1))
    }

    private fun segmentedDiagonal(
        bounds: Bounds,
        direction: DiagonalDirection,
        totalDurationMs: Long,
        segmentCount: Int,
        finalDelayAfterMs: Long = 0L
    ): List<StrokeSpec> {
        val safeSegmentCount = segmentCount.coerceAtLeast(2)
        val (start, end) = direction.points(bounds)
        val duration = max(70L, totalDurationMs / safeSegmentCount)
        return (0 until safeSegmentCount).map { index ->
            val fromT = index.toFloat() / safeSegmentCount
            val toT = (index + 1).toFloat() / safeSegmentCount
            val delay = if (index == safeSegmentCount - 1) finalDelayAfterMs else 80L
            StrokeSpec(
                startX = lerp(start.first, end.first, fromT),
                startY = lerp(start.second, end.second, fromT),
                endX = lerp(start.first, end.first, toT),
                endY = lerp(start.second, end.second, toT),
                durationMs = duration,
                delayAfterMs = delay,
                directionLabel = direction.label,
                segmented = true,
                segmentIndex = index + 1,
                segmentCount = safeSegmentCount
            )
        }
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + ((end - start) * fraction)
    }

    private enum class DiagonalDirection {
        TopLeftToBottomRight,
        TopRightToBottomLeft,
        BottomLeftToTopRight,
        BottomRightToTopLeft;

        val label: String
            get() = when (this) {
                TopLeftToBottomRight -> "TopLeft to BottomRight"
                TopRightToBottomLeft -> "TopRight to BottomLeft"
                BottomLeftToTopRight -> "BottomLeft to TopRight"
                BottomRightToTopLeft -> "BottomRight to TopLeft"
            }

        fun points(bounds: Bounds): Pair<Pair<Float, Float>, Pair<Float, Float>> {
            return when (this) {
                TopLeftToBottomRight -> (bounds.left to bounds.top) to (bounds.right to bounds.bottom)
                TopRightToBottomLeft -> (bounds.right to bounds.top) to (bounds.left to bounds.bottom)
                BottomLeftToTopRight -> (bounds.left to bounds.bottom) to (bounds.right to bounds.top)
                BottomRightToTopLeft -> (bounds.right to bounds.bottom) to (bounds.left to bounds.top)
            }
        }
    }

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float = max(1f, abs(right - left))
        val height: Float = max(1f, abs(bottom - top))
        val safetyInset: Float = min(width, height).coerceAtMost(120f) * 0.08f

        fun inset(): Bounds {
            return Bounds(
                left = left + safetyInset,
                top = top + safetyInset,
                right = right - safetyInset,
                bottom = bottom - safetyInset
            )
        }

        companion object {
            fun fromValues(values: CalibrationValues): Bounds {
                return Bounds(
                    left = min(values.topLeftX, values.bottomRightX),
                    top = min(values.topLeftY, values.bottomRightY),
                    right = max(values.topLeftX, values.bottomRightX),
                    bottom = max(values.topLeftY, values.bottomRightY)
                )
            }

            fun fromLegacy(topLeft: Pair<Float, Float>, bottomRight: Pair<Float, Float>): Bounds {
                return Bounds(
                    left = min(topLeft.first, bottomRight.first),
                    top = min(topLeft.second, bottomRight.second),
                    right = max(topLeft.first, bottomRight.first),
                    bottom = max(topLeft.second, bottomRight.second)
                )
            }
        }
    }
}
