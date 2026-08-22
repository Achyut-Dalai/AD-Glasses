package com.ad_glasses.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Secure credentials for a relay controlled by the app owner. */
object CloudServerPrefs {
    private const val PREFS_NAME = "cloud_server_prefs"
    private const val SECRET_PREFS_NAME = "cloud_server_secrets"
    private const val LEGACY_PREFS_NAME = "pro_subscription_server_prefs"
    private const val LEGACY_SECRET_PREFS_NAME = "pro_subscription_server_secrets"
    private const val KEY_API_TOKEN = "api_token"
    private const val KEY_ACCOUNT_EMAIL = "account_email"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun encryptedPrefs(context: Context, name: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun secrets(context: Context) = encryptedPrefs(context, SECRET_PREFS_NAME)

    fun getApiToken(context: Context): String {
        val current = secrets(context).getString(KEY_API_TOKEN, "").orEmpty().trim()
        if (current.isNotBlank()) return current

        val legacy = runCatching {
            encryptedPrefs(context, LEGACY_SECRET_PREFS_NAME)
                .getString(KEY_API_TOKEN, "")
                .orEmpty()
                .trim()
        }.getOrDefault("")
        if (legacy.isNotBlank()) setApiToken(context, legacy)
        return legacy
    }

    fun setApiToken(context: Context, token: String?) {
        val value = token.orEmpty().trim()
        val saved = secrets(context).edit().apply {
            if (value.isBlank()) remove(KEY_API_TOKEN) else putString(KEY_API_TOKEN, value)
        }.commit()
        check(saved) { "Unable to securely store the cloud API token" }
    }

    fun getAccountEmail(context: Context): String {
        val current = prefs(context).getString(KEY_ACCOUNT_EMAIL, "").orEmpty().trim()
        if (current.isNotBlank()) return current
        return context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT_EMAIL, "")
            .orEmpty()
            .trim()
    }

    fun setAccountEmail(context: Context, email: String?) {
        val value = email.orEmpty().trim()
        prefs(context).edit().apply {
            if (value.isBlank()) remove(KEY_ACCOUNT_EMAIL) else putString(KEY_ACCOUNT_EMAIL, value)
        }.apply()
    }
}
