package com.genesisofthewind.bifrost

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.data.CalibrationValues
import com.genesisofthewind.bifrost.engine.ImageTraceEngine
import com.genesisofthewind.bifrost.engine.ShapeCommand
import com.genesisofthewind.bifrost.engine.StrokePlan
import com.genesisofthewind.bifrost.engine.TraceMode
import com.genesisofthewind.bifrost.engine.TracePreset
import com.genesisofthewind.bifrost.engine.TracePresets
import com.genesisofthewind.bifrost.engine.TraceSettings
import com.genesisofthewind.bifrost.services.CanvasSelectorOverlayService
import com.genesisofthewind.bifrost.services.DrawAccessibilityService
import com.genesisofthewind.bifrost.services.FloatingOverlayService
import com.genesisofthewind.bifrost.ui.theme.BackgroundDark
import com.genesisofthewind.bifrost.ui.theme.BifrostTheme
import com.genesisofthewind.bifrost.ui.theme.BorderDark
import com.genesisofthewind.bifrost.ui.theme.ButtonBorderDark
import com.genesisofthewind.bifrost.ui.theme.ButtonDark
import com.genesisofthewind.bifrost.ui.theme.CyanAccent
import com.genesisofthewind.bifrost.ui.theme.EmeraldAccent
import com.genesisofthewind.bifrost.ui.theme.RedAccent
import com.genesisofthewind.bifrost.ui.theme.RedMuted
import com.genesisofthewind.bifrost.ui.theme.SurfaceDark
import com.genesisofthewind.bifrost.ui.theme.TextMuted
import com.genesisofthewind.bifrost.ui.theme.TextPrimary
import com.genesisofthewind.bifrost.ui.theme.TextSecondary
import com.genesisofthewind.bifrost.ui.theme.White

class MainActivity : ComponentActivity() {
    private lateinit var calibrationStore: CalibrationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calibrationStore = CalibrationStore(this)
        refreshStatus()

        setContent {
            BifrostTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundDark) {
                    MainScreen(
                        calibrationStore = calibrationStore,
                        onOpenAccessibility = { openAccessibilitySettings() },
                        onStartOverlay = { startOverlay() },
                        onStopOverlay = { stopOverlay() },
                        onStartCanvasSelector = { startCanvasSelector() },
                        onStopCanvasSelector = { stopCanvasSelector() },
                        onRefreshStatus = { refreshStatus() },
                        onRunSafeTestGesture = { runSafeTestGesture() },
                        onRunCommand = { runGesture(it) },
                        onRunTracePlan = { runStrokePlan(it) },
                        onCancelDrawing = { cancelCurrentDrawing() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun openAccessibilitySettings() {
        BifrostDebug.record("Opening accessibility settings")
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            BifrostDebug.record("Overlay permission requested")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            BifrostDebug.record("Overlay start requested")
            BifrostDebug.setOverlayRunning(true)
            startService(Intent(this, FloatingOverlayService::class.java))
        }
    }

    private fun stopOverlay() {
        BifrostDebug.record("Overlay stop requested from app")
        BifrostDebug.setOverlayRunning(false)
        stopService(Intent(this, FloatingOverlayService::class.java))
    }

    private fun startCanvasSelector() {
        if (!Settings.canDrawOverlays(this)) {
            BifrostDebug.record("Canvas selector needs overlay permission")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            BifrostDebug.record("Canvas selector mode requested")
            startService(Intent(this, CanvasSelectorOverlayService::class.java))
        }
    }

    private fun stopCanvasSelector() {
        BifrostDebug.record("Canvas selector hide requested")
        stopService(Intent(this, CanvasSelectorOverlayService::class.java))
    }

    private fun refreshStatus() {
        BifrostDebug.refreshAccessibilityStatus(this)
        BifrostDebug.refreshOverlayPermission(this)
        BifrostDebug.refreshDisplayInfo(this)
    }

    private fun runSafeTestGesture() {
        BifrostDebug.record("Safe test gesture requested from app")
        runGesture(ShapeCommand.SafeTestGesture)
    }

    private fun runGesture(command: ShapeCommand) {
        DrawAccessibilityService.getInstance()?.executeCommand(command)
            ?: BifrostDebug.record("Gesture unavailable: accessibility service is not connected")
    }

    private fun runStrokePlan(plan: StrokePlan) {
        DrawAccessibilityService.getInstance()?.executeStrokePlan(plan)
            ?: BifrostDebug.record("Trace unavailable: accessibility service is not connected")
    }

    private fun cancelCurrentDrawing() {
        DrawAccessibilityService.getInstance()?.cancelCurrentDrawing()
            ?: BifrostDebug.record("Cancel unavailable: accessibility service is not connected")
    }
}

@Composable
fun MainScreen(
    calibrationStore: CalibrationStore,
    onOpenAccessibility: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onStartCanvasSelector: () -> Unit,
    onStopCanvasSelector: () -> Unit,
    onRefreshStatus: () -> Unit,
    onRunSafeTestGesture: () -> Unit,
    onRunCommand: (ShapeCommand) -> Unit,
    onRunTracePlan: (StrokePlan) -> Unit,
    onCancelDrawing: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Status", "Overlay", "Calibration", "Test Shapes", "Image", "Debug")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppHeader()
        CompactStatusPanel()
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = TextPrimary,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> StatusSection(onOpenAccessibility, onRefreshStatus, onRunSafeTestGesture)
            1 -> OverlaySection(onStartOverlay, onStopOverlay, onStartCanvasSelector, onStopCanvasSelector)
            2 -> CalibrationSection(calibrationStore)
            3 -> TestShapesSection(calibrationStore, onRunCommand)
            4 -> ImageImportSection(calibrationStore, onRunTracePlan, onCancelDrawing)
            5 -> DebugSection(onRefreshStatus)
        }
    }
}

