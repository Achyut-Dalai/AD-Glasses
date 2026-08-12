package com.achyut.adglasses.ai.image

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Resolves Android's selected assistant independently of CyanBridge's selected image target. */
object DefaultAssistantResolver {
    fun packageName(context: Context): String? {
        ComponentName.unflattenFromString(
            Settings.Secure.getString(
                context.contentResolver,
                "voice_interaction_service",
            ),
        )?.packageName?.let { return it }

        return Intent(Intent.ACTION_VOICE_COMMAND)
            .resolveActivity(context.packageManager)
            ?.packageName
    }
}
