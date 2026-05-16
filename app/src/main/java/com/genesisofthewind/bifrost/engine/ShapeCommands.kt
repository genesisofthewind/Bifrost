package com.genesisofthewind.bifrost.engine

sealed class ShapeCommand {
    object SafeTestGesture : ShapeCommand()
    object CalibratedLine : ShapeCommand()
    object CalibratedDiagonal : ShapeCommand()
    object DiagonalTopLeftToBottomRight : ShapeCommand()
    object DiagonalTopRightToBottomLeft : ShapeCommand()
    object DiagonalBottomLeftToTopRight : ShapeCommand()
    object DiagonalBottomRightToTopLeft : ShapeCommand()
    object SegmentedDiagonal : ShapeCommand()
    object SegmentedTopLeftToBottomRight : ShapeCommand()
    object SegmentedTopRightToBottomLeft : ShapeCommand()
    object CalibratedSmallSquare : ShapeCommand()
    object CalibratedXShape : ShapeCommand()
    object ReverseXShape : ShapeCommand()
    object SegmentedXShape : ShapeCommand()
    data class Tap(val x: Float, val y: Float, val durationMs: Long) : ShapeCommand()
    data class Line(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long) : ShapeCommand()
    object TestLine : ShapeCommand()
    object TestSquare : ShapeCommand()
    object Stop : ShapeCommand()
}
