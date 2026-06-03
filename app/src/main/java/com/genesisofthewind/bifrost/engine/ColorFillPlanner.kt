package com.genesisofthewind.bifrost.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.data.PaletteEntry
import com.genesisofthewind.bifrost.data.PaletteProfile
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ImageDrawMode(val label: String) {
    OutlineOnly("Outline Only"),
    OutlineAutoColor("Outline + Auto Color (Easy Mode)")
}

data class ColorFillResult(
    val strokePlan: StrokePlan,
    val debugLines: List<String>,
    val warning: String?
)

class ColorFillPlanner(private val calibrationStore: CalibrationStore) {
    fun createOutlineAndFillPlan(
        source: Bitmap,
        outlinePlan: StrokePlan,
        profile: PaletteProfile,
        maxFillRegions: Int = 90
    ): ColorFillResult {
        val bounds = DrawingBounds.fromCalibration(calibrationStore)
        val sample = scaleForPlanning(source)
        val fit = bounds.fit(sample.width, sample.height)
        val regions = detectFillRegions(sample, profile.entries, maxFillRegions)
        val strokes = mutableListOf<StrokeSpec>()
        val warnings = mutableListOf<String>()

        addTapIfValid(strokes, profile.penToolX, profile.penToolY, "select pen/outline tool")
        profile.entries.firstOrNull { it.colorName.equals("Black", ignoreCase = true) }?.let { black ->
            addTapIfValid(strokes, black.tapX, black.tapY, "select outline color Black")
        }
        strokes.addAll(outlinePlan.strokes)

        if (regions.isNotEmpty()) {
            addTapIfValid(strokes, profile.fillToolX, profile.fillToolY, "select bucket/fill tool")
        }

        regions.groupBy { it.entry.colorName }.forEach { (_, colorRegions) ->
            val entry = colorRegions.first().entry
            if (!isValidTap(entry.tapX, entry.tapY)) {
                warnings.add("Color ${entry.colorName} skipped: swatch coordinate is not calibrated.")
                return@forEach
            }
            addTapIfValid(strokes, entry.tapX, entry.tapY, "select color ${entry.colorName}")
            colorRegions.sortedWith(compareBy({ it.centerY }, { it.centerX })).forEach { region ->
                val x = fit.left + (region.centerX / max(1f, sample.width - 1f)) * fit.width
                val y = fit.top + (region.centerY / max(1f, sample.height - 1f)) * fit.height
                addTapIfValid(strokes, x, y, "fill ${entry.colorName}")
            }
        }

        if (regions.size >= maxFillRegions) {
            warnings.add("Color fills capped at $maxFillRegions regions. Use a simpler image or outline-only mode if fills look noisy.")
        }

        val finalStrokes = strokes.mapIndexed { index, stroke ->
            stroke.copy(segmentIndex = index + 1, segmentCount = strokes.size)
        }
        val estimatedMs = finalStrokes.sumOf { it.durationMs + it.delayAfterMs }
        val colorCounts = regions.groupingBy { it.entry.colorName }.eachCount()
        val debugLines = buildList {
            add("command: Outline + Auto Color Easy Mode")
            add("draw mode: ${ImageDrawMode.OutlineAutoColor.label}")
            add("palette profile: ${profile.name}")
            add("brush tool tap: ${profile.penToolX},${profile.penToolY}")
            add("fill tool tap: ${profile.fillToolX},${profile.fillToolY}")
            profile.entries.forEach { entry -> add("${entry.colorName} tap: ${entry.tapX},${entry.tapY}") }
            add("source image: ${source.width}x${source.height}")
            add("planning image: ${sample.width}x${sample.height}")
            add("selector bounds: left=${bounds.left}, top=${bounds.top}, right=${bounds.right}, bottom=${bounds.bottom}")
            add("fit bounds: left=${fit.left}, top=${fit.top}, right=${fit.right}, bottom=${fit.bottom}")
            add("outline strokes: ${outlinePlan.strokes.size}")
            add("colors detected: ${colorCounts.keys.joinToString().ifBlank { "none" }}")
            colorCounts.forEach { (colorName, count) -> add("$colorName fill regions: $count") }
            add("total fill regions: ${regions.size}")
            add("estimated color taps: ${regions.size + colorCounts.size + if (regions.isNotEmpty()) 1 else 0}")
            add("execution order: pen tool -> outline -> fill tool -> color swatch -> region taps")
            add("generated strokes including taps: ${finalStrokes.size}")
            add("estimated draw time: ${estimatedMs / 1000f}s")
            warnings.forEach { add("warning: $it") }
        }

        return ColorFillResult(
            strokePlan = StrokePlan("OutlineAutoColorEasyMode", outlinePlan.debugLines + debugLines, finalStrokes),
            debugLines = debugLines,
            warning = warnings.firstOrNull()
        )
    }

