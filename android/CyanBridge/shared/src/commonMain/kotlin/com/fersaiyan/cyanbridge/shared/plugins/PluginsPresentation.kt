package com.fersaiyan.cyanbridge.shared.plugins

/** Platform-neutral data displayed by the Community Plugins presentation. */
data class CommunityPluginCardData(
    val title: String,
    val author: String,
    val description: String,
    val badge: String,
    val downloadsAll: Int,
    val downloadsMonthly: Int,
    val downloadsWeekly: Int,
    val votesAll: Int,
    val votesMonthly: Int,
    val votesWeekly: Int,
    val trendAll: Int,
    val trendMonthly: Int,
    val trendWeekly: Int,
    val id: String = "",
    val taskerNetLink: String? = null,
    val downloadUrl: String? = null,
) {
    fun downloads(window: PluginTimeWindow): Int = when (window) {
        PluginTimeWindow.ALL_TIME -> downloadsAll
        PluginTimeWindow.MONTHLY -> downloadsMonthly
        PluginTimeWindow.WEEKLY -> downloadsWeekly
    }

    fun votes(window: PluginTimeWindow): Int = when (window) {
        PluginTimeWindow.ALL_TIME -> votesAll
        PluginTimeWindow.MONTHLY -> votesMonthly
        PluginTimeWindow.WEEKLY -> votesWeekly
    }

    fun trend(window: PluginTimeWindow): Int = when (window) {
        PluginTimeWindow.ALL_TIME -> trendAll
        PluginTimeWindow.MONTHLY -> trendMonthly
        PluginTimeWindow.WEEKLY -> trendWeekly
    }
}

enum class PluginTimeWindow {
    ALL_TIME,
    WEEKLY,
    MONTHLY,
}

/** Shared form state; networking and persistence stay platform-owned. */
data class PublishPluginUiState(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val category: String = "",
    val taskerNetLink: String = "",
    val titleError: String? = null,
    val authorError: String? = null,
    val descriptionError: String? = null,
    val taskerNetLinkError: String? = null,
    val isSubmitting: Boolean = false,
)

object CommunityPluginCatalog {
    val categories = listOf(
        "Productivity",
        "Accessibility",
        "Planner",
        "Mobility",
        "Operations",
        "Language",
        "Other",
    )
}
