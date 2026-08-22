package com.fersaiyan.cyanbridge.ai.router

import android.content.Context

/**
 * Temporary source-compatibility shim for one inherited MainActivity call site.
 * There is no CLI relay transport anymore: requests go directly to the selected API provider.
 */
@Deprecated("CLI relay was removed; use ApiTokenClient")
object CliRelayClient {
    suspend fun chat(
        context: Context,
        chatId: String,
        prompt: String,
        messages: List<Map<String, String>>,
        modelOverride: String? = null,
    ): Result<String> = ApiTokenClient.chat(
        context = context,
        messages = messages,
    )
}
