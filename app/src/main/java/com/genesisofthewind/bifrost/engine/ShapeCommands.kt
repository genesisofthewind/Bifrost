package com.genesisofthewind.bifrost.engine

sealed class ShapeCommand {
    object SafeTestGesture : ShapeCommand()
    object CalibratedLine : ShapeCommand()
    object CalibratedDiagonal : ShapeCommand()
    object CalibratedSmallSquare : ShapeCommand()
    object CalibratedXShape : ShapeCommand()
    data class Tap(val x: Float, val y: Float, val durationMs: Long) : ShapeCommand()
    data class Line(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long) : ShapeCommand()
    object TestLine : ShapeCommand()
    object TestSquare : ShapeCommand()
    object Stop : ShapeCommand()
}
