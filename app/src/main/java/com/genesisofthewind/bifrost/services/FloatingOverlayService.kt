package com.genesisofthewind.bifrost.services

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.genesisofthewind.bifrost.BifrostDebug
import com.genesisofthewind.bifrost.engine.ShapeCommand
import kotlin.math.abs
import kotlin.math.hypot

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: LinearLayout? = null
    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (floatingView == null) {
            showOverlay()
        }
        BifrostDebug.record("Overlay service started")
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (floatingView != null) return

        val params = WindowManager.LayoutParams(
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
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val bubble = Button(this).apply {
            text = "BIFROST"
            textSize = 12f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(6, 182, 212))
            minWidth = dp(96)
            minHeight = dp(48)
        }
        root.addView(bubble)

        val menu = createMenu()
        menu.visibility = View.GONE
        root.addView(menu)

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var moved = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (hypot(dx, dy) > dp(8)) {
                            moved = true
                        }
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(root, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved && abs(event.rawX - initialTouchX) < dp(8) && abs(event.rawY - initialTouchY) < dp(8)) {
                            BifrostDebug.record("Overlay tapped")
                            isExpanded = !isExpanded
                            menu.visibility = if (isExpanded) View.VISIBLE else View.GONE
                            BifrostDebug.record(if (isExpanded) "Overlay expanded" else "Overlay collapsed")
                            windowManager.updateViewLayout(root, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })

        floatingView = root
        windowManager.addView(floatingView, params)
        BifrostDebug.setOverlayRunning(true)
        BifrostDebug.record("Overlay view added")
    }

    private fun createMenu(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.argb(230, 17, 17, 20))

            addView(TextView(this@FloatingOverlayService).apply {
                text = "Bifrost Controls"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, dp(6))
            })

            addView(menuButton("Start Test Gesture") {
                BifrostDebug.record("Overlay start test gesture tapped")
                DrawAccessibilityService.getInstance()?.executeCommand(ShapeCommand.SafeTestGesture)
                    ?: BifrostDebug.record("Accessibility service is not connected")
            })

            addView(menuButton("Run Calibrated Test Line") {
                BifrostDebug.record("Overlay calibrated line tapped")
                DrawAccessibilityService.getInstance()?.executeCommand(ShapeCommand.CalibratedLine)
                    ?: BifrostDebug.record("Accessibility service is not connected")
            })

            addView(menuButton("Run Calibrated Test Square") {
                BifrostDebug.record("Overlay calibrated square tapped")
                DrawAccessibilityService.getInstance()?.executeCommand(ShapeCommand.CalibratedSmallSquare)
                    ?: BifrostDebug.record("Accessibility service is not connected")
            })

            addView(menuButton("Run Known-Good Square") {
                BifrostDebug.record("Overlay known-good square tapped")
                DrawAccessibilityService.getInstance()?.executeCommand(ShapeCommand.CalibratedSmallSquare)
                    ?: BifrostDebug.record("Accessibility service is not connected")
            })

            addView(menuButton("Run Calibrated Test Diagonal") {
                BifrostDebug.record("Overlay calibrated diagonal tapped")
                DrawAccessibilityService.getInstance()?.executeCommand(ShapeCommand.CalibratedDiagonal)
                    ?: BifrostDebug.record("Accessibility service is not connected")
            })

            addView(menuButton("Run Calibrated Test X") {
                BifrostDebug.record("Overlay calibrated X tapped")
                DrawAccessibilityService.getInstance()?.executeCommand(ShapeCommand.CalibratedXShape)
                    ?: BifrostDebug.record("Accessibility service is not connected")
            })

            addView(menuButton("Pause/Stop Current Action") {
                BifrostDebug.record("Overlay stop action tapped")
                DrawAccessibilityService.getInstance()?.executeCommand(ShapeCommand.Stop)
            })

            addView(menuButton("Close Overlay") {
                BifrostDebug.record("Overlay close tapped")
                stopSelf()
            })
        }
    }

    private fun menuButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            minWidth = dp(180)
            minHeight = dp(44)
            setOnClickListener { onClick() }
        }
    }

    override fun onDestroy() {
        removeOverlay()
        BifrostDebug.record("Overlay service destroyed")
        super.onDestroy()
    }

    private fun removeOverlay() {
        val view = floatingView ?: return
        runCatching {
            windowManager.removeView(view)
            BifrostDebug.record("Overlay view removed")
        }.onFailure {
            BifrostDebug.record("Overlay remove failed: ${it.message}")
        }
        floatingView = null
        isExpanded = false
        BifrostDebug.setOverlayRunning(false)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
