package com.genesisofthewind.bifrost.engine

sealed class ShapeCommand {
    object TestLine : ShapeCommand()
    object TestSquare : ShapeCommand()
    object Stop : ShapeCommand()
}
