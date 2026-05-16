package com.genesisofthewind.bifrost.engine

sealed class ShapeCommand {
    object SafeTestGesture : ShapeCommand()
    object TestLine : ShapeCommand()
    object TestSquare : ShapeCommand()
    object Stop : ShapeCommand()
}
