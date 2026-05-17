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
import android.widget.ScrollView
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

            val actions = LinearLayout(this@FloatingOverlayService).apply {
                orientation = LinearLayout.VERTICAL

                addView(sectionLabel("Basic"))
                addView(menuButton("Start Test Gesture") {
                    BifrostDebug.record("Overlay start test gesture tapped")
                    executeOverlayCommand(ShapeCommand.SafeTestGesture)
                })

                addView(sectionLabel("Calibrated"))
                addView(menuButton("Test Line") {
                    BifrostDebug.record("Overlay calibrated line tapped")
                    executeOverlayCommand(ShapeCommand.CalibratedLine)
                })

                addView(menuButton("Known-Good Square") {
                    BifrostDebug.record("Overlay known-good square tapped")
                    executeOverlayCommand(ShapeCommand.CalibratedSmallSquare)
                })

                addView(menuButton("Canvas Selector") {
                    BifrostDebug.record("Overlay canvas selector tapped")
                    startService(Intent(this@FloatingOverlayService, CanvasSelectorOverlayService::class.java))
                })

                addView(menuButton("Diagonal TL->BR") {
                    BifrostDebug.record("Overlay diagonal TL->BR tapped")
                    executeOverlayCommand(ShapeCommand.DiagonalTopLeftToBottomRight)
                })

                addView(menuButton("Diagonal TR->BL") {
                    BifrostDebug.record("Overlay diagonal TR->BL tapped")
                    executeOverlayCommand(ShapeCommand.DiagonalTopRightToBottomLeft)
                })

                addView(menuButton("X Shape") {
                    BifrostDebug.record("Overlay calibrated X tapped")
                    executeOverlayCommand(ShapeCommand.CalibratedXShape)
                })

                addView(sectionLabel("Debug"))
                addView(menuButton("Segmented TL->BR") {
                    BifrostDebug.record("Overlay segmented TL->BR tapped")
                    executeOverlayCommand(ShapeCommand.SegmentedTopLeftToBottomRight)
                })

                addView(menuButton("Reverse X") {
                    BifrostDebug.record("Overlay reverse X tapped")
                    executeOverlayCommand(ShapeCommand.ReverseXShape)
                })

                addView(menuButton("Pause/Stop Action") {
                    BifrostDebug.record("Overlay stop action tapped")
                    executeOverlayCommand(ShapeCommand.Stop)
                })
            }

            addView(ScrollView(this@FloatingOverlayService).apply {
                addView(actions)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(260)
                )
            })

            addView(menuButton("Close Overlay") {
                BifrostDebug.record("Overlay close tapped")
                stopSelf()
            })
        }
    }

    private fun executeOverlayCommand(command: ShapeCommand) {
        DrawAccessibilityService.getInstance()?.executeCommand(command)
            ?: BifrostDebug.record("Accessibility service is not connected")
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(6), 0, dp(2))
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
