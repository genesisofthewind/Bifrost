package com.genesisofthewind.bifrost.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.data.CalibrationValues
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class TraceMode(val label: String) {
    FillScanline("Fill Trace / Scanline Trace"),
    Outline("Outline Trace"),
    SparseSketch("Sparse Sketch Trace"),
    BalancedHybrid("Balanced / Hybrid"),
    CartoonFillReady("Cartoon Fill Ready"),
    CharacterDetail("Character Detail")
}

data class TraceSettings(
    val mode: TraceMode,
    val threshold: Int,
    val invert: Boolean,
    val rowStep: Int,
    val minRunLength: Int,
    val maxStrokes: Int,
    val strokeDurationMs: Long,
    val delayBetweenStrokesMs: Long,
    val edgeSensitivity: Int = 55,
    val minComponentSize: Int = 4,
    val gapClosePixels: Int = 2
)

data class TracePreset(
    val name: String,
    val description: String,
    val settings: TraceSettings?
)

object TracePresets {
    val Custom = TracePreset("Custom", "Modified settings", null)

    val FastSparse = TracePreset(
        name = "Fast / Sparse",
        description = "Fast rough trace",
        settings = TraceSettings(
            mode = TraceMode.SparseSketch,
            threshold = 128,
            invert = false,
            rowStep = 6,
            minRunLength = 7,
            maxStrokes = 300,
            strokeDurationMs = 25L,
            delayBetweenStrokesMs = 20L,
            edgeSensitivity = 35,
            minComponentSize = 8,
            gapClosePixels = 1
        )
    )

    val Balanced = TracePreset(
        name = "Balanced",
        description = "Cartoon default",
        settings = TraceSettings(
            mode = TraceMode.BalancedHybrid,
            threshold = 140,
            invert = false,
            rowStep = 2,
            minRunLength = 3,
            maxStrokes = 1200,
            strokeDurationMs = 35L,
            delayBetweenStrokesMs = 30L,
            edgeSensitivity = 55,
            minComponentSize = 5,
            gapClosePixels = 2
        )
    )

    val DenseDetail = TracePreset(
        name = "Dense Detail",
        description = "More detail, slower draw",
        settings = TraceSettings(
            mode = TraceMode.BalancedHybrid,
            threshold = 128,
            invert = false,
            rowStep = 1,
            minRunLength = 1,
            maxStrokes = 2200,
            strokeDurationMs = 40L,
            delayBetweenStrokesMs = 25L,
            edgeSensitivity = 70,
            minComponentSize = 1,
            gapClosePixels = 1
        )
    )

    val TomodachiCartoon = TracePreset(
        name = "Tomodachi Cartoon",
        description = "Bucket-fill friendly",
        settings = TraceSettings(
            mode = TraceMode.CartoonFillReady,
            threshold = 142,
            invert = false,
            rowStep = 1,
            minRunLength = 2,
            maxStrokes = 1800,
            strokeDurationMs = 35L,
            delayBetweenStrokesMs = 25L,
            edgeSensitivity = 45,
            minComponentSize = 10,
            gapClosePixels = 3
        )
    )

    val CleanCartoonPng = TracePreset(
        name = "Clean Cartoon PNG",
        description = "Transparent cleanup",
        settings = TraceSettings(
            mode = TraceMode.CartoonFillReady,
            threshold = 140,
            invert = false,
            rowStep = 1,
            minRunLength = 2,
            maxStrokes = 1800,
            strokeDurationMs = 35L,
            delayBetweenStrokesMs = 25L,
            edgeSensitivity = 50,
            minComponentSize = 12,
            gapClosePixels = 3
        )
    )

    val CharacterDetail = TracePreset(
        name = "Character Detail",
        description = "Interior markings",
        settings = TraceSettings(
            mode = TraceMode.CharacterDetail,
            threshold = 132,
            invert = false,
            rowStep = 1,
            minRunLength = 1,
            maxStrokes = 2400,
            strokeDurationMs = 35L,
            delayBetweenStrokesMs = 25L,
            edgeSensitivity = 78,
            minComponentSize = 2,
            gapClosePixels = 1
        )
    )

