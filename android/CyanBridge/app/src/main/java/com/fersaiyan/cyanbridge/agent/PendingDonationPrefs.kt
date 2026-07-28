package com.fersaiyan.cyanbridge.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PendingDonationPrefs {
    private const val PREFS_NAME = "pending_donation_prefs"
    private const val SECRET_PREFS_NAME = "pending_donation_secrets"
    private const val KEY_INVOICE_URL = "invoice_url"
    private const val KEY_STATUS_URL = "status_url"
    private const val KEY_AMOUNT = "amount"
    private const val KEY_AWAITING_RETURN = "awaiting_return"

    data class PendingDonation(
        val invoiceUrl: String,
        val statusUrl: String,
        val amount: String,
    )

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

    fun save(context: Context, pending: PendingDonation) {
        val saved = secretPrefs(context).edit()
            .putString(KEY_INVOICE_URL, pending.invoiceUrl)
            .putString(KEY_STATUS_URL, pending.statusUrl)
            .putString(KEY_AMOUNT, pending.amount)
            .commit()
        check(saved) { "Unable to securely save donation checkout state" }
        prefs(context).edit()
            // Remove the plaintext state and credentials written by the legacy flow.
            .remove(KEY_INVOICE_URL)
            .remove(KEY_STATUS_URL)
            .remove(KEY_AMOUNT)
            .remove("api_token")
            .remove("email")
            .remove("subscription_id")
            .apply()
    }

    fun get(context: Context): PendingDonation? {
        val p = prefs(context)
        val encrypted = secretPrefs(context)
        val securePending = PendingDonation(
            invoiceUrl = encrypted.getString(KEY_INVOICE_URL, "").orEmpty().trim(),
            statusUrl = encrypted.getString(KEY_STATUS_URL, "").orEmpty().trim(),
            amount = encrypted.getString(KEY_AMOUNT, "").orEmpty().trim(),
        )
        if (securePending.invoiceUrl.isNotBlank() && securePending.statusUrl.isNotBlank()) {
            p.edit()
                .remove(KEY_INVOICE_URL)
                .remove(KEY_STATUS_URL)
                .remove(KEY_AMOUNT)
                .remove("api_token")
                .remove("email")
                .remove("subscription_id")
                .apply()
            return securePending
        }

        // Migrate a pending legacy donation once, removing its old credential copies.
        p.edit()
            .remove("api_token")
            .remove("email")
            .remove("subscription_id")
            .apply()
        val invoiceUrl = p.getString(KEY_INVOICE_URL, "").orEmpty().trim()
        val statusUrl = p.getString(KEY_STATUS_URL, "").orEmpty().trim()
        val amount = p.getString(KEY_AMOUNT, "").orEmpty().trim()
        if (invoiceUrl.isBlank() || statusUrl.isBlank()) return null
        return PendingDonation(
            invoiceUrl = invoiceUrl,
            statusUrl = statusUrl,
            amount = amount,
        ).also { save(context, it) }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        secretPrefs(context).edit().clear().apply()
    }

    fun setAwaitingReturn(context: Context, awaiting: Boolean) {
        prefs(context).edit().putBoolean(KEY_AWAITING_RETURN, awaiting).apply()
    }

    fun isAwaitingReturn(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AWAITING_RETURN, false)
    }
}
