package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantWebMode
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantWebModePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ADProductPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun freshInstallShowsWelcomeUntilOnboardingCompletes() {
        assertFalse(ADWelcomePreferences.isComplete(context))

        ADWelcomePreferences.markComplete(context)

        assertTrue(ADWelcomePreferences.isComplete(context))
    }

    @Test
    fun themeDefaultsToMonochromeAndPersistsDarkMonochrome() {
        assertEquals(ADThemeStyle.MONOCHROME, ADThemePreferences.get(context))

        ADThemePreferences.set(context, ADThemeStyle.DARK_MONOCHROME)

        assertEquals(ADThemeStyle.DARK_MONOCHROME, ADThemePreferences.get(context))
    }

    @Test
    fun retiredLightThemeNamesMigrateToMonochrome() {
        val prefs = context.getSharedPreferences("ad_glasses_theme", Context.MODE_PRIVATE)

        prefs.edit().putString("style", "MONO").commit()
        assertEquals(ADThemeStyle.MONOCHROME, ADThemePreferences.get(context))

        prefs.edit().putString("style", "VIBE").commit()
        assertEquals(ADThemeStyle.MONOCHROME, ADThemePreferences.get(context))
    }

    @Test
    fun webModeDefaultsToAutoAndCanForceWeb() {
        assertEquals(AssistantWebMode.AUTO, AssistantWebModePreferences.get(context))
        assertNull(AssistantWebModePreferences.explicitOverride(context))

        AssistantWebModePreferences.set(context, AssistantWebMode.ON)

        assertEquals(AssistantWebMode.ON, AssistantWebModePreferences.get(context))
        assertEquals(true, AssistantWebModePreferences.explicitOverride(context))
    }

    private fun clearPreferences() {
        context.getSharedPreferences("ad_glasses_welcome", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("ad_glasses_theme", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("assistant_web_mode", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
