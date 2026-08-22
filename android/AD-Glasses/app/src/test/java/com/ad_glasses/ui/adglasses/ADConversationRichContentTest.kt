package com.ad_glasses.ui.adglasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ADConversationRichContentTest {

    @Test
    fun separatesProseCodeAndLinks() {
        val blocks = parseADConversationBlocks(
            """
            Here is the result.

            ```kotlin
            val answer = 42
            ```

            [Open source](https://example.com/article)
            """.trimIndent(),
        )

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is ADConversationBlock.TextBlock)
        assertTrue(blocks[1] is ADConversationBlock.CodeBlock)
        assertTrue(blocks[2] is ADConversationBlock.LinkBlock)
        assertEquals("kotlin", (blocks[1] as ADConversationBlock.CodeBlock).language)
        assertEquals(ADConversationLinkKind.LINK, (blocks[2] as ADConversationBlock.LinkBlock).kind)
    }

    @Test
    fun classifiesCommonMediaTargets() {
        val image = parseADConversationBlocks("![Photo](https://example.com/result.jpg)").single()
        val video = parseADConversationBlocks("[Watch](https://example.com/result.mp4)").single()
        val audio = parseADConversationBlocks("[Listen](https://example.com/result.m4a)").single()
        val document = parseADConversationBlocks("[Read](https://example.com/result.pdf)").single()

        assertEquals(ADConversationLinkKind.IMAGE, (image as ADConversationBlock.LinkBlock).kind)
        assertEquals(ADConversationLinkKind.VIDEO, (video as ADConversationBlock.LinkBlock).kind)
        assertEquals(ADConversationLinkKind.AUDIO, (audio as ADConversationBlock.LinkBlock).kind)
        assertEquals(ADConversationLinkKind.DOCUMENT, (document as ADConversationBlock.LinkBlock).kind)
    }

    @Test
    fun extractsInlineMarkdownLinkWithoutDroppingSurroundingProse() {
        val blocks = parseADConversationBlocks(
            "You can read the [full source](https://example.com/source) for details.",
        )

        assertEquals(3, blocks.size)
        assertEquals("You can read the", (blocks[0] as ADConversationBlock.TextBlock).text)
        assertEquals("full source", (blocks[1] as ADConversationBlock.LinkBlock).label)
        assertEquals("for details.", (blocks[2] as ADConversationBlock.TextBlock).text)
    }

    @Test
    fun extractsRawUrlInsideProse() {
        val blocks = parseADConversationBlocks("Result: https://example.com/report.pdf is ready")

        assertEquals(3, blocks.size)
        assertEquals("Result:", (blocks[0] as ADConversationBlock.TextBlock).text)
        assertEquals(ADConversationLinkKind.DOCUMENT, (blocks[1] as ADConversationBlock.LinkBlock).kind)
        assertEquals("is ready", (blocks[2] as ADConversationBlock.TextBlock).text)
    }

    @Test
    fun preservesNormalMultilineTextAsOneReadableBlock() {
        val blocks = parseADConversationBlocks("First line\nSecond line")
        assertEquals(1, blocks.size)
        assertEquals(
            "First line\nSecond line",
            (blocks.single() as ADConversationBlock.TextBlock).text,
        )
    }
}
