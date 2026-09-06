package com.adglasses.app.core.communication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

enum class CommunicationDelivery {
    Direct,
    SystemUi,
}

class CommunicationManager(context: Context) {
    private val appContext = context.applicationContext

    /** Direct call when permission exists, otherwise an explicit dialer handoff. */
    fun call(numberOrTarget: String): CommunicationDelivery {
        val requested = numberOrTarget.trim()
        require(requested.isNotEmpty()) { "Choose who to call" }
        val destination = resolvePhoneTarget(requested)
        val canCall = hasPermission(Manifest.permission.CALL_PHONE)
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        appContext.startActivity(
            Intent(action, Uri.parse("tel:${Uri.encode(destination)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return if (canCall) CommunicationDelivery.Direct else CommunicationDelivery.SystemUi
    }

    /** Direct SMS when permission exists, otherwise an explicit SMS-composer handoff. */
    fun text(numberOrTarget: String, message: String): CommunicationDelivery {
        val requested = numberOrTarget.trim()
        require(requested.isNotEmpty()) { "Choose who to message" }
        require(message.isNotBlank()) { "The message is empty" }
        val destination = resolvePhoneTarget(requested)
        val canSend = hasPermission(Manifest.permission.SEND_SMS)
        if (canSend) {
            @Suppress("DEPRECATION")
            SmsManager.getDefault().sendTextMessage(destination, null, message, null, null)
            return CommunicationDelivery.Direct
        }

        appContext.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(destination)}"))
                .putExtra("sms_body", message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return CommunicationDelivery.SystemUi
    }

    fun openNotificationAccess() {
        appContext.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Resolve human contact names locally before constructing tel:/smsto: URIs. Raw phone numbers
     * never need contacts permission; named targets do. Exact display-name matches win, while an
     * ambiguous partial match is rejected instead of risking a call/message to the wrong person.
     */
    private fun resolvePhoneTarget(raw: String): String {
        if (looksLikeDirectAddress(raw)) return raw
        require(hasPermission(Manifest.permission.READ_CONTACTS)) {
            "Allow Contacts access to call or message saved contacts by name"
        }

        val candidates = mutableListOf<ContactPhone>()
        appContext.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER),
            "${Phone.DISPLAY_NAME_PRIMARY} LIKE ?",
            arrayOf("%$raw%"),
            "${Phone.IS_PRIMARY} DESC",
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
            val numberColumn = cursor.getColumnIndexOrThrow(Phone.NUMBER)
            while (cursor.moveToNext() && candidates.size < 20) {
                val name = cursor.getString(nameColumn)?.trim().orEmpty()
                val number = cursor.getString(numberColumn)?.trim().orEmpty()
                if (name.isNotBlank() && number.isNotBlank()) {
                    candidates += ContactPhone(name = name, number = number)
                }
            }
        }

        require(candidates.isNotEmpty()) { "I couldn't find a saved contact matching $raw" }
        candidates.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let { return it.number }

        val distinctNames = candidates.map { it.name.lowercase() }.distinct()
        require(distinctNames.size == 1) {
            "More than one saved contact matches $raw. Use the full contact name or phone number."
        }
        return candidates.first().number
    }

    private fun looksLikeDirectAddress(value: String): Boolean =
        value.any(Char::isDigit) && value.all { char ->
            char.isDigit() || char in "+-() #*.,;"
        }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private data class ContactPhone(
        val name: String,
        val number: String,
    )
}
