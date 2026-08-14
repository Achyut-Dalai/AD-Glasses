package com.fersaiyan.cyanbridge.agent

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Configuration for infrastructure owned by the AD Glasses user. */
class CloudSettingsActivity : AppCompatActivity() {
    private lateinit var relayUrl: EditText
    private lateinit var apiToken: EditText
    private lateinit var accountEmail: EditText
    private lateinit var requestsModel: EditText
    private lateinit var questionsModel: EditText
    private lateinit var tasksModel: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Cloud AI"

        val density = resources.displayMetrics.density
        val padding = (20 * density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        content.addView(TextView(this).apply {
            text = "Connect AD Glasses to infrastructure you control. No subscription or author account is required."
            textSize = 16f
        }, matchWrap())

        relayUrl = field(content, "Relay base URL (https://...)", AiProviderPrefs.getRelayBaseUrl(this))
        apiToken = field(content, "API token (optional)", CloudServerPrefs.getApiToken(this)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        accountEmail = field(content, "Account email (optional)", CloudServerPrefs.getAccountEmail(this)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        requestsModel = field(content, "Chat/request model", CloudAiPrefs.getRequestsModel(this))
        questionsModel = field(content, "Image/question model", CloudAiPrefs.getQuestionsModel(this))
        tasksModel = field(content, "Automation/task model", CloudAiPrefs.getTasksModel(this))

        content.addView(Button(this).apply {
            text = "Save configuration"
            setOnClickListener { save(showConfirmation = true) }
        }, matchWrap())

        content.addView(Button(this).apply {
            text = "Save and test connection"
            setOnClickListener { testConnection() }
        }, matchWrap())

        status = TextView(this).apply {
            text = if (AiProviderPrefs.isRelayConfigured(this@CloudSettingsActivity)) {
                "Cloud relay configured"
            } else {
                "Cloud relay is not configured"
            }
            setPadding(0, padding / 2, 0, 0)
        }
        content.addView(status, matchWrap())

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun field(parent: LinearLayout, hint: String, value: String): EditText {
        return EditText(this).also { input ->
            input.hint = hint
            input.setText(value)
            input.isSingleLine = true
            parent.addView(input, matchWrap())
        }
    }

    private fun save(showConfirmation: Boolean): Boolean {
        val url = relayUrl.text.toString().trim().trimEnd('/')
        if (url.isNotBlank() && !url.startsWith("https://") && !url.startsWith("http://")) {
            relayUrl.error = "Use a full http:// or https:// URL"
            return false
        }
        AiProviderPrefs.setRelayBaseUrl(this, url)
        CloudServerPrefs.setApiToken(this, apiToken.text.toString())
        CloudServerPrefs.setAccountEmail(this, accountEmail.text.toString())
        CloudAiPrefs.setRequestsModel(this, requestsModel.text.toString())
        CloudAiPrefs.setQuestionsModel(this, questionsModel.text.toString())
        CloudAiPrefs.setTasksModel(this, tasksModel.text.toString())
        if (showConfirmation) Toast.makeText(this, "Cloud configuration saved", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun testConnection() {
        if (!save(showConfirmation = false)) return
        status.text = "Testing relay…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CloudRelayClient.fetchAvailableModels(this@CloudSettingsActivity)
            }
            status.text = result.fold(
                onSuccess = { models -> "Connected. ${models.size} model(s) available." },
                onFailure = { error -> error.message ?: "Connection failed" },
            )
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
