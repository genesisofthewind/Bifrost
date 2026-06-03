package com.genesisofthewind.bifrost.services

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.genesisofthewind.bifrost.BifrostDebug
import com.genesisofthewind.bifrost.data.PaletteProfileStore

class PaletteCalibrationOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var paletteStore: PaletteProfileStore
    private var rootView: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var controlPanelWidth = 0
    private var crosshairSize = 0
    private var rootPadding = 0
    private var targetKey: String = PaletteProfileStore.TARGET_FILL_TOOL
    private var targetLabel: String = "Fill / Bucket Tool"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        paletteStore = PaletteProfileStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetKey = intent?.getStringExtra(EXTRA_TARGET_KEY) ?: PaletteProfileStore.TARGET_FILL_TOOL
        targetLabel = intent?.getStringExtra(EXTRA_TARGET_LABEL) ?: paletteStore.targetForKey(targetKey)?.label ?: "Target"
        showCrosshair()
        BifrostDebug.record("Palette calibration started for $targetLabel")
        return START_NOT_STICKY
    }

    private fun showCrosshair() {
        removeOverlay()
        val target = paletteStore.targetForKey(targetKey)
        val startX = target?.x?.toInt()?.takeIf { it > 0 } ?: 900
        val startY = target?.y?.toInt()?.takeIf { it > 0 } ?: 500
        controlPanelWidth = dp(220)
        crosshairSize = dp(72)
        rootPadding = dp(4)

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
            x = startX - rootPadding - controlPanelWidth - (crosshairSize / 2)
            y = startY - rootPadding - (crosshairSize / 2)
        }
        params = layoutParams

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(rootPadding, rootPadding, rootPadding, rootPadding)
            setBackgroundColor(Color.argb(35, 0, 0, 0))
        }

        root.addView(controlPanel())
        root.addView(crosshairView())
        rootView = root
        windowManager.addView(root, layoutParams)
    }

    private fun crosshairView(): FrameLayout {
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(70, 6, 182, 212))
                setStroke(dp(2), Color.rgb(6, 182, 212))
            }
            addView(View(this@PaletteCalibrationOverlayService).apply {
                setBackgroundColor(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(dp(2), dp(72), Gravity.CENTER)
            })
            addView(View(this@PaletteCalibrationOverlayService).apply {
                setBackgroundColor(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(dp(72), dp(2), Gravity.CENTER)
            })
            addView(TextView(this@PaletteCalibrationOverlayService).apply {
                text = "+"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            })
            layoutParams = LinearLayout.LayoutParams(crosshairSize, crosshairSize)
            setOnTouchListener(dragListener())
        }
    }

    private fun controlPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.argb(230, 17, 17, 20))
            layoutParams = LinearLayout.LayoutParams(controlPanelWidth, LinearLayout.LayoutParams.WRAP_CONTENT)

            addView(TextView(this@PaletteCalibrationOverlayService).apply {
                text = "Tap target setup"
                textSize = 12f
                setTextColor(Color.LTGRAY)
                setPadding(0, 0, 0, dp(3))
            })
            addView(TextView(this@PaletteCalibrationOverlayService).apply {
                text = "Move crosshair to $targetLabel"
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, dp(5))
            })
            addView(TextView(this@PaletteCalibrationOverlayService).apply {
                text = "Controls stay left so the Tomodachi palette remains visible."
                textSize = 10f
                setTextColor(Color.LTGRAY)
                setPadding(0, 0, 0, dp(5))
            })
            addView(buttonGrid(listOf(
                "Up" to { moveBy(0, -dp(5)) },
                "Left" to { moveBy(-dp(5), 0) },
                "Right" to { moveBy(dp(5), 0) },
                "Down" to { moveBy(0, dp(5)) }
            )))
            addView(button("Save Current Position") { saveCurrentPosition() })
            addView(button("Close") { stopSelf() })
        }
    }

    private fun dragListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                val layoutParams = params ?: return false
                val root = rootView ?: return false
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(root, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        BifrostDebug.record("Palette crosshair moved to ${centerX()},${centerY()} for $targetLabel")
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun buttonGrid(buttons: List<Pair<String, () -> Unit>>): GridLayout {
        return GridLayout(this).apply {
            columnCount = 2
            buttons.forEach { (label, action) -> addView(button(label, action)) }
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 11f
            isAllCaps = false
            minWidth = dp(90)
            minHeight = dp(38)
            setOnClickListener { onClick() }
        }
    }

    private fun moveBy(dx: Int, dy: Int) {
        val root = rootView ?: return
        val layoutParams = params ?: return
        layoutParams.x += dx
        layoutParams.y += dy
        windowManager.updateViewLayout(root, layoutParams)
        BifrostDebug.record("Palette crosshair nudged to ${centerX()},${centerY()} for $targetLabel")
    }

    private fun saveCurrentPosition() {
        val x = centerX().toFloat()
        val y = centerY().toFloat()
        val profile = paletteStore.updateTapTarget(targetKey, x, y)
        BifrostDebug.record("Captured $targetLabel: ${x.toInt()},${y.toInt()} for ${profile.name}; saved")
        stopSelf()
    }

    private fun centerX(): Int {
        return (params?.x ?: 0) + rootPadding + controlPanelWidth + (crosshairSize / 2)
    }

    private fun centerY(): Int {
        return (params?.y ?: 0) + rootPadding + (crosshairSize / 2)
    }

    override fun onDestroy() {
        removeOverlay()
        BifrostDebug.record("Palette calibration overlay closed")
        super.onDestroy()
    }

    private fun removeOverlay() {
        rootView?.let { view -> runCatching { windowManager.removeView(view) } }
        rootView = null
        params = null
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val EXTRA_TARGET_KEY = "extra_target_key"
        const val EXTRA_TARGET_LABEL = "extra_target_label"
    }
}
