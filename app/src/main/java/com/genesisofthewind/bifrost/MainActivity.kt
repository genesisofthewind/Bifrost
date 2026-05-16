package com.genesisofthewind.bifrost

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genesisofthewind.bifrost.data.CalibrationStore
import com.genesisofthewind.bifrost.engine.ShapeCommand
import com.genesisofthewind.bifrost.services.DrawAccessibilityService
import com.genesisofthewind.bifrost.services.FloatingOverlayService
import com.genesisofthewind.bifrost.ui.theme.*

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
                        onOpenAccessibility = { openAccessibilitySettings() },
                        onStartOverlay = { startOverlay() },
                        onStopOverlay = { stopOverlay() },
                        onRefreshStatus = { refreshStatus() },
                        onCalibrate = { calibrate() },
                        onRunSafeTestGesture = { runSafeTestGesture() },
                        onDrawLine = { runGesture(ShapeCommand.TestLine) },
                        onDrawSquare = { runGesture(ShapeCommand.TestSquare) },
                        onStop = { runGesture(ShapeCommand.Stop) }
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
            startService(Intent(this, FloatingOverlayService::class.java))
        }
    }

    private fun stopOverlay() {
        BifrostDebug.record("Overlay stop requested from app")
        stopService(Intent(this, FloatingOverlayService::class.java))
    }

    private fun calibrate() {
        calibrationStore.saveTopLeft(100f, 100f)
        calibrationStore.saveBottomRight(900f, 900f)
        BifrostDebug.record("Calibration defaults saved")
    }

    private fun refreshStatus() {
        BifrostDebug.refreshAccessibilityStatus(this)
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
}

@Composable
fun MainScreen(
    onOpenAccessibility: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onRefreshStatus: () -> Unit,
    onCalibrate: () -> Unit,
    onRunSafeTestGesture: () -> Unit,
    onDrawLine: () -> Unit,
    onDrawSquare: () -> Unit,
    onStop: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Header()

        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar
            Sidebar()

            // Main Content Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Control Card
                    ControlCard(
                        modifier = Modifier.weight(1f),
                        onOpenAccessibility = onOpenAccessibility,
                        onStartOverlay = onStartOverlay,
                        onStopOverlay = onStopOverlay,
                        onRefreshStatus = onRefreshStatus,
                        onCalibrate = onCalibrate,
                        onRunSafeTestGesture = onRunSafeTestGesture,
                        onDrawLine = onDrawLine,
                        onDrawSquare = onDrawSquare,
                        onStop = onStop
                    )

                    // Info Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        AccessibilityStatusCard()
                        DisplayInfoCard()
                        CalibrationCard()
                        DebugStatusCard()
                        FloatingBubbleMock()
                    }
                }
            }
        }
    }
}

@Composable
fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(HeaderDark)
            .border(1.dp, BorderDark)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(CyanAccent, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bifrost", style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.Bold)
                    Text("V0.1.0-MVP", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp), fontFamily = FontFamily.Monospace)
                }
                Text("AYN THOR DUAL-SCREEN HANDHELD UTILITY", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val accessibilityReady = BifrostDebug.accessibilityRuntimeReady.value
            StatusChip(
                "ACCESSIBILITY: ${if (accessibilityReady) "READY" else "DISCONNECTED"}",
                if (accessibilityReady) EmeraldAccent else RedAccent
            )
            StatusChip("OVERLAY: ACTIVE", EmeraldAccent)
        }
    }
}

