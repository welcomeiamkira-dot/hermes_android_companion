package com.kirti.jarvis.companion

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class TelegramTypingDetector : AccessibilityService() {
    companion object {
        const val TAG = "JARVIS-TypingDetector"
    }

    override fun onServiceConnected() {
        Log.i(TAG, "TelegramTypingDetector connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Placeholder: observe Telegram input field events only.
        Log.i(TAG, "Event: type=${event?.eventType}, package=${event?.packageName}")
    }

    override fun onInterrupt() {
        Log.i(TAG, "TelegramTypingDetector interrupted")
    }
}
