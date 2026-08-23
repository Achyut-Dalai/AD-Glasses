package com.ad_glasses.ai.router

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ad_glasses.agent.LocalAgentPrefs as AutomationPrefs
import com.ad_glasses.shared.settings.AgentProviderType
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AgentInferencePurpose {
    CLASSIFICATION,
    UI_PLANNING,
}

data class AgentInferenceResult(
    val content: String,
    val usedImage: Boolean,
    val mediaStatus: String,
)

/** Cloud-only inference boundary. Local Agent remains an automation capability, not an LLM. */
object AgentInferenceRouter {
    private const val TIMING_TAG = "AssistantTiming"

    suspend fun complete(
        context: Context,
        purpose: AgentInferencePurpose,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        providerType: AgentProviderType = AutomationPrefs.getProviderType(context),
        onToken: ((String) -> Unit)? = null,
        webRequested: Boolean = false,
    ): String = completeText(
        context = context,
        purpose = purpose,
        sessionId = sessionId,
        systemPrompt = systemPrompt,
        userPrompt = userPrompt,
        webRequested = webRequested,
    )

    suspend fun completeUiPlanning(
        context: Context,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        imagePath: String?,
        allowRemoteImageUpload: Boolean,
        providerType: AgentProviderType = AutomationPrefs.getProviderType(context),
        onToken: ((String) -> Unit)? = null,
        webRequested: Boolean = false,
    ): AgentInferenceResult {
        val usableImagePath = imagePath?.trim()?.takeIf { File(it).isFile }
        if (!imagePath.isNullOrBlank() && usableImagePath == null) {
            throw IllegalStateException("The requested image file is missing. AD did not fall back to a text-only answer.")
        }
        if (usableImagePath == null) {
            return AgentInferenceResult(
                content = completeText(
                    context = context,
                    purpose = AgentInferencePurpose.UI_PLANNING,
                    sessionId = sessionId,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    webRequested = webRequested,
                ),
                usedImage = false,
                mediaStatus = "Cloud text inference",
            )
        }
        if (!allowRemoteImageUpload) {
            throw IllegalStateException("Remote image upload is off. The image was not sent.")
        }

        val startedAt = SystemClock.elapsedRealtime()
        val sessionLabel = sessionId.takeLast(8)
        Log.i(TIMING_TAG, "stage=cloud_image_start thread=$sessionLabel")
        val imageContent = try {
            withContext(Dispatchers.IO) {
                ApiTokenClient.image(
                    context = context,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    imagePath = usableImagePath,
                    maxTokens = UI_PLANNING_MAX_TOKENS,
                ).getOrThrow()
            }
        } catch (error: CancellationException) {
            Log.i(
                TIMING_TAG,
                "stage=cloud_image_cancelled thread=$sessionLabel elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            throw error
        } catch (error: Exception) {
            Log.w(
                TIMING_TAG,
                "stage=cloud_image_failed thread=$sessionLabel elapsedMs=${SystemClock.elapsedRealtime() - startedAt} type=${error::class.java.simpleName}",
            )
            throw IllegalStateException(
                "The active Cloud AI profile could not analyze this image: " +
                    (error.message ?: error::class.java.simpleName),
                error,
            )
        }
        Log.i(
            TIMING_TAG,
            "stage=cloud_image_done thread=$sessionLabel elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )

        return AgentInferenceResult(
            content = imageContent,
            usedImage = true,
            mediaStatus = "Image attached to the active Cloud AI profile.",
        )
    }

    fun isRemotePlanner(providerType: AgentProviderType): Boolean = true

    private suspend fun completeText(
        context: Context,
        purpose: AgentInferencePurpose,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        webRequested: Boolean,
    ): String {
        val maxTokens = if (purpose == AgentInferencePurpose.CLASSIFICATION) 256 else 512
        val startedAt = SystemClock.elapsedRealtime()
        val sessionLabel = sessionId.takeLast(8)
        Log.i(TIMING_TAG, "stage=cloud_text_start thread=$sessionLabel purpose=$purpose")
        return try {
            withContext(Dispatchers.IO) {
                ApiTokenClient.chat(
                    context = context,
                    messages = messages(systemPrompt, userPrompt),
                    maxTokens = maxTokens,
                    webRequested = webRequested,
                ).getOrThrow()
            }.also {
                Log.i(
                    TIMING_TAG,
                    "stage=cloud_text_done thread=$sessionLabel purpose=$purpose elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                )
            }
        } catch (error: CancellationException) {
            Log.i(
                TIMING_TAG,
                "stage=cloud_text_cancelled thread=$sessionLabel purpose=$purpose elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            throw error
        } catch (error: Exception) {
            Log.w(
                TIMING_TAG,
                "stage=cloud_text_failed thread=$sessionLabel purpose=$purpose elapsedMs=${SystemClock.elapsedRealtime() - startedAt} type=${error::class.java.simpleName}",
            )
            throw error
        }
    }

    private fun messages(systemPrompt: String, userPrompt: String): List<Map<String, String>> = listOf(
        mapOf("role" to "system", "content" to systemPrompt),
        mapOf("role" to "user", "content" to userPrompt),
    )

    private const val UI_PLANNING_MAX_TOKENS = 512
}
