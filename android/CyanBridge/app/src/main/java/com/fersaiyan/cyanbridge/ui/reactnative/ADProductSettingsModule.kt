package com.fersaiyan.cyanbridge.ui.reactnative

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.ai.router.CliRelayBackend
import com.fersaiyan.cyanbridge.localmodels.remote.RemoteOpenAiPrefs
import com.fersaiyan.cyanbridge.localmodels.storage.InstalledLocalModel
import com.fersaiyan.cyanbridge.localmodels.storage.LocalModelStorageRepository
import com.fersaiyan.cyanbridge.privacy.PrivacyPrefs
import com.fersaiyan.cyanbridge.ui.hasAccessibilityServicePermission
import com.fersaiyan.cyanbridge.ui.hasBluetooth
import com.fersaiyan.cyanbridge.ui.hasCameraPermission
import com.fersaiyan.cyanbridge.ui.localization.AppLanguage
import com.fersaiyan.cyanbridge.ui.localization.AppLanguagePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Product-facing preferences and permission state for React Native settings surfaces. */
class ADProductSettingsModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingImportPromise: Promise? = null

    private val activityListener = object : BaseActivityEventListener() {
        override fun onActivityResult(
            activity: Activity,
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
        ) {
            if (requestCode != REQUEST_IMPORT_MODEL) return
            val promise = pendingImportPromise ?: return
            pendingImportPromise = null
            val uri = data?.data
            if (resultCode != Activity.RESULT_OK || uri == null) {
                promise.resolve(null)
                return
            }
            scope.launch {
                runCatching {
                    val sourceName = resolveDisplayName(uri.toString())
                    val managedFile = LocalModelStorageRepository.copyUriToManagedModelFile(
                        context = reactContext,
                        uri = uri,
                        preferredName = sourceName,
                    )
                    LocalModelStorageRepository.registerImportedModel(
                        context = reactContext,
                        displayName = sourceName.substringBeforeLast('.').ifBlank { sourceName },
                        file = managedFile,
                    )
                }.onSuccess { model -> promise.resolve(modelMap(model, selected = true)) }
                    .onFailure { error ->
                        promise.reject("E_MODEL_IMPORT", error.message ?: "Could not import that model", error)
                    }
            }
        }
    }

    init {
        reactContext.addActivityEventListener(activityListener)
    }

    override fun getName(): String = "ADProductSettings"

    override fun invalidate() {
        pendingImportPromise?.resolve(null)
        pendingImportPromise = null
        scope.cancel()
        super.invalidate()
    }

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
            val selectedModelId = LocalModelStorageRepository.getSelectedModelId(reactContext)
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
                        pushMap(modelMap(model, selected = selectedModelId == model.id))
                    }
                })
            }
        }.onSuccess(promise::resolve)
            .onFailure { promise.reject("E_PRODUCT_SETTINGS", it.message ?: "Could not read settings", it) }
    }

    @ReactMethod
    fun importLocalModel(promise: Promise) {
        if (pendingImportPromise != null) {
            promise.reject("E_MODEL_IMPORT_BUSY", "A model import is already open")
            return
        }
        val activity = currentActivity
        if (activity == null) {
            promise.reject("E_MODEL_IMPORT_ACTIVITY", "The app is not ready to open a model file")
            return
        }
        pendingImportPromise = promise
        activity.startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
            REQUEST_IMPORT_MODEL,
        )
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

    private fun resolveDisplayName(uriString: String): String {
        val uri = android.net.Uri.parse(uriString)
        val queried = runCatching {
            reactContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "local-model.bin"
    }

    private fun modelMap(model: InstalledLocalModel, selected: Boolean) = Arguments.createMap().apply {
        putString("id", model.id)
        putString("name", model.displayName)
        putDouble("sizeBytes", model.sizeBytes.toDouble())
        putBoolean("selected", selected)
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            reactContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val REQUEST_IMPORT_MODEL = 47031
    }
}
