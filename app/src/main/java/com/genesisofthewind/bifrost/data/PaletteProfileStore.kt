package com.genesisofthewind.bifrost.data

import android.content.Context
import android.content.SharedPreferences

data class PaletteEntry(
    val colorName: String,
    val tapX: Float,
    val tapY: Float,
    val red: Int,
    val green: Int,
    val blue: Int
)

data class PaletteProfile(
    val name: String,
    val penToolX: Float,
    val penToolY: Float,
    val fillToolX: Float,
    val fillToolY: Float,
    val entries: List<PaletteEntry>
)

data class PaletteTapTarget(
    val key: String,
    val label: String,
    val x: Float,
    val y: Float,
    val required: Boolean
)

class PaletteProfileStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("palette_profile_prefs", Context.MODE_PRIVATE)

    fun loadEasyModeProfile(): PaletteProfile {
        val fallback = defaultEasyModeProfile()
        return PaletteProfile(
            name = prefs.getString("profile_name", fallback.name) ?: fallback.name,
            penToolX = prefs.getFloat("pen_tool_x", fallback.penToolX),
            penToolY = prefs.getFloat("pen_tool_y", fallback.penToolY),
            fillToolX = prefs.getFloat("fill_tool_x", fallback.fillToolX),
            fillToolY = prefs.getFloat("fill_tool_y", fallback.fillToolY),
            entries = fallback.entries.mapIndexed { index, entry ->
                entry.copy(
                    tapX = prefs.getFloat("${entry.key}_x", prefs.getFloat("entry_${index}_x", entry.tapX)),
                    tapY = prefs.getFloat("${entry.key}_y", prefs.getFloat("entry_${index}_y", entry.tapY))
                )
            }
        )
    }

    fun saveEasyModeProfile(profile: PaletteProfile) {
        val editor = prefs.edit()
            .putString("profile_name", profile.name)
            .putFloat("pen_tool_x", profile.penToolX)
            .putFloat("pen_tool_y", profile.penToolY)
            .putFloat("fill_tool_x", profile.fillToolX)
            .putFloat("fill_tool_y", profile.fillToolY)
        profile.entries.forEachIndexed { index, entry ->
            editor
                .putFloat("entry_${index}_x", entry.tapX)
                .putFloat("entry_${index}_y", entry.tapY)
                .putFloat("${entry.key}_x", entry.tapX)
                .putFloat("${entry.key}_y", entry.tapY)
        }
        editor.apply()
    }

    fun resetEasyModeProfile(): PaletteProfile {
        val profile = defaultEasyModeProfile()
        saveEasyModeProfile(profile)
        return profile
    }

    fun updateTapTarget(targetKey: String, x: Float, y: Float): PaletteProfile {
        val profile = loadEasyModeProfile()
        val updated = when (targetKey) {
            TARGET_BRUSH_TOOL -> profile.copy(penToolX = x, penToolY = y)
            TARGET_FILL_TOOL -> profile.copy(fillToolX = x, fillToolY = y)
            else -> profile.copy(entries = profile.entries.map { entry ->
                if (entry.key == targetKey) entry.copy(tapX = x, tapY = y) else entry
            })
        }
        saveEasyModeProfile(updated)
        return updated
    }

    fun tapTargets(profile: PaletteProfile = loadEasyModeProfile()): List<PaletteTapTarget> {
        return buildList {
            add(PaletteTapTarget(TARGET_BRUSH_TOOL, "Brush Tool", profile.penToolX, profile.penToolY, true))
            add(PaletteTapTarget(TARGET_FILL_TOOL, "Fill / Bucket Tool", profile.fillToolX, profile.fillToolY, true))
            profile.entries.forEach { entry ->
                add(PaletteTapTarget(entry.key, entry.colorName, entry.tapX, entry.tapY, entry.colorName in REQUIRED_COLOR_NAMES))
            }
        }
    }

    fun targetForKey(targetKey: String, profile: PaletteProfile = loadEasyModeProfile()): PaletteTapTarget? {
        return tapTargets(profile).firstOrNull { it.key == targetKey }
    }

    fun missingRequiredTargets(profile: PaletteProfile = loadEasyModeProfile()): List<String> {
        return tapTargets(profile)
            .filter { it.required && (it.x <= 0f || it.y <= 0f) }
            .map { it.label }
    }

    companion object {
        const val TARGET_BRUSH_TOOL = "brush_tool"
        const val TARGET_FILL_TOOL = "fill_tool"

        val REQUIRED_COLOR_NAMES = setOf("Black", "Pink", "Blue", "Red", "White")

        fun defaultEasyModeProfile(): PaletteProfile {
            return PaletteProfile(
                name = "Tomodachi Life Easy Mode",
                penToolX = 755f,
                penToolY = 222f,
                fillToolX = 646f,
                fillToolY = 222f,
                entries = listOf(
                    PaletteEntry("Black", 1164f, 518f, 10, 10, 12),
                    PaletteEntry("Dark Gray", 1227f, 518f, 85, 88, 94),
                    PaletteEntry("Light Gray", 1292f, 518f, 184, 188, 196),
                    PaletteEntry("White", 1352f, 518f, 245, 245, 245),
                    PaletteEntry("Red", 1164f, 584f, 190, 18, 36),
                    PaletteEntry("Dark Pink", 1227f, 584f, 197, 38, 112),
                    PaletteEntry("Pink", 1292f, 584f, 235, 73, 205),
                    PaletteEntry("Brown", 1164f, 650f, 120, 67, 38),
                    PaletteEntry("Tan", 1292f, 650f, 232, 167, 117),
                    PaletteEntry("Orange", 1164f, 716f, 239, 95, 39),
                    PaletteEntry("Yellow", 1227f, 716f, 240, 196, 32),
                    PaletteEntry("Green", 1164f, 782f, 28, 163, 70),
                    PaletteEntry("Cyan", 1292f, 782f, 41, 181, 171),
                    PaletteEntry("Blue", 1164f, 848f, 31, 72, 224),
                    PaletteEntry("Purple", 1292f, 848f, 94, 46, 196)
                )
            )
        }
    }
}

val PaletteEntry.key: String
    get() = "color_${colorName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}"
