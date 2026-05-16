package com.genesisofthewind.bifrost.data

import android.content.Context
import android.content.SharedPreferences

data class CalibrationValues(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long,
    val topLeftX: Float,
    val topLeftY: Float,
    val bottomRightX: Float,
    val bottomRightY: Float
)

class CalibrationStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("calibration_prefs", Context.MODE_PRIVATE)

    fun saveValues(values: CalibrationValues) {
        prefs.edit()
            .putFloat("start_x", values.startX)
            .putFloat("start_y", values.startY)
            .putFloat("end_x", values.endX)
            .putFloat("end_y", values.endY)
            .putLong("duration_ms", values.durationMs)
            .putFloat("tl_x", values.topLeftX)
            .putFloat("tl_y", values.topLeftY)
            .putFloat("br_x", values.bottomRightX)
            .putFloat("br_y", values.bottomRightY)
            .apply()
    }

    fun getValues(): CalibrationValues {
        return CalibrationValues(
            startX = prefs.getFloat("start_x", 120f),
            startY = prefs.getFloat("start_y", 120f),
            endX = prefs.getFloat("end_x", 260f),
            endY = prefs.getFloat("end_y", 120f),
            durationMs = prefs.getLong("duration_ms", 220L),
            topLeftX = prefs.getFloat("tl_x", 100f),
            topLeftY = prefs.getFloat("tl_y", 100f),
            bottomRightX = prefs.getFloat("br_x", 900f),
            bottomRightY = prefs.getFloat("br_y", 900f)
        )
    }

    fun resetValues() {
        saveValues(
            CalibrationValues(
                startX = 120f,
                startY = 120f,
                endX = 260f,
                endY = 120f,
                durationMs = 220L,
                topLeftX = 100f,
                topLeftY = 100f,
                bottomRightX = 900f,
                bottomRightY = 900f
            )
        )
    }

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
