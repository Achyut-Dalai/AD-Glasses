package com.ad_glasses.localmodels.session

import android.content.Context
import android.util.Log
import com.ad_glasses.localmodels.catalog.LocalModelCatalogEntry
import com.ad_glasses.localmodels.device.DeviceCapabilityService
import com.ad_glasses.localmodels.engine.EngineLoadConfig
import com.ad_glasses.localmodels.engine.GenerationConfig
import com.ad_glasses.localmodels.engine.GenerationResult
import com.ad_glasses.localmodels.engine.LiteRtLocalInferenceEngine
import com.ad_glasses.localmodels.engine.LlamaCppLocalInferenceEngine
import com.ad_glasses.localmodels.engine.LocalInferenceEngine
import com.ad_glasses.localmodels.settings.LocalComputeBackend
import com.ad_glasses.localmodels.settings.LocalGenerationSettings
import com.ad_glasses.localmodels.settings.LocalModelRuntime
import com.ad_glasses.localmodels.settings.LocalModelSettingsRepository
import com.ad_glasses.localmodels.provider.LocalModelRequestPriority
import com.ad_glasses.localmodels.storage.InstalledLocalModel
import com.ad_glasses.localmodels.storage.LocalModelStorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class LocalSessionState {
    data object Idle : LocalSessionState()
    data object ModelNotLoaded : LocalSessionState()
    data class Loading(val modelId: String) : LocalSessionState()
    data class Ready(val modelId: String) : LocalSessionState()
    data class Generating(val modelId: String, val requestId: String) : LocalSessionState()
    data class Error(val message: String) : LocalSessionState()
}

data class LocalSessionSnapshot(
    val state: LocalSessionState,
    val loadedModelId: String?,
    val loadedModelPath: String?,
    val activeRequestId: String?,
    val activeBackend: LocalComputeBackend?,
    val gpuFallbackMessage: String?,
)

data class LocalModelLoadDetails(
    val activeBackend: LocalComputeBackend,
    val activeGpuLayers: Int,
    val fallbackReason: String?,
)

data class LocalWarmupProbeResult(
    val promptTokens: Int,
    val generatedTokens: Int,
    val elapsedMs: Long,
    val backend: LocalComputeBackend,
    val fallbackReason: String?,
) {
    val totalTokens: Int get() = (promptTokens + generatedTokens).coerceAtLeast(1)
}

object LocalChatSessionManager {
    private const val APPROX_CHARS_PER_TOKEN = 4
    private const val TOKEN_GUARD_SAFETY_MARGIN = 1

    private val mutex = Mutex()
    private val operationGate = LocalModelOperationGate()

    private var engine: LocalInferenceEngine? = null
    private var loadedModelId: String? = null
    private var loadedModelPath: String? = null
    private var loadedConfig: EngineLoadConfig? = null
    private var activeBackend: LocalComputeBackend? = null
    private var activeGpuLayers: Int = 0
    private var gpuFallbackMessage: String? = null
    private var activeRequestId: String? = null
    private var pendingHighPriorityRequests: Int = 0
    private var activeRequestPriority: LocalModelRequestPriority? = null
    private var lastGenerationCappedByMaxTokens: Boolean = false
    private var state: LocalSessionState = LocalSessionState.ModelNotLoaded

    suspend fun snapshot(): LocalSessionSnapshot = mutex.withLock {
        LocalSessionSnapshot(
            state = state,
            loadedModelId = loadedModelId,
            loadedModelPath = loadedModelPath,
            activeRequestId = activeRequestId,
            activeBackend = activeBackend,
            gpuFallbackMessage = gpuFallbackMessage,
        )
    }