@Composable
fun AppHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text("Bifrost", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Compact Thor controls", color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun CompactStatusPanel() {
    Section("Live Status") {
        StatusRow("Accessibility enabled", BifrostDebug.accessibilityEnabled.value)
        StatusRow("Accessibility runtime ready", BifrostDebug.accessibilityRuntimeReady.value)
        StatusRow("Overlay permission granted", BifrostDebug.overlayPermissionGranted.value)
        StatusRow("Overlay running", BifrostDebug.overlayRunning.value)
    }
}

@Composable
fun StatusSection(
    onOpenAccessibility: () -> Unit,
    onRefreshStatus: () -> Unit,
    onRunSafeTestGesture: () -> Unit
) {
    Section("Status") {
        FullWidthButton("Refresh Status", onRefreshStatus)
        FullWidthButton("Open Accessibility Settings", onOpenAccessibility)
        FullWidthButton("Run Safe Test Gesture", onRunSafeTestGesture)
    }
}

@Composable
fun OverlaySection(
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onStartCanvasSelector: () -> Unit,
    onStopCanvasSelector: () -> Unit
) {
    Section("Overlay") {
        FullWidthButton("Start Floating Overlay", onStartOverlay)
        FullWidthButton("Stop Floating Overlay", onStopOverlay, danger = true)
        FullWidthButton("Canvas Selector Mode", onStartCanvasSelector)
        FullWidthButton("Hide Canvas Selector", onStopCanvasSelector, danger = true)
    }
}

