package com.fersaiyan.cyanbridge.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs as AutomationPrefs
import com.fersaiyan.cyanbridge.agent.LocalModelsConfigureActivity
import com.fersaiyan.cyanbridge.agent.ProSubscriptionActivity
import com.fersaiyan.cyanbridge.agent.ProSubscriptionPrefs
import com.fersaiyan.cyanbridge.agent.ProSubscriptionSettingsActivity
import com.fersaiyan.cyanbridge.agent.ProSubscriptionVerifier
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.ai.vision.VisionProfile
import com.fersaiyan.cyanbridge.ai.vision.VisionProfilePreferences
import com.fersaiyan.cyanbridge.shared.chat.ChatRole
import com.fersaiyan.cyanbridge.chat.ChatStore
import com.fersaiyan.cyanbridge.localagent.LocalAgentController
import com.fersaiyan.cyanbridge.localagent.LocalAgentIntents
import com.fersaiyan.cyanbridge.localagent.LocalAgentPrefs as AgentRuntimePrefs
import com.fersaiyan.cyanbridge.localagent.accessibility.LocalAgentAccessibilityService
import com.fersaiyan.cyanbridge.localagent.daily.DailyFactsReminderScheduler
import com.fersaiyan.cyanbridge.localagent.dailyfacts.DailyBulletsSettings
import com.fersaiyan.cyanbridge.localagent.memory.LocalAgentMemoryStore
import com.fersaiyan.cyanbridge.localagent.userfacts.ChatMemoryPrefs
import com.fersaiyan.cyanbridge.localmodels.session.LocalChatSessionManager
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCapturePrefs
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCaptureService
import com.fersaiyan.cyanbridge.memoryvault.MemoryModeManager
import com.fersaiyan.cyanbridge.shared.settings.MemoryPrivacyMode
import com.fersaiyan.cyanbridge.shared.settings.MemorySourceType
import com.fersaiyan.cyanbridge.memoryvault.MemorySyncPreparationService
import com.fersaiyan.cyanbridge.memoryvault.MemoryVaultBootstrap
import com.fersaiyan.cyanbridge.memoryvault.MemoryVaultService
import com.fersaiyan.cyanbridge.memoryvault.VaultLockStateManager
import com.fersaiyan.cyanbridge.privacy.LocalDataBackupManager
import com.fersaiyan.cyanbridge.privacy.LocalDataClearer
import com.fersaiyan.cyanbridge.privacy.PrivacyPrefs
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.shared.settings.CaptureSource
import com.fersaiyan.cyanbridge.audio.MeetingCapturePrefs
import com.fersaiyan.cyanbridge.audio.MeetingCaptureService
import com.fersaiyan.cyanbridge.ui.appearance.AppearanceActivity
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.debug.DebugLogSupport
import com.fersaiyan.cyanbridge.ui.localagent.AppBlacklistActivity
import com.fersaiyan.cyanbridge.ui.localagent.DailyFactsActivity
import com.fersaiyan.cyanbridge.ui.localagent.DailySummaryActivity
import com.fersaiyan.cyanbridge.ui.localagent.PendingActionsActivity
import com.fersaiyan.cyanbridge.ui.localagent.ScreenCapturesActivity
import com.fersaiyan.cyanbridge.ui.recordings.RecordingsListActivity
import com.fersaiyan.cyanbridge.shared.settings.SettingsSection
import com.fersaiyan.cyanbridge.shared.ui.settings.SettingsScreen
import com.fersaiyan.cyanbridge.shared.ui.settings.SettingsScreenActions
import com.fersaiyan.cyanbridge.shared.ui.settings.SettingsUiState
import com.fersaiyan.cyanbridge.ui.localization.AppLanguage
import com.fersaiyan.cyanbridge.ui.localization.AppLanguagePreferences
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity(), SettingsScreenActions {

    private var settingsUiState by mutableStateOf(SettingsUiState())
    private var expandedSections by mutableStateOf<Set<SettingsSection>>(emptySet())
    private var agentReceiverRegistered = false
    private var meetingReceiverRegistered = false
    private var checkedExistingAutoAudioPermission = false

    private val sectionPrefs by lazy {
        getSharedPreferences("settings_sections", MODE_PRIVATE)
    }

    private val exportDataLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri?.let(::exportLocalDataToUri)
    }

    private val importDataLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(::importLocalDataFromUri)
    }

    private val agentStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != LocalAgentIntents.ACTION_STATUS_CHANGED) return
            intent.getStringExtra(LocalAgentIntents.EXTRA_STATUS)?.let {
                AgentRuntimePrefs.setStatus(this@SettingsActivity, it)
            }
            intent.getStringExtra(LocalAgentIntents.EXTRA_LAST_ERROR)?.let {
                AgentRuntimePrefs.setLastError(this@SettingsActivity, it)
            }
            refreshSettingsUi()
        }
    }

    private val meetingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != MeetingCaptureService.ACTION_STATE) return
            val source = intent.getStringExtra(MeetingCaptureService.EXTRA_SOURCE)
                ?.let { runCatching { CaptureSource.valueOf(it) }.getOrNull() }
            settingsUiState = settingsUiState.copy(
                meetingRecording = intent.getBooleanExtra(MeetingCaptureService.EXTRA_IS_RECORDING, false),
                meetingCaptureSource = source,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        expandedSections = SettingsSection.entries.filterTo(mutableSetOf()) { section ->
            sectionPrefs.getBoolean(sectionPreferenceKey(section), true)
        }
        refreshSettingsUi()

        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                SettingsScreen(
                    state = settingsUiState,
                    expandedSections = expandedSections,
                    onToggleSection = ::toggleSection,
                    actions = this@SettingsActivity,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSettingsUi()
        if (ProSubscriptionPrefs.isSubscribed(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                ProSubscriptionVerifier.verifyNow(this@SettingsActivity)
                withContext(Dispatchers.Main) { refreshSettingsUi() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerLocalReceivers()
        LocalAgentController.requestStatus(this)
        ensureExistingAutoAudioNotificationPermission()
        refreshSettingsUi()
    }

    override fun onStop() {
        unregisterLocalReceivers()
        super.onStop()
    }

    private fun registerLocalReceivers() {
        val broadcasts = LocalBroadcastManager.getInstance(this)
        if (!agentReceiverRegistered) {
            broadcasts.registerReceiver(agentStatusReceiver, IntentFilter(LocalAgentIntents.ACTION_STATUS_CHANGED))
            agentReceiverRegistered = true
        }
        if (!meetingReceiverRegistered) {
            broadcasts.registerReceiver(meetingStateReceiver, IntentFilter(MeetingCaptureService.ACTION_STATE))
            meetingReceiverRegistered = true
        }
    }

    private fun unregisterLocalReceivers() {
        val broadcasts = LocalBroadcastManager.getInstance(this)
        if (agentReceiverRegistered) {
            broadcasts.unregisterReceiver(agentStatusReceiver)
            agentReceiverRegistered = false
        }
        if (meetingReceiverRegistered) {
            broadcasts.unregisterReceiver(meetingStateReceiver)
            meetingReceiverRegistered = false
        }
    }

    private fun toggleSection(section: SettingsSection) {
        expandedSections = if (section in expandedSections) {
            expandedSections - section
        } else {
            expandedSections + section
        }
        sectionPrefs.edit()
            .putBoolean(sectionPreferenceKey(section), section in expandedSections)
            .apply()
    }

    private fun refreshSettingsUi() {
        MemoryVaultBootstrap.ensureInitialized(this)
        val meeting = MeetingCapturePrefs.getState(this)
        val autoAudioEnabled = AutoAudioCapturePrefs.isEnabled(this)
        val providerType = AutomationPrefs.getProviderType(this)
        val memoryMode = MemoryModeManager.getSelectedMode(this)
        syncAgentProviderToAiProvider(providerType)
        settingsUiState = SettingsUiState(
            isProSubscribed = ProSubscriptionPrefs.isActiveLocally(this),
            proPlan = formatPlan(ProSubscriptionPrefs.getPlan(this)),
            appLanguageLabel = AppLanguagePreferences.selected(this).displayName(this),
            visionProfileLabel = localizedVisionProfileName(VisionProfilePreferences.get(this).profile),
            providerType = providerType,
            localAgentAutomationEnabled = AutomationPrefs.isLocalAgentAutomationEnabled(this),
            localAgentRequireConfirmation = AutomationPrefs.isRequireConfirmationEnabled(this),
            localAgentMaxSteps = AutomationPrefs.getMaxSteps(this),
            accessibilityEnabled = isLocalAgentAccessibilityServiceEnabled(),
            autoCaptureEnabled = AutomationPrefs.isAutoCaptureEnabled(this) &&
                MemoryModeManager.isScreenOcrCaptureEnabled(this),
            captureIntervalMinutes = AutomationPrefs.getCaptureIntervalMin(this),
            dailyFactsReminderEnabled = AutomationPrefs.isDailyFactsReminderEnabled(this),
            dailySummaryRefreshHours = AutomationPrefs.getDailySummaryAutoRefreshHours(this),
            autoSaveDailyFactsEnabled = ChatMemoryPrefs.isAutoSaveDailyFactsEnabled(this),
            extractUserFactCandidatesEnabled = ChatMemoryPrefs.isExtractUserFactCandidatesEnabled(this),
            maxTokensPerBullet = DailyBulletsSettings.getMaxTokensPerBullet(this),
            memoryMode = memoryMode,
            memoryModeAvailability = MemoryModeManager.modeAvailabilityText(memoryMode),
            memorySyncStatus = "Encrypted Sync: ${MemoryModeManager.modeAvailabilityText(MemoryPrivacyMode.ENCRYPTED_SYNC)}",
            memoryCloudStatus = "Cloud: ${MemoryModeManager.modeAvailabilityText(MemoryPrivacyMode.FAST_CLOUD_MEMORY)}\n" +
                "Confidential: ${MemoryModeManager.modeAvailabilityText(MemoryPrivacyMode.CONFIDENTIAL_CLOUD_BETA)}",
            syncExplicit = MemoryModeManager.isSourceSyncEnabled(this, MemorySourceType.EXPLICIT_USER_FACT),
            syncDaily = MemoryModeManager.isSourceSyncEnabled(this, MemorySourceType.AUTO_DAILY_FACT),
            syncOcr = MemoryModeManager.isSourceSyncEnabled(this, MemorySourceType.SCREEN_OCR),
            syncDerived = MemoryModeManager.isSourceSyncEnabled(this, MemorySourceType.DERIVED_SUMMARY),
            ocrRetentionDays = MemoryModeManager.getScreenOcrRetentionDays(this),
            vaultLocked = VaultLockStateManager.isLocked(this),
            vaultRequiresPassphrase = VaultLockStateManager.requiresPassphrase(this),
            transcriptStorageEnabled = PrivacyPrefs.isTranscriptStorageEnabled(this),
            redactNamesEnabled = PrivacyPrefs.isRedactNamesEnabled(this),
            includeFullTranscriptionInExports = PrivacyPrefs.isIncludeFullTranscriptionInExportsEnabled(this),
            autoAudioCaptureEnabled = autoAudioEnabled,
            autoAudioVisualNotesEnabled = AutoAudioCapturePrefs.isVisualNotesEnabled(this),
            autoAudioSpeechExtendEnabled = AutoAudioCapturePrefs.isSpeechExtendEnabled(this),
            autoAudioLoopsPerSync = AutoAudioCapturePrefs.getLoopsPerSync(this),
            autoAudioDebugText = buildAutoAudioDebugText(autoAudioEnabled),
            requireActionConfirmation = AgentRuntimePrefs.isRequireActionConfirmationEnabled(this),
            autoExecuteLowRisk = AgentRuntimePrefs.isAutoExecuteLowRiskEnabled(this),
            agentStatus = AgentRuntimePrefs.getStatus(this),
            agentLastError = AgentRuntimePrefs.getLastError(this),
            meetingRecording = meeting.isRecording,
            meetingCaptureSource = meeting.source,
        )
    }

    override fun onDestinationSelected(destination: AppDestination) {
        val target = when (destination) {
            AppDestination.GLASSES -> Intent(this, MainActivity::class.java)
            AppDestination.CHATS -> buildRecentChatIntent()
            AppDestination.MEDIA -> Intent(this, RecordingsListActivity::class.java)
            AppDestination.PLUGINS -> Intent(this, CommunityPluginsActivity::class.java)
            AppDestination.SETTINGS -> return
        }
        target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(target)
    }

    override fun openAppearance() {
        startActivity(Intent(this, AppearanceActivity::class.java))
    }

    override fun openAppLanguageSelection() {
        val languages = AppLanguage.entries
        val selected = AppLanguagePreferences.selected(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.language_selection_title)
            .setSingleChoiceItems(
                languages.map { it.displayName(this) }.toTypedArray(),
                languages.indexOf(selected),
            ) { dialog, which ->
                AppLanguagePreferences.select(this, languages[which])
                dialog.dismiss()
                refreshSettingsUi()
            }
            .show()
    }

    override fun openVisionProfileSelection() {
        val profiles = VisionProfile.entries
        val selected = VisionProfilePreferences.get(this).profile
        AlertDialog.Builder(this)
            .setTitle(R.string.vision_profile_selection_title)
            .setSingleChoiceItems(
                profiles.map(::localizedVisionProfileName).toTypedArray(),
                profiles.indexOf(selected),
            ) { dialog, which ->
                VisionProfilePreferences.setProfile(this, profiles[which])
                dialog.dismiss()
                refreshSettingsUi()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun editVisionInstructions() {
        showTextEditorDialog(
            title = getString(R.string.vision_custom_instructions_title),
            initial = VisionProfilePreferences.get(this).customInstructions,
            hint = getString(R.string.vision_custom_instructions_hint),
        ) { instructions ->
            VisionProfilePreferences.setCustomInstructions(this, instructions)
            refreshSettingsUi()
        }
    }

    override fun openSubscription() {
        val target = if (ProSubscriptionPrefs.isActiveLocally(this)) {
            ProSubscriptionSettingsActivity::class.java
        } else {
            ProSubscriptionActivity::class.java
        }
        startActivity(Intent(this, target))
    }

    override fun setProviderType(type: AgentProviderType) {
        AutomationPrefs.setProviderType(this, type)
        syncAgentProviderToAiProvider(type)
        if (type != AgentProviderType.LOCAL_AGENT) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { LocalChatSessionManager.unload() }
            }
        }
        refreshSettingsUi()
    }

    override fun openLocalModels() {
        startActivity(Intent(this, LocalModelsConfigureActivity::class.java))
    }

    override fun setLocalAgentAutomationEnabled(enabled: Boolean) {
        AutomationPrefs.setLocalAgentAutomationEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun setLocalAgentRequireConfirmation(enabled: Boolean) {
        AutomationPrefs.setRequireConfirmationEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun setLocalAgentMaxSteps(value: Int) {
        AutomationPrefs.setMaxSteps(this, value)
        refreshSettingsUi()
    }

    override fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle(com.fersaiyan.cyanbridge.R.string.onboarding_accessibility_disclosure_title)
            .setMessage(com.fersaiyan.cyanbridge.R.string.onboarding_accessibility_disclosure_body)
            .setNegativeButton("Not now", null)
            .setPositiveButton("Continue") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .onFailure {
                        Toast.makeText(this, "Unable to open accessibility settings", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    override fun setAutoCaptureEnabled(enabled: Boolean) {
        AutomationPrefs.setAutoCaptureEnabled(this, enabled)
        MemoryModeManager.setScreenOcrCaptureEnabled(this, enabled)
        Toast.makeText(this, if (enabled) "Auto-capture enabled" else "Auto-capture disabled", Toast.LENGTH_SHORT).show()
        refreshSettingsUi()
    }

    override fun setCaptureIntervalMinutes(value: Int) {
        AutomationPrefs.setCaptureIntervalMin(this, value)
        refreshSettingsUi()
    }

    override fun openBlacklistApps() {
        startActivity(Intent(this, AppBlacklistActivity::class.java))
    }

    override fun openScreenCaptures() {
        startActivity(Intent(this, ScreenCapturesActivity::class.java))
    }

    override fun setDailyFactsReminderEnabled(enabled: Boolean) {
        if (enabled && !hasPostNotificationsPermission()) {
            XXPermissions.with(this)
                .permission(Permission.POST_NOTIFICATIONS)
                .request { _, allGranted ->
                    updateDailyFactsReminder(allGranted)
                }
            return
        }
        updateDailyFactsReminder(enabled)
    }

    private fun updateDailyFactsReminder(enabled: Boolean) {
        AutomationPrefs.setDailyFactsReminderEnabled(this, enabled)
        DailyFactsReminderScheduler.scheduleIfEnabled(this, enabled = enabled)
        Toast.makeText(
            this,
            if (enabled) "Daily facts reminder enabled" else "Daily facts reminder disabled",
            Toast.LENGTH_SHORT,
        ).show()
        refreshSettingsUi()
    }

    override fun openDailyFactsDraft() {
        startActivity(
            Intent(this, DailyFactsActivity::class.java)
                .putExtra(DailyFactsActivity.EXTRA_MODE, DailyFactsActivity.MODE_DRAFT),
        )
    }

    override fun openConfirmedDailyFacts() {
        startActivity(
            Intent(this, DailyFactsActivity::class.java)
                .putExtra(DailyFactsActivity.EXTRA_MODE, DailyFactsActivity.MODE_CONFIRMED),
        )
    }

    override fun openDailySummary() {
        startActivity(Intent(this, DailySummaryActivity::class.java))
    }

    override fun setDailySummaryRefreshHours(value: Int) {
        AutomationPrefs.setDailySummaryAutoRefreshHours(this, value)
        refreshSettingsUi()
    }

    override fun setAutoSaveDailyFactsEnabled(enabled: Boolean) {
        ChatMemoryPrefs.setAutoSaveDailyFactsEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun setExtractUserFactCandidatesEnabled(enabled: Boolean) {
        ChatMemoryPrefs.setExtractUserFactCandidatesEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun editBulletPrompt() {
        val current = DailyBulletsSettings.getCustomBulletPrompt(this).ifBlank { DEFAULT_BULLET_PROMPT }
        showTextEditorDialog(
            title = "Bullet Generation Prompt",
            initial = current,
            hint = "Leave empty to use default prompt",
        ) { updated ->
            DailyBulletsSettings.setCustomBulletPrompt(this, updated)
            Toast.makeText(this, "Custom bullet prompt saved", Toast.LENGTH_SHORT).show()
        }
    }

    override fun resetBulletPrompt() {
        DailyBulletsSettings.setCustomBulletPrompt(this, "")
        Toast.makeText(this, "Bullet prompt reset to default", Toast.LENGTH_SHORT).show()
    }

    override fun setMaxTokensPerBullet(value: Int) {
        DailyBulletsSettings.setMaxTokensPerBullet(this, value)
        refreshSettingsUi()
    }

    override fun editAgentPersona() {
        LocalAgentMemoryStore.ensureSeedFiles(this)
        val file = LocalAgentMemoryStore.agentPersonaFile(this)
        showTextEditorDialog(
            title = "Agent personality",
            initial = LocalAgentMemoryStore.readText(file),
        ) { updated ->
            LocalAgentMemoryStore.writeText(file, updated)
            Toast.makeText(this, "Saved agent personality", Toast.LENGTH_SHORT).show()
        }
    }

    override fun editUserFacts() {
        LocalAgentMemoryStore.ensureSeedFiles(this)
        val file = LocalAgentMemoryStore.userFactsFile(this)
        showTextEditorDialog(
            title = "User facts",
            initial = LocalAgentMemoryStore.readText(file),
        ) { updated ->
            LocalAgentMemoryStore.writeText(file, updated)
            Toast.makeText(this, "Saved user facts", Toast.LENGTH_SHORT).show()
        }
    }

    override fun viewContextDebug() {
        val debug = AgentRuntimePrefs.getLastContextInjectionDebug(this)
        val atMs = AgentRuntimePrefs.getLastContextInjectionAtMs(this)
        val message = if (debug.isBlank()) {
            "No context injection recorded yet."
        } else {
            "Last injected: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(atMs))}\n\n$debug"
        }
        AlertDialog.Builder(this)
            .setTitle("Context Injection Debug")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun setMemoryMode(mode: MemoryPrivacyMode) {
        if (!ProSubscriptionPrefs.isActiveLocally(this) && mode != MemoryPrivacyMode.PRIVATE_LOCAL) {
            Toast.makeText(this, "This memory mode requires a Pro subscription", Toast.LENGTH_SHORT).show()
            return
        }
        MemoryModeManager.setSelectedMode(this, mode)
        if (mode != MemoryPrivacyMode.ENCRYPTED_SYNC) {
            lifecycleScope.launch(Dispatchers.IO) {
                MemorySyncPreparationService.cancelAllQueued("Mode switched away from Encrypted Sync")
            }
        }
        refreshSettingsUi()
    }

    override fun setMemorySync(source: MemorySourceType, enabled: Boolean) {
        MemoryModeManager.setSourceSyncEnabled(this, source, enabled)
        if (!enabled) {
            lifecycleScope.launch(Dispatchers.IO) {
                MemorySyncPreparationService.cancelAllQueued("Sync eligibility tightened for ${source.name.lowercase()}")
            }
        }
        refreshSettingsUi()
    }

    override fun setOcrRetentionDays(value: Int) {
        MemoryModeManager.setScreenOcrRetentionDays(this, value)
        lifecycleScope.launch(Dispatchers.IO) {
            MemoryVaultService.enforceScreenOcrRetention(this@SettingsActivity)
        }
        refreshSettingsUi()
    }

    override fun deletePassiveCapture() {
        AlertDialog.Builder(this)
            .setTitle("Delete passive OCR capture?")
            .setMessage("This deletes local OCR snapshots and their search index artifacts. This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    LocalAgentMemoryStore.deleteAllPassiveCapture(this@SettingsActivity)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Passive OCR capture deleted", Toast.LENGTH_SHORT).show()
                        refreshSettingsUi()
                    }
                }
            }
            .show()
    }

    override fun lockVault() {
        VaultLockStateManager.lock(this)
        Toast.makeText(this, "Vault locked", Toast.LENGTH_SHORT).show()
        refreshSettingsUi()
    }

    override fun unlockVault() {
        if (VaultLockStateManager.requiresPassphrase(this)) {
            showPassphraseDialog("Unlock vault") { passphrase ->
                val unlocked = VaultLockStateManager.unlockWithPassphrase(this, passphrase.toCharArray())
                Toast.makeText(this, if (unlocked) "Vault unlocked" else "Invalid passphrase", Toast.LENGTH_SHORT).show()
                refreshSettingsUi()
            }
        } else {
            val unlocked = VaultLockStateManager.unlockWithDevice(this)
            Toast.makeText(this, if (unlocked) "Vault unlocked" else "Unable to unlock vault", Toast.LENGTH_SHORT).show()
            refreshSettingsUi()
        }
    }

    override fun setVaultPassphrase() {
        showPassphraseDialog("Set vault passphrase") { passphrase ->
            val set = VaultLockStateManager.setPassphrase(this, passphrase.toCharArray())
            Toast.makeText(
                this,
                if (set) "Passphrase set. Vault locked." else "Could not set passphrase. Unlock vault first.",
                Toast.LENGTH_LONG,
            ).show()
            refreshSettingsUi()
        }
    }

    override fun clearVaultPassphrase() {
        VaultLockStateManager.clearPassphrase(this)
        Toast.makeText(this, "Passphrase requirement cleared", Toast.LENGTH_SHORT).show()
        refreshSettingsUi()
    }

    override fun resetVault() {
        AlertDialog.Builder(this)
            .setTitle("Reset memory vault?")
            .setMessage("This removes encrypted memory payloads, policy metadata, sync queue state, and lock keys. Existing plain files remain. This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    LocalAgentMemoryStore.resetVault(this@SettingsActivity)
                    LocalAgentMemoryStore.ensureSeedFiles(this@SettingsActivity)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Memory vault reset", Toast.LENGTH_LONG).show()
                        refreshSettingsUi()
                    }
                }
            }
            .show()
    }

    override fun setTranscriptStorageEnabled(enabled: Boolean) {
        PrivacyPrefs.setTranscriptStorageEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun setRedactNamesEnabled(enabled: Boolean) {
        PrivacyPrefs.setRedactNamesEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun setIncludeFullTranscriptionEnabled(enabled: Boolean) {
        PrivacyPrefs.setIncludeFullTranscriptionInExportsEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun setAutoAudioCaptureEnabled(enabled: Boolean) {
        if (enabled && !hasPostNotificationsPermission()) {
            XXPermissions.with(this)
                .permission(Permission.POST_NOTIFICATIONS)
                .request { _, allGranted ->
                    if (allGranted) {
                        enableAutoAudioCapture()
                    } else {
                        Toast.makeText(
                            this,
                            "Notifications permission denied. Auto audio capture needs a foreground notification.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    refreshSettingsUi()
                }
            return
        }
        if (enabled) enableAutoAudioCapture() else disableAutoAudioCapture()
        refreshSettingsUi()
    }

    override fun setAutoAudioVisualNotesEnabled(enabled: Boolean) {
        AutoAudioCapturePrefs.setVisualNotesEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun setAutoAudioSpeechExtendEnabled(enabled: Boolean) {
        AutoAudioCapturePrefs.setSpeechExtendEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun setAutoAudioLoopsPerSync(value: Int) {
        if (value !in 1..96) return
        AutoAudioCapturePrefs.setLoopsPerSync(this, value)
        refreshSettingsUi()
    }

    override fun exportLocalData() {
        val fileName = "cyanbridge_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.zip"
        exportDataLauncher.launch(fileName)
    }

    override fun importLocalData() {
        AlertDialog.Builder(this)
            .setTitle("Import local data?")
            .setMessage("This will overwrite current local chats, memory files, recordings, and settings from the selected backup ZIP.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import") { _, _ ->
                importDataLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            }
            .show()
    }

    override fun clearLocalData() {
        AlertDialog.Builder(this)
            .setTitle("Clear local data?")
            .setMessage("This will delete all chats, notes, capture sessions, and audio recordings stored on this device. This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                Toast.makeText(this, "Clearing data...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch(Dispatchers.IO) {
                    val result = LocalDataClearer.clearAll(this@SettingsActivity)
                    withContext(Dispatchers.Main) {
                        val message = if (result.errors.isEmpty()) {
                            "Local data cleared (deleted files: ${result.deletedFiles})"
                        } else {
                            "Cleared with warnings: ${result.errors.joinToString()}"
                        }
                        Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
                        refreshSettingsUi()
                    }
                }
            }
            .show()
    }

    override fun setRequireActionConfirmation(enabled: Boolean) {
        AgentRuntimePrefs.setRequireActionConfirmationEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun setAutoExecuteLowRisk(enabled: Boolean) {
        AgentRuntimePrefs.setAutoExecuteLowRiskEnabled(this, enabled)
        refreshSettingsUi()
    }

    override fun openPendingActions() {
        startActivity(Intent(this, PendingActionsActivity::class.java))
    }

    override fun startAgent() {
        runAgentCommand("Starting...") { LocalAgentController.start(this) }
    }

    override fun stopAgent() {
        runAgentCommand("Stopping...") { LocalAgentController.stop(this) }
    }

    override fun runAgentDemo() {
        Toast.makeText(
            this,
            "Demo: I will read the screen content through your glasses in 5 seconds...",
            Toast.LENGTH_LONG,
        ).show()
        runAgentCommand("Running demo...") { LocalAgentController.demo(this) }
    }

    override fun sendDebugLogs() {
        showLogSubmissionDialog()
    }

    override fun stopMeetingCapture() {
        MeetingCaptureService.stop(this)
    }

    private fun runAgentCommand(
        optimisticStatus: String,
        command: () -> LocalAgentController.CommandResult,
    ) {
        val result = command()
        if (result.ok) {
            AgentRuntimePrefs.setStatus(this, optimisticStatus)
            AgentRuntimePrefs.clearLastError(this)
        } else {
            AgentRuntimePrefs.setStatus(this, "Error")
            AgentRuntimePrefs.setLastError(this, result.error ?: result.userMessage)
        }
        Toast.makeText(this, result.userMessage, Toast.LENGTH_SHORT).show()
        LocalAgentController.requestStatus(this)
        refreshSettingsUi()
    }

    private fun exportLocalDataToUri(uri: Uri) {
        Toast.makeText(this, "Exporting data...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { LocalDataBackupManager.exportToZip(this@SettingsActivity, uri) }
                .onSuccess { result ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Export complete: ${result.threadCount} chats, ${result.messageCount} messages, ${result.memoryFileCount} memory files, ${result.recordingFileCount} recordings, ${result.vaultItemCount} vault items.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun importLocalDataFromUri(uri: Uri) {
        Toast.makeText(this, "Importing data...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { LocalDataBackupManager.importFromZip(this@SettingsActivity, uri) }
                .onSuccess { result ->
                    withContext(Dispatchers.Main) {
                        refreshSettingsUi()
                        Toast.makeText(
                            this@SettingsActivity,
                            "Import complete: ${result.threadCount} chats, ${result.messageCount} messages, ${result.memoryFileCount} memory files, ${result.recordingFileCount} recordings, ${result.vaultItemCount} vault items.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun showLogSubmissionDialog() {
        val issueTypes = arrayOf(
            "P2P/WiFi sync issue",
            "Image query failed",
            "Voice command not working",
            "BLE connection issue",
            "App crash/ANR",
            "Other/General",
        )
        var selectedType = issueTypes.first()
        val input = EditText(this).apply {
            hint = "Describe what happened (optional)"
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle("Send Debug Logs")
            .setSingleChoiceItems(issueTypes, 0) { _, which -> selectedType = issueTypes[which] }
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                submitDebugLogs(selectedType, input.text?.toString()?.trim()?.take(2_000) ?: "No description")
            }
            .show()
    }

    private fun submitDebugLogs(issueType: String, description: String) {
        Toast.makeText(this, "Collecting logs...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                DebugLogSupport.sendLogsToServer(
                    context = this@SettingsActivity,
                    issueType = issueType,
                    description = description,
                    logs = DebugLogSupport.collectLogcat(),
                    deviceInfo = DebugLogSupport.buildDeviceInfo(this@SettingsActivity),
                )
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    val message = if (result.isSuccess) {
                        "Logs sent successfully. Thank you for helping debug."
                    } else {
                        "Failed to send logs: ${result.exceptionOrNull()?.message}"
                    }
                    Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Error collecting logs: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showPassphraseDialog(title: String, onSubmit: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Passphrase"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                val passphrase = input.text?.toString().orEmpty()
                if (passphrase.isBlank()) {
                    Toast.makeText(this, "Passphrase cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    onSubmit(passphrase)
                }
            }
            .show()
    }

    private fun showTextEditorDialog(
        title: String,
        initial: String,
        hint: String? = null,
        onSave: (String) -> Unit,
    ) {
        val input = EditText(this).apply {
            setText(initial)
            setSelection(text?.length ?: 0)
            setHint(hint)
            minLines = 8
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ -> onSave(input.text?.toString().orEmpty()) }
            .show()
    }

    private fun isLocalAgentAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!enabled) return false
        val expected = ComponentName(this, LocalAgentAccessibilityService::class.java).flattenToString()
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return services.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            XXPermissions.isGranted(this, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun ensureExistingAutoAudioNotificationPermission() {
        if (checkedExistingAutoAudioPermission ||
            !AutoAudioCapturePrefs.isEnabled(this) ||
            hasPostNotificationsPermission()
        ) {
            return
        }
        checkedExistingAutoAudioPermission = true
        XXPermissions.with(this)
            .permission(Permission.POST_NOTIFICATIONS)
            .request { _, allGranted ->
                if (!allGranted) {
                    Toast.makeText(
                        this,
                        "Enable notifications to keep auto audio capture running in the background.",
                        Toast.LENGTH_LONG,
                    ).show()
                    disableAutoAudioCapture()
                }
                refreshSettingsUi()
            }
    }

    private fun enableAutoAudioCapture() {
        AutoAudioCapturePrefs.setEnabled(this, true)
        val loops = AutoAudioCapturePrefs.getLoopsPerSync(this)
        Toast.makeText(this, "Auto audio capture enabled (sync every $loops loops)", Toast.LENGTH_SHORT).show()
        AutoAudioCaptureService.start(this)
    }

    private fun disableAutoAudioCapture() {
        AutoAudioCapturePrefs.setEnabled(this, false)
        Toast.makeText(this, "Auto audio capture disabled", Toast.LENGTH_SHORT).show()
        AutoAudioCaptureService.stop(this)
    }

    private fun buildAutoAudioDebugText(enabled: Boolean): String {
        val permissionGranted = hasPostNotificationsPermission()
        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val channelText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            val importance = manager.getNotificationChannel("auto_audio_capture")?.importance
            "channel=${when (importance) {
                null -> "missing"
                android.app.NotificationManager.IMPORTANCE_NONE -> "blocked"
                android.app.NotificationManager.IMPORTANCE_MIN -> "min"
                android.app.NotificationManager.IMPORTANCE_LOW -> "low"
                android.app.NotificationManager.IMPORTANCE_DEFAULT -> "default"
                android.app.NotificationManager.IMPORTANCE_HIGH -> "high"
                else -> importance.toString()
            }}"
        } else {
            ""
        }
        return listOf(
            if (enabled) "auto-audio: ON" else "auto-audio: OFF",
            "syncEvery=${AutoAudioCapturePrefs.getLoopsPerSync(this)}x15m",
            "visualNotes=${if (AutoAudioCapturePrefs.isVisualNotesEnabled(this)) "on" else "off"}",
            "speechExtend=${if (AutoAudioCapturePrefs.isSpeechExtendEnabled(this)) "on" else "off"}",
            if (permissionGranted) "perm=ok" else "perm=blocked",
            if (notificationsEnabled) "appNotifs=on" else "appNotifs=off",
            channelText,
            "last=${AutoAudioCapturePrefs.getLastPauseReason(this).ifBlank { "(none)" }}",
        ).filter(String::isNotBlank).joinToString(" · ")
    }

    private fun buildRecentChatIntent(): Intent {
        val last = ChatStore.listNonEmptyThreads().firstOrNull()
        val lastUserAt = last?.let { thread ->
            ChatStore.listMessages(thread.id)
                .lastOrNull { it.role == ChatRole.USER }
                ?.createdAt
        } ?: 0L
        val openChatId = last?.id?.takeIf {
            lastUserAt > 0L && System.currentTimeMillis() - lastUserAt < 30 * 60 * 1_000
        }
        return Intent(this, ChatThreadActivity::class.java).apply {
            if (openChatId != null) putExtra(ChatThreadActivity.EXTRA_CHAT_ID, openChatId)
        }
    }

    private fun syncAgentProviderToAiProvider(type: AgentProviderType) {
        AiProviderPrefs.setProvider(
            this,
            when (type) {
                AgentProviderType.PRO_SUBSCRIPTION -> AiProviderType.CLI_RELAY
                AgentProviderType.LOCAL_AGENT -> AiProviderType.LOCAL_MODELS
                AgentProviderType.TASKER -> AiProviderType.MOCK
            },
        )
    }

    private fun formatPlan(raw: String): String = when (raw.lowercase(Locale.US)) {
        "monthly" -> "Monthly"
        "yearly" -> "Yearly"
        else -> "Pro"
    }

    private fun localizedVisionProfileName(profile: VisionProfile): String = when (profile) {
        VisionProfile.WALKING -> getString(R.string.vision_profile_walking)
        VisionProfile.DETAILED -> getString(R.string.vision_profile_detailed)
    }

    private fun sectionPreferenceKey(section: SettingsSection): String {
        val legacyCardName = when (section) {
            SettingsSection.AI_AUTOMATION -> "card_agent_provider"
            SettingsSection.LOCAL_AGENT -> "card_local_agent_settings"
            SettingsSection.MEMORY_PRIVACY -> "card_memory_privacy"
            SettingsSection.TRANSCRIPTS -> "card_transcripts"
            SettingsSection.DATA -> "card_data"
            SettingsSection.AGENT -> "card_agent"
            SettingsSection.FAQ -> "card_faq"
            SettingsSection.SUPPORT -> "support"
        }
        return "section_expanded_$legacyCardName"
    }

    companion object {
        private val DEFAULT_BULLET_PROMPT = """You summarize one mobile screen OCR event into exactly one bullet.

The app package is provided below, and the app name may also appear inside the OCR text.

APP_PACKAGE: ${'$'}{event.packageName}
EVENT_TIME: ${'$'}{event.time}
OCR_TEXT: ${'$'}{event.text}

Return JSON only: {"skip": false, "bullet": "...", "confidence": 0.0}

Rules:
- Keep bullet factual and concise (max 26 words)
- Preserve concrete details like person names, contact names, topics, or action context when visible
- If OCR is too noisy or meaningless, set skip=true
- Do not invent details outside OCR
""".trimIndent()
    }
}