    val OutlineFocused = TracePreset(
        name = "Outline Focused",
        description = "Edges only",
        settings = TraceSettings(
            mode = TraceMode.Outline,
            threshold = 132,
            invert = false,
            rowStep = 1,
            minRunLength = 2,
            maxStrokes = 1600,
            strokeDurationMs = 35L,
            delayBetweenStrokesMs = 25L,
            edgeSensitivity = 55,
            minComponentSize = 4,
            gapClosePixels = 2
        )
    )

    val All = listOf(TomodachiCartoon, CleanCartoonPng, CharacterDetail, FastSparse, Balanced, DenseDetail, OutlineFocused, Custom)
}

data class ImageTraceResult(
    val processedBitmap: Bitmap,
    val strokePlan: StrokePlan,
    val warning: String?
)

private const val ALPHA_THRESHOLD = 24

class ImageTraceEngine(private val calibrationStore: CalibrationStore) {
    fun createTracePlan(source: Bitmap, settings: TraceSettings): ImageTraceResult {
        val values = calibrationStore.getValues()
        val canvas = TraceBounds.fromValues(values).inset()
        val preprocessed = preprocessSource(source)
        val sample = preprocessed.bitmap
        val processed = Bitmap.createBitmap(sample.width, sample.height, Bitmap.Config.ARGB_8888)
        val threshold = settings.threshold.coerceIn(0, 255)
        val rowStep = settings.rowStep.coerceIn(1, 16)
        val minRunLength = settings.minRunLength.coerceIn(1, 64)
        val maxStrokes = settings.maxStrokes.coerceIn(20, 3000)
        val strokeDurationMs = settings.strokeDurationMs.coerceIn(15L, 1200L)
        val delayBetweenStrokesMs = settings.delayBetweenStrokesMs.coerceIn(0L, 500L)
        val edgeSensitivity = settings.edgeSensitivity.coerceIn(1, 100)
        val minComponentSize = settings.minComponentSize.coerceIn(1, 200)
        val gapClosePixels = settings.gapClosePixels.coerceIn(0, 6)

        val fit = fitInsideCanvas(sample.width, sample.height, canvas)
        val rawBlackPixels = Array(sample.height) { BooleanArray(sample.width) }
        for (y in 0 until sample.height) {
            var x = 0
            while (x < sample.width) {
                val pixel = sample.getPixel(x, y)
                val black = Color.alpha(pixel) >= ALPHA_THRESHOLD && isBlack(pixel, threshold, settings.invert)
                rawBlackPixels[y][x] = black
                processed.setPixel(x, y, if (black) Color.BLACK else Color.WHITE)
                x++
            }
        }

        val blackPixels = connectShortHorizontalGaps(removeSpecks(rawBlackPixels, minNeighbors = 2), maxGap = 1)
        paintPixels(processed, blackPixels)
        val baseEdgePixels = connectShortVerticalGaps(
            connectShortHorizontalGaps(removeSpecks(edgePixels(blackPixels, processed), minNeighbors = 1), maxGap = gapClosePixels.coerceAtMost(2)),
            maxGap = gapClosePixels.coerceAtMost(2)
        )
        val cartoonEdgePixels = connectShortVerticalGaps(
            connectShortHorizontalGaps(removeSpecks(baseEdgePixels, minNeighbors = 1), maxGap = gapClosePixels),
            maxGap = gapClosePixels
        )
        val internalDarkPixels = removeSpecks(rawBlackPixels, minNeighbors = 1)
        val characterDetailPixels = connectShortVerticalGaps(
            connectShortHorizontalGaps(
                combinePixels(combinePixels(baseEdgePixels, colorBoundaryEdges(sample, edgeSensitivity)), internalDarkPixels),
                maxGap = gapClosePixels.coerceAtMost(1)
            ),
            maxGap = gapClosePixels.coerceAtMost(1)
        )
        val cleanupInput = when (settings.mode) {
            TraceMode.FillScanline -> blackPixels
            TraceMode.Outline -> baseEdgePixels
            TraceMode.SparseSketch -> baseEdgePixels
            TraceMode.BalancedHybrid -> baseEdgePixels
            TraceMode.CartoonFillReady -> cartoonEdgePixels
            TraceMode.CharacterDetail -> characterDetailPixels
        }
        val cleanup = removeSmallComponents(
            pixels = cleanupInput,
            minComponentSize = when (settings.mode) {
                TraceMode.CharacterDetail -> minComponentSize.coerceAtMost(2)
                else -> minComponentSize
            },
            keepDistance = 2
        )
        val tracePixels = cleanup.pixels
        paintPixels(processed, tracePixels)
        val effectiveRowStep = when (settings.mode) {
            TraceMode.FillScanline -> rowStep
            TraceMode.Outline -> rowStep
            TraceMode.SparseSketch -> (rowStep * 2).coerceIn(2, 24)
            TraceMode.BalancedHybrid -> rowStep
            TraceMode.CartoonFillReady -> rowStep
            TraceMode.CharacterDetail -> rowStep
        }
        val effectiveMinRun = when (settings.mode) {
            TraceMode.FillScanline -> minRunLength
            TraceMode.Outline -> minRunLength
            TraceMode.SparseSketch -> (minRunLength * 2).coerceIn(2, 96)
            TraceMode.BalancedHybrid -> minRunLength
            TraceMode.CartoonFillReady -> minRunLength
            TraceMode.CharacterDetail -> minRunLength
        }
        val result = when (settings.mode) {
            TraceMode.FillScanline -> horizontalRuns(
                pixels = tracePixels,
                fit = fit,
                rowStep = effectiveRowStep,
                minRunLength = effectiveMinRun,
                maxStrokes = maxStrokes,
                strokeDurationMs = strokeDurationMs,
                delayBetweenStrokesMs = delayBetweenStrokesMs,
                label = "fill scanline"
            )
            TraceMode.Outline -> outlineRuns(
                pixels = tracePixels,
                fit = fit,
                rowStep = effectiveRowStep,
                minRunLength = effectiveMinRun,
                maxStrokes = maxStrokes,
                strokeDurationMs = strokeDurationMs,
                delayBetweenStrokesMs = delayBetweenStrokesMs
            )
            TraceMode.SparseSketch -> horizontalRuns(
                pixels = tracePixels,
                fit = fit,
                rowStep = effectiveRowStep,
                minRunLength = effectiveMinRun,
                maxStrokes = maxStrokes,
                strokeDurationMs = strokeDurationMs,
                delayBetweenStrokesMs = delayBetweenStrokesMs,
                label = "sparse sketch"
            )
            TraceMode.BalancedHybrid -> balancedHybridRuns(
                edgePixels = tracePixels,
                fillPixels = blackPixels,
                fit = fit,
                rowStep = effectiveRowStep,
                minRunLength = effectiveMinRun,
                maxStrokes = maxStrokes,
                strokeDurationMs = strokeDurationMs,
                delayBetweenStrokesMs = delayBetweenStrokesMs
            )
            TraceMode.CartoonFillReady -> cartoonFillReadyRuns(
                edgePixels = tracePixels,
                fit = fit,
                rowStep = effectiveRowStep,
                minRunLength = effectiveMinRun,
                maxStrokes = maxStrokes,
                strokeDurationMs = strokeDurationMs,
                delayBetweenStrokesMs = delayBetweenStrokesMs
            )
            TraceMode.CharacterDetail -> outlineRuns(
                pixels = tracePixels,
                fit = fit,
                rowStep = effectiveRowStep,
                minRunLength = effectiveMinRun,
                maxStrokes = maxStrokes,
                strokeDurationMs = strokeDurationMs,
                delayBetweenStrokesMs = delayBetweenStrokesMs
            )
        }

        val warning = if (result.capped) {
            "Trace capped at $maxStrokes strokes. Increase row step, raise minimum run length, or use a simpler image."
        } else {
            null
        }
        val finalStrokes = result.strokes.mapIndexed { index, stroke ->
            stroke.copy(segmentIndex = index + 1, segmentCount = result.strokes.size)
        }
        val estimatedDrawTimeMs = finalStrokes.sumOf { it.durationMs + it.delayAfterMs }
        val debugLines = buildList {
            add("command: Imported Image Trace")
            add("trace mode: ${settings.mode.label}")
            add("source image: ${source.width}x${source.height}")
            add("alpha-aware preprocessing: ${preprocessed.alphaAwareUsed}")
            add("alpha crop bounds: ${preprocessed.cropLabel}")
            add("processed image: ${processed.width}x${processed.height}")
            add("selector bounds: left=${canvas.left}, top=${canvas.top}, right=${canvas.right}, bottom=${canvas.bottom}")
            add("fit bounds: left=${fit.left}, top=${fit.top}, right=${fit.right}, bottom=${fit.bottom}")
            add("threshold: $threshold")
            add("invert: ${settings.invert}")
            add("edge sensitivity: $edgeSensitivity")
            add("minimum component size: $minComponentSize")
            add("removed small components: ${cleanup.removedComponents}")
            add("removed small pixels: ${cleanup.removedPixels}")
            add("gap close pixels: $gapClosePixels")
            add("row step: $effectiveRowStep")
            add("minimum run length: $effectiveMinRun")
            add("max strokes: $maxStrokes")
            add("stroke duration ms: $strokeDurationMs")
            add("delay between strokes ms: $delayBetweenStrokesMs")
            add("generated strokes: ${finalStrokes.size}")
            add("estimated draw time: ${estimatedDrawTimeMs / 1000f}s")
            warning?.let { add("warning: $it") }
        }
        return ImageTraceResult(processed, StrokePlan("ImportedImageTrace", debugLines, finalStrokes), warning)
    }

