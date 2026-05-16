package com.genesisofthewind.bifrost

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf

private const val MAX_MESSAGES = 8

object BifrostDebug {
    const val TAG = "BifrostDebug"

    val messages = mutableStateListOf("Bifrost ready")
    val accessibilityEnabled = mutableStateOf(false)
    val accessibilityRuntimeReady = mutableStateOf(false)
    val displayInfo = mutableStateListOf("Display info not refreshed yet")

    fun record(message: String) {
        Log.d(TAG, message)
        messages.add(0, message)
        while (messages.size > MAX_MESSAGES) {
            messages.removeAt(messages.lastIndex)
        }
    }

    fun setAccessibilityRuntimeReady(isReady: Boolean) {
        accessibilityRuntimeReady.value = isReady
        record("Accessibility runtime ready: $isReady")
    }

    fun refreshAccessibilityStatus(context: Context) {
        val enabled = isBifrostAccessibilityEnabled(context)
        accessibilityEnabled.value = enabled
        record("Accessibility enabled in settings: $enabled")
    }

    fun refreshDisplayInfo(context: Context) {
        val nextLines = mutableListOf<String>()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            nextLines.add("Current window: ${bounds.width()} x ${bounds.height()}")
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            nextLines.add("Current window: ${metrics.widthPixels} x ${metrics.heightPixels}")
        }

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.displays.forEach { display ->
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            nextLines.add(
                "Display ${display.displayId}: ${display.name}, " +
                    "${metrics.widthPixels}x${metrics.heightPixels}, ${display.refreshRate}Hz"
            )
        }

        displayInfo.clear()
        displayInfo.addAll(nextLines)
        record("Display info refreshed: ${displayInfo.size} lines")
    }

    private fun isBifrostAccessibilityEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, "com.genesisofthewind.bifrost.services.DrawAccessibilityService")
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val managerSeesService = enabledServices.any { service ->
            service.resolveInfo.serviceInfo.packageName == expectedComponent.packageName &&
                service.resolveInfo.serviceInfo.name == expectedComponent.className
        }

        val enabledSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val secureSettingsSeesService = enabledSetting.split(':').any { flattened ->
            ComponentName.unflattenFromString(flattened)?.let { component ->
                component.packageName == expectedComponent.packageName &&
                    component.className == expectedComponent.className
            } ?: false
        }

        return managerSeesService || secureSettingsSeesService
    }
}
