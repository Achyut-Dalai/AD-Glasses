package com.fersaiyan.cyanbridge.ai.image

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.databinding.ActivityExternalGeminiAutomationDiagnosticsBinding
import com.fersaiyan.cyanbridge.ui.CommunityPluginPrefs

class ExternalGeminiAutomationDiagnosticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExternalGeminiAutomationDiagnosticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExternalGeminiAutomationDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRunDiagnostics.setOnClickListener { runDiagnostics() }
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        runDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) runDiagnostics()
    }

    private fun runDiagnostics() {
        val session = ExternalImageAutomationStore.current(this)
        val selectedAssistantMode = intent.getStringExtra("assistant").orEmpty()
        val defaultAssistantPackage = DefaultAssistantResolver.packageName(this)
        val target = ImageAutomationTarget.forAssistantMode(selectedAssistantMode, defaultAssistantPackage)
        val targetPackage = target.packageNames.firstOrNull(::isPackageInstalled)
        val importedTarget = TaskerImageProfileStore.target(this)
        val importedVersion = TaskerImageProfileStore.version(this)
        val profileSupportsTarget = TaskerImageProfileCompatibility.supports(
            target = target,
            importedTarget = importedTarget,
            importedVersion = importedVersion,
        )
        val checks = listOf(
            check(
                title = "Selected CyanBridge image target",
                ok = target.imageAutomationSupported,
                detail = targetDetail(target, defaultAssistantPackage),
            ),
            check(
                title = "Phone default assistant",
                ok = defaultAssistantPackage != null,
                detail = defaultAssistantPackage ?: "Android did not report a default assistant",
            ),
            check(
                title = "Selected target app installed",
                ok = targetPackage != null,
                detail = targetPackage ?: target.packageNames.joinToString().ifBlank { "No image target selected" },
            ),
            check(
                title = "Tasker installed",
                ok = isPackageInstalled(ExternalImageAutomationIntents.TASKER_PACKAGE),
                detail = ExternalImageAutomationIntents.TASKER_PACKAGE,
            ),
            check(
                title = "AutoInput installed",
                ok = isPackageInstalled(ExternalImageAutomationIntents.AUTO_INPUT_PACKAGE),
                detail = ExternalImageAutomationIntents.AUTO_INPUT_PACKAGE,
            ),
            check(
                title = "AutoInput accessibility service enabled",
                ok = isAutoInputAccessibilityEnabled(),
                detail = "Enable an accessibility service owned by ${ExternalImageAutomationIntents.AUTO_INPUT_PACKAGE}",
            ),
            check(
                title = "Tasker profile/plugin enabled",
                ok = CommunityPluginPrefs.isTaskerAssistantEnabled(this),
                detail = "CyanBridge's imported-profile switch",
            ),
            check(
                title = "Imported profile supports selected target",
                ok = profileSupportsTarget,
                detail = profileDetail(target, importedTarget, importedVersion),
            ),
            check(
                title = "Phone unlocked",
                ok = !isDeviceLocked(),
                detail = "Unlock the phone before external UI automation",
            ),
            check(
                title = "Latest image URI readable",
                ok = session?.let { isUriReadable(it.imageUri) } == true,
                detail = session?.imageUri ?: "No image question has produced a URI yet",
            ),
            check(
                title = "Selected target can receive image shares",
                ok = targetPackage?.let(::canResolveImageShare) == true,
                detail = targetPackage ?: "Selected target package unavailable",
            ),
        )

        val firstFailure = checks.firstOrNull { !it.ok }
        binding.tvDiagnosticsResult.text = buildString {
            appendLine("Assistant image automation diagnostics")
            appendLine()
            checks.forEach { entry ->
                appendLine("${if (entry.ok) "[OK]" else "[FAIL]"} ${entry.title}: ${entry.detail}")
            }
            appendLine()
            if (firstFailure == null) {
                append("[OK] All checked stages are ready.")
            } else {
                append("Blocked at: ${firstFailure.title}. ${firstFailure.detail}")
            }
            session?.let {
                appendLine()
                appendLine()
                append("Last Tasker status: ${it.state.stage.wireName}")
                it.state.error?.takeIf(String::isNotBlank)?.let { error ->
                    append(" ($error)")
                }
            }
        }
    }

    private fun check(title: String, ok: Boolean, detail: String) = DiagnosticCheck(title, ok, detail)

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun isAutoInputAccessibilityEnabled(): Boolean {
        if (Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
            return false
        }
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it.packageName == ExternalImageAutomationIntents.AUTO_INPUT_PACKAGE }
    }

    private fun isDeviceLocked(): Boolean {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return keyguard.isDeviceLocked
    }

    private fun isUriReadable(uriString: String): Boolean = runCatching {
        contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
            input.read(ByteArray(1))
        } != null
    }.getOrDefault(false)

    private fun canResolveImageShare(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setType("image/jpeg")
            setPackage(packageName)
        }
        @Suppress("DEPRECATION")
        return intent.resolveActivity(packageManager) != null
    }

    private fun targetDetail(target: ImageAutomationTarget, defaultAssistantPackage: String?): String = when (target) {
        ImageAutomationTarget.GEMINI -> "Gemini image profile (${target.wireName})"
        ImageAutomationTarget.CHATGPT ->
            "ChatGPT selected (${target.wireName}); image automation is intentionally unavailable pending end-to-end validation"
        ImageAutomationTarget.NONE ->
            "${defaultAssistantPackage ?: "Unknown assistant"}; voice launch only, image questions unavailable"
    }

    private fun profileDetail(
        target: ImageAutomationTarget,
        importedTarget: String?,
        importedVersion: String?,
    ): String {
        if (!target.imageAutomationSupported) {
            return "No supported ${target.label} image profile is available"
        }
        val reported = if (importedTarget == null || importedVersion == null) {
            "no profile version reported"
        } else {
            "$importedTarget $importedVersion"
        }
        return "reported=$reported; required=${target.wireName} ${target.requiredProfileVersion}"
    }

    private data class DiagnosticCheck(
        val title: String,
        val ok: Boolean,
        val detail: String,
    )
}
