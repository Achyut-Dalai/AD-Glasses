package com.fersaiyan.cyanbridge.agent

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

/**
 * Handles browser return for web checkout and maps callback params to local entitlement state.
 * Expected query params (all optional, backend-defined):
 * - status: success|cancel|error
 * - plan: free_trial|cheap|standard|max
 * - token: entitlement/session token
 * - expires_at_ms: epoch millis
 * - message: short user-facing message
 */
class WebSubscriptionCallbackActivity : AppCompatActivity() {

    private data class CallbackResult(
        val success: Boolean,
        val message: String,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data
        if (data?.path == "/restore") {
            restoreVerifiedAccount(data.getQueryParameter("api_token"), data.getQueryParameter("email"))
            return
        }
        val status = data?.getQueryParameter("status")?.trim()?.lowercase().orEmpty()
        val message = data?.getQueryParameter("message")?.trim().orEmpty()
        if (status == "success") {
            thread {
                val verified = ProSubscriptionVerifier.verifyNow(this, strictForTesting = false)
                val result = CallbackResult(
                    success = verified.active,
                    message = when {
                        verified.active && message.isNotBlank() -> message
                        verified.active -> "Subscription verified"
                        else -> "Payment returned successfully, but the subscription is still awaiting server confirmation."
                    },
                )
                runOnUiThread { finishCallback(result) }
            }
            return
        }

        finishCallback(applyNonSuccessCallback(status, message))
    }

    private fun restoreVerifiedAccount(apiToken: String?, email: String?) {
        val token = apiToken?.trim().orEmpty()
        val verifiedEmail = ProSubscriptionServerPrefs.normalizeAccountEmail(email)
        if (token.isBlank() || !ProSubscriptionServerPrefs.isUsableAccountEmail(verifiedEmail)) {
            finishCallback(CallbackResult(false, "Email verification could not restore this account."))
            return
        }

        ProSubscriptionServerPrefs.setApiToken(this, token)
        ProSubscriptionServerPrefs.setVerifiedAccountEmail(this, verifiedEmail)
        thread {
            val verified = ProSubscriptionVerifier.verifyNow(this, strictForTesting = false)
            val message = if (verified.active) {
                "Email verified and subscription restored"
            } else {
                "Email verified. Choose a plan to continue."
            }
            runOnUiThread { finishCallback(CallbackResult(verified.active, message)) }
        }
    }

    private fun finishCallback(result: CallbackResult) {
        val destination = if (result.success) {
            ProSubscriptionSettingsActivity::class.java
        } else {
            ProSubscriptionActivity::class.java
        }

        startActivity(Intent(this, destination).apply {
            putExtra(ProSubscriptionActivity.EXTRA_CALLBACK_MESSAGE, result.message)
            putExtra(ProSubscriptionActivity.EXTRA_FROM_WEB_CALLBACK, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun applyNonSuccessCallback(status: String, message: String): CallbackResult {
        val failureMessage = if (message.isNotBlank()) {
            message
        } else {
            when (status) {
                "cancel" -> "Subscription canceled"
                "error" -> "Subscription failed"
                else -> "Subscription not completed"
            }
        }

        return CallbackResult(success = false, message = failureMessage)
    }
}