    suspend fun ensureModelLoaded(
        context: Context,
        model: InstalledLocalModel,
        catalogEntry: LocalModelCatalogEntry?,
        settings: LocalGenerationSettings,
    ): LocalModelLoadDetails {
        return operationGate.withExclusiveOperation {
            mutex.withLock {
            Log.i(
                TAG,
                "ensureModelLoaded model=${model.displayName} runtime=${settings.modelRuntime} backend=${settings.computeBackend} context=${settings.contextSize} gpuLayers=${settings.gpuLayers}",
            )
            val file = File(model.absolutePath)
            if (!file.exists()) {
                LocalModelStorageRepository.removeInstalled(context, model.id)
                state = LocalSessionState.Error("Selected local model file is missing")
                throw IllegalStateException("Selected model file is missing. Re-import or download again.")
            }

            val capabilityEntry = catalogEntry ?: LocalModelCatalogEntry(
                id = model.id,
                displayName = model.displayName,
                family = "custom",
                sourceUrl = null,
                sourcePageUrl = null,
                expectedFilename = model.fileName,
                sha256 = model.sha256,
                sizeBytes = model.sizeBytes,
                quantization = model.quantization ?: "unknown",
                contextSizeDefault = settings.contextSize,
                promptTemplateId = model.promptTemplateId ?: "generic_chatml",
                minRamGb = DeviceCapabilityService.recommendedMinRamGbForModelBytes(model.sizeBytes),
                minStorageGb = 1.0,
                shortDescription = "Imported GGUF model",
                tags = listOf("offline"),
                gatedDownload = false,
                licenseTermsNote = model.licenseTermsNote ?: "Respect the source model license.",
                enabled = true,
            )

            val capability = DeviceCapabilityService.assess(
                snapshot = DeviceCapabilityService.snapshot(context),
                entry = capabilityEntry,
                requireDownloadHeadroom = false,
            )
            if (!capability.supported) {
                state = LocalSessionState.Error(capability.blockers.joinToString(" "))
                throw IllegalStateException(capability.blockers.joinToString(" "))
            }

            val loadConfig = EngineLoadConfig(
                contextSize = settings.contextSize.coerceIn(
                    LocalGenerationSettings.MIN_CONTEXT_SIZE,
                    LocalGenerationSettings.MAX_CONTEXT_SIZE,
                ),
                cpuThreads = settings.cpuThreads.coerceIn(1, 16),
                computeBackend = settings.computeBackend,
                gpuLayers = settings.gpuLayers.coerceIn(-1, 999),
            )

            if (
                loadedModelId == model.id &&
                loadedModelPath == model.absolutePath &&
                loadedConfig == loadConfig &&
                isEngineCompatibleWithRuntime(settings.modelRuntime)
            ) {
                state = LocalSessionState.Ready(model.id)
                return@withLock LocalModelLoadDetails(
                    activeBackend = activeBackend ?: settings.computeBackend,
                    activeGpuLayers = if (activeBackend == LocalComputeBackend.GPU || activeBackend == LocalComputeBackend.NPU_EXPERIMENTAL) {
                        activeGpuLayers
                    } else {
                        0
                    },
                    fallbackReason = gpuFallbackMessage,
                )
            }

            if (engine != null && !isEngineCompatibleWithRuntime(settings.modelRuntime)) {
                runCatching { engine?.unloadModel() }
                engine = null
            }

            state = LocalSessionState.Loading(model.id)
            val llm = engine ?: createEngine(settings.modelRuntime, context).also { engine = it }

            runCatching {
                llm.loadModel(
                    modelPath = model.absolutePath,
                    config = loadConfig,
                )
            }.onFailure {
                Log.e(TAG, "Failed to load local model ${model.displayName}", it)
                state = LocalSessionState.Error(it.message ?: "Failed to load local model")
                throw it
            }.onSuccess { loadResult ->
                Log.i(
                    TAG,
                    "Local model loaded model=${model.displayName} activeBackend=${loadResult.activeBackend} fallback=${loadResult.fallbackReason}",
                )
                activeBackend = loadResult.activeBackend
                activeGpuLayers = loadResult.activeGpuLayers
                gpuFallbackMessage = loadResult.fallbackReason
            }

            loadedModelId = model.id
            loadedModelPath = model.absolutePath
            loadedConfig = loadConfig
            state = LocalSessionState.Ready(model.id)

                return@withLock LocalModelLoadDetails(
                    activeBackend = activeBackend ?: settings.computeBackend,
                    activeGpuLayers = if (activeBackend == LocalComputeBackend.GPU || activeBackend == LocalComputeBackend.NPU_EXPERIMENTAL) {
                        activeGpuLayers
                    } else {
                        0
                    },
                    fallbackReason = gpuFallbackMessage,
                )
            }
        }
    }

    suspend fun streamGenerate(
        settings: LocalGenerationSettings,
        prompt: String,
        onToken: (String) -> Unit,
        imagePaths: List<String> = emptyList(),
        audioPath: String? = null,
        requestPriority: LocalModelRequestPriority = LocalModelRequestPriority.HIGH,
        maxTokensOverride: Int? = null,
    ): String {
        return generateInternal(
            settings = settings,
            prompt = prompt,
            onToken = onToken,
            imagePaths = imagePaths,
            audioPath = audioPath,
            requestPriority = requestPriority,
            maxTokensOverride = maxTokensOverride,
        ).text
    }