    private fun detectFillRegions(
        bitmap: Bitmap,
        entries: List<PaletteEntry>,
        maxFillRegions: Int
    ): List<ColorRegion> {
        val width = bitmap.width
        val height = bitmap.height
        val labels = Array(height) { IntArray(width) { -1 } }
        val visited = Array(height) { BooleanArray(width) }
        val usableEntries = entries.filterNot { it.colorName.equals("Black", ignoreCase = true) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 32 || isOutlineLike(pixel)) continue
                labels[y][x] = nearestEntryIndex(pixel, usableEntries)
            }
        }

        val regions = mutableListOf<ColorRegion>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val label = labels[y][x]
                if (label < 0 || visited[y][x]) continue
                visited[y][x] = true
                queue.add(Pair(x, y))
                var count = 0
                var sumX = 0L
                var sumY = 0L
                while (queue.isNotEmpty()) {
                    val (cx, cy) = queue.removeFirst()
                    count++
                    sumX += cx
                    sumY += cy
                    for ((nx, ny) in neighbors(cx, cy, width, height)) {
                        if (!visited[ny][nx] && labels[ny][nx] == label) {
                            visited[ny][nx] = true
                            queue.add(Pair(nx, ny))
                        }
                    }
                }
                if (count >= 10) {
                    regions.add(ColorRegion(usableEntries[label], sumX.toFloat() / count, sumY.toFloat() / count, count))
                }
            }
        }

        return regions
            .sortedWith(compareByDescending<ColorRegion> { it.pixelCount }.thenBy { it.centerY }.thenBy { it.centerX })
            .take(maxFillRegions)
    }

    private fun nearestEntryIndex(pixel: Int, entries: List<PaletteEntry>): Int {
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        entries.forEachIndexed { index, entry ->
            val distance = colorDistance(pixel, entry)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun colorDistance(pixel: Int, entry: PaletteEntry): Float {
        val red = Color.red(pixel) - entry.red
        val green = Color.green(pixel) - entry.green
        val blue = Color.blue(pixel) - entry.blue
        return sqrt((red * red + green * green + blue * blue).toFloat())
    }

    private fun isOutlineLike(pixel: Int): Boolean {
        val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
        return brightness < 48
    }

    private fun neighbors(x: Int, y: Int, width: Int, height: Int): List<Pair<Int, Int>> {
        return buildList {
            if (x > 0) add(Pair(x - 1, y))
            if (x < width - 1) add(Pair(x + 1, y))
            if (y > 0) add(Pair(x, y - 1))
            if (y < height - 1) add(Pair(x, y + 1))
        }
    }

    private fun scaleForPlanning(source: Bitmap): Bitmap {
        val maxSide = 96
        val largest = max(source.width, source.height)
        if (largest <= maxSide) return source
        val scale = maxSide.toFloat() / largest
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun addTapIfValid(strokes: MutableList<StrokeSpec>, x: Float, y: Float, label: String) {
        if (!isValidTap(x, y)) return
        strokes.add(StrokeSpec(x, y, x + 1f, y + 1f, 60L, 120L, label))
    }

    private fun isValidTap(x: Float, y: Float): Boolean {
        return x > 0f && y > 0f
    }

    private data class ColorRegion(
        val entry: PaletteEntry,
        val centerX: Float,
        val centerY: Float,
        val pixelCount: Int
    )

    private data class DrawingBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float = right - left
        val height: Float = bottom - top

        fun fit(imageWidth: Int, imageHeight: Int): DrawingBounds {
            val imageAspect = imageWidth.toFloat() / max(1f, imageHeight.toFloat())
            val boundsAspect = width / max(1f, height)
            return if (imageAspect > boundsAspect) {
                val fittedHeight = width / imageAspect
                val topOffset = top + (height - fittedHeight) / 2f
                DrawingBounds(left, topOffset, right, topOffset + fittedHeight)
            } else {
                val fittedWidth = height * imageAspect
                val leftOffset = left + (width - fittedWidth) / 2f
                DrawingBounds(leftOffset, top, leftOffset + fittedWidth, bottom)
            }
        }

        companion object {
            fun fromCalibration(calibrationStore: CalibrationStore): DrawingBounds {
                val values = calibrationStore.getValues()
                val left = min(values.topLeftX, values.bottomRightX)
                val top = min(values.topLeftY, values.bottomRightY)
                val right = max(values.topLeftX, values.bottomRightX)
                val bottom = max(values.topLeftY, values.bottomRightY)
                val inset = min(right - left, bottom - top) * 0.04f
                return DrawingBounds(left + inset, top + inset, right - inset, bottom - inset)
            }
        }
    }
}
