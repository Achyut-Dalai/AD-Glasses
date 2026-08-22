package com.ad_glasses.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ad_glasses.localmodels.catalog.LocalModelCatalogEntry
import com.ad_glasses.localmodels.catalog.LocalModelCatalogRepository
import com.ad_glasses.localmodels.device.DeviceCapabilityService
import com.ad_glasses.localmodels.device.DeviceSnapshot
import com.ad_glasses.localmodels.download.ModelDownloadForegroundService
import com.ad_glasses.localmodels.remote.RemoteOpenAiClient
import com.ad_glasses.localmodels.remote.RemoteOpenAiPrefs
import com.ad_glasses.localmodels.session.LocalChatSessionManager
import com.ad_glasses.localmodels.settings.LocalComputeBackend
import com.ad_glasses.localmodels.settings.LocalGenerationSettings
import com.ad_glasses.localmodels.settings.LocalModelPerformanceProfile
import com.ad_glasses.localmodels.settings.LocalModelRuntime
import com.ad_glasses.localmodels.settings.LocalModelRuntimeCompatibility
import com.ad_glasses.localmodels.settings.LocalModelSettingsRepository
import com.ad_glasses.localmodels.storage.InstalledLocalModel
import com.ad_glasses.localmodels.storage.LocalModelStorageRepository
import com.ad_glasses.localmodels.templates.PromptTemplateRegistry
import com.ad_glasses.plugins.PluginVoicePermissions
import com.ad_glasses.shared.localmodels.InstalledModelUiItem
import com.ad_glasses.shared.localmodels.LocalModelCatalogUiItem
import com.ad_glasses.shared.localmodels.LocalModelDownloadUiState
import com.ad_glasses.shared.localmodels.LocalModelGenerationUiState
import com.ad_glasses.shared.localmodels.LocalModelOptionField
import com.ad_glasses.shared.localmodels.LocalModelTextField
import com.ad_glasses.shared.localmodels.LocalModelToggleField
import com.ad_glasses.shared.localmodels.LocalModelsAction
import com.ad_glasses.shared.localmodels.LocalModelsConfigureUiState
import com.ad_glasses.shared.localmodels.LocalModelsSection
import com.ad_glasses.shared.localmodels.RemoteInferenceUiState
import com.ad_glasses.shared.localmodels.StudioBridgeUiState
import com.ad_glasses.ui.MyApplication
import com.ad_glasses.ui.debug.DebugLogSupport
import androidx.activity.compose.setContent
import com.ad_glasses.ui.adglasses.ADGlassesTheme
import com.ad_glasses.ui.adglasses.ADLocalModelsConfigureScreen
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local-model settings hosted entirely by Compose. Runtime/model/download work remains Android-owned,
 * but no hidden View tree is used as a state container.
 */
class LocalModelsConfigureActivity : AppCompatActivity() {
    private var uiState by mutableStateOf(LocalModelsConfigureUiState())
    private var installedModels: List<InstalledLocalModel> = emptyList()
    private var deviceSnapshot: DeviceSnapshot? = null
    private var downloadReceiver: BroadcastReceiver? = null
    private var warmupJob: Job? = null
    private var savedSnapshot: SettingsSnapshot? = null

    private val sectionPrefs by lazy { getSharedPreferences(SECTION_PREFS, MODE_PRIVATE) }

