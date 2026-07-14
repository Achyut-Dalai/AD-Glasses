package com.fersaiyan.cyanbridge.bridge.core

/**
 * Sealed class representing display commands that can be sent to glasses.
 */
sealed class DisplayCommand {

    /** Show a single text message. */
    data class Text(
        val text: String,
        val priority: DisplayPriority = DisplayPriority.NORMAL,
        val ttlMs: Long? = null,
    ) : DisplayCommand()

    /** Show multiple lines of text, optionally paginated. */
    data class Lines(
        val lines: List<String>,
        val page: Int = 0,
        val totalPages: Int? = null,
    ) : DisplayCommand()

    /** Show a structured card with title, body, and optional actions. */
    data class Card(
        val title: String,
        val body: String,
        val actions: List<DisplayAction> = emptyList(),
    ) : DisplayCommand()

    /** Clear the glasses display. */
    data object Clear : DisplayCommand()

    companion object
}

enum class DisplayPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT,
}

data class DisplayAction(
    val label: String,
    val actionId: String,
)
