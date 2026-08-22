package com.ad_glasses.ui.onboarding

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ad_glasses.shared.ui.onboarding.OnboardingLanguageOption
import com.ad_glasses.shared.ui.onboarding.WelcomeScreen
import com.ad_glasses.ui.theme.ADGlassesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun setupRequiresAnExplicitLanguageChoice() {
        var selectedLanguage: String? = null
        composeRule.setContent {
            ADGlassesTheme {
                WelcomeScreen(
                    languageOptions = listOf(
                        OnboardingLanguageOption("ENGLISH", "English"),
                        OnboardingLanguageOption("SPANISH", "Español"),
                    ),
                    selectedLanguageId = "",
                    languageSelectionComplete = false,
                    onLanguageSelected = { selectedLanguage = it.id },
                    onStartSetup = {},
                )
            }
        }

        composeRule.onNodeWithText("Start setup").assertIsNotEnabled()
        composeRule.onNodeWithText("Español").performClick()
        composeRule.runOnIdle { assertEquals("SPANISH", selectedLanguage) }
    }

    @Test
    fun setupIsEnabledAfterLanguageWasSelected() {
        composeRule.setContent {
            ADGlassesTheme {
                WelcomeScreen(
                    languageOptions = listOf(OnboardingLanguageOption("ENGLISH", "English")),
                    selectedLanguageId = "ENGLISH",
                    languageSelectionComplete = true,
                    onLanguageSelected = {},
                    onStartSetup = {},
                )
            }
        }

        composeRule.onNodeWithText("Start setup").assertIsEnabled()
    }
}
