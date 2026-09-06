package com.adglasses.app.core.communication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class CommunicationManager(context: Context) {
    private val appContext = context.applicationContext

    fun call(number: String) {
        val normalized = number.trim()
        require(normalized.isNotEmpty())
        val canCall = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        appContext.startActivity(Intent(action, Uri.parse("tel:${Uri.encode(normalized)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun text(number: String, message: String) {
        val normalized = number.trim()
        require(normalized.isNotEmpty())
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            SmsManager.getDefault().sendTextMessage(normalized, null, message, null, null)
        } else {
            appContext.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(normalized)}"))
                    .putExtra("sms_body", message)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openNotificationAccess() {
        appContext.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
