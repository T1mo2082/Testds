package com.example.voicecommander

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A type-safe enum to represent the gestures our app can perform.
 */
enum class GestureType {
    SCROLL_DOWN, // Represents a swipe-up gesture
    LIKE_POST    // Represents a double-tap gesture
}

/**
 * A singleton object that acts as a communication bus between services.
 * The VoiceCommandService emits gesture requests, and the GestureAccessibilityService collects them.
 */
object GestureDispatch {
    private val _gestureFlow = MutableSharedFlow<GestureType>()
    val gestureFlow = _gestureFlow.asSharedFlow()

    suspend fun send(type: GestureType) {
        _gestureFlow.emit(type)
    }
}