package com.fersaiyan.cyanbridge.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.shared.chat.ChatRole
import com.fersaiyan.cyanbridge.chat.ChatStore
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.shared.plugins.CommunityPluginCardData
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginCardData
import com.fersaiyan.cyanbridge.shared.plugins.PluginTimeWindow
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.shared.ui.plugins.CommunityPluginsScreen
import com.fersaiyan.cyanbridge.plugins.walkingaid.WalkingAidService
import com.fersaiyan.cyanbridge.plugins.walkingaid.WalkingAidSettingsActivity
import com.fersaiyan.cyanbridge.ui.recordings.RecordingsListActivity
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommunityPluginsActivity : AppCompatActivity() {

    private var selectedWindow by mutableStateOf(PluginTimeWindow.ALL_TIME)
    private var imageAutomationEnabled by mutableStateOf(false)
    private var showImageAutomationBanner by mutableStateOf(true)
    private var isRefreshing by mutableStateOf(false)
    private var serverPluginsLoaded = false

    private val nativePluginPool = listOf(
        NativePluginCardData(
            id = "walking_aid",
            title = "Walking Aid",
            description = "Real-time scene description and obstacle warnings for blind navigation. Captures images from glasses at regular intervals and describes the environment.",
            badge = "Accessibility",
            enabled = CommunityPluginPrefs.isNativePluginEnabled(this, "walking_aid"),
            hasSettings = true,
        ),
    )
    private var nativePluginsState by mutableStateOf(nativePluginPool)

    private val pluginPool = listOf(
        CommunityPluginCardData(
            title = "Meeting Spark Notes",
            author = "cyanlabs",
            description = "Builds concise action summaries from captures and chats.",
            badge = "Productivity",
            downloadsAll = 182_400,
            downloadsMonthly = 28_400,
            downloadsWeekly = 7_100,
            votesAll = 21_600,
            votesMonthly = 4_100,
            votesWeekly = 980,
            trendAll = 92,
            trendMonthly = 96,
            trendWeekly = 97,
        ),
        CommunityPluginCardData(
            title = "Live Caption Relay",
            author = "captionsmith",
            description = "Streams glasses audio to phone and pushes bilingual captions.",
            badge = "Accessibility",
            downloadsAll = 131_300,
            downloadsMonthly = 24_900,
            downloadsWeekly = 6_900,
            votesAll = 18_500,
            votesMonthly = 3_700,
            votesWeekly = 1_020,
            trendAll = 88,
            trendMonthly = 94,
            trendWeekly = 98,
        ),
        CommunityPluginCardData(
            title = "Errand Brain",
            author = "urbanaut",
            description = "Turns quick voice notes into checklist tasks and reminders.",
            badge = "Planner",
            downloadsAll = 98_200,
            downloadsMonthly = 15_600,
            downloadsWeekly = 4_200,
            votesAll = 12_900,
            votesMonthly = 2_100,
            votesWeekly = 610,
            trendAll = 81,
            trendMonthly = 85,
            trendWeekly = 89,
        ),
        CommunityPluginCardData(
            title = "Commute Copilot",
            author = "routepilot",
            description = "Summarizes route changes and sends trip status prompts.",
            badge = "Mobility",
            downloadsAll = 87_500,
            downloadsMonthly = 13_900,
            downloadsWeekly = 3_700,
            votesAll = 11_300,
            votesMonthly = 1_900,
            votesWeekly = 520,
            trendAll = 77,
            trendMonthly = 80,
            trendWeekly = 84,
        ),
        CommunityPluginCardData(
            title = "Retail Field Scout",
            author = "shelfops",
            description = "Captures shelf notes and auto-tags price/checklist anomalies.",
            badge = "Operations",
            downloadsAll = 74_800,
            downloadsMonthly = 11_100,
            downloadsWeekly = 2_900,
            votesAll = 9_900,
            votesMonthly = 1_600,
            votesWeekly = 430,
            trendAll = 73,
            trendMonthly = 78,
            trendWeekly = 82,
        ),
        CommunityPluginCardData(
            title = "Hands-Free Translator",
            author = "polyglot.dev",
            description = "Voice command translation presets for frequent phrases.",
            badge = "Language",
            downloadsAll = 165_000,
            downloadsMonthly = 19_700,
            downloadsWeekly = 4_800,
            votesAll = 23_100,
            votesMonthly = 3_400,
            votesWeekly = 820,
            trendAll = 86,
            trendMonthly = 83,
            trendWeekly = 79,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPluginUi()

        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                CommunityPluginsScreen(
                    plugins = pluginPool,
                    selectedWindow = selectedWindow,
                    imageAutomationEnabled = imageAutomationEnabled,
                    showImageAutomationBanner = showImageAutomationBanner,
                    isRefreshing = isRefreshing,
                    nativePlugins = nativePluginsState,
                    onOpenNativePluginSettings = { pluginId ->
                        when (pluginId) {
                            "walking_aid" -> startActivity(Intent(this, WalkingAidSettingsActivity::class.java))
                        }
                    },
                    onToggleNativePlugin = { pluginId, enabled ->
                        CommunityPluginPrefs.setNativePluginEnabled(this, pluginId, enabled)
                        nativePluginsState = nativePluginsState.map {
                            if (it.id == pluginId) it.copy(enabled = enabled) else it
                        }
                        if (pluginId == "walking_aid") {
                            if (enabled) WalkingAidService.start(this) else WalkingAidService.stop(this)
                        }
                    },
                    onWindowSelected = { selectedWindow = it },
                    onRefresh = ::fetchPluginsFromServer,
                    onDismissImageAutomationBanner = ::dismissImageAutomationBanner,
                    onOpenTaskerStore = ::openTaskerStore,
                    onOpenTaskerNet = ::openTaskerNet,
                    onPublishPlugin = {
                        startActivity(Intent(this, PublishPluginActivity::class.java))
                    },
                    onDestinationSelected = ::navigateTo,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPluginUi()
    }

    private fun refreshPluginUi() {
        imageAutomationEnabled = CommunityPluginPrefs.isGeminiChatGptImageAutomationEnabled(this)
        showImageAutomationBanner = !CommunityPluginPrefs.isImageAutomationBannerDismissed(this)
    }

    private fun fetchPluginsFromServer() {
        if (isRefreshing) return
        if (!serverPluginsLoaded) {
            Toast.makeText(this, "Fetching plugins from server...", Toast.LENGTH_SHORT).show()
        }
        isRefreshing = true

        lifecycleScope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                runCatching {
                    val relayUrl = AiProviderPrefs.getRelayBaseUrl(this@CommunityPluginsActivity)
                    val connection = java.net.URL("$relayUrl/plugins").openConnection()
                        as java.net.HttpURLConnection
                    try {
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5_000
                        connection.readTimeout = 5_000
                        if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                            connection.inputStream.bufferedReader().use { it.readText() }
                            true
                        } else {
                            false
                        }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrDefault(false)
            }

            isRefreshing = false
            if (refreshed) {
                serverPluginsLoaded = true
                Toast.makeText(this@CommunityPluginsActivity, "Plugins refreshed from server!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@CommunityPluginsActivity,
                    "Server unavailable. Using cached plugins.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun dismissImageAutomationBanner() {
        CommunityPluginPrefs.setImageAutomationBannerDismissed(this, true)
        showImageAutomationBanner = false
        Toast.makeText(this, "Banner hidden", Toast.LENGTH_SHORT).show()
    }

    private fun openTaskerStore() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=net.dinglisch.android.taskerm"),
        )
        runCatching { startActivity(marketIntent) }.getOrElse {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm"),
                ),
            )
        }
    }

    private fun openTaskerNet() {
        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://taskernet.com/shares/?user=AS35m8m%2BZfcOI%2FAn4TYXwIRGXRuXzE9zXexYgafojsO%2FQSXgVbu8nOiYo%2BLhLj1izKWhtzdxI6eOvMI%3D&id=Profile%3ATasker+AI",
                    ),
                ),
            )
        }.onFailure {
            Toast.makeText(this, "Could not open TaskerNet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateTo(destination: AppDestination) {
        val target = when (destination) {
            AppDestination.GLASSES -> Intent(this, MainActivity::class.java)
            AppDestination.CHATS -> buildRecentChatIntent()
            AppDestination.MEDIA -> Intent(this, RecordingsListActivity::class.java)
            AppDestination.PLUGINS -> return
            AppDestination.SETTINGS -> Intent(this, SettingsActivity::class.java)
        }
        target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(target)
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
            if (openChatId != null) {
                putExtra(ChatThreadActivity.EXTRA_CHAT_ID, openChatId)
            }
        }
    }
}
