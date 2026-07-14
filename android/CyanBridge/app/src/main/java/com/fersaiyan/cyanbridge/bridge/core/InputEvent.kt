package com.fersaiyan.cyanbridge.bridge.core

/**
 * Events received from the glasses (button presses, gestures, etc.).
 */
sealed class InputEvent {

    data class Button(
        val button: String,
        val gesture: GestureType,
    ) : InputEvent()

    data class Touch(
        val side: Side? = null,
        val gesture: GestureType,
    ) : InputEvent()

    data class HeadGesture(
        val direction: HeadDirection,
        val confidence: Float? = null,
    ) : InputEvent()

    data class VoiceText(
        val text: String,
        val isFinal: Boolean = true,
    ) : InputEvent()

    data class Battery(
        val level: Int,
        val charging: Boolean? = null,
    ) : InputEvent()
}

enum class GestureType {
    SINGLE_TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP,
    SWIPE_DOWN,
}

enum class Side {
    LEFT,
    RIGHT,
}

enum class HeadDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    NOD,
    SHAKE,
}
