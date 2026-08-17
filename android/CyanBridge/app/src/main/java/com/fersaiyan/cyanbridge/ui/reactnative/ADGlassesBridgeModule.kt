package com.fersaiyan.cyanbridge.ui.reactnative

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidModeCommandExecutor
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantMode
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantModeAction
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantModeCommand
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.ai.router.CliRelayBackend
import com.fersaiyan.cyanbridge.localmodels.remote.RemoteOpenAiPrefs
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardAction
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import com.fersaiyan.cyanbridge.shared.glasses.OtaFirmwareSource
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import com.fersaiyan.cyanbridge.ui.DeviceBindActivity
import com.oudmon.ble.base.bluetooth.BleOperateManager

/**
 * Thin bridge from the new product shell to the proven Android runtime.
 * It deliberately contains no glasses protocol logic: protocol ownership stays native.
 */
class ADGlassesBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "ADGlassesBridge"

    @ReactMethod
    fun getDashboardState(promise: Promise) {
        val map = Arguments.createMap()
        val state = currentDashboardState()
        val connected = state?.connectionLabel?.startsWith("Connected", ignoreCase = true)
            ?: BleOperateManager.getInstance().isConnected
        map.putBoolean("connected", connected)
        map.putBoolean("connecting", state?.connectionLabel?.contains("Connecting", ignoreCase = true) == true)
        map.putString("deviceName", "Glasses")
        state?.batteryPercent?.let { map.putInt("batteryPercent", it) }
        state?.storageLabel?.takeIf { it != "--" }?.let { map.putString("storageLabel", it) }
        map.putBoolean("syncActive", state?.transfer?.isVisible == true)
        promise.resolve(map)
    }

    @ReactMethod
    fun openNativeRoute(route: String) {
        when (route.lowercase()) {
            "device-setup", "pairing" -> startActivity(Intent(reactContext, DeviceBindActivity::class.java))
            "accessibility" -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "assistant-settings" -> startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            "app-settings", "storage-settings" -> openAppSettings()
        }
    }

    @ReactMethod
    fun runAction(action: String, payload: ReadableMap?) {
        when (action) {
            "scan" -> dispatch(GlassesDashboardAction.Scan)
            "reconnect" -> dispatch(GlassesDashboardAction.Reconnect)
            "disconnect" -> dispatch(GlassesDashboardAction.Disconnect)
            "startSync" -> dispatch(GlassesDashboardAction.StartSync)
            "stopSync" -> dispatch(GlassesDashboardAction.StopSync)
            "capturePhoto" -> dispatch(GlassesDashboardAction.CapturePhoto)
            "toggleVideo" -> dispatch(GlassesDashboardAction.ToggleVideo)
            "startRecording" -> dispatch(GlassesDashboardAction.StartMeetingCapture)
            "stopRecording" -> dispatch(GlassesDashboardAction.StopMeetingCapture)
            "voiceQuestion" -> dispatch(GlassesDashboardAction.TestVoiceQuestion)
            "imageQuestion" -> dispatch(GlassesDashboardAction.TestImageQuestion)
            "chooseFirmware" -> dispatch(GlassesDashboardAction.RequestOtaFirmware(OtaFirmwareSource.PERSONAL_FILE))
            "cancelFirmware" -> dispatch(GlassesDashboardAction.CancelOta)
            "openDeviceSetup" -> startActivity(Intent(reactContext, DeviceBindActivity::class.java))
            "openAccessibility" -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "openAssistantSettings" -> startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            "openAppSettings", "openStorageSettings" -> openAppSettings()
            "completeOnboarding" -> completeOnboarding()
            "capabilityToggle" -> toggleCapability(payload)
            "setAiProvider" -> setAiProvider(payload?.stringOrNull("provider"))
            "saveRelay" -> saveRelay(payload)
            "saveRemoteServer" -> saveRemoteServer(payload)
            "exitApp" -> currentActivity?.finishAffinity()
        }
    }

    private fun completeOnboarding() {
        reactContext.getSharedPreferences("cyanbridge_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .apply()
    }

    private fun setAiProvider(provider: String?) {
        when (provider) {
            "Gemini" -> {
                LocalAgentPrefs.setGlassesAssistantMode(reactContext, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                LocalAgentPrefs.setProviderType(reactContext, AgentProviderType.PRO_SUBSCRIPTION)
                AiProviderPrefs.setProvider(reactContext, AiProviderType.CLI_RELAY)
                AiProviderPrefs.setRelayBackend(reactContext, CliRelayBackend.GEMINI)
            }
            "OpenAI / Codex" -> {
                LocalAgentPrefs.setGlassesAssistantMode(reactContext, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                LocalAgentPrefs.setProviderType(reactContext, AgentProviderType.PRO_SUBSCRIPTION)
                AiProviderPrefs.setProvider(reactContext, AiProviderType.CLI_RELAY)
                AiProviderPrefs.setRelayBackend(reactContext, CliRelayBackend.CODEX)
            }
            "Local AI" -> {
                LocalAgentPrefs.setGlassesAssistantMode(reactContext, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                LocalAgentPrefs.setProviderType(reactContext, AgentProviderType.LOCAL_AGENT)
                AiProviderPrefs.setProvider(reactContext, AiProviderType.LOCAL_MODELS)
            }
        }
    }

    private fun saveRelay(payload: ReadableMap?) {
        val url = payload?.stringOrNull("url") ?: return
        val backend = payload.stringOrNull("backend")
        AiProviderPrefs.setRelayBaseUrl(reactContext, url)
        AiProviderPrefs.setRelayBackend(
            reactContext,
            if (backend == "OpenAI / Codex") CliRelayBackend.CODEX else CliRelayBackend.GEMINI,
        )
        AiProviderPrefs.setProvider(reactContext, AiProviderType.CLI_RELAY)
    }

    private fun saveRemoteServer(payload: ReadableMap?) {
        if (payload == null) return
        payload.stringOrNull("url")?.let { RemoteOpenAiPrefs.setBaseUrl(reactContext, it) }
        payload.stringOrNull("model")?.let { RemoteOpenAiPrefs.setModel(reactContext, it) }
        payload.stringOrNull("apiKey")?.let { RemoteOpenAiPrefs.setApiKey(reactContext, it) }
        if (payload.hasKey("enabled") && !payload.isNull("enabled")) {
            RemoteOpenAiPrefs.setEnabled(reactContext, payload.getBoolean("enabled"))
        }
    }

    private fun toggleCapability(payload: ReadableMap?) {
        if (payload == null) return
        val name = payload.stringOrNull("name") ?: return
        val enabled = payload.hasKey("enabled") && !payload.isNull("enabled") && payload.getBoolean("enabled")
        val mode = when (name) {
            "Translate" -> AssistantMode.TRANSLATOR
            "Soundbites" -> AssistantMode.MEETING_NOTES
            "Timeline" -> AssistantMode.VISUAL_DIARY
            "DayNote" -> AssistantMode.AUTO_DIARY
            "Cron" -> AssistantMode.ERRAND_BRAIN
            "Automation" -> AssistantMode.LOCAL_AGENT
            else -> return
        }
        AndroidModeCommandExecutor(reactContext).execute(
            AssistantModeCommand(
                mode = mode,
                action = if (enabled) AssistantModeAction.START else AssistantModeAction.STOP,
            ),
        )
    }

    private fun dispatch(action: GlassesDashboardAction) {
        val activity = ADRuntimeRegistry.mainActivity() ?: return
        activity.runOnUiThread {
            runCatching {
                val method = MainActivity::class.java.declaredMethods.firstOrNull {
                    it.name == "handleDashboardAction" && it.parameterTypes.size == 1
                } ?: return@runCatching
                method.isAccessible = true
                method.invoke(activity, action)
            }
        }
    }

    private fun currentDashboardState(): GlassesDashboardUiState? {
        val activity = ADRuntimeRegistry.mainActivity() ?: return null
        return runCatching {
            val getter = MainActivity::class.java.declaredMethods.firstOrNull {
                it.name == "getDashboardState" && it.parameterTypes.isEmpty()
            } ?: return@runCatching null
            getter.isAccessible = true
            getter.invoke(activity) as? GlassesDashboardUiState
        }.getOrNull()
    }

    private fun startActivity(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        reactContext.startActivity(intent)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${reactContext.packageName}"),
            ),
        )
    }

    private fun ReadableMap.stringOrNull(key: String): String? =
        if (hasKey(key) && !isNull(key)) getString(key) else null
}
