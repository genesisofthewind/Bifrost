package com.thor.drawbridge.engine

sealed class ShapeCommand {
    object TestLine : ShapeCommand()
    object TestSquare : ShapeCommand()
    object Stop : ShapeCommand()
}
