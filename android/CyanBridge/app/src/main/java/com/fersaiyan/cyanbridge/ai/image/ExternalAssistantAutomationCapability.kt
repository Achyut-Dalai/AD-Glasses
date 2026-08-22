package com.fersaiyan.cyanbridge.ai.image

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build

data class ExternalAssistantAutomationCapability(
    val target: ImageAutomationTarget,
    val targetPackage: String?,
    val adAccessibilityConnected: Boolean,
    val imageShareAvailable: Boolean,
    val phoneLocked: Boolean,
)

object ExternalAssistantAutomationPolicy {
    fun voiceBlockingReason(capability: ExternalAssistantAutomationCapability): String? = when {
        capability.target == ImageAutomationTarget.NONE ->
            "Set Gemini or ChatGPT as your phone's default assistant first."
        capability.targetPackage == null -> "Install or update ${capability.target.label} first."
        else -> null
    }

    fun imageBlockingReason(capability: ExternalAssistantAutomationCapability): String? = when {
        capability.target == ImageAutomationTarget.NONE ->
            "Set Gemini or ChatGPT as your phone's default assistant first."
        capability.targetPackage == null ->
            "Install or update ${capability.target.label} first."
        capability.phoneLocked ->
            "Unlock your phone before using external image automation."
        !capability.adAccessibilityConnected ->
            "Enable AD Glasses accessibility access for assistant image handoff."
        !capability.imageShareAvailable ->
            "${capability.target.label} cannot receive image shares on this phone."
        else -> null
    }
}

object ExternalAssistantAutomationInspector {
    fun inspect(context: Context): ExternalAssistantAutomationCapability {
        val target = ImageAutomationTarget.forDefaultAssistant(DefaultAssistantResolver.packageName(context))
        val targetPackage = target.packageNames.firstOrNull { isPackageInstalled(context, it) }
        return ExternalAssistantAutomationCapability(
            target = target,
            targetPackage = targetPackage,
            adAccessibilityConnected = com.fersaiyan.cyanbridge.localagent.LocalAgentAccessibilityBridge.isConnected(),
            imageShareAvailable = targetPackage?.let { canResolveImageShare(context, it) } == true,
            phoneLocked = isDeviceLocked(context),
        )
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun canResolveImageShare(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "image/jpeg"; setPackage(packageName) }
        return intent.resolveActivity(context.packageManager) != null
    }

    private fun isDeviceLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) keyguard.isDeviceLocked else {
            @Suppress("DEPRECATION")
            keyguard.isKeyguardLocked
        }
    }
}
