package com.example.voicecommander

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class GestureAccessibilityService : AccessibilityService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            GestureDispatch.gestureFlow.collectLatest { gesture ->
                when (gesture) {
                    GestureType.SCROLL_DOWN -> performScroll()
                    GestureType.LIKE_POST -> performDoubleTap()
                }
            }
        }
    }

    private fun performScroll() {
        val metrics: DisplayMetrics = resources.displayMetrics
        val middleX = metrics.widthPixels / 2
        val startY = metrics.heightPixels * 0.75
        val endY = metrics.heightPixels * 0.25

        val path = Path().apply {
            moveTo(middleX.toFloat(), startY.toFloat())
            lineTo(middleX.toFloat(), endY.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 200)) // 200ms duration
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun performDoubleTap() {
        val metrics: DisplayMetrics = resources.displayMetrics
        val middleX = metrics.widthPixels / 2
        val middleY = metrics.heightPixels / 2

        val tapPath = Path().apply {
            moveTo(middleX.toFloat(), middleY.toFloat())
        }

        // A double tap is two single taps in quick succession
        val tap1 = GestureDescription.StrokeDescription(tapPath, 0, 50)
        val tap2 = GestureDescription.StrokeDescription(tapPath, 100, 50) // 100ms delay

        val gesture = GestureDescription.Builder()
            .addStroke(tap1)
            .addStroke(tap2)
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for this implementation
    }

    override fun onInterrupt() {
        // Not needed for this implementation
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}