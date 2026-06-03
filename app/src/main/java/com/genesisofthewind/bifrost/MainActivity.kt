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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.genesisofthewind.bifrost.data.PaletteEntry
import com.genesisofthewind.bifrost.data.PaletteProfile
import com.genesisofthewind.bifrost.data.PaletteProfileStore
import com.genesisofthewind.bifrost.data.PaletteTapTarget
import com.genesisofthewind.bifrost.data.TraceSettingsStore
import com.genesisofthewind.bifrost.engine.ColorFillPlanner
import com.genesisofthewind.bifrost.engine.ImageDrawMode
import com.genesisofthewind.bifrost.engine.ImageTraceEngine
import com.genesisofthewind.bifrost.engine.ShapeCommand
import com.genesisofthewind.bifrost.engine.StrokePlan
import com.genesisofthewind.bifrost.engine.StrokeSpec
import com.genesisofthewind.bifrost.engine.TraceMode
import com.genesisofthewind.bifrost.engine.TracePreset
import com.genesisofthewind.bifrost.engine.TracePresets
import com.genesisofthewind.bifrost.engine.TraceSettings
import com.genesisofthewind.bifrost.services.CanvasSelectorOverlayService
import com.genesisofthewind.bifrost.services.DrawAccessibilityService
import com.genesisofthewind.bifrost.services.PaletteCalibrationOverlayService
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
    onStartCanvasSelector: () -> Unit,
    onStopCanvasSelector: () -> Unit,
    onRefreshStatus: () -> Unit,
    onRunSafeTestGesture: () -> Unit,
    onRunCommand: (ShapeCommand) -> Unit,
    onRunTracePlan: (StrokePlan) -> Unit,
    onCancelDrawing: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var savedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val traceSettingsStore = remember { TraceSettingsStore(context) }
    val imageTraceState = remember {
        ImageTraceUiState(
            initialSettings = traceSettingsStore.loadSettings(),
            initialPreset = TracePresets.findByName(traceSettingsStore.loadPresetName())
        )
    }
    val tabs = listOf("Status", "Selector", "Calibration", "Test Shapes", "Image", "Debug")

    LaunchedEffect(savedImageUri) {
        val uriText = savedImageUri
        if (uriText != null && imageTraceState.sourceBitmap == null) {
            decodeBitmapFromUri(context, uriText)?.let { bitmap ->
                imageTraceState.sourceBitmap = bitmap
                imageTraceState.sourceUriText = uriText
                BifrostDebug.record("Image restored: ${bitmap.width}x${bitmap.height}")
            } ?: BifrostDebug.record("Image restore failed")
        }
    }

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
            1 -> SelectorSection(onStartCanvasSelector, onStopCanvasSelector)
            2 -> CalibrationSection(calibrationStore)
            3 -> TestShapesSection(calibrationStore, onRunCommand)
            4 -> ImageImportSection(
                calibrationStore = calibrationStore,
                imageState = imageTraceState,
                traceSettingsStore = traceSettingsStore,
                onImageUriChanged = { savedImageUri = it },
                onRunTracePlan = onRunTracePlan,
                onCancelDrawing = onCancelDrawing
            )
            5 -> DebugSection(onRefreshStatus)
        }
    }
}

