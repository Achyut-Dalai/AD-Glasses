package com.ad_glasses.shared.notes

import kotlin.test.Test
import kotlin.test.assertTrue

class SummaryMarkdownFormatterTest {
    @Test
    fun formatEmitsStableHeadingsInOrderAndFillsEmptySections() {
        val output = SummaryMarkdownFormatter.format(
            StructuredSummary(
                title = "Test Title",
                summaryBullets = emptyList(),
                actionItems = emptyList(),
                keyDecisions = emptyList(),
                openQuestions = emptyList(),
            )
        )

        val expectedHeadings = listOf(
            "# Test Title",
            "## Summary",
            "## Action items",
            "## Key decisions",
            "## Open questions",
            "## Timeline highlights",
        )
        var lastIndex = -1
        expectedHeadings.forEach { heading ->
            val index = output.indexOf(heading)
            assertTrue(index >= 0, "Missing heading: $heading")
            assertTrue(index > lastIndex, "Heading out of order: $heading")
            lastIndex = index
        }
        assertTrue(output.contains("## Summary\n- (none)"))
        assertTrue(output.contains("## Action items\n- (none)"))
        assertTrue(output.contains("## Key decisions\n- (none)"))
        assertTrue(output.contains("## Open questions\n- (none)"))
        assertTrue(output.contains("## Timeline highlights\n- (none)"))
    }

    @Test
    fun formatNormalizesBulletsAndTrimsTheTitle() {
        val output = SummaryMarkdownFormatter.format(
            StructuredSummary(
                title = "  ",
                summaryBullets = listOf("-  first\nline", "   second   line   "),
                actionItems = listOf("- todo:   x"),
                keyDecisions = emptyList(),
                openQuestions = emptyList(),
            )
        )

        assertTrue(output.startsWith("# Meeting Notes"))
        assertTrue(output.contains("- first line"))
        assertTrue(output.contains("- second line"))
        assertTrue(output.contains("## Action items\n- todo: x"))
    }
}
