package com.genesisofthewind.bifrost.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.data.CalibrationValues
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class TraceSettings(
    val threshold: Int,
    val invert: Boolean,
    val rowStep: Int,
    val maxStrokes: Int = 650
)

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
        val strokes = mutableListOf<StrokeSpec>()
        var capped = false

        val fit = fitInsideCanvas(sample.width, sample.height, canvas)
        for (y in 0 until sample.height) {
            var x = 0
            while (x < sample.width) {
                val black = isBlack(sample.getPixel(x, y), threshold, settings.invert)
                processed.setPixel(x, y, if (black) Color.BLACK else Color.WHITE)
                x++
            }
        }

        for (y in 0 until sample.height step rowStep) {
            var x = 0
            while (x < sample.width) {
                while (x < sample.width && processed.getPixel(x, y) != Color.BLACK) {
                    x++
                }
                val runStart = x
                while (x < sample.width && processed.getPixel(x, y) == Color.BLACK) {
                    x++
                }
                val runEnd = x - 1
                if (runStart <= runEnd) {
                    if (strokes.size >= settings.maxStrokes) {
                        capped = true
                        break
                    }
                    val startX = fit.left + (runStart.toFloat() / max(1, sample.width - 1)) * fit.width
                    val endX = fit.left + (runEnd.toFloat() / max(1, sample.width - 1)) * fit.width
                    val drawY = fit.top + (y.toFloat() / max(1, sample.height - 1)) * fit.height
                    if (abs(endX - startX) >= 1f) {
                        strokes.add(
                            StrokeSpec(
                                startX = startX,
                                startY = drawY,
                                endX = endX,
                                endY = drawY,
                                durationMs = 70L,
                                delayAfterMs = 45L,
                                directionLabel = "image scanline row=$y",
                                segmented = true,
                                segmentIndex = strokes.size + 1,
                                segmentCount = 1
                            )
                        )
                    }
                }
            }
            if (capped) break
        }

        val warning = if (capped) {
            "Trace capped at ${settings.maxStrokes} strokes. Increase row step or use a simpler image."
        } else {
            null
        }
        val finalStrokes = strokes.mapIndexed { index, stroke ->
            stroke.copy(segmentIndex = index + 1, segmentCount = strokes.size)
        }
        val debugLines = buildList {
            add("command: Imported Image Trace")
            add("source image: ${source.width}x${source.height}")
            add("processed image: ${processed.width}x${processed.height}")
            add("selector bounds: left=${canvas.left}, top=${canvas.top}, right=${canvas.right}, bottom=${canvas.bottom}")
            add("fit bounds: left=${fit.left}, top=${fit.top}, right=${fit.right}, bottom=${fit.bottom}")
            add("threshold: $threshold")
            add("invert: ${settings.invert}")
            add("row step: $rowStep")
            add("generated strokes: ${finalStrokes.size}")
            add("estimated draw count: ${finalStrokes.size}")
            warning?.let { add("warning: $it") }
        }
        return ImageTraceResult(processed, StrokePlan("ImportedImageTrace", debugLines, finalStrokes), warning)
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
