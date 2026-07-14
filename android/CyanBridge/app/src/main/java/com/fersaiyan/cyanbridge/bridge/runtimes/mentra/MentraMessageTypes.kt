package com.fersaiyan.cyanbridge.bridge.runtimes.mentra

/**
 * Constants for MentraOS message types used in the relay protocol.
 *
 * Messages flow between MentraOS-compatible apps and the local relay:
 *   App → Relay: connection_init, display_event, subscription_update
 *   Relay → App: connection_ack, data_stream, settings_update
 */
object MentraMessageTypes {

    // ── App → Relay ──────────────────────────────────────────────────────────

    /** Sent by the app to initiate a session. */
    const val TPA_CONNECTION_INIT = "tpa_connection_init"

    /** Sent by the app to render display content on the glasses. */
    const val DISPLAY_EVENT = "display_event"

    /** Sent by the app to subscribe to input streams (button, head, etc.). */
    const val SUBSCRIPTION_UPDATE = "subscription_update"

    /** Sent by the app to update the dashboard view. */
    const val DASHBOARD_CONTENT_UPDATE = "dashboard_content_update"

    // ── Relay → App ──────────────────────────────────────────────────────────

    /** Acknowledges a successful connection init. */
    const val TPA_CONNECTION_ACK = "tpa_connection_ack"

    /** Reports an error during connection or message processing. */
    const val TPA_CONNECTION_ERROR = "tpa_connection_error"

    /** Notifies the app that it has been stopped by the relay. */
    const val APP_STOPPED = "app_stopped"

    /** Forwards a real-time input event (button, head, etc.) to the app. */
    const val DATA_STREAM = "data_stream"

    /** Pushes updated settings from the relay to the app. */
    const val SETTINGS_UPDATE = "settings_update"

    // ── Stream types (for DATA_STREAM) ───────────────────────────────────────

    /** Button press event (short / long). */
    const val STREAM_BUTTON_PRESS = "button_press"

    /** Head position / orientation update. */
    const val STREAM_HEAD_POSITION = "head_position"

    /** Voice transcription result. */
    const val STREAM_TRANSCRIPTION = "transcription"

    /** Glasses battery level update. */
    const val STREAM_BATTERY = "glasses_battery_update"
}
