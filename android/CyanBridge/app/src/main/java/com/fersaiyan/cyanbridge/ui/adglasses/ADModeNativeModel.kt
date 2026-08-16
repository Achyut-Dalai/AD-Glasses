package com.fersaiyan.cyanbridge.ui.adglasses

/** Native mode actions exposed by the AD Glasses surface. */
enum class ADModeAction {
    START,
    STOP,
}

data class ADModeCardState(
    val title: String,
    val description: String,
    val enabled: Boolean,
    val action: ADModeAction,
)

internal fun ADAutomation.toModeCard(activeTitle: String?): ADModeCardState {
    val active = activeTitle == title
    return ADModeCardState(
        title = title,
        description = summary,
        enabled = active,
        action = if (active) ADModeAction.STOP else ADModeAction.START,
    )
}
