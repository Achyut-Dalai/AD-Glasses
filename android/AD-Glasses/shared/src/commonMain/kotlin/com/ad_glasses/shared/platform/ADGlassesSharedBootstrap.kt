package com.ad_glasses.shared.platform

import com.ad_glasses.shared.appearance.AppearanceSettings
import com.ad_glasses.shared.navigation.AppDestination
import com.ad_glasses.shared.notes.StructuredSummary
import com.ad_glasses.shared.notes.SummaryMarkdownFormatter

/** A small stable entry point for Swift/Objective-C framework integration smoke tests. */
object ADGlassesSharedBootstrap {
    fun applicationName(): String = "ADGlasses"

    fun defaultAccentProfileId(): String = AppearanceSettings().accentProfileId

    fun defaultDestinationId(): String = AppDestination.CHATS.name

    /** String-only smoke surface for Swift while native note persistence remains host-owned. */
    fun meetingSummaryPreviewMarkdown(): String = SummaryMarkdownFormatter.format(
        StructuredSummary(
            title = "Shared meeting summary",
            summaryBullets = listOf("Formatting runs in Kotlin Multiplatform common code."),
            actionItems = listOf("Validate the framework on macOS and iOS hardware."),
            keyDecisions = listOf("Keep device transport in native adapters."),
            openQuestions = listOf("Which iOS persistence layer should own saved notes?"),
        )
    )
}
