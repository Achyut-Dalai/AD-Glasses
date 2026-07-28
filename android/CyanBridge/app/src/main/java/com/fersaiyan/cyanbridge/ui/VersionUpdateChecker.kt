package com.fersaiyan.cyanbridge.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object VersionUpdateChecker {

    private const val KEY_LAST_CHECK_TIME = "last_version_check_time"
    private const val KEY_REMINDED_VERSION = "reminded_version"
    private const val CHECK_INTERVAL_HOURS = 6

    fun checkForUpdates(context: Context) {
        val prefs = context.getSharedPreferences("version_check", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
        val currentTime = System.currentTimeMillis()
        val hoursSinceLastCheck = (currentTime - lastCheck) / (1000 * 60 * 60)

        if (hoursSinceLastCheck < CHECK_INTERVAL_HOURS && lastCheck > 0) {
            return
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIME, currentTime).apply()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val relayUrl = AiProviderPrefs.getRelayBaseUrl(context)
                val url = java.net.URL("$relayUrl/version/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(response)
                    val latestVersion = json.optString("version", "")
                    val downloadUrl = json.optString("download_url", "")

                    withContext(Dispatchers.Main) {
                        if (latestVersion.isNotBlank()) {
                            checkAndShowUpdateDialog(context, latestVersion, downloadUrl)
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Silently fail - version check is not critical
            }
        }
    }

    private fun checkAndShowUpdateDialog(context: Context, latestVersion: String, downloadUrl: String) {
        val prefs = context.getSharedPreferences("version_check", Context.MODE_PRIVATE)
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

        val remindedVersion = prefs.getString(KEY_REMINDED_VERSION, "") ?: ""

        if (latestVersion != currentVersion && latestVersion != remindedVersion) {
            showUpdateDialog(context, latestVersion, downloadUrl)
        }
    }

    fun showUpdateDialog(context: Context, latestVersion: String, downloadUrl: String) {
        val activity = context as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }

        val prefs = context.getSharedPreferences("version_check", Context.MODE_PRIVATE)
        val dialog = Dialog(activity)
        dialog.setContentView(
            ComposeView(activity).apply {
                setContent {
                    val appearance by rememberAppearanceSettings(AppearancePreferences(activity))
                    CyanBridgeTheme(appearance) {
                        VersionUpdateDialogContent(
                            currentVersion = currentVersion,
                            latestVersion = latestVersion,
                            onDownload = {
                                try {
                                    val url = downloadUrl.ifBlank {
                                        "https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK/releases"
                                    }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
                                }
                                dialog.dismiss()
                            },
                            onPlayStore = {
                                Toast.makeText(context, "Play Store version coming soon!", Toast.LENGTH_SHORT).show()
                            },
                            onLater = {
                                prefs.edit().putString(KEY_REMINDED_VERSION, latestVersion).apply()
                                dialog.dismiss()
                            },
                        )
                    }
                }
            },
        )
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    @Composable
    private fun VersionUpdateDialogContent(
        currentVersion: String,
        latestVersion: String,
        onDownload: () -> Unit,
        onPlayStore: () -> Unit,
        onLater: () -> Unit,
    ) {
        Card(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Update available", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Current version: $currentVersion\nLatest version: $latestVersion",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Download from GitHub")
                }
                OutlinedButton(onClick = onPlayStore, modifier = Modifier.fillMaxWidth()) {
                    Text("Get it on Play Store")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onLater) { Text("Later") }
                }
            }
        }
    }

    fun forceCheckForUpdates(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val relayUrl = AiProviderPrefs.getRelayBaseUrl(context)
                val url = java.net.URL("$relayUrl/version/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(response)
                    val latestVersion = json.optString("version", "")
                    val downloadUrl = json.optString("download_url", "")

                    withContext(Dispatchers.Main) {
                        if (latestVersion.isNotBlank()) {
                            showUpdateDialog(context, latestVersion, downloadUrl)
                        } else {
                            Toast.makeText(context, "Could not check for updates", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Server unavailable for update check", Toast.LENGTH_SHORT).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not check for updates", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