    suspend fun consumeLastGenerationCappedFlag(): Boolean = mutex.withLock {
        val capped = lastGenerationCappedByMaxTokens
        lastGenerationCappedByMaxTokens = false
        capped
    }

    private suspend fun generateInternal(
        settings: LocalGenerationSettings,
        prompt: String,
        onToken: (String) -> Unit,
        imagePaths: List<String>,
        audioPath: String?,
        requestPriority: LocalModelRequestPriority,
        maxTokensOverride: Int? = null,
    ): GenerationResult {
        val reqId = UUID.randomUUID().toString()

        Log.i(
            TAG,
            "generateInternal requestId=$reqId priority=$requestPriority promptChars=${prompt.length} images=${imagePaths.size} hasAudio=${!audioPath.isNullOrBlank()} maxTokensOverride=${maxTokensOverride ?: -1}",
        )

        if (requestPriority == LocalModelRequestPriority.HIGH) {
            mutex.withLock {
                pendingHighPriorityRequests += 1
            }
        }

        if (requestPriority == LocalModelRequestPriority.LOW) {
            waitForHighPriorityRequestsToDrain()
        }

        var highPendingConsumed = false
        try {
            return operationGate.withExclusiveOperation {
                val (llm, modelId, contextTokens) = mutex.withLock {
                    if (requestPriority == LocalModelRequestPriority.HIGH) {
                        pendingHighPriorityRequests = (pendingHighPriorityRequests - 1).coerceAtLeast(0)
                        highPendingConsumed = true
                    }
                    val currentEngine = engine
                        ?: throw IllegalStateException("Local engine not initialized")
                    val currentModelId = loadedModelId
                        ?: throw IllegalStateException("No local model loaded")
                    lastGenerationCappedByMaxTokens = false
                    activeRequestId = reqId
                    activeRequestPriority = requestPriority
                    state = LocalSessionState.Generating(currentModelId, reqId)
                    Triple(
                        currentEngine,
                        currentModelId,
                        loadedConfig?.contextSize ?: settings.contextSize,
                    )
                }

                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val requestedMaxTokens = (maxTokensOverride ?: settings.maxTokens).coerceAtLeast(1)
                        val promptTokens = runCatching {
                            llm.tokenizeCount(prompt).coerceAtLeast(1)
                        }.getOrElse {
                            Log.w(TAG, "Tokenizer count failed; using conservative estimate", it)
                            estimateApproxTokens(prompt).coerceAtLeast(1)
                        }
                        val budget = LocalContextBudgetPolicy.resolve(
                            contextTokens = contextTokens.coerceAtLeast(1),
                            promptTokens = promptTokens,
                            requestedOutputTokens = requestedMaxTokens,
                        )
                        val maxTokens = budget.effectiveOutputTokens
                        if (budget.wasOutputClamped) {
                            Log.i(
                                TAG,
                                "Clamped local output requestId=$reqId requested=${budget.requestedOutputTokens} effective=$maxTokens prompt=${budget.promptTokens} context=${budget.contextTokens} reserved=${budget.reservedHeadroomTokens}",
                            )
                        }
                        val streamedText = StringBuilder()
                        var approxGeneratedTokens = 0
                        var guardTriggered = false
                        var raw: GenerationResult? = null

                        try {
                            raw = llm.generate(
                                config = GenerationConfig(
                                    prompt = prompt,
                                    temperature = settings.temperature,
                                    topP = settings.topP,
                                    topK = settings.topK,
                                    maxTokens = maxTokens,
                                    repetitionPenalty = settings.repetitionPenalty,
                                    seed = settings.seed,
                                    structuredJson = settings.experimentalStructuredJson,
                                    imagePaths = imagePaths,
                                    audioPath = audioPath,
                                ),
                                onToken = { chunk ->
                                    if (chunk.isBlank() || guardTriggered) return@generate

                                    streamedText.append(chunk)
                                    onToken(chunk)

                                    approxGeneratedTokens += estimateApproxTokens(chunk)
                                    if (approxGeneratedTokens >= (maxTokens + TOKEN_GUARD_SAFETY_MARGIN)) {
                                        guardTriggered = true
                                        runCatching {
                                            runBlocking { llm.cancelGeneration() }
                                        }
                                    }
                                },
                            )
                        } catch (err: Throwable) {
                            if (!guardTriggered) throw err
                        }

                        val preferredText = if (streamedText.isNotBlank()) {
                            streamedText.toString()
                        } else {
                            raw?.text.orEmpty()
                        }

                        val guardedText = truncateToApproxTokens(preferredText, maxTokens)
                        val guardedTokenCount = (raw?.tokenCount ?: 0)
                            .coerceAtLeast(estimateApproxTokens(guardedText))
                            .coerceAtMost(maxTokens)
                        val capped = guardTriggered ||
                            guardedTokenCount >= maxTokens ||
                            estimateApproxTokens(preferredText) > maxTokens ||
                            (raw?.cappedByMaxTokens == true)

                        GenerationResult(
                            text = guardedText,
                            tokenCount = guardedTokenCount,
                            cappedByMaxTokens = capped,
                        )
                    }
                }

                mutex.withLock {
                    activeRequestId = null
                    activeRequestPriority = null
                    result.fold(
                        onSuccess = {
                            lastGenerationCappedByMaxTokens = it.cappedByMaxTokens
                            state = LocalSessionState.Ready(modelId)
                            it
                        },
                        onFailure = { err ->
                            Log.e(TAG, "Local generation failed requestId=$reqId", err)
                            lastGenerationCappedByMaxTokens = false
                            state = LocalSessionState.Error(err.message ?: "Local generation failed")
                            throw err
                        },
                    )
                }
            }
        } finally {
            if (requestPriority == LocalModelRequestPriority.HIGH && !highPendingConsumed) {
                mutex.withLock {
                    pendingHighPriorityRequests = (pendingHighPriorityRequests - 1).coerceAtLeast(0)
                }
            }
        }
    }

    private suspend fun waitForHighPriorityRequestsToDrain() {
        while (true) {
            val shouldWait = mutex.withLock {
                pendingHighPriorityRequests > 0 || activeRequestPriority == LocalModelRequestPriority.HIGH
            }
            if (!shouldWait) return
            delay(120L)
        }
    }

    private fun estimateApproxTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length / APPROX_CHARS_PER_TOKEN).coerceAtLeast(1)
    }

    private fun truncateToApproxTokens(text: String, maxTokens: Int): String {
        if (text.isBlank()) return text
        val cap = maxTokens.coerceAtLeast(1) * APPROX_CHARS_PER_TOKEN
        if (text.length <= cap) return text
        return text.take(cap).trimEnd()
    }

    suspend fun cancelActiveGeneration() {
        val llm = mutex.withLock {
            if (activeRequestId == null) null else engine
        } ?: return
        Log.i(TAG, "Cancelling active local generation")
        runCatching { llm.cancelGeneration() }
    }

    suspend fun unload() {
        operationGate.withExclusiveOperation {
            mutex.withLock {
                Log.i(TAG, "Unloading local chat session modelId=${loadedModelId.orEmpty()}")
                unloadEngineAndClearStateLocked()
            }
        }
    }

    /**
     * Removes an installed model only after any active native-engine operation has finished.
     * Callers should use this instead of deleting model files through the storage repository.
     */
    suspend fun removeInstalledModel(context: Context, modelId: String): Boolean {
        return operationGate.withExclusiveOperation {
            mutex.withLock {
                if (loadedModelId == modelId) {
                    Log.i(TAG, "Unloading local model before removal modelId=$modelId")
                    unloadEngineAndClearStateLocked()
                }
            }
            val removed = withContext(Dispatchers.IO) {
                LocalModelStorageRepository.removeInstalled(context, modelId)
            }
            if (removed) {
                LocalModelSettingsRepository.clearForModel(context, modelId)
            }
            removed
        }
    }

    private suspend fun unloadEngineAndClearStateLocked() {
        runCatching { engine?.unloadModel() }
            .onFailure { Log.w(TAG, "Local engine unload failed", it) }
        loadedModelId = null
        loadedModelPath = null
        loadedConfig = null
        activeBackend = null
        activeGpuLayers = 0
        gpuFallbackMessage = null
        activeRequestId = null
        activeRequestPriority = null
        lastGenerationCappedByMaxTokens = false
        state = LocalSessionState.ModelNotLoaded
    }

    suspend fun runWarmupProbe(
        settings: LocalGenerationSettings,
        onToken: (String) -> Unit,
    ): LocalWarmupProbeResult {
        val backend = mutex.withLock { activeBackend ?: settings.computeBackend }
        val modelPathSnapshot = mutex.withLock { loadedModelPath }
        val skipGenerationForStability = shouldSkipWarmupGeneration(modelPathSnapshot)

        if (skipGenerationForStability) {
            val benchmarkPrompt = "Reply with only: OK"
            val start = System.currentTimeMillis()
            val promptTokens = tokenizeWithLoadedModel(benchmarkPrompt)?.coerceAtLeast(1) ?: 1
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val fallback = mutex.withLock { gpuFallbackMessage }
            val stabilityNote = "Qwen3.5 warm-up generation skipped due upstream llama.cpp instability"
            val mergedFallback = listOfNotNull(fallback, stabilityNote)
                .filter { it.isNotBlank() }
                .joinToString(" | ")
                .ifBlank { null }

            return LocalWarmupProbeResult(
                promptTokens = promptTokens,
                generatedTokens = 1,
                elapsedMs = elapsed,
                backend = backend,
                fallbackReason = mergedFallback,
            )
        }

        val isAccel = backend == LocalComputeBackend.GPU || backend == LocalComputeBackend.NPU_EXPERIMENTAL
        val benchmarkPrompt = if (isAccel) {
            "Reply with only: OK"
        } else {
            "Count numbers from 1 to 20 separated by spaces on one line."
        }
        val warmupTokens = if (isAccel) {
            settings.maxTokens.coerceIn(4, 12)
        } else {
            settings.maxTokens.coerceIn(16, 48)
        }

        val start = System.currentTimeMillis()
        val result = generateInternal(
            settings = settings.copy(
                maxTokens = warmupTokens,
                experimentalStructuredJson = false,
            ),
            prompt = benchmarkPrompt,
            onToken = onToken,
            imagePaths = emptyList(),
            audioPath = null,
            requestPriority = LocalModelRequestPriority.HIGH,
        )
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)

        val promptTokens = tokenizeWithLoadedModel(benchmarkPrompt)?.coerceAtLeast(1) ?: 1
        val generatedTokens = tokenizeWithLoadedModel(result.text)?.coerceAtLeast(1)
            ?: result.tokenCount.coerceAtLeast(1)

        val fallback = mutex.withLock { gpuFallbackMessage }

        return LocalWarmupProbeResult(
            promptTokens = promptTokens,
            generatedTokens = generatedTokens,
            elapsedMs = elapsed,
            backend = backend,
            fallbackReason = fallback,
        )
    }

    private suspend fun tokenizeWithLoadedModel(text: String): Int? {
        return operationGate.withExclusiveOperation {
            val llm = mutex.withLock { engine } ?: return@withExclusiveOperation null
            runCatching { llm.tokenizeCount(text) }
                .onFailure { Log.w(TAG, "Local token count failed", it) }
                .getOrNull()
        }
    }

    private fun isEngineCompatibleWithRuntime(runtime: LocalModelRuntime): Boolean {
        val currentEngine = engine ?: return false
        return when (runtime) {
            LocalModelRuntime.LLAMA_CPP -> currentEngine is LlamaCppLocalInferenceEngine
            LocalModelRuntime.LITERT -> currentEngine is LiteRtLocalInferenceEngine
            LocalModelRuntime.REMOTE_OPENAI -> false
        }
    }

    private fun createEngine(runtime: LocalModelRuntime, context: Context): LocalInferenceEngine {
        return when (runtime) {
            LocalModelRuntime.LLAMA_CPP -> LlamaCppLocalInferenceEngine()
            LocalModelRuntime.LITERT -> LiteRtLocalInferenceEngine(context)
            LocalModelRuntime.REMOTE_OPENAI -> throw IllegalStateException(
                "REMOTE_OPENAI does not use a local engine. Requests go through RemoteOpenAiClient."
            )
        }
    }

    private fun shouldSkipWarmupGeneration(modelPath: String?): Boolean {
        val normalized = modelPath.orEmpty().lowercase()
        if (normalized.isBlank()) return false
        return normalized.contains("qwen3.5") || normalized.contains("qwen35")
    }

    private const val TAG = "LocalChatSession"
}
