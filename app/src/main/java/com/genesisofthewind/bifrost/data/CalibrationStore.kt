package com.genesisofthewind.bifrost.data

import android.content.Context
import android.content.SharedPreferences

class CalibrationStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("calibration_prefs", Context.MODE_PRIVATE)

    fun saveTopLeft(x: Float, y: Float) {
        prefs.edit().putFloat("tl_x", x).putFloat("tl_y", y).apply()
    }

    fun saveBottomRight(x: Float, y: Float) {
        prefs.edit().putFloat("br_x", x).putFloat("br_y", y).apply()
    }

    fun getTopLeft(): Pair<Float, Float> {
        return Pair(prefs.getFloat("tl_x", 0f), prefs.getFloat("tl_y", 0f))
    }

    fun getBottomRight(): Pair<Float, Float> {
        return Pair(prefs.getFloat("br_x", 1000f), prefs.getFloat("br_y", 1000f))
    }

    fun saveDisplayId(displayId: Int) {
        prefs.edit().putInt("display_id", displayId).apply()
    }

    fun getDisplayId(): Int {
        return prefs.getInt("display_id", 0)
    }
}
