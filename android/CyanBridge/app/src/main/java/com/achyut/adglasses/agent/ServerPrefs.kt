package com.achyut.adglasses.agent

import android.content.Context
import android.content.SharedPreferences
import android.util.Patterns
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale

object ServerPrefs {
    private const val PREFS_NAME = "adglasses_server_prefs"
    private const val SECRET_PREFS_NAME = "adglasses_server_secrets"
    private const val KEY_API_TOKEN = "api_token"
    private const val KEY_ACCOUNT_EMAIL = "account_email"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun secretPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            SECRET_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiToken(context: Context): String {
        return secretPrefs(context).getString(KEY_API_TOKEN, "").orEmpty().trim()
    }

    fun setApiToken(context: Context, token: String?) {
        val value = token?.trim().orEmpty()
        secretPrefs(context).edit().apply {
            if (value.isBlank()) remove(KEY_API_TOKEN) else putString(KEY_API_TOKEN, value)
        }.apply()
    }

    fun getAccountEmail(context: Context): String =
        sanitizeAccountEmail(prefs(context).getString(KEY_ACCOUNT_EMAIL, ""))

    fun setAccountEmail(context: Context, email: String?) {
        val value = sanitizeAccountEmail(email)
        prefs(context).edit().apply {
            if (value.isBlank()) remove(KEY_ACCOUNT_EMAIL) else putString(KEY_ACCOUNT_EMAIL, value)
        }.apply()
    }

    private fun normalizeAccountEmail(email: String?): String =
        email?.trim()?.lowercase(Locale.US).orEmpty()

    private fun sanitizeAccountEmail(email: String?): String {
        val normalized = normalizeAccountEmail(email)
        return if (Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) normalized else ""
    }
}
