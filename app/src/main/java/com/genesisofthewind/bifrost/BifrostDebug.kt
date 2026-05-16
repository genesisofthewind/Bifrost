package com.genesisofthewind.bifrost

import android.util.Log
import androidx.compose.runtime.mutableStateListOf

private const val MAX_MESSAGES = 8

object BifrostDebug {
    const val TAG = "BifrostDebug"

    val messages = mutableStateListOf("Bifrost ready")

    fun record(message: String) {
        Log.d(TAG, message)
        messages.add(0, message)
        while (messages.size > MAX_MESSAGES) {
            messages.removeAt(messages.lastIndex)
        }
    }
}
