package com.achyut.adglasses.ui.localagent

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.achyut.adglasses.agent.LocalAgentPrefs
import com.achyut.adglasses.shared.ui.localagent.AppBlacklistScreen
import com.achyut.adglasses.shared.ui.localagent.BlacklistAppItem as SharedBlacklistAppItem
import com.achyut.adglasses.ui.appearance.AppearancePreferences
import com.achyut.adglasses.ui.appearance.rememberAppearanceSettings
import com.achyut.adglasses.ui.theme.AdGlassesTheme
import kotlin.concurrent.thread

/**
 * UI to blacklist apps from Local Agent screen-content capture.
 *
 * Lists ALL installed apps/packages. Requires:
 *   <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
 */
class AppBlacklistActivity : AppCompatActivity() {

    private var allApps: List<SharedBlacklistAppItem> = emptyList()
    private var filteredApps by mutableStateOf<List<SharedBlacklistAppItem>>(emptyList())
    private var query by mutableStateOf("")
    private var hideSystemApps by mutableStateOf(false)
    private var selectedPackages by mutableStateOf<Set<String>>(emptySet())
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemApps = LocalAgentPrefs.isHideSystemAppsEnabled(this)
        selectedPackages = LocalAgentPrefs.getCaptureBlacklistPackages(this)
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            AdGlassesTheme(appearance) {
                AppBlacklistScreen(
                    apps = filteredApps,
                    totalCount = allApps.size,
                    query = query,
                    hideSystemApps = hideSystemApps,
                    selectedPackages = selectedPackages,
                    isLoading = isLoading,
                    onQueryChange = {
                        query = it
                        refreshFilteredList()
                    },
                    onHideSystemAppsChange = {
                        hideSystemApps = it
                        LocalAgentPrefs.setHideSystemAppsEnabled(this, it)
                        refreshFilteredList()
                    },
                    onTogglePackage = ::togglePackage,
                    onSave = ::saveSelection,
                    onBack = ::finish,
                )
            }
        }

        loadAppsAsync()
    }

    private fun loadAppsAsync() {
        isLoading = true

        thread {
            val pm = packageManager
            val list = pm.getInstalledApplications(0)

            val items = list
                .asSequence()
                .mapNotNull { ai ->
                    val pkg = ai.packageName?.trim().orEmpty()
                    if (pkg.isBlank()) return@mapNotNull null

                    val label = runCatching { pm.getApplicationLabel(ai).toString().trim() }
                        .getOrNull()
                        .orEmpty()
                        .ifBlank { pkg }

                    val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

                    SharedBlacklistAppItem(
                        packageName = pkg,
                        label = label,
                        isSystemApp = isSystem,
                    )
                }
                .distinctBy { it.packageName }
                .sortedWith(compareBy<SharedBlacklistAppItem> { it.label.lowercase() }.thenBy { it.packageName })
                .toList()

            runOnUiThread {
                allApps = items
                isLoading = false
                refreshFilteredList()
            }
        }
    }

    private fun refreshFilteredList() {
        val q = query.trim().lowercase()

        filteredApps = allApps.filter {
            val okSystem = !(hideSystemApps && it.isSystemApp)
            val okQuery = q.isBlank() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            okSystem && okQuery
        }
    }

    private fun togglePackage(packageName: String) {
        selectedPackages = if (packageName in selectedPackages) {
            selectedPackages - packageName
        } else {
            selectedPackages + packageName
        }
    }

    private fun saveSelection() {
        LocalAgentPrefs.setCaptureBlacklistPackages(this, selectedPackages)
        Toast.makeText(this, "Saved blacklist (${selectedPackages.size} apps)", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, Intent())
        finish()
    }
}