    private data class TraceBuildResult(val strokes: List<StrokeSpec>, val capped: Boolean)

    private fun horizontalRuns(
        pixels: Array<BooleanArray>,
        fit: TraceBounds,
        rowStep: Int,
        minRunLength: Int,
        maxStrokes: Int,
        strokeDurationMs: Long,
        delayBetweenStrokesMs: Long,
        label: String
    ): TraceBuildResult {
        val strokes = mutableListOf<StrokeSpec>()
        var capped = false
        val height = pixels.size
        val width = pixels.firstOrNull()?.size ?: 0
        for (y in 0 until height step rowStep) {
            var x = 0
            while (x < width) {
                while (x < width && !pixels[y][x]) x++
                val runStart = x
                while (x < width && pixels[y][x]) x++
                val runEnd = x - 1
                if (runStart <= runEnd && runEnd - runStart + 1 >= minRunLength) {
                    if (strokes.size >= maxStrokes) {
                        capped = true
                        break
                    }
                    addHorizontalStroke(strokes, fit, width, height, runStart, runEnd, y, strokeDurationMs, delayBetweenStrokesMs, label)
                }
            }
            if (capped) break
        }
        return TraceBuildResult(strokes, capped)
    }

    private fun outlineRuns(
        pixels: Array<BooleanArray>,
        fit: TraceBounds,
        rowStep: Int,
        minRunLength: Int,
        maxStrokes: Int,
        strokeDurationMs: Long,
        delayBetweenStrokesMs: Long
    ): TraceBuildResult {
        val horizontal = horizontalRuns(pixels, fit, rowStep, minRunLength, maxStrokes, strokeDurationMs, delayBetweenStrokesMs, "outline horizontal")
        if (horizontal.capped || horizontal.strokes.size >= maxStrokes) return horizontal

        val strokes = horizontal.strokes.toMutableList()
        val height = pixels.size
        val width = pixels.firstOrNull()?.size ?: 0
        var capped = false
        for (x in 0 until width step rowStep) {
            var y = 0
            while (y < height) {
                while (y < height && !pixels[y][x]) y++
                val runStart = y
                while (y < height && pixels[y][x]) y++
                val runEnd = y - 1
                if (runStart <= runEnd && runEnd - runStart + 1 >= minRunLength) {
                    if (strokes.size >= maxStrokes) {
                        capped = true
                        break
                    }
                    addVerticalStroke(strokes, fit, width, height, x, runStart, runEnd, strokeDurationMs, delayBetweenStrokesMs)
                }
            }
            if (capped) break
        }
        return TraceBuildResult(strokes, capped)
    }

