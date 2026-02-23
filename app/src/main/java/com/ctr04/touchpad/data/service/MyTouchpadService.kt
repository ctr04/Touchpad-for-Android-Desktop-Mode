package com.ctr04.touchpad.data.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import com.ctr04.touchpad.R
import com.ctr04.touchpad.common.utils.TouchpadEventBus
import com.ctr04.touchpad.domain.entities.remoteInput.MouseAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class MyTouchpadService : AccessibilityService() {

    private lateinit var externalWindowManager: WindowManager
    private lateinit var windowManager: WindowManager
    private lateinit var cursorView: ImageView

    private var targetDisplayId: Int = -1

    private var displayWidth = 0
    private var displayHeight = 0

    private var virtualX = 0f
    private var virtualY = 0f

    private val hotSpotX = 4.5f
    private val hotSpotY = 3.5f

    private var isRefreshing = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshTargetDisplay()
        }

        override fun onDisplayRemoved(displayId: Int) {
            refreshTargetDisplay()
        }

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == targetDisplayId) {
                refreshTargetDisplay()
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        refreshTargetDisplay()

        serviceScope.launch {
            TouchpadEventBus.events.collect { data ->
                processRemoteInput(data.dx, data.dy, data.isClick, data.scroll)
            }
        }
    }

    private fun processRemoteInput(dx: Float, dy: Float, isClick: Byte, scroll: Float) {
        val size = getDisplaySize(targetDisplayId)
        val currentWidth = size.first
        val currentHeight = size.second

        if (currentWidth == 0 || currentHeight == 0) return

        if (dx != 0f || dy != 0f) {
            virtualX = (virtualX + dx).coerceIn(0f, currentWidth.toFloat())
            virtualY = (virtualY + dy).coerceIn(0f, currentHeight.toFloat())
            updateCursorUI(virtualX, virtualY)
        }

        if (scroll != 0f) {
            injectScroll(virtualX, virtualY, scroll, currentHeight)
        } else {
            when (isClick) {
                MouseAction.MOUSE_CLICK_LEFT.byte, MouseAction.PAD_TAP.byte -> {
                    injectClick(virtualX, virtualY)
                }

                MouseAction.MOUSE_CLICK_RIGHT.byte -> {
                    injectRightClick(virtualX, virtualY)
                }

                MouseAction.MOUSE_CLICK_MIDDLE.byte -> {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    private fun updateCursorUI(x: Float, y: Float) {
        if (!::cursorView.isInitialized || !cursorView.isAttachedToWindow) return
        try {
            val params = cursorView.layoutParams as WindowManager.LayoutParams
            params.x = (x - hotSpotX).toInt()
            params.y = (y - hotSpotY).toInt()
            externalWindowManager.updateViewLayout(cursorView, params)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun injectClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gestureBuilder = GestureDescription.Builder().addStroke(stroke)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            gestureBuilder.setDisplayId(targetDisplayId)
        }

        dispatchGesture(gestureBuilder.build(), null, null)
    }

    private fun injectRightClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }

        val longPressStroke = GestureDescription.StrokeDescription(path, 0, 600)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(longPressStroke)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            gestureBuilder.setDisplayId(targetDisplayId)
        }

        dispatchGesture(gestureBuilder.build(), null, null)
    }

    private fun injectScroll(x: Float, y: Float, scrollAmount: Float, height: Int) {

        val startY = y.coerceIn(0f, height.toFloat())
        val targetY = (y - (scrollAmount * 100)).coerceIn(0f, height.toFloat())

        if (abs(targetY - startY) < 1f) return

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, targetY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 200)
        val gestureBuilder = GestureDescription.Builder().addStroke(stroke)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            gestureBuilder.setDisplayId(targetDisplayId)
        }

        dispatchGesture(gestureBuilder.build(), null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayManager.unregisterDisplayListener(displayListener)

        serviceScope.cancel()
        if (::cursorView.isInitialized) externalWindowManager.removeView(cursorView)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        refreshTargetDisplay()
    }

    private fun refreshTargetDisplay() {
        if (isRefreshing) return
        isRefreshing = true

        try {


            val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val displays = displayManager.displays

            val targetDisplay = if (displays.size > 1) displays[1] else displays[0]

            val size = getDisplaySize(targetDisplay.displayId)
            displayWidth = size.first
            displayHeight = size.second

            if (targetDisplayId != targetDisplay.displayId) {
                targetDisplayId = targetDisplay.displayId

                updateDisplayContext(targetDisplay)
                TouchpadEventBus.updateDisplayName(targetDisplay.name)
            }

            virtualX = virtualX.coerceIn(0f, displayWidth.toFloat())
            virtualY = virtualY.coerceIn(0f, displayHeight.toFloat())
            updateCursorUI(virtualX, virtualY)
        } finally {
            isRefreshing = false
        }
    }

    private fun updateDisplayContext(display: android.view.Display) {
        if (::cursorView.isInitialized && ::externalWindowManager.isInitialized) {
            try { externalWindowManager.removeViewImmediate(cursorView) } catch (_: Exception) {}
        }

        val displayContext = createDisplayContext(display)
        externalWindowManager = displayContext.getSystemService(WINDOW_SERVICE) as WindowManager

        cursorView = ImageView(displayContext).apply {
            setImageResource(R.drawable.pointer_arrow)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            externalWindowManager.addView(cursorView, params)
        } catch (_: Exception) {}
    }

    private fun getDisplaySize(displayId: Int): Pair<Int, Int> {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(displayId) ?: return Pair(0, 0)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayContext = createDisplayContext(display)

            val config = displayContext.resources.configuration
            val density = displayContext.resources.displayMetrics.density

            val widthPx = (config.screenWidthDp * density).toInt()
            val heightPx = (config.screenHeightDp * density).toInt()

            Pair(widthPx, heightPx)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }
}