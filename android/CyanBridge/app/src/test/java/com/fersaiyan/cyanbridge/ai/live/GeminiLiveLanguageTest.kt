package com.fersaiyan.cyanbridge.ai.live

import com.fersaiyan.cyanbridge.ai.vision.ImageQuestionDefaults
import com.fersaiyan.cyanbridge.ai.vision.ImageQuestionPromptResolver
import com.fersaiyan.cyanbridge.ai.vision.ImageQuestionRoute
import com.fersaiyan.cyanbridge.ai.vision.ImageQuestionSettings
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveLanguageTest {
    @Test
    fun `Korean default image question remains available to Gemini Live`() {
        val prompt = ImageQuestionPromptResolver.resolve(
            ImageQuestionSettings(
                appLanguageTag = "ko",
                defaultQuestion = ImageQuestionDefaults.questionForLanguage("ko"),
                usesBuiltInDefault = true,
            ),
            userQuestion = null,
        ).forRoute(ImageQuestionRoute.PRO_RELAY)

        assertTrue(prompt.contains("(ko)"))
        assertTrue(prompt.contains("이미지를 간단히 설명해 주세요"))
    }
}