    private fun balancedHybridRuns(
        edgePixels: Array<BooleanArray>,
        fillPixels: Array<BooleanArray>,
        fit: TraceBounds,
        rowStep: Int,
        minRunLength: Int,
        maxStrokes: Int,
        strokeDurationMs: Long,
        delayBetweenStrokesMs: Long
    ): TraceBuildResult {
        val outline = outlineRuns(edgePixels, fit, rowStep, minRunLength, maxStrokes, strokeDurationMs, delayBetweenStrokesMs)
        if (outline.capped || outline.strokes.size >= maxStrokes) return outline

        val remaining = maxStrokes - outline.strokes.size
        val fill = horizontalRuns(
            pixels = fillPixels,
            fit = fit,
            rowStep = (rowStep * 3).coerceIn(3, 18),
            minRunLength = (minRunLength * 3).coerceIn(3, 96),
            maxStrokes = remaining,
            strokeDurationMs = strokeDurationMs,
            delayBetweenStrokesMs = delayBetweenStrokesMs,
            label = "hybrid light fill"
        )
        return TraceBuildResult(outline.strokes + fill.strokes, fill.capped)
    }

    private fun cartoonFillReadyRuns(
        edgePixels: Array<BooleanArray>,
        fit: TraceBounds,
        rowStep: Int,
        minRunLength: Int,
        maxStrokes: Int,
        strokeDurationMs: Long,
        delayBetweenStrokesMs: Long
    ): TraceBuildResult {
        val horizontal = horizontalRuns(
            pixels = edgePixels,
            fit = fit,
            rowStep = rowStep,
            minRunLength = minRunLength,
            maxStrokes = maxStrokes,
            strokeDurationMs = strokeDurationMs,
            delayBetweenStrokesMs = delayBetweenStrokesMs,
            label = "cartoon outline horizontal"
        )
        if (horizontal.capped || horizontal.strokes.size >= maxStrokes) return horizontal

        val vertical = verticalRuns(
            pixels = edgePixels,
            fit = fit,
            columnStep = rowStep,
            minRunLength = minRunLength,
            maxStrokes = maxStrokes - horizontal.strokes.size,
            strokeDurationMs = strokeDurationMs,
            delayBetweenStrokesMs = delayBetweenStrokesMs,
            label = "cartoon outline vertical"
        )
        return TraceBuildResult(horizontal.strokes + vertical.strokes, vertical.capped)
    }

