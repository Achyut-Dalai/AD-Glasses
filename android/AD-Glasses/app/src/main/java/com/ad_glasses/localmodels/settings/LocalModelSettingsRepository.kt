package com.ad_glasses.localmodels.settings

import android.content.Context
import com.ad_glasses.localmodels.catalog.LocalModelCatalogEntry
import com.ad_glasses.localmodels.catalog.LocalModelCatalogRepository
import com.ad_glasses.localmodels.storage.LocalModelStorageRepository
import org.json.JSONObject

object LocalModelSettingsRepository {
    private const val PREFS_NAME = "local_model_settings"
    private const val KEY_SETTINGS_BY_MODEL = "settings_by_model"
    private const val KEY_HF_TOKEN = "hf_token"
    private const val LEGACY_PREFS = "local_models_prefs"
    private const val LEGACY_KEY_USE_GPU = "use_gpu"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHuggingFaceToken(context: Context): String {
        return prefs(context).getString(KEY_HF_TOKEN, "")?.trim().orEmpty()
    }

    fun setHuggingFaceToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_HF_TOKEN, token.trim()).apply()
    }

    fun getForModel(context: Context, modelId: String): LocalGenerationSettings {
        val all = readAllSettings(context)
        val existing = all.optJSONObject(modelId)
        val entry = LocalModelCatalogRepository.findById(modelId)
        val installed = LocalModelStorageRepository.getInstalled(context, modelId)

        val profile = runCatching {
            LocalModelPerformanceProfile.valueOf(existing?.optString("profile").orEmpty())
        }.getOrElse { LocalModelPerformanceProfile.BALANCED }

        val defaults = LocalGenerationSettings.defaultsFor(entry, profile)
        if (existing == null) {
            return installed?.let { model ->
                defaults.copy(
                    modelRuntime = LocalModelRuntimeCompatibility.enforce(model.format, defaults.modelRuntime),
                )
            } ?: defaults
        }

        val requestedRuntime = runCatching {
            LocalModelRuntime.valueOf(existing.optString("model_runtime", defaults.modelRuntime.name))
        }.getOrElse { defaults.modelRuntime }
        val compatibleRuntime = installed?.let { model ->
            LocalModelRuntimeCompatibility.enforce(model.format, requestedRuntime)
        } ?: requestedRuntime

        return LocalGenerationSettings(
            profile = profile,
            temperature = existing.optDouble("temperature", defaults.temperature)
                .coerceIn(0.0, 2.0),
            topP = existing.optDouble("top_p", defaults.topP)
                .coerceIn(0.0, 1.0),
            topK = existing.optInt("top_k", defaults.topK)
                .coerceIn(0, 200),
            maxTokens = existing.optInt("max_tokens", defaults.maxTokens)
                .coerceIn(LocalGenerationSettings.MIN_MAX_TOKENS, LocalGenerationSettings.MAX_MAX_TOKENS),
            repetitionPenalty = existing.optDouble("repetition_penalty", defaults.repetitionPenalty)
                .coerceIn(0.8, 2.0),
            contextSize = existing.optInt("context_size", defaults.contextSize)
                .coerceIn(LocalGenerationSettings.MIN_CONTEXT_SIZE, LocalGenerationSettings.MAX_CONTEXT_SIZE),
            seed = existing.optInt("seed", defaults.seed),
            systemPromptOverride = existing.optString("system_prompt_override", defaults.systemPromptOverride),
            templateOverrideId = existing.optString("template_override", "").ifBlank { null },
            experimentalStructuredJson = existing.optBoolean(
                "experimental_structured_json",
                defaults.experimentalStructuredJson,
            ),
            computeBackend = runCatching {
                val raw = existing.optString("compute_backend", defaults.computeBackend.name)
                when (raw) {
                    "GPU_EXPERIMENTAL", "GPU" -> LocalComputeBackend.GPU
                    "NPU_EXPERIMENTAL" -> LocalComputeBackend.NPU_EXPERIMENTAL
                    "CPU" -> LocalComputeBackend.CPU
                    else -> LocalComputeBackend.valueOf(raw)
                }
            }.getOrElse {
                if (legacyUseGpu(context)) LocalComputeBackend.GPU else defaults.computeBackend
            },
            cpuThreads = existing.optInt("cpu_threads", defaults.cpuThreads)
                .coerceIn(1, 16),
            gpuLayers = existing.optInt("gpu_layers", defaults.gpuLayers)
                .coerceIn(-1, 999),
            modelRuntime = compatibleRuntime,
        )
    }

    fun hasSavedSettings(context: Context, modelId: String): Boolean {
        return readAllSettings(context).has(modelId)
    }

    /** Persists the device recommendation once, without overwriting later user customization. */
    @Synchronized
    fun initializeCatalogDefaultsIfMissing(
        context: Context,
        entry: LocalModelCatalogEntry,
        profile: LocalModelPerformanceProfile,
    ): LocalGenerationSettings {
        if (hasSavedSettings(context, entry.id)) {
            return getForModel(context, entry.id)
        }
        val defaults = LocalGenerationSettings.defaultsFor(entry, profile)
        saveForModel(context, entry.id, defaults)
        return defaults
    }

    fun saveForModel(context: Context, modelId: String, settings: LocalGenerationSettings) {
        val installed = LocalModelStorageRepository.getInstalled(context, modelId)
        val safeSettings = installed?.let { model ->
            settings.copy(
                modelRuntime = LocalModelRuntimeCompatibility.enforce(model.format, settings.modelRuntime),
            )
        } ?: settings
        val all = readAllSettings(context)
        all.put(
            modelId,
            JSONObject()
                .put("profile", safeSettings.profile.name)
                .put("temperature", safeSettings.temperature)
                .put("top_p", safeSettings.topP)
                .put("top_k", safeSettings.topK)
                .put("max_tokens", safeSettings.maxTokens)
                .put("repetition_penalty", safeSettings.repetitionPenalty)
                .put("context_size", safeSettings.contextSize)
                .put("seed", safeSettings.seed)
                .put("system_prompt_override", safeSettings.systemPromptOverride)
                .put("template_override", safeSettings.templateOverrideId.orEmpty())
                .put("experimental_structured_json", safeSettings.experimentalStructuredJson)
                .put("compute_backend", safeSettings.computeBackend.name)
                .put("cpu_threads", safeSettings.cpuThreads)
                .put("gpu_layers", safeSettings.gpuLayers)
                .put("model_runtime", safeSettings.modelRuntime.name),
        )
        prefs(context).edit().putString(KEY_SETTINGS_BY_MODEL, all.toString()).apply()
    }

    fun clearForModel(context: Context, modelId: String) {
        val all = readAllSettings(context)
        all.remove(modelId)
        prefs(context).edit().putString(KEY_SETTINGS_BY_MODEL, all.toString()).apply()
    }

    private fun readAllSettings(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_SETTINGS_BY_MODEL, "{}") ?: "{}"
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun legacyUseGpu(context: Context): Boolean {
        return context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(LEGACY_KEY_USE_GPU, false)
    }
}
