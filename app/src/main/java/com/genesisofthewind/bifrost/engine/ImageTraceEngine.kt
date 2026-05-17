package com.genesisofthewind.bifrost.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.data.CalibrationValues
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class TraceMode(val label: String) {
    FillScanline("Fill Trace / Scanline Trace"),
    Outline("Outline Trace"),
    SparseSketch("Sparse Sketch Trace")
}

data class TraceSettings(
    val mode: TraceMode,
    val threshold: Int,
    val invert: Boolean,
    val rowStep: Int,
    val minRunLength: Int,
    val maxStrokes: Int,
    val strokeDurationMs: Long,
    val delayBetweenStrokesMs: Long
)

data class TracePreset(
    val name: String,
    val description: String,
    val settings: TraceSettings?
)

object TracePresets {
    val Custom = TracePreset("Custom", "Modified settings", null)

    val FastSketch = TracePreset(
        name = "Fast Sketch",
        description = "Fast rough trace",
        settings = TraceSettings(
            mode = TraceMode.SparseSketch,
            threshold = 128,
            invert = false,
            rowStep = 6,
            minRunLength = 8,
            maxStrokes = 300,
            strokeDurationMs = 25L,
            delayBetweenStrokesMs = 20L
        )
    )

    val SimpleOutline = TracePreset(
        name = "Simple Outline",
        description = "Balanced outline",
        settings = TraceSettings(
            mode = TraceMode.Outline,
            threshold = 128,
            invert = false,
            rowStep = 3,
            minRunLength = 5,
            maxStrokes = 800,
            strokeDurationMs = 35L,
            delayBetweenStrokesMs = 30L
        )
    )

    val CleanCartoon = TracePreset(
        name = "Clean Cartoon",
        description = "Cartoon/sticker art",
        settings = TraceSettings(
            mode = TraceMode.Outline,
            threshold = 140,
            invert = false,
            rowStep = 2,
            minRunLength = 6,
            maxStrokes = 1200,
            strokeDurationMs = 35L,
            delayBetweenStrokesMs = 30L
        )
    )

    val DenseDetail = TracePreset(
        name = "Dense Detail",
        description = "More detail, slower draw",
        settings = TraceSettings(
            mode = TraceMode.Outline,
            threshold = 128,
            invert = false,
            rowStep = 1,
            minRunLength = 2,
            maxStrokes = 2000,
            strokeDurationMs = 40L,
            delayBetweenStrokesMs = 25L
        )
    )

    val All = listOf(FastSketch, SimpleOutline, CleanCartoon, DenseDetail, Custom)
}

data class ImageTraceResult(
    val processedBitmap: Bitmap,
    val strokePlan: StrokePlan,
    val warning: String?
)

class ImageTraceEngine(private val calibrationStore: CalibrationStore) {
    fun createTracePlan(source: Bitmap, settings: TraceSettings): ImageTraceResult {
        val values = calibrationStore.getValues()
        val canvas = TraceBounds.fromValues(values).inset()
        val sample = scaleForTracing(source)
        val processed = Bitmap.createBitmap(sample.width, sample.height, Bitmap.Config.ARGB_8888)
        val threshold = settings.threshold.coerceIn(0, 255)
        val rowStep = settings.rowStep.coerceIn(1, 16)
        val minRunLength = settings.minRunLength.coerceIn(1, 64)
        val maxStrokes = settings.maxStrokes.coerceIn(20, 3000)
        val strokeDurationMs = settings.strokeDurationMs.coerceIn(40L, 1200L)
        val delayBetweenStrokesMs = settings.delayBetweenStrokesMs.coerceIn(0L, 500L)

        val fit = fitInsideCanvas(sample.width, sample.height, canvas)
        val blackPixels = Array(sample.height) { BooleanArray(sample.width) }
        for (y in 0 until sample.height) {
            var x = 0
            while (x < sample.width) {
                val black = isBlack(sample.getPixel(x, y), threshold, settings.invert)
                blackPixels[y][x] = black
                processed.setPixel(x, y, if (black) Color.BLACK else Color.WHITE)
                x++
            }
        }

        val tracePixels = when (settings.mode) {
            TraceMode.FillScanline -> blackPixels
            TraceMode.Outline -> edgePixels(blackPixels, processed)
            TraceMode.SparseSketch -> edgePixels(blackPixels, processed)
        }
        val effectiveRowStep = when (settings.mode) {
            TraceMode.FillScanline -> rowStep
            TraceMode.Outline -> rowStep
            TraceMode.SparseSketch -> (rowStep * 2).coerceIn(2, 24)
        }
        val effectiveMinRun = when (settings.mode) {
            TraceMode.FillScanline -> minRunLength
            TraceMode.Outline -> minRunLength
            TraceMode.SparseSketch -> (minRunLength * 2).coerceIn(2, 96)
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
            add("processed image: ${processed.width}x${processed.height}")
            add("selector bounds: left=${canvas.left}, top=${canvas.top}, right=${canvas.right}, bottom=${canvas.bottom}")
            add("fit bounds: left=${fit.left}, top=${fit.top}, right=${fit.right}, bottom=${fit.bottom}")
            add("threshold: $threshold")
            add("invert: ${settings.invert}")
            add("row step: $effectiveRowStep")
            add("minimum run length: $effectiveMinRun")
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
        delayBetweenStrokesMs: Long
    ) {
        val drawX = fit.left + (x.toFloat() / max(1, imageWidth - 1)) * fit.width
        val startY = fit.top + (runStart.toFloat() / max(1, imageHeight - 1)) * fit.height
        val endY = fit.top + (runEnd.toFloat() / max(1, imageHeight - 1)) * fit.height
        if (abs(endY - startY) >= 1f) {
            strokes.add(
                StrokeSpec(drawX, startY, drawX, endY, strokeDurationMs, delayBetweenStrokesMs, "outline vertical col=$x", true)
            )
        }
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
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        val gray = ((red * 0.299f) + (green * 0.587f) + (blue * 0.114f)).roundToInt()
        val black = gray < threshold
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
