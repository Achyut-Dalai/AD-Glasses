package com.fersaiyan.cyanbridge.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs as AgentPrefs
import com.fersaiyan.cyanbridge.localagent.accessibility.LocalAgentAccessibilityService
import com.fersaiyan.cyanbridge.localagent.daily.DailyFactsReminderScheduler
import com.fersaiyan.cyanbridge.localagent.memory.LocalAgentMemoryStore
import com.fersaiyan.cyanbridge.memoryvault.MemoryModeManager
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCapturePrefs
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCaptureService
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.shared.ui.onboarding.FeatureOnboardingScreen
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme

class OnboardingFeatureActivity : AppCompatActivity() {

    private var featureIndex = 0
    private var featureEnabled by mutableStateOf(false)
    private var localAgentAutomationEnabled by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)

    data class OnboardingFeature(
        val iconRes: Int,
        val titleRes: Int,
        val descriptionRes: Int,
        val detailsRes: Int,
        val togglePrefKey: String? = null,
        val toggleLabel: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        featureIndex = intent.getIntExtra(EXTRA_FEATURE_INDEX, 0)
        setupFeatureScreen()
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
    }

    private fun setupFeatureScreen() {
        val feature = FEATURES.getOrNull(featureIndex) ?: run {
            finishOnboarding()
            return
        }

        val isAccessibilityFeature = featureIndex == SCREEN_MEMORY_FEATURE_INDEX
        featureEnabled = getFeatureDefaultState(featureIndex)
        localAgentAutomationEnabled = AgentPrefs.isLocalAgentAutomationEnabled(this)
        accessibilityEnabled = isLocalAgentAccessibilityServiceEnabled()
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                FeatureOnboardingScreen(
                    title = getString(feature.titleRes),
                    description = getString(feature.descriptionRes),
                    details = getString(feature.detailsRes),
                    featureToggleLabel = feature.toggleLabel,
                    featureEnabled = featureEnabled,
                    showAccessibilityDisclosure = isAccessibilityFeature,
                    accessibilityEnabled = accessibilityEnabled,
                    localAgentAutomationEnabled = localAgentAutomationEnabled,
                    backLabel = if (featureIndex == 0) "Skip all" else "Back",
                    nextLabel = if (featureIndex == FEATURES.lastIndex) "Get started" else "Next",
                    onFeatureEnabledChange = {
                        featureEnabled = it
                        setFeatureState(featureIndex, it)
                    },
                    onLocalAgentAutomationChange = {
                        localAgentAutomationEnabled = it
                        AgentPrefs.setLocalAgentAutomationEnabled(this, it)
                        if (it) LocalAgentMemoryStore.ensureSeedFiles(this)
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onBack = {
                        if (featureIndex == 0) skipAllOnboarding() else goToFeature(featureIndex - 1)
                    },
                    onNext = {
                        if (featureIndex == FEATURES.lastIndex) finishOnboarding() else goToFeature(featureIndex + 1)
                    },
                )
            }
        }
    }

    private fun getFeatureDefaultState(index: Int): Boolean {
        return when (index) {
            0 -> AgentPrefs.isDailyFactsReminderEnabled(this)
            1 -> AgentPrefs.isAutoCaptureEnabled(this) && MemoryModeManager.isScreenOcrCaptureEnabled(this)
            2 -> AutoAudioCapturePrefs.isEnabled(this)
            else -> false
        }
    }

    private fun setFeatureState(index: Int, enabled: Boolean) {
        when (index) {
            0 -> {
                AgentPrefs.setDailyFactsReminderEnabled(this, enabled)
                DailyFactsReminderScheduler.scheduleIfEnabled(this, enabled)
            }
            1 -> {
                AgentPrefs.setAutoCaptureEnabled(this, enabled)
                MemoryModeManager.setScreenOcrCaptureEnabled(this, enabled)
            }
            2 -> {
                AutoAudioCapturePrefs.setEnabled(this, enabled)
                if (enabled) {
                    AutoAudioCaptureService.start(this)
                } else {
                    AutoAudioCaptureService.stop(this)
                }
            }
            3 -> {
                if (enabled) {
                    LocalAgentMemoryStore.ensureSeedFiles(this)
                }
            }
        }
    }

    private fun refreshAccessibilityStatus() {
        accessibilityEnabled = isLocalAgentAccessibilityServiceEnabled()
    }

    private fun isLocalAgentAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!accessibilityEnabled) return false

        val expected = ComponentName(this, LocalAgentAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun goToFeature(index: Int) {
        startActivity(Intent(this, OnboardingFeatureActivity::class.java).apply {
            putExtra(EXTRA_FEATURE_INDEX, index)
        })
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        finish()
    }

    private fun finishOnboarding() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .apply()

        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }

    private fun skipAllOnboarding() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .apply()

        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }

    companion object {
        private const val EXTRA_FEATURE_INDEX = "feature_index"
        private const val PREFS = "cyanbridge_prefs"
        private const val SCREEN_MEMORY_FEATURE_INDEX = 1

        private val FEATURES = listOf(
            OnboardingFeature(
                iconRes = R.drawable.ic_device_heycyan,
                titleRes = R.string.onboarding_daily_facts_title,
                descriptionRes = R.string.onboarding_daily_facts_desc,
                detailsRes = R.string.onboarding_daily_facts_details,
                togglePrefKey = "daily_facts",
                toggleLabel = "Enable daily fact verification"
            ),
            OnboardingFeature(
                iconRes = R.drawable.ic_device_heycyan,
                titleRes = R.string.onboarding_screen_capture_title,
                descriptionRes = R.string.onboarding_screen_capture_desc,
                detailsRes = R.string.onboarding_screen_capture_details,
                togglePrefKey = "screen_capture",
                toggleLabel = "Enable automatic screen text capture"
            ),
            OnboardingFeature(
                iconRes = R.drawable.ic_device_generic_audio,
                titleRes = R.string.onboarding_audio_capture_title,
                descriptionRes = R.string.onboarding_audio_capture_desc,
                detailsRes = R.string.onboarding_audio_capture_details,
                togglePrefKey = "audio_capture",
                toggleLabel = "Enable continuous audio recording"
            ),
            OnboardingFeature(
                iconRes = R.drawable.ic_device_heycyan,
                titleRes = R.string.onboarding_agent_personality_title,
                descriptionRes = R.string.onboarding_agent_personality_desc,
                detailsRes = R.string.onboarding_agent_personality_details
            ),
            OnboardingFeature(
                iconRes = R.drawable.ic_device_heycyan,
                titleRes = R.string.onboarding_local_storage_title,
                descriptionRes = R.string.onboarding_local_storage_desc,
                detailsRes = R.string.onboarding_local_storage_details
            ),
            OnboardingFeature(
                iconRes = R.drawable.ic_device_heycyan,
                titleRes = R.string.onboarding_pro_sub_title,
                descriptionRes = R.string.onboarding_pro_sub_desc,
                detailsRes = R.string.onboarding_pro_sub_details
            )
        )

        fun launchIfNeeded(activity: AppCompatActivity) {
            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean("onboarding_completed", false)) return

            activity.startActivity(Intent(activity, OnboardingFeatureActivity::class.java))
        }
    }
}
