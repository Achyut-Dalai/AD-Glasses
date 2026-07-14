package com.fersaiyan.cyanbridge.bridge.core

/**
 * Capabilities that a glasses device adapter may support.
 * Not all adapters support all capabilities.
 */
enum class GlassesCapability {
    TEXT_DISPLAY,
    LINE_DISPLAY,
    CARD_DISPLAY,
    IMAGE_DISPLAY,
    CLEAR_DISPLAY,
    TOUCH_INPUT,
    BUTTON_INPUT,
    HEAD_GESTURE_INPUT,
    BATTERY_STATUS,
    BRIGHTNESS_CONTROL,
    MICROPHONE_AUDIO,
    SPEAKER_AUDIO,
    NOTIFICATIONS,
    DASHBOARD,
    PAGINATION,
}
