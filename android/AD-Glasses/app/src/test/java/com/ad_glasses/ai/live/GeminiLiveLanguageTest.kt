package com.ad_glasses.ai.live

import com.ad_glasses.ai.vision.ImageQuestionDefaults
import com.ad_glasses.ai.vision.ImageQuestionPromptResolver
import com.ad_glasses.ai.vision.ImageQuestionRoute
import com.ad_glasses.ai.vision.ImageQuestionSettings
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