    private val importModelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importModel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = requestClose()
            },
        )
        refreshAllUi(markSaved = true)
        setContent {
            ADGlassesTheme {
                ADLocalModelsConfigureScreen(
                    state = uiState,
                    onAction = ::handleAction,
                )
            }
        }
        registerDownloadReceiver()
    }

    override fun onDestroy() {
        unregisterDownloadReceiver()
        warmupJob?.cancel()
        super.onDestroy()
    }

    private fun handleAction(action: LocalModelsAction) {
        when (action) {
            LocalModelsAction.Back -> requestClose()
            LocalModelsAction.DiscardChangesAndBack -> finish()
            LocalModelsAction.Refresh -> refreshAllUi(markSaved = true)
            LocalModelsAction.ImportModel -> importModelLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            is LocalModelsAction.SelectInstalledModel -> selectInstalledModel(action.id)
            LocalModelsAction.ShowSelectedModelInfo -> showSelectedModelInfo()
            LocalModelsAction.UnloadSelectedModel -> unloadSelectedModel()
            LocalModelsAction.RemoveSelectedModel -> confirmRemoveSelectedModel()
            is LocalModelsAction.DownloadCatalogModel -> LocalModelCatalogRepository.findById(action.id)?.let(::requestDownload)
            is LocalModelsAction.ShowCatalogModelInfo -> {
                val entry = LocalModelCatalogRepository.findById(action.id) ?: return
                showCatalogInfo(entry, installedModels.any { it.catalogId == entry.id })
            }
            LocalModelsAction.CancelDownload -> cancelDownload()
            LocalModelsAction.RunWarmup -> runWarmupProbe()
            LocalModelsAction.SaveGenerationSettings -> saveGenerationSettings()
            is LocalModelsAction.ToggleSection -> toggleSection(action.section)
            is LocalModelsAction.UpdateText -> updateText(action.field, action.value)
            is LocalModelsAction.SelectOption -> selectOption(action.field, action.index)
            is LocalModelsAction.SetToggle -> setToggle(action.field, action.enabled)
            LocalModelsAction.TestRemoteServer -> testRemoteServerConnection()
            LocalModelsAction.SaveRemoteServer -> saveRemoteServerConfig()
            LocalModelsAction.ShowStudioBridgeApiKeyHelp -> showApiKeyHelpDialog()
            LocalModelsAction.SaveStudioBridge -> saveStudioBridgeConfig()
        }
    }

    private fun refreshAllUi(markSaved: Boolean) {
        LocalModelStorageRepository.cleanupMissingModels(this)
        installedModels = LocalModelStorageRepository.listInstalled(this)
        if (installedModels.isNotEmpty() && selectedModel() == null) {
            LocalModelsPrefs.setSelectedModelId(this, installedModels.first().id)
        }
        deviceSnapshot = DeviceCapabilityService.snapshot(this)

        val downloadState = currentDownloadState()
        uiState = buildState(
            generation = generationForSelectedModel(),
            remote = remoteStateFromPrefs(),
            studio = studioStateFromPrefs(),
            download = downloadState,
            warmupResult = LocalModelsPrefs.getLastBenchmark(this),
        )
        if (markSaved) savedSnapshot = currentSettingsSnapshot()
        publishUnsavedState()
    }

    private fun buildState(
        generation: LocalModelGenerationUiState,
        remote: RemoteInferenceUiState,
        studio: StudioBridgeUiState,
        download: LocalModelDownloadUiState = uiState.download,
        warmupResult: String = uiState.warmupResult,
    ): LocalModelsConfigureUiState {
        val snapshot = deviceSnapshot ?: DeviceCapabilityService.snapshot(this).also { deviceSnapshot = it }
        val installedByCatalogId = installedModels.associateBy { it.catalogId }
        val selected = selectedModel()
        val ramGb = snapshot.totalRamBytes / GIB
        val freeGb = snapshot.freeStorageBytes / GIB
        return LocalModelsConfigureUiState(
            engineStatus = "Selected runtime: ${selectedRuntime(generation).label}",
            deviceSummary = "ABI: ${snapshot.primaryAbi} | RAM: ${format1(ramGb)} GB | Free storage: ${format2(freeGb)} GB",
            selectedModelStatus = selectedModelStatus(selected),
            emptyStateMessage = if (installedModels.isEmpty()) {
                "No local model installed yet. Start with a catalog download or import GGUF/LiteRT files (.gguf/.litertlm/.task)."
            } else {
                ""
            },
            installedModels = installedModels.map {
                InstalledModelUiItem(it.id, "${it.displayName} (${humanSize(it.sizeBytes)})")
            },
            selectedInstalledModelId = selected?.id,
            catalog = LocalModelCatalogRepository.curatedModels.map { entry ->
                val installed = installedByCatalogId[entry.id]
                LocalModelCatalogUiItem(
                    id = entry.id,
                    title = entry.displayName,
                    details = "${entry.quantization} · ${humanSize(entry.sizeBytes)} · tags: ${entry.tags.joinToString(", ")}",
                    status = statusText(entry, installed),
                    downloadLabel = downloadLabel(entry, installed),
                    canDownload = canDownloadCatalogEntry(entry, installed),
                )
            },
            catalogExpanded = isSectionExpanded(LocalModelsSection.CATALOG),
            remoteServerExpanded = isSectionExpanded(LocalModelsSection.REMOTE_SERVER),
            studioBridgeExpanded = isSectionExpanded(LocalModelsSection.STUDIO_BRIDGE),
            generationSettingsExpanded = isSectionExpanded(LocalModelsSection.GENERATION_SETTINGS),
            download = download,
            hasUnsavedChanges = false,
            warmupResult = warmupResult,
            generation = generation,
            remoteServer = remote,
            studioBridge = studio,
        )
    }

    private fun generationForSelectedModel(): LocalModelGenerationUiState {
        val selected = selectedModel()
        val settings = selected?.let { LocalModelSettingsRepository.getForModel(this, it.id) }
            ?: LocalGenerationSettings.defaultsFor(null, LocalModelPerformanceProfile.BALANCED)
        return generationUi(settings)
    }

    private fun generationUi(settings: LocalGenerationSettings): LocalModelGenerationUiState {
        val templates = buildList {
            add("Auto (catalog default)")
            addAll(PromptTemplateRegistry.templates.map { "${it.label} (${it.id})" })
        }
        val templateIndex = settings.templateOverrideId
            ?.let { id -> PromptTemplateRegistry.templates.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        val runtimeNote = runtimeNote(settings.modelRuntime)
        val computeNote = computeBackendNote(settings.computeBackend, settings.modelRuntime)
        return LocalModelGenerationUiState(
            profileOptions = LocalModelPerformanceProfile.entries.map { it.label },
            profileIndex = settings.profile.ordinal,
            runtimeOptions = LocalModelRuntime.entries.map { it.label },
            runtimeIndex = settings.modelRuntime.ordinal,
            runtimeNote = runtimeNote,
            computeBackendOptions = LocalComputeBackend.entries.map { it.label },
            computeBackendIndex = settings.computeBackend.ordinal,
            computeBackendNote = computeNote,
            cpuThreads = settings.cpuThreads.toString(),
            gpuLayers = settings.gpuLayers.toString(),
            gpuLayersEnabled = settings.computeBackend != LocalComputeBackend.CPU,
            temperature = settings.temperature.toString(),
            topP = settings.topP.toString(),
            topK = settings.topK.toString(),
            maxTokens = settings.maxTokens.toString(),
            repetitionPenalty = settings.repetitionPenalty.toString(),
            contextSize = settings.contextSize.toString(),
            seed = settings.seed.toString(),
            templateOptions = templates,
            templateIndex = templateIndex,
            experimentalStructuredJson = settings.experimentalStructuredJson,
            systemPrompt = settings.systemPromptOverride,
            huggingFaceToken = LocalModelsPrefs.getHuggingFaceToken(this),
        )
    }

    private fun remoteStateFromPrefs(statusOverride: String? = null): RemoteInferenceUiState {
        val enabled = RemoteOpenAiPrefs.isEnabled(this)
        return RemoteInferenceUiState(
            enabled = enabled,
            baseUrl = RemoteOpenAiPrefs.getBaseUrl(this),
            modelName = RemoteOpenAiPrefs.getModel(this),
            apiKey = RemoteOpenAiPrefs.getApiKey(this),
            status = statusOverride ?: if (enabled && RemoteOpenAiPrefs.isConfigured(this)) {
                "Active: ${RemoteOpenAiPrefs.getModel(this)} @ ${RemoteOpenAiPrefs.getBaseUrl(this)}"
            } else {
                ""
            },
        )
    }

    private fun studioStateFromPrefs(statusOverride: String? = null): StudioBridgeUiState {
        val enabled = RemoteOpenAiPrefs.isBridgeEnabled(this)
        val status = statusOverride ?: when {
            RemoteOpenAiPrefs.isBridgeConfigured(this) -> "Bridge configured for voice approvals."
            enabled -> "Bridge enabled but server URL, model, or API key is missing."
            else -> ""
        }
        return StudioBridgeUiState(
            enabled = enabled,
            apiKey = RemoteOpenAiPrefs.getApiKey(this),
            status = status,
        )
    }

    private fun currentDownloadState(): LocalModelDownloadUiState {
        if (!ModelDownloadForegroundService.isDownloading) {
            val result = ModelDownloadForegroundService.lastResult
            return if (result == null) {
                LocalModelDownloadUiState()
            } else {
                LocalModelDownloadUiState(
                    isInFlight = false,
                    message = when {
                        result.success -> "Download complete"
                        result.error == "cancelled" -> "Download cancelled"
                        else -> "Download failed: ${result.error ?: "unknown error"}"
                    },
                    progressPercent = if (result.success) 100 else null,
                )
            }
        }
        val pct = ModelDownloadForegroundService.lastPercent
        val downloaded = ModelDownloadForegroundService.lastDownloadedBytes ?: 0L
        val total = ModelDownloadForegroundService.lastTotalBytes ?: 0L
        val modelId = ModelDownloadForegroundService.downloadingModelId
        val name = LocalModelCatalogRepository.findById(modelId)?.displayName ?: modelId.orEmpty()
        val message = ModelDownloadForegroundService.lastStatusMessage ?: if (pct != null && pct > 0) {
            "Downloading $name: $pct% (${humanSize(downloaded)} / ${if (total > 0) humanSize(total) else "?"})"
        } else {
            "Downloading $name…"
        }
        return LocalModelDownloadUiState(true, message, pct?.takeIf { it > 0 })
    }

    private fun selectInstalledModel(id: String) {
        if (installedModels.none { it.id == id }) return
        LocalModelsPrefs.setSelectedModelId(this, id)
        val generation = generationForSelectedModel().copy(
            huggingFaceToken = uiState.generation.huggingFaceToken,
        )
        uiState = buildState(
            generation = generation,
            remote = uiState.remoteServer,
            studio = uiState.studioBridge,
        )
        savedSnapshot = (savedSnapshot ?: currentSettingsSnapshot()).copy(
            selectedModelId = id,
            generation = generationInput(generation),
        )
        publishUnsavedState()
    }

    private fun updateText(field: LocalModelTextField, value: String) {
        uiState = when (field) {
            LocalModelTextField.CPU_THREADS -> uiState.copy(generation = uiState.generation.copy(cpuThreads = value))
            LocalModelTextField.GPU_LAYERS -> uiState.copy(generation = uiState.generation.copy(gpuLayers = value))
            LocalModelTextField.TEMPERATURE -> uiState.copy(generation = uiState.generation.copy(temperature = value))
            LocalModelTextField.TOP_P -> uiState.copy(generation = uiState.generation.copy(topP = value))
            LocalModelTextField.TOP_K -> uiState.copy(generation = uiState.generation.copy(topK = value))
            LocalModelTextField.MAX_TOKENS -> uiState.copy(generation = uiState.generation.copy(maxTokens = value))
            LocalModelTextField.REPETITION_PENALTY -> uiState.copy(generation = uiState.generation.copy(repetitionPenalty = value))
            LocalModelTextField.CONTEXT_SIZE -> uiState.copy(generation = uiState.generation.copy(contextSize = value))
            LocalModelTextField.SEED -> uiState.copy(generation = uiState.generation.copy(seed = value))
            LocalModelTextField.SYSTEM_PROMPT -> uiState.copy(generation = uiState.generation.copy(systemPrompt = value))
            LocalModelTextField.HUGGING_FACE_TOKEN -> uiState.copy(generation = uiState.generation.copy(huggingFaceToken = value))
            LocalModelTextField.REMOTE_BASE_URL -> uiState.copy(remoteServer = uiState.remoteServer.copy(baseUrl = value))
            LocalModelTextField.REMOTE_MODEL_NAME -> uiState.copy(remoteServer = uiState.remoteServer.copy(modelName = value))
            LocalModelTextField.REMOTE_API_KEY -> uiState.copy(remoteServer = uiState.remoteServer.copy(apiKey = value))
            LocalModelTextField.STUDIO_BRIDGE_API_KEY -> uiState.copy(studioBridge = uiState.studioBridge.copy(apiKey = value))
        }
        publishUnsavedState()
    }

    private fun selectOption(field: LocalModelOptionField, requestedIndex: Int) {
        var generation = uiState.generation
        when (field) {
            LocalModelOptionField.PROFILE -> {
                val profile = LocalModelPerformanceProfile.entries.getOrNull(requestedIndex) ?: return
                val selected = selectedModel()
                val catalog = selected?.catalogId?.let(LocalModelCatalogRepository::findById)
                val defaults = LocalGenerationSettings.defaultsFor(catalog, profile)
                val compatibleDefaults = selected?.let { model ->
                    defaults.copy(
                        modelRuntime = LocalModelRuntimeCompatibility.enforce(model.format, defaults.modelRuntime),
                    )
                } ?: defaults
                generation = generationUi(
                    compatibleDefaults.copy(
                        computeBackend = selectedBackend(generation),
                        cpuThreads = parseCpuThreads(generation.cpuThreads, compatibleDefaults.cpuThreads),
                        gpuLayers = parseGpuLayers(generation.gpuLayers, compatibleDefaults.gpuLayers),
                    ),
                ).copy(huggingFaceToken = generation.huggingFaceToken)
            }
            LocalModelOptionField.RUNTIME -> {
                val runtime = LocalModelRuntime.entries.getOrNull(requestedIndex) ?: return
                val model = selectedModel()
                if (model != null && !LocalModelRuntimeCompatibility.isCompatible(model.format, runtime)) {
                    val required = LocalModelRuntimeCompatibility.requiredRuntime(model.format)
                    Toast.makeText(
                        this,
                        required?.let { "${model.fileName} requires the ${it.label} runtime" }
                            ?: "The model format could not be verified. Re-import the model.",
                        Toast.LENGTH_LONG,
                    ).show()
                    return
                }
                generation = generation.copy(
                    runtimeIndex = requestedIndex,
                    runtimeNote = runtimeNote(runtime),
                    computeBackendNote = computeBackendNote(selectedBackend(generation), runtime),
                )
            }
            LocalModelOptionField.COMPUTE_BACKEND -> {
                val backend = LocalComputeBackend.entries.getOrNull(requestedIndex) ?: return
                generation = generation.copy(
                    computeBackendIndex = requestedIndex,
                    gpuLayersEnabled = backend != LocalComputeBackend.CPU,
                    computeBackendNote = computeBackendNote(backend, selectedRuntime(generation)),
                )
            }
            LocalModelOptionField.TEMPLATE -> {
                val max = generation.templateOptions.lastIndex.coerceAtLeast(0)
                generation = generation.copy(templateIndex = requestedIndex.coerceIn(0, max))
            }
        }
        uiState = uiState.copy(
            generation = generation,
            engineStatus = "Selected runtime: ${selectedRuntime(generation).label}",
        )
        publishUnsavedState()
    }

    private fun setToggle(field: LocalModelToggleField, enabled: Boolean) {
        uiState = when (field) {
            LocalModelToggleField.EXPERIMENTAL_STRUCTURED_JSON ->
                uiState.copy(generation = uiState.generation.copy(experimentalStructuredJson = enabled))
            LocalModelToggleField.REMOTE_SERVER_ENABLED ->
                uiState.copy(remoteServer = uiState.remoteServer.copy(enabled = enabled))
            LocalModelToggleField.STUDIO_BRIDGE_ENABLED ->
                uiState.copy(studioBridge = uiState.studioBridge.copy(enabled = enabled))
        }
        publishUnsavedState()
    }

    private fun toggleSection(section: LocalModelsSection) {
        val key = sectionKey(section)
        val next = !sectionPrefs.getBoolean(key, false)
        sectionPrefs.edit().putBoolean(key, next).apply()
        uiState = when (section) {
            LocalModelsSection.CATALOG -> uiState.copy(catalogExpanded = next)
            LocalModelsSection.REMOTE_SERVER -> uiState.copy(remoteServerExpanded = next)
            LocalModelsSection.STUDIO_BRIDGE -> uiState.copy(studioBridgeExpanded = next)
            LocalModelsSection.GENERATION_SETTINGS -> uiState.copy(generationSettingsExpanded = next)
        }
    }

    private fun isSectionExpanded(section: LocalModelsSection): Boolean =
        sectionPrefs.getBoolean(sectionKey(section), false)

    private fun sectionKey(section: LocalModelsSection): String = "section_expanded_${section.name.lowercase()}"

    private fun saveGenerationSettings() {
        val generation = uiState.generation
        LocalModelsPrefs.setHuggingFaceToken(this, generation.huggingFaceToken)
        val model = selectedModel()
        if (model == null) {
            Toast.makeText(this, "Saved token. Install a model to save generation settings.", Toast.LENGTH_SHORT).show()
            markGenerationSaved()
            refreshCatalogOnly()
            return
        }

        val existing = LocalModelSettingsRepository.getForModel(this, model.id)
        val requestedRuntime = selectedRuntime(generation)
        if (!LocalModelRuntimeCompatibility.isCompatible(model.format, requestedRuntime)) {
            val required = LocalModelRuntimeCompatibility.requiredRuntime(model.format)
            Toast.makeText(
                this,
                required?.let { "${model.fileName} can only use the ${it.label} runtime" }
                    ?: "The model format could not be verified. Re-import the model.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val settings = LocalGenerationSettings(
            profile = LocalModelPerformanceProfile.entries.getOrElse(generation.profileIndex) { existing.profile },
            temperature = generation.temperature.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: existing.temperature,
            topP = generation.topP.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: existing.topP,
            topK = generation.topK.toIntOrNull()?.coerceIn(0, 200) ?: existing.topK,
            maxTokens = parseBoundedInt(generation.maxTokens, LocalGenerationSettings.MIN_MAX_TOKENS, LocalGenerationSettings.MAX_MAX_TOKENS)
                ?: existing.maxTokens,
            repetitionPenalty = generation.repetitionPenalty.toDoubleOrNull()?.coerceIn(0.8, 2.0)
                ?: existing.repetitionPenalty,
            contextSize = parseBoundedInt(generation.contextSize, LocalGenerationSettings.MIN_CONTEXT_SIZE, LocalGenerationSettings.MAX_CONTEXT_SIZE)
                ?: existing.contextSize,
            seed = generation.seed.toIntOrNull() ?: existing.seed,
            systemPromptOverride = generation.systemPrompt.trim(),
            templateOverrideId = selectedTemplateId(generation),
            experimentalStructuredJson = generation.experimentalStructuredJson,
            modelRuntime = requestedRuntime,
            computeBackend = selectedBackend(generation),
            cpuThreads = parseCpuThreads(generation.cpuThreads, existing.cpuThreads),
            gpuLayers = parseGpuLayers(generation.gpuLayers, existing.gpuLayers),
        )
        LocalModelSettingsRepository.saveForModel(this, model.id, settings)
        uiState = uiState.copy(
            generation = generationUi(settings).copy(huggingFaceToken = generation.huggingFaceToken),
            engineStatus = "Selected runtime: ${settings.modelRuntime.label}",
        )
        setResult(RESULT_OK)
        Toast.makeText(this, "Saved: max output ${settings.maxTokens}, context ${settings.contextSize}", Toast.LENGTH_SHORT).show()
        markGenerationSaved()
        refreshCatalogOnly()
    }

    private fun saveRemoteServerConfig(showToast: Boolean = true): Boolean {
        val remote = uiState.remoteServer
        val url = remote.baseUrl.trim()
        val model = remote.modelName.trim()
        val apiKey = remote.apiKey.trim()
        if (remote.enabled && url.isBlank()) {
            Toast.makeText(this, "Base URL is required when remote server is enabled", Toast.LENGTH_SHORT).show()
            return false
        }
        if (remote.enabled && model.isBlank()) {
            Toast.makeText(this, "Model name is required when remote server is enabled", Toast.LENGTH_SHORT).show()
            return false
        }
        if (apiKey.isNotBlank() && !RemoteOpenAiPrefs.isCredentialTransportAllowed(url)) {
            Toast.makeText(this, "API keys require HTTPS, a private LAN address, or a Tailscale IP", Toast.LENGTH_LONG).show()
            return false
        }

        RemoteOpenAiPrefs.setBaseUrl(this, url)
        RemoteOpenAiPrefs.setModel(this, model)
        RemoteOpenAiPrefs.setApiKey(this, apiKey)
        RemoteOpenAiPrefs.setEnabled(this, remote.enabled)
        var studio = uiState.studioBridge.copy(apiKey = apiKey)
        val status = if (remote.enabled) "Active: $model @ $url" else ""
        if (RemoteOpenAiPrefs.isBridgeEnabled(this)) {
            val restarted = (application as? MyApplication)?.startStudioBridge() == true
            studio = studio.copy(
                status = if (restarted) {
                    "Bridge reconnecting with the updated server settings."
                } else {
                    "Bridge needs a model and microphone access before it can reconnect."
                },
            )
        }
        uiState = uiState.copy(
            remoteServer = remote.copy(baseUrl = url, modelName = model, apiKey = apiKey, status = status),
            studioBridge = studio,
        )
        setResult(RESULT_OK)
        if (showToast) {
            Toast.makeText(
                this,
                if (remote.enabled) "Remote server saved: $model @ $url" else "Remote server config saved (disabled)",
                Toast.LENGTH_SHORT,
            ).show()
        }
        markRemoteSaved(apiKey)
        return true
    }

    private fun testRemoteServerConnection() {
        if (!saveRemoteServerConfig(showToast = false)) return
        if (uiState.remoteServer.baseUrl.isBlank()) {
            uiState = uiState.copy(remoteServer = uiState.remoteServer.copy(status = "Enter a base URL first"))
            return
        }
        uiState = uiState.copy(remoteServer = uiState.remoteServer.copy(status = "Testing connection…"))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { RemoteOpenAiClient.healthCheck(this@LocalModelsConfigureActivity) }
            }
            uiState = uiState.copy(
                remoteServer = uiState.remoteServer.copy(
                    status = result.fold(
                        onSuccess = { "Connection: $it" },
                        onFailure = { "Connection failed: ${it.message}" },
                    ),
                ),
            )
            result.onSuccess {
                Toast.makeText(this@LocalModelsConfigureActivity, "Server reachable", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@LocalModelsConfigureActivity, "Connection failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveStudioBridgeConfig() {
        var studio = uiState.studioBridge
        val enabled = studio.enabled
        val apiKey = studio.apiKey.trim()
        if (enabled) {
            val baseUrl = RemoteOpenAiPrefs.getBaseUrl(this)
            when {
                baseUrl.isBlank() -> {
                    Toast.makeText(this, "Configure the Remote Server base URL first", Toast.LENGTH_LONG).show()
                    uiState = uiState.copy(studioBridge = studio.copy(enabled = false))
                    publishUnsavedState()
                    return
                }
                apiKey.isBlank() -> {
                    Toast.makeText(this, "API key is required for the bridge", Toast.LENGTH_SHORT).show()
                    return
                }
                RemoteOpenAiPrefs.getModel(this).isBlank() -> {
                    Toast.makeText(this, "Configure a Remote Server model for response classification", Toast.LENGTH_LONG).show()
                    return
                }
                !RemoteOpenAiPrefs.isCredentialTransportAllowed(baseUrl) -> {
                    Toast.makeText(this, "API keys require HTTPS, a private LAN address, or a Tailscale IP", Toast.LENGTH_LONG).show()
                    return
                }
                !SpeechRecognizer.isRecognitionAvailable(this) -> {
                    Toast.makeText(this, "No speech recognizer is available on this device", Toast.LENGTH_LONG).show()
                    uiState = uiState.copy(studioBridge = studio.copy(enabled = false))
                    publishUnsavedState()
                    return
                }
                !PluginVoicePermissions.hasRequiredPermissions(this) -> {
                    PluginVoicePermissions.ensure(this) { saveStudioBridgeConfig() }
                    return
                }
            }
            RemoteOpenAiPrefs.setApiKey(this, apiKey)
            uiState = uiState.copy(remoteServer = uiState.remoteServer.copy(apiKey = apiKey))
        }

        RemoteOpenAiPrefs.setBridgeEnabled(this, enabled)
        val app = application as? MyApplication
        studio = if (enabled) {
            if (app?.startStudioBridge() != true) {
                studio.copy(status = "Bridge could not start. Check model and microphone access.")
            } else {
                Toast.makeText(this, "Studio Bridge enabled. Connecting…", Toast.LENGTH_SHORT).show()
                studio.copy(apiKey = apiKey, status = "Bridge connecting…")
            }
        } else {
            app?.stopStudioBridge()
            Toast.makeText(this, "Studio Bridge disabled", Toast.LENGTH_SHORT).show()
            studio.copy(status = "")
        }
        uiState = uiState.copy(studioBridge = studio)
        markStudioSaved(saveApiKey = enabled)
    }

    private fun requestDownload(entry: LocalModelCatalogEntry) {
        if (uiState.download.isInFlight || ModelDownloadForegroundService.isDownloading) {
            Toast.makeText(this, "A model download is already in progress", Toast.LENGTH_SHORT).show()
            return
        }
        if (entry.sourceUrl.isNullOrBlank()) {
            Toast.makeText(this, "No direct source URL for this entry. Use manual import.", Toast.LENGTH_LONG).show()
            return
        }
        val hfToken = LocalModelsPrefs.getHuggingFaceToken(this).trim().ifBlank { null }
        if (entry.gatedDownload && hfToken == null) {
            Toast.makeText(this, "This model is gated. Save a Hugging Face token after accepting model terms.", Toast.LENGTH_LONG).show()
            return
        }
        val assessment = DeviceCapabilityService.assess(
            snapshot = DeviceCapabilityService.snapshot(this),
            entry = entry,
            requireDownloadHeadroom = true,
        )
        if (!assessment.supported) {
            Toast.makeText(this, assessment.blockers.joinToString(" "), Toast.LENGTH_LONG).show()
            return
        }
        val start = {
            uiState = uiState.copy(
                download = LocalModelDownloadUiState(true, "Starting download: ${entry.displayName}", null),
            )
            refreshCatalogOnly()
            ModelDownloadForegroundService.startDownload(this, entry.id, hfToken)
        }
        if (assessment.warnings.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Device warning")
                .setMessage(assessment.warnings.joinToString("\n"))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ -> start() }
                .show()
        } else {
            start()
        }
    }

    private fun cancelDownload() {
        ModelDownloadForegroundService.cancelDownload(this)
        uiState = uiState.copy(download = LocalModelDownloadUiState(message = "Download cancelled"))
        refreshCatalogOnly()
    }

    private fun registerDownloadReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ModelDownloadForegroundService.BROADCAST_PROGRESS -> {
                        val pct = intent.getIntExtra(ModelDownloadForegroundService.EXTRA_PERCENT, 0)
                        val downloaded = intent.getLongExtra(ModelDownloadForegroundService.EXTRA_DOWNLOADED_BYTES, 0L)
                        val total = intent.getLongExtra(ModelDownloadForegroundService.EXTRA_TOTAL_BYTES, 0L)
                        val statusMessage = intent.getStringExtra(ModelDownloadForegroundService.EXTRA_STATUS_MESSAGE)
                        val message = statusMessage ?: "Downloading: $pct% (${humanSize(downloaded)} / ${if (total > 0L) humanSize(total) else "?"})"
                        uiState = uiState.copy(download = LocalModelDownloadUiState(true, message, pct.takeIf { it > 0 }))
                    }
                    ModelDownloadForegroundService.BROADCAST_DOWNLOAD_FINISHED -> {
                        val success = intent.getBooleanExtra(ModelDownloadForegroundService.EXTRA_SUCCESS, false)
                        val error = intent.getStringExtra(ModelDownloadForegroundService.EXTRA_ERROR)
                        if (success) {
                            refreshAllUi(markSaved = false)
                            uiState = uiState.copy(download = LocalModelDownloadUiState(false, "Download complete", 100))
                        } else {
                            val message = if (error == "cancelled") "Download cancelled" else "Download failed: ${error ?: "unknown error"}"
                            uiState = uiState.copy(download = LocalModelDownloadUiState(false, message, null))
                            if (error != "cancelled") Toast.makeText(this@LocalModelsConfigureActivity, message, Toast.LENGTH_LONG).show()
                            refreshCatalogOnly()
                        }
                    }
                }
            }
        }
        downloadReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(ModelDownloadForegroundService.BROADCAST_DOWNLOAD_FINISHED)
            addAction(ModelDownloadForegroundService.BROADCAST_PROGRESS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun unregisterDownloadReceiver() {
        downloadReceiver?.let { runCatching { unregisterReceiver(it) } }
        downloadReceiver = null
    }

    private fun importModel(uri: Uri) {
        uiState = uiState.copy(download = LocalModelDownloadUiState(message = "Importing model…"))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    LocalModelStorageRepository.importModelFromUri(
                        context = this@LocalModelsConfigureActivity,
                        uri = uri,
                    )
                }
            }
            result.onSuccess { model ->
                refreshAllUi(markSaved = false)
                uiState = uiState.copy(download = LocalModelDownloadUiState(message = "Import complete: ${model.displayName}"))
                setResult(RESULT_OK)
                Toast.makeText(this@LocalModelsConfigureActivity, "Imported ${model.displayName}", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                uiState = uiState.copy(download = LocalModelDownloadUiState(message = "Import failed: ${error.message}"))
                Toast.makeText(this@LocalModelsConfigureActivity, error.message ?: "Import failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun unloadSelectedModel() {
        lifecycleScope.launch {
            runCatching { LocalChatSessionManager.unload() }
            Toast.makeText(this@LocalModelsConfigureActivity, "Local model unloaded", Toast.LENGTH_SHORT).show()
            refreshSelectedStatusOnly()
        }
    }

    private fun confirmRemoveSelectedModel() {
        val selected = selectedModel()
        if (selected == null) {
            Toast.makeText(this, "No model selected", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Remove model?")
            .setMessage("Delete ${selected.displayName} from local storage?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    val result = runCatching {
                        LocalChatSessionManager.removeInstalledModel(
                            context = this@LocalModelsConfigureActivity,
                            modelId = selected.id,
                        )
                    }
                    result.fold(
                        onSuccess = { removed ->
                            if (removed) {
                                Toast.makeText(
                                    this@LocalModelsConfigureActivity,
                                    "Removed ${selected.displayName}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@LocalModelsConfigureActivity,
                                    "Model was already removed",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                this@LocalModelsConfigureActivity,
                                error.message ?: "Could not remove the model",
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                    refreshAllUi(markSaved = true)
                }
            }
            .show()
    }

    private fun runWarmupProbe() {
        if (warmupJob?.isActive == true) return
        val model = selectedModel()
        if (model == null) {
            Toast.makeText(this, "Install or select a model first", Toast.LENGTH_SHORT).show()
            return
        }
        val settings = LocalModelSettingsRepository.getForModel(this, model.id)
        val entry = LocalModelCatalogRepository.findById(model.catalogId)
        uiState = uiState.copy(warmupResult = "Running warm-up…")
        warmupJob = lifecycleScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val loadDetails = LocalChatSessionManager.ensureModelLoaded(
                        context = this@LocalModelsConfigureActivity,
                        model = model,
                        catalogEntry = entry,
                        settings = settings,
                    )
                    val warmup = LocalChatSessionManager.runWarmupProbe(settings = settings, onToken = {})
                    loadDetails to warmup
                }
            }
            if (isFinishing || isDestroyed) return@launch
            outcome.fold(
                onSuccess = { (loadDetails, result) ->
                    val genTps = (result.generatedTokens * 1000.0 / result.elapsedMs).coerceAtLeast(0.1)
                    val totalTps = (result.totalTokens * 1000.0 / result.elapsedMs).coerceAtLeast(0.1)
                    val accelerated = result.backend != LocalComputeBackend.CPU
                    val backend = when (result.backend) {
                        LocalComputeBackend.NPU_EXPERIMENTAL -> "NPU"
                        LocalComputeBackend.GPU -> "GPU"
                        LocalComputeBackend.CPU -> "CPU"
                    }
                    val layers = if (accelerated) {
                        val value = if (loadDetails.activeGpuLayers == -1) "auto(-1)" else loadDetails.activeGpuLayers.toString()
                        ", n_gpu_layers=$value"
                    } else ""
                    val fallback = if (!loadDetails.fallbackReason.isNullOrBlank() && !accelerated) " | fallback: CPU" else ""
                    val message = "Warm-up complete: ${format2(genTps)} gen tok/s, ${format2(totalTps)} total tok/s, ${result.elapsedMs}ms, backend=$backend$layers$fallback"
                    Log.i(TAG, message)
                    LocalModelsPrefs.setLastBenchmark(this@LocalModelsConfigureActivity, message)
                    uiState = uiState.copy(warmupResult = message)
                    if (!loadDetails.fallbackReason.isNullOrBlank() && !accelerated) {
                        Toast.makeText(this@LocalModelsConfigureActivity, loadDetails.fallbackReason, Toast.LENGTH_LONG).show()
                    }
                },
                onFailure = { error ->
                    val message = if (error is CancellationException) {
                        "Warm-up cancelled"
                    } else {
                        Log.e(TAG, "Warm-up failed", error)
                        "Warm-up failed: ${error.message ?: "unknown error"}"
                    }
                    uiState = uiState.copy(warmupResult = message)
                    if (error !is CancellationException && shouldOfferDebugLogs(error)) {
                        DebugLogSupport.showSupportOptionsDialog(
                            activity = this@LocalModelsConfigureActivity,
                            title = "Local runtime issue",
                            issueType = "Local runtime issue",
                            description = "The local model warm-up failed. This can help diagnose LiteRT, Vulkan, or GPU initialization issues.",
                            extraInfo = linkedMapOf(
                                "screen" to "local_models_configure",
                                "selected_runtime" to selectedRuntime(uiState.generation).name,
                                "selected_backend" to selectedBackend(uiState.generation).name,
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun shouldOfferDebugLogs(error: Throwable): Boolean =
        selectedRuntime(uiState.generation) == LocalModelRuntime.LITERT ||
            selectedBackend(uiState.generation) != LocalComputeBackend.CPU ||
            DebugLogSupport.isLocalRuntimeIssue(error.message, error)

    private fun showSelectedModelInfo() {
        val selected = selectedModel()
        if (selected == null) {
            Toast.makeText(this, "No model selected", Toast.LENGTH_SHORT).show()
            return
        }
        val exists = File(selected.absolutePath).exists()
        AlertDialog.Builder(this)
            .setTitle(selected.displayName)
            .setMessage(
                buildString {
                    appendLine("Family: ${LocalModelCatalogRepository.findById(selected.catalogId)?.family ?: "custom"}")
                    appendLine("Quantization: ${selected.quantization ?: "unknown"}")
                    appendLine("Size: ${humanSize(selected.sizeBytes)}")
                    appendLine("Template: ${selected.promptTemplateId ?: "auto"}")
                    appendLine("Location: ${selected.absolutePath}")
                    appendLine("Status: ${if (exists) "ready" else "failed (missing file)"}")
                    appendLine("SHA-256: ${selected.sha256 ?: "n/a"}")
                    appendLine("License: ${selected.licenseTermsNote ?: "Check source model card"}")
                },
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showCatalogInfo(entry: LocalModelCatalogEntry, installed: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(entry.displayName)
            .setMessage(
                buildString {
                    appendLine("Family: ${entry.family}")
                    appendLine("Runtime: ${entry.engine}")
                    appendLine("Format: ${entry.format}")
                    appendLine("Quantization: ${entry.quantization}")
                    appendLine("File size: ${humanSize(entry.sizeBytes)}")
                    appendLine("Prompt template: ${entry.promptTemplateId}")
                    appendLine("RAM tier: ${entry.minRamGb} GB+")
                    appendLine("Storage tier: ${entry.minStorageGb} GB+")
                    appendLine("Source: ${entry.sourcePageUrl ?: entry.sourceUrl ?: "manual import"}")
                    appendLine("Status: ${if (installed) "ready" else statusText(entry, null)}")
                    appendLine("License/terms: ${entry.licenseTermsNote}")
                },
            )
            .setNegativeButton("Close", null)
            .setPositiveButton("Open Source") { _, _ ->
                val url = entry.sourcePageUrl ?: entry.sourceUrl ?: return@setPositiveButton
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            .show()
    }

    private fun showApiKeyHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("API Key for Studio Bridge")
            .setMessage(
                "The Studio Bridge uses the same API key as your Remote Server configuration.\n\n" +
                    "Create or copy the API key from your desktop model server, then paste it here. " +
                    "For Tailnet/LAN use, configure the matching OpenAI-compatible base URL in the Remote Server section first.",
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun requestClose() {
        if (!hasUnsavedSettings()) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("Your local-model settings have changed. Leave without saving?")
            .setNegativeButton("Keep editing", null)
            .setPositiveButton("Discard") { _, _ -> finish() }
            .show()
    }

    private fun refreshCatalogOnly() {
        val installedByCatalogId = installedModels.associateBy { it.catalogId }
        uiState = uiState.copy(
            catalog = LocalModelCatalogRepository.curatedModels.map { entry ->
                val installed = installedByCatalogId[entry.id]
                LocalModelCatalogUiItem(
                    id = entry.id,
                    title = entry.displayName,
                    details = "${entry.quantization} · ${humanSize(entry.sizeBytes)} · tags: ${entry.tags.joinToString(", ")}",
                    status = statusText(entry, installed),
                    downloadLabel = downloadLabel(entry, installed),
                    canDownload = canDownloadCatalogEntry(entry, installed),
                )
            },
        )
    }

    private fun refreshSelectedStatusOnly() {
        uiState = uiState.copy(selectedModelStatus = selectedModelStatus(selectedModel()))
    }

    private fun selectedModel(): InstalledLocalModel? {
        val id = LocalModelsPrefs.getSelectedModelId(this)
        return installedModels.firstOrNull { it.id == id }
    }

    private fun selectedModelStatus(model: InstalledLocalModel?): String {
        if (model == null) return "Status: not downloaded"
        return "Status: ${if (File(model.absolutePath).exists()) "ready" else "failed (missing file)"} | ${model.displayName}"
    }

    private fun statusText(entry: LocalModelCatalogEntry, installed: InstalledLocalModel?): String {
        if (installed != null) return "Status: ready"
        val assessment = assess(entry)
        val ramStatus = if (assessment.ramSuitable) {
            "RAM suitable: ${format1(assessment.ramGb)} GB (model minimum ${format1(entry.minRamGb)} GB)"
        } else {
            "RAM unsuitable: device has ${format1(assessment.ramGb)} GB, model needs at least ${format1(entry.minRamGb)} GB"
        }
        val deviceStatus = if (assessment.supported) {
            "Device suitable: $ramStatus"
        } else {
            val other = assessment.blockers.filterNot { it.startsWith("RAM unsuitable:") }.joinToString(" ")
            "Device not suitable: $ramStatus${if (other.isBlank()) "." else ". $other"}"
        }
        val warnings = assessment.warnings.takeIf { it.isNotEmpty() }?.joinToString(" ")?.let { " Warning: $it" }.orEmpty()
        val availability = when {
            entry.comingSoon -> "Status: coming soon (Snapdragon NPU AOT build in progress)"
            entry.sourceUrl.isNullOrBlank() -> "Status: manual import recommended"
            entry.gatedDownload -> "Status: downloadable (requires token + accepted terms)"
            else -> "Status: not downloaded"
        }
        return "$deviceStatus.$warnings\n$availability"
    }

    private fun downloadLabel(entry: LocalModelCatalogEntry, installed: InstalledLocalModel?): String = when {
        uiState.download.isInFlight && ModelDownloadForegroundService.downloadingModelId == entry.id -> "Downloading..."
        installed != null -> "Installed"
        entry.sourceUrl.isNullOrBlank() -> "Manual import"
        !assess(entry).supported -> "Unavailable on device"
        entry.gatedDownload -> "Download (token)"
        else -> "Download"
    }

    private fun canDownloadCatalogEntry(entry: LocalModelCatalogEntry, installed: InstalledLocalModel?): Boolean {
        if (uiState.download.isInFlight || ModelDownloadForegroundService.isDownloading || installed != null ||
            !entry.enabled || entry.comingSoon || entry.sourceUrl.isNullOrBlank()
        ) return false
        if (entry.gatedDownload && LocalModelsPrefs.getHuggingFaceToken(this).isBlank()) return false
        return assess(entry).supported
    }

    private fun assess(entry: LocalModelCatalogEntry) = DeviceCapabilityService.assess(
        snapshot = deviceSnapshot ?: DeviceCapabilityService.snapshot(this).also { deviceSnapshot = it },
        entry = entry,
        requireDownloadHeadroom = !entry.sourceUrl.isNullOrBlank(),
    )

    private fun runtimeNote(runtime: LocalModelRuntime): String = when (runtime) {
        LocalModelRuntime.LLAMA_CPP -> "Use llama.cpp for GGUF models."
        LocalModelRuntime.LITERT -> "Use LiteRT for Google LiteRT-LM packages (.litertlm/.task)."
        LocalModelRuntime.REMOTE_OPENAI -> "Use a remote server on your LAN or Tailnet. Configure it in the Remote Server section above."
    }

    private fun computeBackendNote(backend: LocalComputeBackend, runtime: LocalModelRuntime): String {
        val base = when (backend) {
            LocalComputeBackend.NPU_EXPERIMENTAL ->
                "NPU (Experimental) delegate uses Snapdragon NPU or NNAPI hardware acceleration where available."
            LocalComputeBackend.GPU -> if (runtime == LocalModelRuntime.LITERT) {
                "LiteRT GPU backend uses Adreno GPU hardware acceleration. If GPU init fails, AD Glasses falls back to CPU."
            } else {
                "GPU backend offloads model layers to GPU. Use -1 for auto layer offload. If GPU init fails, the app falls back to CPU."
            }
            LocalComputeBackend.CPU -> if (runtime == LocalModelRuntime.LITERT) {
                "LiteRT CPU mode is safest for first runs. Move to GPU or NPU after a successful warm-up."
            } else {
                "CPU mode is the most compatible option. Increase CPU threads for speed if your device remains responsive."
            }
        }
        val selected = selectedModel()
        val catalog = selected?.catalogId?.let(LocalModelCatalogRepository::findById)
        val npuWarning = if (backend == LocalComputeBackend.NPU_EXPERIMENTAL && catalog?.npuSupported != true) {
            "\n⚠️ Standard GGUF / LiteRT packages are not NPU-compiled. CPU or GPU is recommended unless using an NPU-compiled package."
        } else ""
        return base + npuWarning
    }

    private fun selectedRuntime(generation: LocalModelGenerationUiState): LocalModelRuntime =
        LocalModelRuntime.entries.getOrElse(generation.runtimeIndex) { LocalModelRuntime.LLAMA_CPP }

    private fun selectedBackend(generation: LocalModelGenerationUiState): LocalComputeBackend =
        LocalComputeBackend.entries.getOrElse(generation.computeBackendIndex) { LocalComputeBackend.CPU }

    private fun selectedTemplateId(generation: LocalModelGenerationUiState): String? {
        if (generation.templateIndex <= 0) return null
        return PromptTemplateRegistry.templates.getOrNull(generation.templateIndex - 1)?.id
    }

    private fun parseCpuThreads(raw: String, fallback: Int): Int =
        raw.trim().toIntOrNull()?.coerceIn(1, 16) ?: fallback.coerceIn(1, 16)

    private fun parseGpuLayers(raw: String, fallback: Int): Int =
        raw.trim().toIntOrNull()?.coerceIn(-1, 999) ?: fallback.coerceIn(-1, 999)

    private fun parseBoundedInt(raw: String, min: Int, max: Int): Int? {
        val cleaned = raw.trim().replace(",", "").replace("_", "").replace(" ", "")
        if (cleaned.isBlank()) return null
        return cleaned.toIntOrNull()?.coerceIn(min, max)
    }

    private fun currentSettingsSnapshot(): SettingsSnapshot = SettingsSnapshot(
        selectedModelId = selectedModel()?.id,
        generation = generationInput(uiState.generation),
        huggingFaceToken = uiState.generation.huggingFaceToken,
        remote = RemoteInput(
            uiState.remoteServer.enabled,
            uiState.remoteServer.baseUrl,
            uiState.remoteServer.modelName,
            uiState.remoteServer.apiKey,
        ),
        studio = StudioInput(uiState.studioBridge.enabled, uiState.studioBridge.apiKey),
    )

    private fun generationInput(g: LocalModelGenerationUiState) = GenerationInput(
        g.profileIndex,
        g.runtimeIndex,
        g.computeBackendIndex,
        g.cpuThreads,
        g.gpuLayers,
        g.temperature,
        g.topP,
        g.topK,
        g.maxTokens,
        g.repetitionPenalty,
        g.contextSize,
        g.seed,
        g.templateIndex,
        g.experimentalStructuredJson,
        g.systemPrompt,
    )

    private fun hasUnsavedSettings(): Boolean = savedSnapshot?.let { it != currentSettingsSnapshot() } == true

    private fun publishUnsavedState() {
        uiState = uiState.copy(hasUnsavedChanges = hasUnsavedSettings())
    }

    private fun markGenerationSaved() {
        val current = currentSettingsSnapshot()
        val saved = savedSnapshot ?: current
        savedSnapshot = saved.copy(
            selectedModelId = current.selectedModelId,
            generation = current.generation,
            huggingFaceToken = current.huggingFaceToken,
        )
        publishUnsavedState()
    }

    private fun markRemoteSaved(sharedApiKey: String) {
        val current = currentSettingsSnapshot()
        val saved = savedSnapshot ?: current
        savedSnapshot = saved.copy(
            remote = current.remote,
            studio = saved.studio.copy(apiKey = sharedApiKey),
        )
        publishUnsavedState()
    }

    private fun markStudioSaved(saveApiKey: Boolean) {
        val current = currentSettingsSnapshot()
        val saved = savedSnapshot ?: current
        savedSnapshot = saved.copy(
            studio = current.studio.copy(apiKey = if (saveApiKey) current.studio.apiKey else saved.studio.apiKey),
            remote = if (saveApiKey) saved.remote.copy(apiKey = current.remote.apiKey) else saved.remote,
        )
        publishUnsavedState()
    }

    private fun humanSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val value = bytes.toDouble()
        return when {
            value >= gb -> "${format2(value / gb)} GB"
            value >= mb -> "${format1(value / mb)} MB"
            value >= kb -> "${format1(value / kb)} KB"
            else -> "$bytes B"
        }
    }

    private fun format1(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun format2(value: Double) = String.format(Locale.US, "%.2f", value)

    private data class GenerationInput(
        val profile: Int,
        val runtime: Int,
        val computeBackend: Int,
        val cpuThreads: String,
        val gpuLayers: String,
        val temperature: String,
        val topP: String,
        val topK: String,
        val maxTokens: String,
        val repetitionPenalty: String,
        val contextSize: String,
        val seed: String,
        val template: Int,
        val structuredJson: Boolean,
        val systemPrompt: String,
    )

    private data class RemoteInput(
        val enabled: Boolean,
        val baseUrl: String,
        val model: String,
        val apiKey: String,
    )

    private data class StudioInput(val enabled: Boolean, val apiKey: String)

    private data class SettingsSnapshot(
        val selectedModelId: String?,
        val generation: GenerationInput,
        val huggingFaceToken: String,
        val remote: RemoteInput,
        val studio: StudioInput,
    )

    private companion object {
        const val TAG = "LocalModelsConfigure"
        const val SECTION_PREFS = "local_models_sections"
        const val GIB = 1024.0 * 1024.0 * 1024.0
    }
}
