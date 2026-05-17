package com.genesisofthewind.bifrost.services

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.genesisofthewind.bifrost.BifrostDebug
import com.genesisofthewind.bifrost.data.CalibrationStore

class CanvasSelectorOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var calibrationStore: CalibrationStore
    private var rootView: LinearLayout? = null
    private var selectorView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var selectorWidth = 600
    private var selectorHeight = 420

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        calibrationStore = CalibrationStore(this)
        showSelector()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (rootView == null) showSelector()
        BifrostDebug.record("Canvas selector mode started")
        return START_NOT_STICKY
    }

    private fun showSelector() {
        if (rootView != null) return

        val values = calibrationStore.getValues()
        selectorWidth = (values.bottomRightX - values.topLeftX).toInt().coerceAtLeast(dp(80))
        selectorHeight = (values.bottomRightY - values.topLeftY).toInt().coerceAtLeast(dp(80))

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = values.topLeftX.toInt()
            y = values.topLeftY.toInt()
        }
        params = layoutParams

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.argb(80, 0, 0, 0))
        }

        val selector = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(45, 6, 182, 212))
                setStroke(dp(2), Color.rgb(6, 182, 212))
            }
            addView(TextView(this@CanvasSelectorOverlayService).apply {
                text = "BIFROST CANVAS"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(dp(8), dp(6), dp(8), dp(6))
            })
        }
        selectorView = selector
        root.addView(selector, LinearLayout.LayoutParams(selectorWidth, selectorHeight))

        root.addView(controlGrid())

        rootView = root
        windowManager.addView(root, layoutParams)
        BifrostDebug.record("Canvas selector shown ${layoutParams.x},${layoutParams.y} ${selectorWidth}x${selectorHeight}")
    }

    private fun controlGrid(): GridLayout {
        return GridLayout(this).apply {
            columnCount = 3
            setPadding(0, dp(6), 0, 0)
            setBackgroundColor(Color.argb(220, 17, 17, 20))

            addView(controlButton("Up") { moveBy(0, -dp(8)) })
            addView(controlButton("Taller") { resizeBy(0, dp(12)) })
            addView(controlButton("Wide") { resizeBy(dp(12), 0) })
            addView(controlButton("Left") { moveBy(-dp(8), 0) })
            addView(controlButton("Save") { saveBounds() })
            addView(controlButton("Right") { moveBy(dp(8), 0) })
            addView(controlButton("Down") { moveBy(0, dp(8)) })
            addView(controlButton("Shorter") { resizeBy(0, -dp(12)) })
            addView(controlButton("Narrow") { resizeBy(-dp(12), 0) })
            addView(controlButton("Reset") { resetBounds() })
            addView(controlButton("Hide") { stopSelf() })
        }
    }

    private fun controlButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 11f
            isAllCaps = false
            minWidth = dp(78)
            minHeight = dp(40)
            setOnClickListener { onClick() }
        }
    }

    private fun moveBy(dx: Int, dy: Int) {
        val root = rootView ?: return
        val layoutParams = params ?: return
        layoutParams.x += dx
        layoutParams.y += dy
        windowManager.updateViewLayout(root, layoutParams)
        BifrostDebug.record("Selector moved to ${layoutParams.x},${layoutParams.y}")
    }

    private fun resizeBy(dw: Int, dh: Int) {
        val selector = selectorView ?: return
        selectorWidth = (selectorWidth + dw).coerceAtLeast(dp(80))
        selectorHeight = (selectorHeight + dh).coerceAtLeast(dp(80))
        selector.layoutParams = LinearLayout.LayoutParams(selectorWidth, selectorHeight)
        selector.requestLayout()
        BifrostDebug.record("Selector resized to ${selectorWidth}x${selectorHeight}")
    }

    private fun saveBounds() {
        val layoutParams = params ?: return
        calibrationStore.saveTopLeft(layoutParams.x.toFloat(), layoutParams.y.toFloat())
        calibrationStore.saveBottomRight((layoutParams.x + selectorWidth).toFloat(), (layoutParams.y + selectorHeight).toFloat())
        BifrostDebug.record("Selector bounds saved: ${layoutParams.x},${layoutParams.y} -> ${layoutParams.x + selectorWidth},${layoutParams.y + selectorHeight}")
    }

    private fun resetBounds() {
        val root = rootView ?: return
        val layoutParams = params ?: return
        layoutParams.x = 100
        layoutParams.y = 100
        selectorWidth = 600
        selectorHeight = 420
        selectorView?.layoutParams = LinearLayout.LayoutParams(selectorWidth, selectorHeight)
        windowManager.updateViewLayout(root, layoutParams)
        saveBounds()
        BifrostDebug.record("Selector reset")
    }

    override fun onDestroy() {
        rootView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        rootView = null
        selectorView = null
        params = null
        BifrostDebug.record("Canvas selector hidden")
        super.onDestroy()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
