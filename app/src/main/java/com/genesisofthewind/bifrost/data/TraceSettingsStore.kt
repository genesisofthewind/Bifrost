package com.genesisofthewind.bifrost.data

import android.content.Context
import android.content.SharedPreferences
import com.genesisofthewind.bifrost.engine.TraceMode
import com.genesisofthewind.bifrost.engine.TracePresets
import com.genesisofthewind.bifrost.engine.TraceSettings

class TraceSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("trace_settings_prefs", Context.MODE_PRIVATE)

    fun savePresetName(name: String) {
        prefs.edit().putString("preset_name", name).apply()
    }

    fun loadPresetName(): String {
        return prefs.getString("preset_name", TracePresets.TomodachiCartoon.name) ?: TracePresets.TomodachiCartoon.name
    }

    fun saveSettings(settings: TraceSettings) {
        prefs.edit()
            .putString("mode", settings.mode.name)
            .putInt("threshold", settings.threshold)
            .putBoolean("invert", settings.invert)
            .putInt("row_step", settings.rowStep)
            .putInt("min_run_length", settings.minRunLength)
            .putInt("max_strokes", settings.maxStrokes)
            .putLong("stroke_duration_ms", settings.strokeDurationMs)
            .putLong("delay_between_strokes_ms", settings.delayBetweenStrokesMs)
            .putInt("edge_sensitivity", settings.edgeSensitivity)
            .putInt("min_component_size", settings.minComponentSize)
            .putInt("gap_close_pixels", settings.gapClosePixels)
            .apply()
    }

    fun loadSettings(): TraceSettings {
        val fallback = TracePresets.findByName(loadPresetName()).settings ?: TracePresets.TomodachiCartoon.settings!!
        return TraceSettings(
            mode = runCatching { TraceMode.valueOf(prefs.getString("mode", fallback.mode.name) ?: fallback.mode.name) }.getOrDefault(fallback.mode),
            threshold = prefs.getInt("threshold", fallback.threshold),
            invert = prefs.getBoolean("invert", fallback.invert),
            rowStep = prefs.getInt("row_step", fallback.rowStep),
            minRunLength = prefs.getInt("min_run_length", fallback.minRunLength),
            maxStrokes = prefs.getInt("max_strokes", fallback.maxStrokes),
            strokeDurationMs = prefs.getLong("stroke_duration_ms", fallback.strokeDurationMs),
            delayBetweenStrokesMs = prefs.getLong("delay_between_strokes_ms", fallback.delayBetweenStrokesMs),
            edgeSensitivity = prefs.getInt("edge_sensitivity", fallback.edgeSensitivity),
            minComponentSize = prefs.getInt("min_component_size", fallback.minComponentSize),
            gapClosePixels = prefs.getInt("gap_close_pixels", fallback.gapClosePixels)
        )
    }
}
