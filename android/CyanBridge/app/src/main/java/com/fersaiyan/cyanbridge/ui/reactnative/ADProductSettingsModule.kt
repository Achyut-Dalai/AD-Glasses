package com.fersaiyan.cyanbridge.ui.reactnative

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.ai.router.CliRelayBackend
import com.fersaiyan.cyanbridge.localmodels.remote.RemoteOpenAiPrefs
import com.fersaiyan.cyanbridge.localmodels.storage.LocalModelStorageRepository
import com.fersaiyan.cyanbridge.privacy.PrivacyPrefs
import com.fersaiyan.cyanbridge.ui.hasAccessibilityServicePermission
import com.fersaiyan.cyanbridge.ui.hasBluetooth
import com.fersaiyan.cyanbridge.ui.hasCameraPermission
import com.fersaiyan.cyanbridge.ui.localization.AppLanguage
import com.fersaiyan.cyanbridge.ui.localization.AppLanguagePreferences

/** Product-facing preferences and permission state for React Native settings surfaces. */
class ADProductSettingsModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "ADProductSettings"

    @ReactMethod
    fun getSettings(promise: Promise) {
        runCatching {
            val provider = when (AiProviderPrefs.getProvider(reactContext)) {
                AiProviderType.LOCAL_MODELS -> "Local AI"
                AiProviderType.CLI_RELAY -> when (AiProviderPrefs.getRelayBackend(reactContext)) {
                    CliRelayBackend.GEMINI -> "Gemini"
                    CliRelayBackend.CODEX -> "OpenAI / Codex"
                }
                else -> "Gemini"
            }
            Arguments.createMap().apply {
                putString("provider", provider)
                putString("relayUrl", AiProviderPrefs.getRelayBaseUrl(reactContext))
                putString(
                    "relayBackend",
                    if (AiProviderPrefs.getRelayBackend(reactContext) == CliRelayBackend.CODEX) {
                        "OpenAI / Codex"
                    } else {
                        "Gemini"
                    },
                )
                putBoolean("relayConfigured", AiProviderPrefs.isRelayConfigured(reactContext))
                putBoolean("remoteEnabled", RemoteOpenAiPrefs.isEnabled(reactContext))
                putString("remoteUrl", RemoteOpenAiPrefs.getBaseUrl(reactContext))
                putString("remoteModel", RemoteOpenAiPrefs.getModel(reactContext))
                putString("language", AppLanguagePreferences.selected(reactContext).name)
                putBoolean("redactNames", PrivacyPrefs.isRedactNamesEnabled(reactContext))
                putBoolean("transcriptStorage", PrivacyPrefs.isTranscriptStorageEnabled(reactContext))
                putBoolean("cameraGranted", hasCameraPermission(reactContext))
                putBoolean("bluetoothGranted", hasBluetooth(reactContext))
                putBoolean("microphoneGranted", hasMicrophonePermission())
                putBoolean("automationGranted", hasAccessibilityServicePermission(reactContext))
                putArray("localModels", Arguments.createArray().apply {
                    LocalModelStorageRepository.listInstalled(reactContext).forEach { model ->
                        pushMap(Arguments.createMap().apply {
                            putString("id", model.id)
                            putString("name", model.displayName)
                            putDouble("sizeBytes", model.sizeBytes.toDouble())
                            putBoolean(
                                "selected",
                                LocalModelStorageRepository.getSelectedModelId(reactContext) == model.id,
                            )
                        })
                    }
                })
            }
        }.onSuccess(promise::resolve)
            .onFailure { promise.reject("E_PRODUCT_SETTINGS", it.message ?: "Could not read settings", it) }
    }

    @ReactMethod
    fun setLanguage(languageName: String) {
        val language = AppLanguage.entries.firstOrNull { it.name == languageName } ?: AppLanguage.SYSTEM
        AppLanguagePreferences.select(reactContext, language)
    }

    @ReactMethod
    fun setRedactNames(enabled: Boolean) {
        PrivacyPrefs.setRedactNamesEnabled(reactContext, enabled)
    }

    @ReactMethod
    fun setTranscriptStorage(enabled: Boolean) {
        PrivacyPrefs.setTranscriptStorageEnabled(reactContext, enabled)
    }

    @ReactMethod
    fun selectLocalModel(modelId: String) {
        LocalModelStorageRepository.setSelectedModelId(reactContext, modelId)
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            reactContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
}
