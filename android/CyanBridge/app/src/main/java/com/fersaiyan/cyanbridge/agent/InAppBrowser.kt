package com.fersaiyan.cyanbridge.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object InAppBrowser {
    fun open(context: Context, url: String) {
        runCatching {
            val uri = Uri.parse(url)
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, uri)
        }.getOrElse {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(fallback)
        }
    }
}