@Composable
fun StatusChip(label: String, color: Color) {
    Row(
        modifier = Modifier
            .background(ButtonDark, RoundedCornerShape(50))
            .border(1.dp, ButtonBorderDark, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Text(label, color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun Sidebar() {
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(HeaderDark)
            .border(1.dp, BorderDark)
            .padding(16.dp)
    ) {
        Text("PROJECT STRUCTURE", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(16.dp))
        val files = listOf(
            "📂 app/src/main",
            "  📂 java/com/genesisofthewind/bifrost",
            "    📄 MainActivity.kt",
            "    📄 DrawEngine.kt",
            "    📄 ShapeCommands.kt",
            "  📂 services",
            "    📄 DrawAccessibilityService.kt"
        )
        files.forEach { file ->
            Text(file, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ButtonDark, RoundedCornerShape(4.dp))
                .border(1.dp, ButtonBorderDark, RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Text("CURRENT TARGET", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Text("Eden Nightly (Top Screen)", fontSize = 12.sp, color = White)
            Text("DisplayID: 001-THOR", fontSize = 10.sp, color = CyanAccent, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ControlCard(
    modifier: Modifier = Modifier,
    onOpenAccessibility: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onRefreshStatus: () -> Unit,
    onCalibrate: () -> Unit,
    onRunSafeTestGesture: () -> Unit,
    onDrawLine: () -> Unit,
    onDrawSquare: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = modifier
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("APP CONTROLLER", style = MaterialTheme.typography.labelMedium, color = White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("JETPACK COMPOSE", fontSize = 10.sp, color = CyanAccent, fontFamily = FontFamily.Monospace)
        }
        
        SophisticatedButton(onClick = onOpenAccessibility, text = "Open Accessibility Settings", icon = "⚙️")
        SophisticatedButton(onClick = onRefreshStatus, text = "Refresh Status", icon = "↻")
        SophisticatedButton(onClick = onStartOverlay, text = "Start Floating Overlay", icon = "☁️")
        SophisticatedButton(onClick = onStopOverlay, text = "Stop Floating Overlay", icon = "✕")
        SophisticatedButton(onClick = onRunSafeTestGesture, text = "Run Safe Test Gesture", icon = "→")
        
        Button(
            onClick = onCalibrate,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent.copy(alpha = 0.2f), contentColor = EmeraldAccent),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.4f))
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📍", fontSize = 14.sp)
                Text("Calibrate Canvas", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SophisticatedButton(onClick = onDrawLine, text = "Test Line", modifier = Modifier.weight(1f))
            SophisticatedButton(onClick = onDrawSquare, text = "Test Square", modifier = Modifier.weight(1f))
        }

        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedMuted.copy(alpha = 0.4f), contentColor = RedAccent),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, RedAccent.copy(alpha = 0.4f))
        ) {
            Text("STOP ALL DRAWING", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
fun SophisticatedButton(onClick: () -> Unit, text: String, icon: String? = null, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ButtonDark, contentColor = TextPrimary),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ButtonBorderDark),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (icon != null) Arrangement.Start else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(modifier = Modifier.size(24.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CalibrationCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Text("CALIBRATION STORE", style = MaterialTheme.typography.labelMedium, color = White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundDark, RoundedCornerShape(4.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                .padding(16.dp)
        ) {
            Text("// SharedPreferences / calibration_data.xml", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(8.dp))
            val items = listOf("TOP_LEFT_X" to "144", "TOP_LEFT_Y" to "280", "BOTTOM_RIGHT_X" to "1780", "BOTTOM_RIGHT_Y" to "1020", "TARGET_DISPLAY" to "1")
            items.forEach { (k, v) ->
                Row {
                    Text("$k: ", color = Color(0xFFF59E0B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(v, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun AccessibilityStatusCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Text("ACCESSIBILITY STATUS", style = MaterialTheme.typography.labelMedium, color = White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))
        StatusLine("Service enabled", BifrostDebug.accessibilityEnabled.value)
        StatusLine("Runtime ready", BifrostDebug.accessibilityRuntimeReady.value)
    }
}

@Composable
fun StatusLine(label: String, value: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text(
            value.toString(),
            color = if (value) EmeraldAccent else RedAccent,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DisplayInfoCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Text("DISPLAY DEBUG", style = MaterialTheme.typography.labelMedium, color = White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundDark, RoundedCornerShape(4.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BifrostDebug.displayInfo.forEach { line ->
                Text(line, color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun DebugStatusCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Text("DEBUG STATUS", style = MaterialTheme.typography.labelMedium, color = White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundDark, RoundedCornerShape(4.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BifrostDebug.messages.forEach { message ->
                Text(message, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun FloatingBubbleMock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .padding(20.dp)
    ) {
        Text("FLOATING BUBBLE (MOCK)", style = MaterialTheme.typography.labelMedium, color = White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 32.dp).border(1.dp, ButtonBorderDark.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .width(180.dp)
                    .height(40.dp)
                    .background(ButtonDark, RoundedCornerShape(50))
                    .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(CyanAccent, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                 SophisticatedMiniButton("START")
                 SophisticatedMiniButton("PAUSE")
                 SophisticatedMiniButton("STOP", RedAccent)
            }
        }
    }
}

@Composable
fun SophisticatedMiniButton(text: String, color: Color = White) {
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(28.dp)
            .background(ButtonBorderDark, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.8f))
    }
}