    private fun edgePixels(blackPixels: Array<BooleanArray>, processed: Bitmap): Array<BooleanArray> {
        val height = blackPixels.size
        val width = blackPixels.firstOrNull()?.size ?: 0
        val edges = Array(height) { BooleanArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val current = blackPixels[y][x]
                val edge = (x > 0 && blackPixels[y][x - 1] != current) ||
                    (x < width - 1 && blackPixels[y][x + 1] != current) ||
                    (y > 0 && blackPixels[y - 1][x] != current) ||
                    (y < height - 1 && blackPixels[y + 1][x] != current)
                edges[y][x] = edge
                processed.setPixel(x, y, if (edge) Color.BLACK else Color.WHITE)
            }
        }
        return edges
    }

    private fun colorBoundaryEdges(source: Bitmap, edgeSensitivity: Int): Array<BooleanArray> {
        val width = source.width
        val height = source.height
        val edges = Array(height) { BooleanArray(width) }
        val threshold = 185 - edgeSensitivity.coerceIn(1, 100)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val current = source.getPixel(x, y)
                if (x < width - 1 && colorDifference(current, source.getPixel(x + 1, y)) >= threshold) {
                    edges[y][x] = true
                    edges[y][x + 1] = true
                }
                if (y < height - 1 && colorDifference(current, source.getPixel(x, y + 1)) >= threshold) {
                    edges[y][x] = true
                    edges[y + 1][x] = true
                }
            }
        }
        return edges
    }

    private fun colorDifference(first: Int, second: Int): Int {
        val redDiff = abs(Color.red(first) - Color.red(second))
        val greenDiff = abs(Color.green(first) - Color.green(second))
        val blueDiff = abs(Color.blue(first) - Color.blue(second))
        val brightnessDiff = abs(gray(first) - gray(second))
        return max(max(redDiff, greenDiff), max(blueDiff, brightnessDiff))
    }

    private fun gray(pixel: Int): Int {
        return ((Color.red(pixel) * 0.299f) + (Color.green(pixel) * 0.587f) + (Color.blue(pixel) * 0.114f)).roundToInt()
    }

    private fun combinePixels(first: Array<BooleanArray>, second: Array<BooleanArray>): Array<BooleanArray> {
        val height = min(first.size, second.size)
        val width = min(first.firstOrNull()?.size ?: 0, second.firstOrNull()?.size ?: 0)
        return Array(height) { y ->
            BooleanArray(width) { x -> first[y][x] || second[y][x] }
        }
    }

    private data class ComponentCleanupResult(
        val pixels: Array<BooleanArray>,
        val removedComponents: Int,
        val removedPixels: Int
    )

    private data class PixelComponent(val pixels: List<Pair<Int, Int>>, val isLarge: Boolean)

    private fun removeSmallComponents(
        pixels: Array<BooleanArray>,
        minComponentSize: Int,
        keepDistance: Int
    ): ComponentCleanupResult {
        if (minComponentSize <= 1) return ComponentCleanupResult(pixels, 0, 0)

        val height = pixels.size
        val width = pixels.firstOrNull()?.size ?: 0
        val visited = Array(height) { BooleanArray(width) }
        val components = mutableListOf<PixelComponent>()
        val largeMask = Array(height) { BooleanArray(width) }

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!pixels[y][x] || visited[y][x]) continue
                val componentPixels = collectComponent(pixels, visited, x, y)
                val isLarge = componentPixels.size >= minComponentSize
                components.add(PixelComponent(componentPixels, isLarge))
                if (isLarge) {
                    componentPixels.forEach { (px, py) -> largeMask[py][px] = true }
                }
            }
        }

        val cleaned = Array(height) { BooleanArray(width) }
        var removedComponents = 0
        var removedPixels = 0
        components.forEach { component ->
            val keep = component.isLarge || component.pixels.any { (x, y) -> nearLargeComponent(largeMask, x, y, keepDistance) }
            if (keep) {
                component.pixels.forEach { (x, y) -> cleaned[y][x] = true }
            } else {
                removedComponents++
                removedPixels += component.pixels.size
            }
        }

        return ComponentCleanupResult(cleaned, removedComponents, removedPixels)
    }

    private fun collectComponent(
        pixels: Array<BooleanArray>,
        visited: Array<BooleanArray>,
        startX: Int,
        startY: Int
    ): List<Pair<Int, Int>> {
        val height = pixels.size
        val width = pixels.firstOrNull()?.size ?: 0
        val queue = ArrayDeque<Pair<Int, Int>>()
        val component = mutableListOf<Pair<Int, Int>>()
        visited[startY][startX] = true
        queue.add(startX to startY)

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            component.add(x to y)
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height && pixels[ny][nx] && !visited[ny][nx]) {
                        visited[ny][nx] = true
                        queue.add(nx to ny)
                    }
                }
            }
        }

        return component
    }

    private fun nearLargeComponent(mask: Array<BooleanArray>, x: Int, y: Int, distance: Int): Boolean {
        val height = mask.size
        val width = mask.firstOrNull()?.size ?: 0
        for (ny in (y - distance)..(y + distance)) {
            for (nx in (x - distance)..(x + distance)) {
                if (nx in 0 until width && ny in 0 until height && mask[ny][nx]) return true
            }
        }
        return false
    }

    private fun removeSpecks(pixels: Array<BooleanArray>, minNeighbors: Int): Array<BooleanArray> {
        val height = pixels.size
        val width = pixels.firstOrNull()?.size ?: 0
        val cleaned = Array(height) { BooleanArray(width) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!pixels[y][x]) continue
                var neighbors = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until width && ny in 0 until height && pixels[ny][nx]) {
                            neighbors++
                        }
                    }
                }
                cleaned[y][x] = neighbors >= minNeighbors
            }
        }
        return cleaned
    }

    private fun connectShortHorizontalGaps(pixels: Array<BooleanArray>, maxGap: Int): Array<BooleanArray> {
        val height = pixels.size
        val width = pixels.firstOrNull()?.size ?: 0
        val connected = Array(height) { y -> pixels[y].copyOf() }
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                while (x < width && pixels[y][x]) x++
                val gapStart = x
                while (x < width && !pixels[y][x]) x++
                val gapEnd = x - 1
                val gapLength = gapEnd - gapStart + 1
                if (gapStart > 0 && x < width && gapLength in 1..maxGap) {
                    for (fillX in gapStart..gapEnd) connected[y][fillX] = true
                }
            }
        }
        return connected
    }

    private fun connectShortVerticalGaps(pixels: Array<BooleanArray>, maxGap: Int): Array<BooleanArray> {
        val height = pixels.size
        val width = pixels.firstOrNull()?.size ?: 0
        val connected = Array(height) { y -> pixels[y].copyOf() }
        for (x in 0 until width) {
            var y = 0
            while (y < height) {
                while (y < height && pixels[y][x]) y++
                val gapStart = y
                while (y < height && !pixels[y][x]) y++
                val gapEnd = y - 1
                val gapLength = gapEnd - gapStart + 1
                if (gapStart > 0 && y < height && gapLength in 1..maxGap) {
                    for (fillY in gapStart..gapEnd) connected[fillY][x] = true
                }
            }
        }
        return connected
    }

    private fun paintPixels(bitmap: Bitmap, pixels: Array<BooleanArray>) {
        for (y in pixels.indices) {
            for (x in pixels[y].indices) {
                bitmap.setPixel(x, y, if (pixels[y][x]) Color.BLACK else Color.WHITE)
            }
        }
    }

    private fun verticalRuns(
        pixels: Array<BooleanArray>,
        fit: TraceBounds,
        columnStep: Int,
        minRunLength: Int,
        maxStrokes: Int,
        strokeDurationMs: Long,
        delayBetweenStrokesMs: Long,
        label: String
    ): TraceBuildResult {
        val strokes = mutableListOf<StrokeSpec>()
        var capped = false
        val height = pixels.size
        val width = pixels.firstOrNull()?.size ?: 0
        for (x in 0 until width step columnStep) {
            var y = 0
            while (y < height) {
                while (y < height && !pixels[y][x]) y++
                val runStart = y
                while (y < height && pixels[y][x]) y++
                val runEnd = y - 1
                if (runStart <= runEnd && runEnd - runStart + 1 >= minRunLength) {
                    if (strokes.size >= maxStrokes) {
                        capped = true
                        break
                    }
                    addVerticalStroke(strokes, fit, width, height, x, runStart, runEnd, strokeDurationMs, delayBetweenStrokesMs, label)
                }
            }
            if (capped) break
        }
        return TraceBuildResult(strokes, capped)
    }

    private fun addHorizontalStroke(
        strokes: MutableList<StrokeSpec>,
        fit: TraceBounds,
        imageWidth: Int,
        imageHeight: Int,
        runStart: Int,
        runEnd: Int,
        y: Int,
        strokeDurationMs: Long,
        delayBetweenStrokesMs: Long,
        label: String
    ) {
        val startX = fit.left + (runStart.toFloat() / max(1, imageWidth - 1)) * fit.width
        val endX = fit.left + (runEnd.toFloat() / max(1, imageWidth - 1)) * fit.width
        val drawY = fit.top + (y.toFloat() / max(1, imageHeight - 1)) * fit.height
        if (abs(endX - startX) >= 1f) {
            strokes.add(
                StrokeSpec(startX, drawY, endX, drawY, strokeDurationMs, delayBetweenStrokesMs, "$label row=$y", true)
            )
        }
    }

    private fun addVerticalStroke(
        strokes: MutableList<StrokeSpec>,
        fit: TraceBounds,
        imageWidth: Int,
        imageHeight: Int,
        x: Int,
        runStart: Int,
        runEnd: Int,
        strokeDurationMs: Long,
        delayBetweenStrokesMs: Long,
        label: String = "outline vertical"
    ) {
        val drawX = fit.left + (x.toFloat() / max(1, imageWidth - 1)) * fit.width
        val startY = fit.top + (runStart.toFloat() / max(1, imageHeight - 1)) * fit.height
        val endY = fit.top + (runEnd.toFloat() / max(1, imageHeight - 1)) * fit.height
        if (abs(endY - startY) >= 1f) {
            strokes.add(
                StrokeSpec(drawX, startY, drawX, endY, strokeDurationMs, delayBetweenStrokesMs, "$label col=$x", true)
            )
        }
    }

    private data class PreprocessedBitmap(
        val bitmap: Bitmap,
        val alphaAwareUsed: Boolean,
        val cropLabel: String
    )

    private fun preprocessSource(source: Bitmap): PreprocessedBitmap {
        val alphaBounds = nonTransparentBounds(source)
        val alphaAware = alphaBounds != null && (
            alphaBounds.left > 0 ||
                alphaBounds.top > 0 ||
                alphaBounds.right < source.width ||
                alphaBounds.bottom < source.height
            )
        val cropped = alphaBounds?.let { bounds ->
            Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width(), bounds.height())
        } ?: source
        val scaled = scaleForTracing(cropped)
        val label = alphaBounds?.let { "${it.left},${it.top} -> ${it.right},${it.bottom}" } ?: "none"
        return PreprocessedBitmap(scaled, alphaAware, label)
    }

    private fun nonTransparentBounds(source: Bitmap): Rect? {
        var left = source.width
        var top = source.height
        var right = -1
        var bottom = -1
        var sawTransparent = false

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val alpha = Color.alpha(source.getPixel(x, y))
                if (alpha < ALPHA_THRESHOLD) {
                    sawTransparent = true
                    continue
                }
                left = min(left, x)
                top = min(top, y)
                right = max(right, x + 1)
                bottom = max(bottom, y + 1)
            }
        }

        if (!sawTransparent || right <= left || bottom <= top) return null
        val margin = 2
        return Rect(
            (left - margin).coerceAtLeast(0),
            (top - margin).coerceAtLeast(0),
            (right + margin).coerceAtMost(source.width),
            (bottom + margin).coerceAtMost(source.height)
        )
    }

    private fun scaleForTracing(source: Bitmap): Bitmap {
        val maxSide = 160
        val sourceMax = max(source.width, source.height)
        if (sourceMax <= maxSide) return source
        val scale = maxSide.toFloat() / sourceMax.toFloat()
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun isBlack(pixel: Int, threshold: Int, invert: Boolean): Boolean {
        val black = gray(pixel) < threshold
        return if (invert) !black else black
    }

    private fun fitInsideCanvas(imageWidth: Int, imageHeight: Int, canvas: TraceBounds): TraceBounds {
        val imageAspect = imageWidth.toFloat() / max(1, imageHeight).toFloat()
        val canvasAspect = canvas.width / canvas.height
        return if (imageAspect > canvasAspect) {
            val height = canvas.width / imageAspect
            val top = canvas.top + (canvas.height - height) / 2f
            TraceBounds(canvas.left, top, canvas.right, top + height)
        } else {
            val width = canvas.height * imageAspect
            val left = canvas.left + (canvas.width - width) / 2f
            TraceBounds(left, canvas.top, left + width, canvas.bottom)
        }
    }

    private data class TraceBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float = max(1f, abs(right - left))
        val height: Float = max(1f, abs(bottom - top))
        val safetyInset: Float = min(width, height).coerceAtMost(120f) * 0.08f

        fun inset(): TraceBounds {
            return TraceBounds(
                left = left + safetyInset,
                top = top + safetyInset,
                right = right - safetyInset,
                bottom = bottom - safetyInset
            )
        }

        companion object {
            fun fromValues(values: CalibrationValues): TraceBounds {
                return TraceBounds(
                    left = min(values.topLeftX, values.bottomRightX),
                    top = min(values.topLeftY, values.bottomRightY),
                    right = max(values.topLeftX, values.bottomRightX),
                    bottom = max(values.topLeftY, values.bottomRightY)
                )
            }
        }
    }
}