fun decodeBitmapFromUri(context: android.content.Context, uriText: String): Bitmap? {
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriText))?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
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
        StatusRow("Selector overlay permission", BifrostDebug.overlayPermissionGranted.value)
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
fun SelectorSection(
    onStartCanvasSelector: () -> Unit,
    onStopCanvasSelector: () -> Unit
) {
    Section("Selector Overlay") {
        Text("Use this to position the drawing area on the top screen. Drawing controls stay on the bottom screen.", color = TextMuted, fontSize = 13.sp)
        FullWidthButton("Show Selector Overlay", onStartCanvasSelector)
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

class ImageTraceUiState(
    initialSettings: TraceSettings = TracePresets.TomodachiCartoon.settings!!,
    initialPreset: TracePreset = TracePresets.TomodachiCartoon
) {
    var sourceBitmap by mutableStateOf<Bitmap?>(null)
    var sourceUriText by mutableStateOf<String?>(null)
    var processedBitmap by mutableStateOf<Bitmap?>(null)
    var traceMode by mutableStateOf(initialSettings.mode)
    var selectedPreset by mutableStateOf(initialPreset)
    var threshold by mutableStateOf(initialSettings.threshold.toFloat())
    var invert by mutableStateOf(initialSettings.invert)
    var rowStepText by mutableStateOf(initialSettings.rowStep.toString())
    var minRunLengthText by mutableStateOf(initialSettings.minRunLength.toString())
    var maxStrokesText by mutableStateOf(initialSettings.maxStrokes.toString())
    var strokeDurationText by mutableStateOf(initialSettings.strokeDurationMs.toString())
    var delayBetweenStrokesText by mutableStateOf(initialSettings.delayBetweenStrokesMs.toString())
    var edgeSensitivityText by mutableStateOf(initialSettings.edgeSensitivity.toString())
    var minComponentSizeText by mutableStateOf(initialSettings.minComponentSize.toString())
    var gapClosePixelsText by mutableStateOf(initialSettings.gapClosePixels.toString())
    var imageDrawMode by mutableStateOf(ImageDrawMode.OutlineOnly)
    var tracePlan by mutableStateOf<StrokePlan?>(null)
    var warning by mutableStateOf<String?>(null)

    fun clearImage() {
        sourceBitmap = null
        sourceUriText = null
        processedBitmap = null
        tracePlan = null
        warning = null
    }
}

class PaletteProfileUiState(profile: PaletteProfile) {
    var name by mutableStateOf(profile.name)
    var penToolX by mutableStateOf(profile.penToolX.toInt().toString())
    var penToolY by mutableStateOf(profile.penToolY.toInt().toString())
    var fillToolX by mutableStateOf(profile.fillToolX.toInt().toString())
    var fillToolY by mutableStateOf(profile.fillToolY.toInt().toString())
    var entries by mutableStateOf(profile.entries.map { PaletteEntryUiState(it) })

    fun load(profile: PaletteProfile) {
        name = profile.name
        penToolX = profile.penToolX.toInt().toString()
        penToolY = profile.penToolY.toInt().toString()
        fillToolX = profile.fillToolX.toInt().toString()
        fillToolY = profile.fillToolY.toInt().toString()
        entries = profile.entries.map { PaletteEntryUiState(it) }
    }

    fun currentProfile(): PaletteProfile {
        return PaletteProfile(
            name = name.ifBlank { "Tomodachi Life Easy Mode" },
            penToolX = penToolX.toFloatOrNull() ?: 0f,
            penToolY = penToolY.toFloatOrNull() ?: 0f,
            fillToolX = fillToolX.toFloatOrNull() ?: 0f,
            fillToolY = fillToolY.toFloatOrNull() ?: 0f,
            entries = entries.map { it.currentEntry() }
        )
    }
}

class PaletteEntryUiState(entry: PaletteEntry) {
    val colorName = entry.colorName
    val red = entry.red
    val green = entry.green
    val blue = entry.blue
    var tapX by mutableStateOf(entry.tapX.toInt().toString())
    var tapY by mutableStateOf(entry.tapY.toInt().toString())

    fun currentEntry(): PaletteEntry {
        return PaletteEntry(
            colorName = colorName,
            tapX = tapX.toFloatOrNull() ?: 0f,
            tapY = tapY.toFloatOrNull() ?: 0f,
            red = red,
            green = green,
            blue = blue
        )
    }
}

@Composable
fun ImageImportSection(
    calibrationStore: CalibrationStore,
    imageState: ImageTraceUiState,
    traceSettingsStore: TraceSettingsStore,
    onImageUriChanged: (String?) -> Unit,
    onRunTracePlan: (StrokePlan) -> Unit,
    onCancelDrawing: () -> Unit
) {
    val context = LocalContext.current
    val traceEngine = remember { ImageTraceEngine(calibrationStore) }
    val colorFillPlanner = remember { ColorFillPlanner(calibrationStore) }
    val paletteProfileStore = remember { PaletteProfileStore(context) }
    val paletteUiState = remember { PaletteProfileUiState(paletteProfileStore.loadEasyModeProfile()) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            BifrostDebug.record("Image import cancelled")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val bitmap = decodeBitmapFromUri(context, uri.toString())
        if (bitmap == null) {
            BifrostDebug.record("Image import failed")
        } else {
            imageState.sourceBitmap = bitmap
            imageState.sourceUriText = uri.toString()
            imageState.processedBitmap = null
            imageState.tracePlan = null
            imageState.warning = null
            onImageUriChanged(uri.toString())
            BifrostDebug.record("Image imported: ${bitmap.width}x${bitmap.height}")
        }
    }

    fun currentTraceSettings(): TraceSettings {
        return TraceSettings(
            mode = imageState.traceMode,
            threshold = imageState.threshold.toInt(),
            invert = imageState.invert,
            rowStep = imageState.rowStepText.toIntOrNull()?.coerceIn(1, 16) ?: 4,
            minRunLength = imageState.minRunLengthText.toIntOrNull()?.coerceIn(1, 64) ?: 3,
            maxStrokes = imageState.maxStrokesText.toIntOrNull()?.coerceIn(20, 3000) ?: 650,
            strokeDurationMs = imageState.strokeDurationText.toLongOrNull()?.coerceIn(15L, 1200L) ?: 70L,
            delayBetweenStrokesMs = imageState.delayBetweenStrokesText.toLongOrNull()?.coerceIn(0L, 500L) ?: 45L,
            edgeSensitivity = imageState.edgeSensitivityText.toIntOrNull()?.coerceIn(1, 100) ?: 55,
            minComponentSize = imageState.minComponentSizeText.toIntOrNull()?.coerceIn(1, 200) ?: 4,
            gapClosePixels = imageState.gapClosePixelsText.toIntOrNull()?.coerceIn(0, 6) ?: 2
        )
    }

    fun markCustom() {
        imageState.selectedPreset = TracePresets.Custom
        imageState.tracePlan = null
        traceSettingsStore.savePresetName(TracePresets.Custom.name)
        traceSettingsStore.saveSettings(currentTraceSettings())
    }

    fun generateTrace(settings: TraceSettings = currentTraceSettings()): StrokePlan? {
        val bitmap = imageState.sourceBitmap
        if (bitmap == null) {
            BifrostDebug.record("Generate trace skipped: no image selected")
            return null
        }
        val result = traceEngine.createTracePlan(bitmap, settings)
        val profile = paletteUiState.currentProfile()
        var colorWarning: String? = null
        val finalPlan = if (imageState.imageDrawMode == ImageDrawMode.OutlineAutoColor) {
            val missingTargets = paletteProfileStore.missingRequiredTargets(profile)
            if (missingTargets.isNotEmpty()) {
                colorWarning = "Easy Mode color warning: missing required targets ${missingTargets.joinToString()}"
                BifrostDebug.record(colorWarning!!)
            }
            val colorResult = colorFillPlanner.createOutlineAndFillPlan(
                source = bitmap,
                outlinePlan = result.strokePlan,
                profile = profile
            )
            colorResult.warning?.let { BifrostDebug.record(it) }
            colorResult.strokePlan
        } else {
            result.strokePlan
        }
        imageState.processedBitmap = result.processedBitmap
        imageState.tracePlan = finalPlan
        imageState.warning = colorWarning ?: result.warning
        BifrostDebug.setStrokePreview(finalPlan.debugLines)
        finalPlan.debugLines.forEach { BifrostDebug.record(it) }
        result.warning?.let { BifrostDebug.record(it) }
        return finalPlan
    }

    fun applyPreset(preset: TracePreset) {
        imageState.selectedPreset = preset
        val settings = preset.settings ?: return
        imageState.traceMode = settings.mode
        imageState.threshold = settings.threshold.toFloat()
        imageState.invert = settings.invert
        imageState.rowStepText = settings.rowStep.toString()
        imageState.minRunLengthText = settings.minRunLength.toString()
        imageState.maxStrokesText = settings.maxStrokes.toString()
        imageState.strokeDurationText = settings.strokeDurationMs.toString()
        imageState.delayBetweenStrokesText = settings.delayBetweenStrokesMs.toString()
        imageState.edgeSensitivityText = settings.edgeSensitivity.toString()
        imageState.minComponentSizeText = settings.minComponentSize.toString()
        imageState.gapClosePixelsText = settings.gapClosePixels.toString()
        imageState.tracePlan = null
        imageState.processedBitmap = null
        imageState.warning = null
        BifrostDebug.record("Trace preset applied: ${preset.name}")
        traceSettingsStore.savePresetName(preset.name)
        traceSettingsStore.saveSettings(settings)
        if (imageState.sourceBitmap != null) {
            generateTrace(settings)
        }
    }

    fun startPaletteCalibration(target: PaletteTapTarget) {
        paletteProfileStore.saveEasyModeProfile(paletteUiState.currentProfile())
        val intent = Intent(context, PaletteCalibrationOverlayService::class.java)
            .putExtra(PaletteCalibrationOverlayService.EXTRA_TARGET_KEY, target.key)
            .putExtra(PaletteCalibrationOverlayService.EXTRA_TARGET_LABEL, target.label)
        context.startService(intent)
        BifrostDebug.record("Palette setup requested: ${target.label}")
    }

    fun runTargetTap(target: PaletteTapTarget) {
        if (target.x <= 0f || target.y <= 0f) {
            BifrostDebug.record("Target test skipped: ${target.label} is not calibrated")
            return
        }
        val stroke = StrokeSpec(target.x, target.y, target.x + 1f, target.y + 1f, 60L, 120L, "test ${target.label}")
        val plan = StrokePlan(
            commandName = "TestPaletteTarget",
            debugLines = listOf("test target: ${target.label}", "tap: ${target.x},${target.y}"),
            strokes = listOf(stroke)
        )
        onRunTracePlan(plan)
    }

    fun runTargets(targets: List<PaletteTapTarget>, commandName: String) {
        val strokes = targets
            .filter { it.x > 0f && it.y > 0f }
            .map { target -> StrokeSpec(target.x, target.y, target.x + 1f, target.y + 1f, 60L, 180L, "test ${target.label}") }
        if (strokes.isEmpty()) {
            BifrostDebug.record("$commandName skipped: no calibrated targets")
            return
        }
        onRunTracePlan(
            StrokePlan(
                commandName = commandName,
                debugLines = targets.map { "target ${it.label}: ${it.x},${it.y}" },
                strokes = strokes
            )
        )
    }

    Section("1. Load Image") {
        FullWidthButton("Load Image", onClick = { imagePicker.launch(arrayOf("image/*")) })
        imageState.sourceBitmap?.let { bitmap ->
            Text("Selected image: ${bitmap.width} x ${bitmap.height}", color = TextSecondary, fontSize = 13.sp)
            ImagePreview("Original Preview", bitmap)
            FullWidthButton("Clear Image", onClick = {
                imageState.clearImage()
                onImageUriChanged(null)
                BifrostDebug.record("Imported image cleared")
            }, danger = true)
        } ?: Text("No image loaded. Pick an image to generate a trace.", color = TextMuted, fontSize = 13.sp)
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section("2. Pick Preset") {
        Text("Recommended Use", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        DebugLine("Tomodachi Simple Cartoon: Best for Kirby, simple cartoons, icons")
        DebugLine("Tomodachi Detailed Character: Best for Pokemon, anime/game characters, more interior detail")
        DebugLine("Soft / Light Character: Best for pale characters, soft anime/game art, Gardevoir-like images")
        DebugLine("Dense Detail: Best for sketchy or artistic output")
        Text("Preset: ${imageState.selectedPreset.name}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(imageState.selectedPreset.description, color = TextMuted, fontSize = 12.sp)
        TracePresetSelector(imageState.selectedPreset, onPresetSelected = { applyPreset(it) })
        imageState.selectedPreset.settings?.let { settings ->
            TracePresetValues(settings)
        }
        Text("Trace Mode: ${imageState.traceMode.label}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        FullWidthButton("Fill Trace / Scanline Trace", onClick = {
            imageState.traceMode = TraceMode.FillScanline
            markCustom()
            imageState.processedBitmap = null
        })
        FullWidthButton("Outline Trace", onClick = {
            imageState.traceMode = TraceMode.Outline
            markCustom()
            imageState.processedBitmap = null
        })
        FullWidthButton("Sparse Sketch Trace", onClick = {
            imageState.traceMode = TraceMode.SparseSketch
            markCustom()
            imageState.processedBitmap = null
        })
        FullWidthButton("Balanced / Hybrid", onClick = {
            imageState.traceMode = TraceMode.BalancedHybrid
            markCustom()
            imageState.processedBitmap = null
        })
        FullWidthButton("Cartoon Fill Ready", onClick = {
            imageState.traceMode = TraceMode.CartoonFillReady
            markCustom()
            imageState.processedBitmap = null
        })
        FullWidthButton("Character Detail", onClick = {
            imageState.traceMode = TraceMode.CharacterDetail
            markCustom()
            imageState.processedBitmap = null
        })
        Text("Threshold: ${imageState.threshold.toInt()}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Slider(
            value = imageState.threshold,
            onValueChange = {
                imageState.threshold = it
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
                checked = imageState.invert,
                onCheckedChange = {
                    imageState.invert = it
                    markCustom()
                }
            )
        }
        CoordinateField("Row step / detail", imageState.rowStepText, {
            imageState.rowStepText = it
            markCustom()
        }, {
            imageState.rowStepText = nudgeText(imageState.rowStepText, it).toIntOrNull()?.coerceIn(1, 16)?.toString() ?: "4"
            markCustom()
        })
        Text("Lower row step = more detail. Higher row step = fewer strokes.", color = TextMuted, fontSize = 12.sp)
        CoordinateField("Minimum run length", imageState.minRunLengthText, {
            imageState.minRunLengthText = it
            markCustom()
        }, {
            imageState.minRunLengthText = nudgeText(imageState.minRunLengthText, it).toIntOrNull()?.coerceIn(1, 64)?.toString() ?: "3"
            markCustom()
        })
        CoordinateField("Edge sensitivity", imageState.edgeSensitivityText, {
            imageState.edgeSensitivityText = it
            markCustom()
        }, {
            imageState.edgeSensitivityText = nudgeText(imageState.edgeSensitivityText, it * 5).toIntOrNull()?.coerceIn(1, 100)?.toString() ?: "55"
            markCustom()
        })
        Text("Higher edge sensitivity preserves more interior color/brightness boundaries.", color = TextMuted, fontSize = 12.sp)
        CoordinateField("Minimum component size", imageState.minComponentSizeText, {
            imageState.minComponentSizeText = it
            markCustom()
        }, {
            imageState.minComponentSizeText = nudgeText(imageState.minComponentSizeText, it).toIntOrNull()?.coerceIn(1, 200)?.toString() ?: "4"
            markCustom()
        })
        Text("Removes tiny disconnected specks. Lower it for detailed character markings.", color = TextMuted, fontSize = 12.sp)
        CoordinateField("Gap close pixels", imageState.gapClosePixelsText, {
            imageState.gapClosePixelsText = it
            markCustom()
        }, {
            imageState.gapClosePixelsText = nudgeText(imageState.gapClosePixelsText, it).toIntOrNull()?.coerceIn(0, 6)?.toString() ?: "2"
            markCustom()
        })
        Text("Connects small same-row/column breaks without broad blob filling.", color = TextMuted, fontSize = 12.sp)
        CoordinateField("Max strokes limit", imageState.maxStrokesText, {
            imageState.maxStrokesText = it
            markCustom()
        }, {
            imageState.maxStrokesText = nudgeText(imageState.maxStrokesText, it * 50).toIntOrNull()?.coerceIn(20, 3000)?.toString() ?: "650"
            markCustom()
        })
        CoordinateField("Stroke duration ms", imageState.strokeDurationText, {
            imageState.strokeDurationText = it
            markCustom()
        }, {
            imageState.strokeDurationText = nudgeText(imageState.strokeDurationText, it * 10).toIntOrNull()?.coerceIn(15, 1200)?.toString() ?: "70"
            markCustom()
        })
        CoordinateField("Delay between strokes ms", imageState.delayBetweenStrokesText, {
            imageState.delayBetweenStrokesText = it
            markCustom()
        }, {
            imageState.delayBetweenStrokesText = nudgeText(imageState.delayBetweenStrokesText, it * 10).toIntOrNull()?.coerceIn(0, 500)?.toString() ?: "45"
            markCustom()
        })
        imageState.processedBitmap?.let { bitmap ->
            ImagePreview("Processed Preview", bitmap)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section("3. Color Fill Easy Mode") {
        Text("Draw Mode", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        CompactTraceButton(
            text = ImageDrawMode.OutlineOnly.label,
            selected = imageState.imageDrawMode == ImageDrawMode.OutlineOnly,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                imageState.imageDrawMode = ImageDrawMode.OutlineOnly
                imageState.tracePlan = null
                BifrostDebug.record("Image draw mode: ${ImageDrawMode.OutlineOnly.label}")
            }
        )
        CompactTraceButton(
            text = ImageDrawMode.OutlineAutoColor.label,
            selected = imageState.imageDrawMode == ImageDrawMode.OutlineAutoColor,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                imageState.imageDrawMode = ImageDrawMode.OutlineAutoColor
                imageState.tracePlan = null
                BifrostDebug.record("Image draw mode: ${ImageDrawMode.OutlineAutoColor.label}")
            }
        )
        Text(
            "Easy Mode draws the black outline first, then taps the bucket tool, palette colors, and detected flat-color regions.",
            color = TextMuted,
            fontSize = 12.sp
        )
        PaletteProfileSection(
            paletteUiState = paletteUiState,
            targets = paletteProfileStore.tapTargets(paletteUiState.currentProfile()),
            onSetTarget = { startPaletteCalibration(it) },
            onTestTarget = { runTargetTap(it) },
            onTestRequiredTargets = {
                runTargets(
                    paletteProfileStore.tapTargets(paletteUiState.currentProfile()).filter { it.required },
                    "TestRequiredPaletteTargets"
                )
            },
            onSave = {
                paletteProfileStore.saveEasyModeProfile(paletteUiState.currentProfile())
                imageState.tracePlan = null
                BifrostDebug.record("Easy Mode palette profile saved")
            },
            onLoad = {
                paletteUiState.load(paletteProfileStore.loadEasyModeProfile())
                imageState.tracePlan = null
                BifrostDebug.record("Easy Mode palette profile loaded")
            },
            onReset = {
                val profile = paletteProfileStore.resetEasyModeProfile()
                paletteUiState.load(profile)
                imageState.tracePlan = null
                BifrostDebug.record("Easy Mode palette profile reset")
            }
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Section("4. Generate And Draw") {
        FullWidthButton("Generate Trace", onClick = { generateTrace() })
        FullWidthButton("Preview Trace Summary", onClick = { generateTrace() })
        FullWidthButton("Draw Imported Image", onClick = {
            val plan = imageState.tracePlan ?: generateTrace()
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
        if (imageState.sourceBitmap == null) {
            Text("No image loaded yet. Pick an image before generating or drawing a trace.", color = TextMuted, fontSize = 13.sp)
        }
        imageState.tracePlan?.let { plan ->
            Text("Trace Summary", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TraceSummary(plan, imageState.traceMode)
            plan.debugLines.forEach { line -> DebugLine(line) }
        }
        imageState.warning?.let { line ->
            Text(line, color = RedAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PaletteProfileSection(
    paletteUiState: PaletteProfileUiState,
    targets: List<PaletteTapTarget>,
    onSetTarget: (PaletteTapTarget) -> Unit,
    onTestTarget: (PaletteTapTarget) -> Unit,
    onTestRequiredTargets: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onReset: () -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    Text("Palette Profile: ${paletteUiState.name}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    DebugLine("Tap Set, move the top-screen crosshair onto the tool/color, then save it from the overlay.")
    targets.forEach { target ->
        PaletteTargetRow(
            target = target,
            onSetTarget = { onSetTarget(target) },
            onTestTarget = { onTestTarget(target) }
        )
    }
    Text("Quick tests", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Black", "Pink", "Fill / Bucket Tool").forEach { label ->
            targets.firstOrNull { it.label == label }?.let { target ->
                CompactTraceButton(
                    text = "Tap $label",
                    selected = false,
                    modifier = Modifier.weight(1f),
                    onClick = { onTestTarget(target) }
                )
            }
        }
    }
    FullWidthButton("Test All Required Targets", onTestRequiredTargets)
    FullWidthButton("Save Profile", onSave)
    FullWidthButton("Load Profile", onLoad)
    FullWidthButton("Reset Profile", onReset, danger = true)
    CompactTraceButton(
        text = if (showAdvanced) "Hide Advanced X/Y Fields" else "Show Advanced X/Y Fields",
        selected = showAdvanced,
        modifier = Modifier.fillMaxWidth(),
        onClick = { showAdvanced = !showAdvanced }
    )
    if (showAdvanced) {
        DebugLine("Advanced fallback: direct coordinate edits are still available, but crosshair setup is easier.")
        CoordinatePairField(
            label = "Brush Tool",
            xValue = paletteUiState.penToolX,
            yValue = paletteUiState.penToolY,
            onXChange = { paletteUiState.penToolX = it },
            onYChange = { paletteUiState.penToolY = it },
            onXNudge = { paletteUiState.penToolX = nudgeText(paletteUiState.penToolX, it * 5) },
            onYNudge = { paletteUiState.penToolY = nudgeText(paletteUiState.penToolY, it * 5) }
        )
        CoordinatePairField(
            label = "Fill / Bucket Tool",
            xValue = paletteUiState.fillToolX,
            yValue = paletteUiState.fillToolY,
            onXChange = { paletteUiState.fillToolX = it },
            onYChange = { paletteUiState.fillToolY = it },
            onXNudge = { paletteUiState.fillToolX = nudgeText(paletteUiState.fillToolX, it * 5) },
            onYNudge = { paletteUiState.fillToolY = nudgeText(paletteUiState.fillToolY, it * 5) }
        )
        paletteUiState.entries.forEach { entry ->
            CoordinatePairField(
                label = entry.colorName,
                xValue = entry.tapX,
                yValue = entry.tapY,
                onXChange = { entry.tapX = it },
                onYChange = { entry.tapY = it },
                onXNudge = { entry.tapX = nudgeText(entry.tapX, it * 5) },
                onYNudge = { entry.tapY = nudgeText(entry.tapY, it * 5) }
            )
        }
    }
}

@Composable
fun PaletteTargetRow(
    target: PaletteTapTarget,
    onSetTarget: () -> Unit,
    onTestTarget: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundDark, RoundedCornerShape(6.dp))
            .border(1.dp, ButtonBorderDark, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (target.required) "${target.label} *" else target.label,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (target.x > 0f && target.y > 0f) "${target.x.toInt()}, ${target.y.toInt()}" else "not set",
                color = if (target.x > 0f && target.y > 0f) TextSecondary else RedAccent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactTraceButton("Set", selected = false, modifier = Modifier.weight(1f), onClick = onSetTarget)
            CompactTraceButton("Test Tap", selected = false, modifier = Modifier.weight(1f), onClick = onTestTarget)
        }
    }
}

@Composable
fun CoordinatePairField(
    label: String,
    xValue: String,
    yValue: String,
    onXChange: (String) -> Unit,
    onYChange: (String) -> Unit,
    onXNudge: (Int) -> Unit,
    onYNudge: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoordinateField("X", xValue, onXChange, onXNudge, modifier = Modifier.weight(1f))
            CoordinateField("Y", yValue, onYChange, onYNudge, modifier = Modifier.weight(1f))
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
            "minRun=${settings.minRunLength}, edge=${settings.edgeSensitivity}, max=${settings.maxStrokes}, " +
            "minComp=${settings.minComponentSize}, gap=${settings.gapClosePixels}, " +
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
    onNudge: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