@Composable
fun CalibrationSection(calibrationStore: CalibrationStore) {
    val state = rememberCalibrationUiState(calibrationStore)

    Section("Manual Coordinates") {
        CoordinateField("Start X", state.startX, { state.startX = it }, { state.startX = nudgeText(state.startX, it) })
        CoordinateField("Start Y", state.startY, { state.startY = it }, { state.startY = nudgeText(state.startY, it) })
        CoordinateField("End X", state.endX, { state.endX = it }, { state.endX = nudgeText(state.endX, it) })
        CoordinateField("End Y", state.endY, { state.endY = it }, { state.endY = nudgeText(state.endY, it) })
        CoordinateField("Duration ms", state.durationMs, { state.durationMs = it }, { state.durationMs = nudgeText(state.durationMs, it * 10) })
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section("Canvas Bounds") {
        CoordinateField("Top-left X", state.topLeftX, { state.topLeftX = it }, { state.topLeftX = nudgeText(state.topLeftX, it) })
        CoordinateField("Top-left Y", state.topLeftY, { state.topLeftY = it }, { state.topLeftY = nudgeText(state.topLeftY, it) })
        CoordinateField("Bottom-right X", state.bottomRightX, { state.bottomRightX = it }, { state.bottomRightX = nudgeText(state.bottomRightX, it) })
        CoordinateField("Bottom-right Y", state.bottomRightY, { state.bottomRightY = it }, { state.bottomRightY = nudgeText(state.bottomRightY, it) })
        FullWidthButton("Save Calibration", {
            calibrationStore.saveValues(state.currentValues())
            BifrostDebug.record("Calibration saved")
        })
        FullWidthButton("Reset Calibration", {
            calibrationStore.resetValues()
            state.load(calibrationStore.getValues())
            BifrostDebug.record("Calibration reset to defaults")
        }, danger = true)
    }
}

@Composable
fun TestShapesSection(
    calibrationStore: CalibrationStore,
    onRunCommand: (ShapeCommand) -> Unit
) {
    val state = rememberCalibrationUiState(calibrationStore)

    fun saveForTest(message: String): CalibrationValues {
        val values = state.currentValues()
        calibrationStore.saveValues(values)
        BifrostDebug.record(message)
        return values
    }

    Section("Test Shapes") {
        FullWidthButton("Test Tap", onClick = {
            val values = saveForTest("Test tap requested")
            onRunCommand(ShapeCommand.Tap(values.startX, values.startY, values.durationMs))
        })
        FullWidthButton("Test Line", onClick = {
            val values = saveForTest("Test line requested")
            onRunCommand(ShapeCommand.Line(values.startX, values.startY, values.endX, values.endY, values.durationMs))
        })
        FullWidthButton("Test Diagonal", onClick = {
            saveForTest("Test diagonal requested")
            onRunCommand(ShapeCommand.DiagonalTopLeftToBottomRight)
        })
        FullWidthButton("Diagonal TopLeft to BottomRight", onClick = {
            saveForTest("Diagonal TL to BR requested")
            onRunCommand(ShapeCommand.DiagonalTopLeftToBottomRight)
        })
        FullWidthButton("Diagonal TopRight to BottomLeft", onClick = {
            saveForTest("Diagonal TR to BL requested")
            onRunCommand(ShapeCommand.DiagonalTopRightToBottomLeft)
        })
        FullWidthButton("Diagonal BottomLeft to TopRight", onClick = {
            saveForTest("Diagonal BL to TR requested")
            onRunCommand(ShapeCommand.DiagonalBottomLeftToTopRight)
        })
        FullWidthButton("Diagonal BottomRight to TopLeft", onClick = {
            saveForTest("Diagonal BR to TL requested")
            onRunCommand(ShapeCommand.DiagonalBottomRightToTopLeft)
        })
        FullWidthButton("Segmented Diagonal Test", onClick = {
            saveForTest("Segmented diagonal requested")
            onRunCommand(ShapeCommand.SegmentedDiagonal)
        })
        FullWidthButton("Segmented TL->BR Test", onClick = {
            saveForTest("Segmented TL->BR requested")
            onRunCommand(ShapeCommand.SegmentedTopLeftToBottomRight)
        })
        FullWidthButton("Segmented TR->BL Test", onClick = {
            saveForTest("Segmented TR->BL requested")
            onRunCommand(ShapeCommand.SegmentedTopRightToBottomLeft)
        })
        FullWidthButton("Test Small Square", onClick = {
            saveForTest("Test small square requested")
            onRunCommand(ShapeCommand.CalibratedSmallSquare)
        })
        FullWidthButton("Run Known-Good Square", onClick = {
            saveForTest("Known-good square requested")
            onRunCommand(ShapeCommand.CalibratedSmallSquare)
        })
        FullWidthButton("Test Square Inside Selector", onClick = {
            saveForTest("Selector square requested")
            onRunCommand(ShapeCommand.CalibratedSmallSquare)
        })
        FullWidthButton("Test X Shape", onClick = {
            saveForTest("Test X shape requested")
            onRunCommand(ShapeCommand.CalibratedXShape)
        })
        FullWidthButton("Reverse X Test", onClick = {
            saveForTest("Reverse X requested")
            onRunCommand(ShapeCommand.ReverseXShape)
        })
        FullWidthButton("Segmented X Test", onClick = {
            saveForTest("Segmented X requested")
            onRunCommand(ShapeCommand.SegmentedXShape)
        })
    }
}

@Composable
fun ImageImportSection(
    calibrationStore: CalibrationStore,
    onRunTracePlan: (StrokePlan) -> Unit,
    onCancelDrawing: () -> Unit
) {
    val context = LocalContext.current
    val traceEngine = remember { ImageTraceEngine(calibrationStore) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var traceMode by remember { mutableStateOf(TraceMode.BalancedHybrid) }
    var selectedPreset by remember { mutableStateOf(TracePresets.Balanced) }
    var threshold by remember { mutableStateOf(140f) }
    var invert by remember { mutableStateOf(false) }
    var rowStepText by remember { mutableStateOf("2") }
    var minRunLengthText by remember { mutableStateOf("3") }
    var maxStrokesText by remember { mutableStateOf("1200") }
    var strokeDurationText by remember { mutableStateOf("35") }
    var delayBetweenStrokesText by remember { mutableStateOf("30") }
    var tracePlan by remember { mutableStateOf<StrokePlan?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            BifrostDebug.record("Image import cancelled")
            return@rememberLauncherForActivityResult
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        if (bitmap == null) {
            BifrostDebug.record("Image import failed")
        } else {
            sourceBitmap = bitmap
            processedBitmap = null
            tracePlan = null
            warning = null
            BifrostDebug.record("Image imported: ${bitmap.width}x${bitmap.height}")
        }
    }

    fun currentTraceSettings(): TraceSettings {
        return TraceSettings(
            mode = traceMode,
            threshold = threshold.toInt(),
            invert = invert,
            rowStep = rowStepText.toIntOrNull()?.coerceIn(1, 16) ?: 4,
            minRunLength = minRunLengthText.toIntOrNull()?.coerceIn(1, 64) ?: 3,
            maxStrokes = maxStrokesText.toIntOrNull()?.coerceIn(20, 3000) ?: 650,
            strokeDurationMs = strokeDurationText.toLongOrNull()?.coerceIn(15L, 1200L) ?: 70L,
            delayBetweenStrokesMs = delayBetweenStrokesText.toLongOrNull()?.coerceIn(0L, 500L) ?: 45L
        )
    }

    fun markCustom() {
        selectedPreset = TracePresets.Custom
        tracePlan = null
    }

    fun generateTrace(settings: TraceSettings = currentTraceSettings()): StrokePlan? {
        val bitmap = sourceBitmap
        if (bitmap == null) {
            BifrostDebug.record("Generate trace skipped: no image selected")
            return null
        }
        val result = traceEngine.createTracePlan(bitmap, settings)
        processedBitmap = result.processedBitmap
        tracePlan = result.strokePlan
        warning = result.warning
        BifrostDebug.setStrokePreview(result.strokePlan.debugLines)
        result.strokePlan.debugLines.forEach { BifrostDebug.record(it) }
        result.warning?.let { BifrostDebug.record(it) }
        return result.strokePlan
    }

    fun applyPreset(preset: TracePreset) {
        selectedPreset = preset
        val settings = preset.settings ?: return
        traceMode = settings.mode
        threshold = settings.threshold.toFloat()
        invert = settings.invert
        rowStepText = settings.rowStep.toString()
        minRunLengthText = settings.minRunLength.toString()
        maxStrokesText = settings.maxStrokes.toString()
        strokeDurationText = settings.strokeDurationMs.toString()
        delayBetweenStrokesText = settings.delayBetweenStrokesMs.toString()
        tracePlan = null
        processedBitmap = null
        warning = null
        BifrostDebug.record("Trace preset applied: ${preset.name}")
        if (sourceBitmap != null) {
            generateTrace(settings)
        }
    }

    Section("Image Import") {
        FullWidthButton("Pick Image", onClick = { imagePicker.launch("image/*") })
        sourceBitmap?.let { bitmap ->
            Text("Selected image: ${bitmap.width} x ${bitmap.height}", color = TextSecondary, fontSize = 13.sp)
            ImagePreview("Original Preview", bitmap)
        } ?: Text("No image selected", color = TextMuted, fontSize = 13.sp)
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section("Black / White Trace") {
        Text("Preset: ${selectedPreset.name}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(selectedPreset.description, color = TextMuted, fontSize = 12.sp)
        TracePresetSelector(selectedPreset, onPresetSelected = { applyPreset(it) })
        selectedPreset.settings?.let { settings ->
            TracePresetValues(settings)
        }
        Text("Trace Mode: ${traceMode.label}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        FullWidthButton("Fill Trace / Scanline Trace", onClick = {
            traceMode = TraceMode.FillScanline
            markCustom()
            processedBitmap = null
        })
        FullWidthButton("Outline Trace", onClick = {
            traceMode = TraceMode.Outline
            markCustom()
            processedBitmap = null
        })
        FullWidthButton("Sparse Sketch Trace", onClick = {
            traceMode = TraceMode.SparseSketch
            markCustom()
            processedBitmap = null
        })
        FullWidthButton("Balanced / Hybrid", onClick = {
            traceMode = TraceMode.BalancedHybrid
            markCustom()
            processedBitmap = null
        })
        Text("Threshold: ${threshold.toInt()}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Slider(
            value = threshold,
            onValueChange = {
                threshold = it
                markCustom()
            },
            valueRange = 0f..255f
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Invert", color = TextPrimary, fontSize = 14.sp)
            Switch(
                checked = invert,
                onCheckedChange = {
                    invert = it
                    markCustom()
                }
            )
        }
        CoordinateField("Row step / detail", rowStepText, {
            rowStepText = it
            markCustom()
        }, {
            rowStepText = nudgeText(rowStepText, it).toIntOrNull()?.coerceIn(1, 16)?.toString() ?: "4"
            markCustom()
        })
        Text("Lower row step = more detail. Higher row step = fewer strokes.", color = TextMuted, fontSize = 12.sp)
        CoordinateField("Minimum run length", minRunLengthText, {
            minRunLengthText = it
            markCustom()
        }, {
            minRunLengthText = nudgeText(minRunLengthText, it).toIntOrNull()?.coerceIn(1, 64)?.toString() ?: "3"
            markCustom()
        })
        CoordinateField("Max strokes limit", maxStrokesText, {
            maxStrokesText = it
            markCustom()
        }, {
            maxStrokesText = nudgeText(maxStrokesText, it * 50).toIntOrNull()?.coerceIn(20, 3000)?.toString() ?: "650"
            markCustom()
        })
        CoordinateField("Stroke duration ms", strokeDurationText, {
            strokeDurationText = it
            markCustom()
        }, {
            strokeDurationText = nudgeText(strokeDurationText, it * 10).toIntOrNull()?.coerceIn(15, 1200)?.toString() ?: "70"
            markCustom()
        })
        CoordinateField("Delay between strokes ms", delayBetweenStrokesText, {
            delayBetweenStrokesText = it
            markCustom()
        }, {
            delayBetweenStrokesText = nudgeText(delayBetweenStrokesText, it * 10).toIntOrNull()?.coerceIn(0, 500)?.toString() ?: "45"
            markCustom()
        })
        processedBitmap?.let { bitmap ->
            ImagePreview("Processed Preview", bitmap)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section("Trace Drawing") {
        FullWidthButton("Generate Trace", onClick = { generateTrace() })
        FullWidthButton("Preview Trace Summary", onClick = { generateTrace() })
        FullWidthButton("Draw Imported Image", onClick = {
            val plan = tracePlan ?: generateTrace()
            if (plan == null) {
                BifrostDebug.record("Draw imported image skipped: no trace plan")
            } else if (plan.strokes.isEmpty()) {
                BifrostDebug.record("Draw imported image skipped: trace has no strokes")
            } else {
                BifrostDebug.record("Draw imported image requested: ${plan.strokes.size} strokes")
                onRunTracePlan(plan)
            }
        })
        FullWidthButton("Cancel Current Drawing", onCancelDrawing, danger = true)
        tracePlan?.let { plan ->
            Text("Trace Summary", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TraceSummary(plan, traceMode)
            plan.debugLines.forEach { line -> DebugLine(line) }
        }
        warning?.let { line ->
            Text(line, color = RedAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TracePresetSelector(
    selectedPreset: TracePreset,
    onPresetSelected: (TracePreset) -> Unit
) {
    val presets = TracePresets.All
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPresets.forEach { preset ->
                    CompactTraceButton(
                        text = preset.name,
                        selected = preset.name == selectedPreset.name,
                        modifier = Modifier.weight(1f),
                        onClick = { onPresetSelected(preset) }
                    )
                }
                if (rowPresets.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TracePresetValues(settings: TraceSettings) {
    DebugLine(
        "sets: ${settings.mode.label}, threshold=${settings.threshold}, rowStep=${settings.rowStep}, " +
            "minRun=${settings.minRunLength}, max=${settings.maxStrokes}, " +
            "duration=${settings.strokeDurationMs}ms, delay=${settings.delayBetweenStrokesMs}ms"
    )
}

@Composable
fun CompactTraceButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) CyanAccent else ButtonDark,
            contentColor = if (selected) BackgroundDark else TextPrimary
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) CyanAccent else ButtonBorderDark),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TraceSummary(plan: StrokePlan, traceMode: TraceMode) {
    val estimatedMs = plan.strokes.sumOf { it.durationMs + it.delayAfterMs }
    DebugLine("trace mode: ${traceMode.label}")
    DebugLine("stroke count: ${plan.strokes.size}")
    DebugLine("estimated draw time: ${estimatedMs / 1000f}s")
}

@Composable
fun ImagePreview(label: String, bitmap: Bitmap) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = label,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(BackgroundDark, RoundedCornerShape(6.dp))
                .border(1.dp, ButtonBorderDark, RoundedCornerShape(6.dp))
                .padding(6.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun DebugSection(onRefreshStatus: () -> Unit) {
    Section("Debug") {
        FullWidthButton("Refresh Debug Info", onRefreshStatus)
        Text("Debug Stroke Preview", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        BifrostDebug.strokePreview.forEach { line ->
            DebugLine(line)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Display Info", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        BifrostDebug.displayInfo.forEach { line ->
            DebugLine(line)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Recent Logs", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        BifrostDebug.messages.forEach { message ->
            DebugLine(message)
        }
    }
}

@Composable
fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun StatusRow(label: String, value: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp)
        Text(
            value.toString(),
            color = if (value) EmeraldAccent else RedAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun FullWidthButton(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) RedMuted else ButtonDark,
            contentColor = if (danger) RedAccent else TextPrimary
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (danger) RedAccent else ButtonBorderDark),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CoordinateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onNudge: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = ButtonBorderDark,
                    focusedContainerColor = BackgroundDark,
                    unfocusedContainerColor = BackgroundDark
                )
            )
            NudgeButton("-", onClick = { onNudge(-1) })
            NudgeButton("+", onClick = { onNudge(1) })
        }
    }
}

@Composable
fun NudgeButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ButtonDark, contentColor = TextPrimary),
        border = androidx.compose.foundation.BorderStroke(1.dp, ButtonBorderDark)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DebugLine(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark, RoundedCornerShape(4.dp))
            .padding(8.dp)
    )
}

@Composable
fun rememberCalibrationUiState(calibrationStore: CalibrationStore): CalibrationUiState {
    val values = remember { calibrationStore.getValues() }
    return remember { CalibrationUiState(values) }
}

class CalibrationUiState(values: CalibrationValues) {
    var startX by mutableStateOf(values.startX.toInt().toString())
    var startY by mutableStateOf(values.startY.toInt().toString())
    var endX by mutableStateOf(values.endX.toInt().toString())
    var endY by mutableStateOf(values.endY.toInt().toString())
    var durationMs by mutableStateOf(values.durationMs.toString())
    var topLeftX by mutableStateOf(values.topLeftX.toInt().toString())
    var topLeftY by mutableStateOf(values.topLeftY.toInt().toString())
    var bottomRightX by mutableStateOf(values.bottomRightX.toInt().toString())
    var bottomRightY by mutableStateOf(values.bottomRightY.toInt().toString())

    fun load(values: CalibrationValues) {
        startX = values.startX.toInt().toString()
        startY = values.startY.toInt().toString()
        endX = values.endX.toInt().toString()
        endY = values.endY.toInt().toString()
        durationMs = values.durationMs.toString()
        topLeftX = values.topLeftX.toInt().toString()
        topLeftY = values.topLeftY.toInt().toString()
        bottomRightX = values.bottomRightX.toInt().toString()
        bottomRightY = values.bottomRightY.toInt().toString()
    }

    fun currentValues(): CalibrationValues {
        return CalibrationValues(
            startX = startX.toFloatOrNull() ?: 120f,
            startY = startY.toFloatOrNull() ?: 120f,
            endX = endX.toFloatOrNull() ?: 260f,
            endY = endY.toFloatOrNull() ?: 120f,
            durationMs = durationMs.toLongOrNull()?.coerceAtLeast(50L) ?: 220L,
            topLeftX = topLeftX.toFloatOrNull() ?: 100f,
            topLeftY = topLeftY.toFloatOrNull() ?: 100f,
            bottomRightX = bottomRightX.toFloatOrNull() ?: 900f,
            bottomRightY = bottomRightY.toFloatOrNull() ?: 900f
        )
    }
}

fun nudgeText(value: String, delta: Int): String {
    return ((value.toIntOrNull() ?: 0) + delta).toString()
}
