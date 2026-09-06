package com.adglasses.app.core.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AIProviderKind(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val managesEndpoint: Boolean = true,
) {
    OpenAI("OpenAI", "https://api.openai.com/v1", "gpt-5.6-luna"),
    Google("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-3.7-flash"),
    DeepSeek("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    OpenRouter("OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto"),
    Groq("Groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-120b"),
    Custom("OpenAI-compatible", "", "", managesEndpoint = false),
}

data class AIProfile(
    val id: String,
    val name: String,
    val provider: AIProviderKind,
    val baseUrl: String,
    val model: String,
) {
    companion object {
        fun new(provider: AIProviderKind, existingCount: Int = 0): AIProfile = AIProfile(
            id = UUID.randomUUID().toString(),
            name = if (existingCount == 0) provider.displayName else "${provider.displayName} ${existingCount + 1}",
            provider = provider,
            baseUrl = provider.defaultBaseUrl,
            model = provider.defaultModel,
        )
    }
}

data class AIConfigurationSnapshot(
    val profiles: List<AIProfile> = emptyList(),
    val activeProfileId: String? = null,
    val activeProfile: AIProfile? = null,
    val configured: Boolean = false,
)

class AIProfileStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cloud_ai_profiles_v1", Context.MODE_PRIVATE)
    private val credentials = EncryptedCredentialStore(appContext)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AIConfigurationSnapshot> = _state.asStateFlow()

    fun newProfile(provider: AIProviderKind): AIProfile =
        AIProfile.new(provider, _state.value.profiles.count { it.provider == provider })

    fun credential(profileId: String): String =
        credentials.read(profileId)?.takeIf { it.isNotBlank() } ?: error("Enter an API key for this Cloud AI profile")

    fun hasCredential(profileId: String): Boolean = !credentials.read(profileId).isNullOrBlank()

    fun save(
        draft: AIProfile,
        apiKeyReplacement: String,
        makeActive: Boolean = true,
    ): AIProfile {
        val existing = _state.value.profiles.firstOrNull { it.id == draft.id }
        val provider = draft.provider
        val name = draft.name.trim().ifBlank { provider.displayName }
        val model = normalizeModel(draft.model, provider)
        require(model.isNotBlank()) { "Choose or enter a model" }
        val base = normalizeBaseUrl(if (provider.managesEndpoint) provider.defaultBaseUrl else draft.baseUrl)
        require(validHttpsBaseUrl(base)) { "Custom API endpoints must be valid HTTPS URLs" }

        if (existing != null && apiKeyReplacement.isBlank()) {
            val oldBase = normalizeBaseUrl(if (existing.provider.managesEndpoint) existing.provider.defaultBaseUrl else existing.baseUrl)
            val scopeChanged = existing.provider != provider || (!provider.managesEndpoint && oldBase != base)
            require(!scopeChanged) { "Enter a new API key after changing the provider or custom endpoint" }
        }

        val replacement = normalizeCredential(apiKeyReplacement)
        if (replacement.isNotBlank()) credentials.write(draft.id, replacement)
        require(hasCredential(draft.id)) { "Enter an API key for this Cloud AI profile" }

        val saved = draft.copy(name = name, baseUrl = base, model = model)
        val updated = _state.value.profiles.toMutableList()
        val index = updated.indexOfFirst { it.id == saved.id }
        if (index >= 0) updated[index] = saved else updated += saved
        val activeId = if (makeActive || _state.value.activeProfileId == null) saved.id else _state.value.activeProfileId
        persist(updated, activeId)
        return saved
    }

    fun setActive(profileId: String) {
        require(_state.value.profiles.any { it.id == profileId }) { "Unknown Cloud AI profile" }
        persist(_state.value.profiles, profileId)
    }

    fun delete(profileId: String) {
        credentials.delete(profileId)
        val updated = _state.value.profiles.filterNot { it.id == profileId }
        val active = if (_state.value.activeProfileId == profileId) updated.firstOrNull()?.id else _state.value.activeProfileId
        persist(updated, active)
    }

    private fun load(): AIConfigurationSnapshot {
        val profiles = runCatching {
            val source = prefs.getString("profiles", null) ?: return@runCatching emptyList()
            val array = JSONArray(source)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    val provider = runCatching { AIProviderKind.valueOf(item.getString("provider")) }.getOrNull()
                        ?: return@repeat
                    add(
                        AIProfile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            provider = provider,
                            baseUrl = item.getString("baseUrl"),
                            model = item.getString("model"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        val requestedActive = prefs.getString("activeProfileId", null)
        val activeId = requestedActive?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
        return snapshot(profiles, activeId)
    }

    private fun persist(profiles: List<AIProfile>, activeProfileId: String?) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("provider", profile.provider.name)
                put("baseUrl", profile.baseUrl)
                put("model", profile.model)
            })
        }
        prefs.edit()
            .putString("profiles", array.toString())
            .putString("activeProfileId", activeProfileId)
            .apply()
        _state.value = snapshot(profiles, activeProfileId)
    }

    private fun snapshot(profiles: List<AIProfile>, activeProfileId: String?): AIConfigurationSnapshot {
        val active = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
        return AIConfigurationSnapshot(
            profiles = profiles,
            activeProfileId = active?.id,
            activeProfile = active,
            configured = active != null && active.model.isNotBlank() && hasCredential(active.id),
        )
    }

    companion object {
        fun normalizeModel(raw: String, provider: AIProviderKind): String {
            var value = raw.trim()
            if (provider == AIProviderKind.Google) {
                val marker = "/models/"
                if (value.contains(marker)) value = value.substringAfter(marker)
                if (value.startsWith("models/")) value = value.removePrefix("models/")
                value = value.substringBefore(":generateContent")
            }
            return value.trim()
        }

        private fun normalizeBaseUrl(raw: String): String {
            var value = raw.trim().trimEnd('/')
            listOf("/chat/completions", "/models", "/responses").forEach { suffix ->
                if (value.endsWith(suffix, ignoreCase = true)) value = value.dropLast(suffix.length).trimEnd('/')
            }
            return value
        }

        private fun validHttpsBaseUrl(raw: String): Boolean = runCatching {
            val uri = URI(raw)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)

        private fun normalizeCredential(raw: String): String {
            var value = raw.trim()
            if (value.startsWith("authorization:", ignoreCase = true)) value = value.substringAfter(':').trim()
            if (value.startsWith("bearer ", ignoreCase = true)) value = value.substring(7).trim()
            if (value.length >= 2 && ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\'')))) {
                value = value.substring(1, value.length - 1)
            }
            require(value.length <= 8_192 && !value.contains('\r') && !value.contains('\n')) { "Invalid API credential" }
            return value.trim()
        }
    }
}

private class EncryptedCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cloud_ai_credentials_v1", Context.MODE_PRIVATE)

    fun read(account: String): String? {
        val envelope = prefs.getString(account, null) ?: return null
        return runCatching {
            val parts = envelope.split(':', limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    fun write(account: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val envelope = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(account, envelope).apply()
    }

    fun delete(account: String) {
        prefs.edit().remove(account).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "ad_glasses_cloud_ai_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
