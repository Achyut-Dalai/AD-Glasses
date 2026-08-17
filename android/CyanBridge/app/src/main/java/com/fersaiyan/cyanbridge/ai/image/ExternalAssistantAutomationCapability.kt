package com.fersaiyan.cyanbridge.ai.image

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

data class ExternalAssistantAutomationCapability(
    val target: ImageAutomationTarget,
    val targetPackage: String?,
    val taskerInstalled: Boolean,
    val autoInputInstalled: Boolean,
    val autoInputAccessibilityEnabled: Boolean,
    val profileCompatible: Boolean,
    val imageShareAvailable: Boolean,
    val phoneLocked: Boolean,
)

object ExternalAssistantAutomationPolicy {
    /**
     * Voice Tasker handoff is a background broadcast path. It must not depend on an
     * installed/default Gemini or ChatGPT app, AutoInput, an unlocked screen, or a
     * share target. Those requirements only apply to the legacy external-image UI path.
     */
    fun voiceBlockingReason(capability: ExternalAssistantAutomationCapability): String? = when {
        !capability.taskerInstalled ->
            "Install Tasker and enable the AD Glasses background profile first."
        !capability.profileCompatible ->
            "Import and verify the AD Glasses Tasker profile first."
        else -> null
    }

    fun imageBlockingReason(capability: ExternalAssistantAutomationCapability): String? = when {
        capability.target == ImageAutomationTarget.NONE ->
            "Set Gemini or ChatGPT as your phone's default assistant first."
        capability.targetPackage == null ->
            "Install or update ${capability.target.label} first."
        !capability.taskerInstalled ->
            "Install Tasker and complete Gemini / ChatGPT automation setup first."
        !capability.profileCompatible ->
            "Import and verify the ${capability.target.label} CyanBridge Tasker profile first."
        capability.phoneLocked ->
            "Unlock your phone before using external image automation."
        !capability.autoInputInstalled ->
            "Install AutoInput and complete Gemini / ChatGPT automation setup first."
        !capability.autoInputAccessibilityEnabled ->
            "Enable AutoInput accessibility before using external image questions."
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
            taskerInstalled = isPackageInstalled(context, ExternalImageAutomationIntents.TASKER_PACKAGE),
            autoInputInstalled = isPackageInstalled(context, ExternalImageAutomationIntents.AUTO_INPUT_PACKAGE),
            autoInputAccessibilityEnabled = isAutoInputAccessibilityEnabled(context),
            profileCompatible = TaskerImageProfileCompatibility.supports(
                target = target,
                importedTarget = TaskerImageProfileStore.target(context),
                importedVersion = TaskerImageProfileStore.version(context),
            ),
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
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            setPackage(packageName)
        }
        return intent.resolveActivity(context.packageManager) != null
    }

    private fun isAutoInputAccessibilityEnabled(context: Context): Boolean {
        if (Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').mapNotNull(ComponentName::unflattenFromString).any { it.packageName == ExternalImageAutomationIntents.AUTO_INPUT_PACKAGE }
    }

    private fun isDeviceLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) keyguard.isDeviceLocked else {
            @Suppress("DEPRECATION")
            keyguard.isKeyguardLocked
        }
    }
}
