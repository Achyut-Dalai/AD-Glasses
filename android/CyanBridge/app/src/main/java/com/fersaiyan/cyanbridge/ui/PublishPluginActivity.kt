package com.fersaiyan.cyanbridge.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.shared.plugins.CommunityPluginCatalog
import com.fersaiyan.cyanbridge.shared.plugins.PublishPluginUiState
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.shared.ui.plugins.PublishPluginScreen
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PublishPluginActivity : AppCompatActivity() {

    private val categories = CommunityPluginCatalog.categories
    private var uiState by mutableStateOf(PublishPluginUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                PublishPluginScreen(
                    state = uiState,
                    categories = categories,
                    onTitleChanged = { title -> uiState = uiState.copy(title = title, titleError = null) },
                    onAuthorChanged = { author -> uiState = uiState.copy(author = author, authorError = null) },
                    onDescriptionChanged = { description ->
                        uiState = uiState.copy(description = description, descriptionError = null)
                    },
                    onCategorySelected = { category -> uiState = uiState.copy(category = category) },
                    onTaskerNetLinkChanged = { link ->
                        uiState = uiState.copy(taskerNetLink = link, taskerNetLinkError = null)
                    },
                    onSubmit = ::submitPlugin,
                    onNavigateBack = ::finish,
                )
            }
        }
    }

    private fun submitPlugin() {
        if (uiState.isSubmitting) return
        val title = uiState.title.trim()
        val author = uiState.author.trim()
        val description = uiState.description.trim()
        val category = uiState.category.trim()
        val taskerNetLink = uiState.taskerNetLink.trim()
        val errors = PublishPluginUiState(
            titleError = if (title.isBlank()) "Title is required" else null,
            authorError = if (author.isBlank()) "Author name is required" else null,
            descriptionError = if (description.isBlank()) "Description is required" else null,
            taskerNetLinkError = if (taskerNetLink.isBlank()) "TaskerNet link is required" else null,
        )
        if (errors.titleError != null || errors.authorError != null ||
            errors.descriptionError != null || errors.taskerNetLinkError != null
        ) {
            uiState = uiState.copy(
                titleError = errors.titleError,
                authorError = errors.authorError,
                descriptionError = errors.descriptionError,
                taskerNetLinkError = errors.taskerNetLinkError,
            )
            return
        }

        uiState = uiState.copy(isSubmitting = true)
        lifecycleScope.launch {
            val submitted = withContext(Dispatchers.IO) {
                submitPluginToServer(
                    title = title,
                    author = author,
                    description = description,
                    category = category,
                    taskerNetLink = taskerNetLink,
                )
            }
            if (submitted) {
                Toast.makeText(
                    this@PublishPluginActivity,
                    "Plugin submitted for review! It will appear in the list once approved.",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            } else {
                uiState = uiState.copy(isSubmitting = false)
                Toast.makeText(
                    this@PublishPluginActivity,
                    "Server unavailable. Please try again later.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun submitPluginToServer(
        title: String,
        author: String,
        description: String,
        category: String,
        taskerNetLink: String,
    ): Boolean {
        return runCatching {
            val relayUrl = AiProviderPrefs.getRelayBaseUrl(this)
            val connection = java.net.URL("$relayUrl/plugins/submit").openConnection()
                as java.net.HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.doOutput = true
                val body = JSONObject(
                    mapOf(
                        "title" to title,
                        "author" to author,
                        "description" to description,
                        "category" to category,
                        "taskernet_link" to taskerNetLink,
                    ),
                ).toString()
                connection.outputStream.use { it.write(body.toByteArray()) }
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }
}
