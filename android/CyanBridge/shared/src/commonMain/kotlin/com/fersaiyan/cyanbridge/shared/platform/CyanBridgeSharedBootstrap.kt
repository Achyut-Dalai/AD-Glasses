package com.fersaiyan.cyanbridge.shared.platform

import com.fersaiyan.cyanbridge.shared.appearance.AppearanceSettings
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.shared.notes.StructuredSummary
import com.fersaiyan.cyanbridge.shared.notes.SummaryMarkdownFormatter

/** A small stable entry point for Swift/Objective-C framework integration smoke tests. */
object CyanBridgeSharedBootstrap {
    fun applicationName(): String = "CyanBridge"

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
