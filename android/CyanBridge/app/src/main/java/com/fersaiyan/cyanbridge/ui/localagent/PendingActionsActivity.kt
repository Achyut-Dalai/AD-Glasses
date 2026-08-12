package com.achyut.adglasses.ui.localagent

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.achyut.adglasses.data.local.entity.PendingAction
import com.achyut.adglasses.localagent.LocalAgentAccessibilityBridge
import com.achyut.adglasses.localagent.LocalAgentActionParser
import com.achyut.adglasses.localagent.LocalAgentDeviceState
import com.achyut.adglasses.localagent.LocalAgentIntents
import com.achyut.adglasses.localagent.LocalAgentPrefs
import com.achyut.adglasses.localagent.LocalAgentService
import com.achyut.adglasses.localagent.actions.LocalAgentActionManager
import com.achyut.adglasses.shared.ui.localagent.PendingActionsScreen
import com.achyut.adglasses.ui.MyApplication
import com.achyut.adglasses.ui.appearance.AppearancePreferences
import com.achyut.adglasses.ui.appearance.rememberAppearanceSettings
import com.achyut.adglasses.ui.theme.CyanBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PendingActionsActivity : AppCompatActivity() {

    private var current: PendingAction? = null
    private var pendingCount by mutableStateOf(0)
    private var renderedAction by mutableStateOf("(no pending actions)")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appearancePreferences = AppearancePreferences(this)
        setContent {
            val appearance by rememberAppearanceSettings(appearancePreferences)
            CyanBridgeTheme(appearance) {
                PendingActionsScreen(
                    pendingCount = pendingCount,
                    renderedAction = renderedAction,
                    hasPendingAction = current != null,
                    onRefresh = ::loadPending,
                    onApprove = ::approveCurrent,
                    onReject = ::rejectCurrent,
                    onBack = ::finish,
                )
            }
        }

        loadPending()
    }

    private fun loadPending() {
        lifecycleScope.launch {
            val dao = MyApplication.database.pendingActionDao()
            val pending = withContext(Dispatchers.IO) {
                dao.getActionsByStatus("pending")
            }

            pendingCount = pending.size
            current = pending.firstOrNull()

            renderedAction = if (current == null) {
                "(no pending actions)"
            } else {
                renderPendingAction(current!!)
            }
        }
    }

    private fun renderPendingAction(p: PendingAction): String {
        val tsText = if (p.ts > 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(p.ts))
        } else "(no-ts)"

        val actions = LocalAgentActionParser.parseList(p.actionJson)
        val humanSummary = actions.joinToString("\n") { action -> describeAction(action) }
        val prettyJson = runCatching {
            val trimmed = p.actionJson.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(2)
            } else {
                trimmed
            }
        }.getOrDefault(p.actionJson)

        return buildString {
            appendLine("id=${p.id}")
            appendLine("ts=$tsText")
            appendLine("source=${p.source}")
            appendLine("status=${p.status}")
            if (!p.result.isNullOrBlank()) appendLine("result=${p.result}")
            appendLine("---")
            if (humanSummary.isNotBlank()) {
                appendLine(humanSummary)
                appendLine("---")
            }
            appendLine(prettyJson)
        }.trimEnd()
    }

    private fun describeAction(action: com.achyut.adglasses.localagent.LocalAgentAction): String {
        return when (action) {
            is com.achyut.adglasses.localagent.LocalAgentAction.OpenApp -> "Open ${action.appName}."
            is com.achyut.adglasses.localagent.LocalAgentAction.ClickText -> "Click ${action.text}."
            is com.achyut.adglasses.localagent.LocalAgentAction.ClickCoord -> "Tap the highlighted control."
            is com.achyut.adglasses.localagent.LocalAgentAction.TypeText -> "Type ${action.text}."
            is com.achyut.adglasses.localagent.LocalAgentAction.PressEnter -> "Press enter."
            is com.achyut.adglasses.localagent.LocalAgentAction.Scroll -> "Scroll the screen."
            is com.achyut.adglasses.localagent.LocalAgentAction.Swipe -> "Swipe the screen."
            is com.achyut.adglasses.localagent.LocalAgentAction.LongPress -> "Long press the selected control."
            is com.achyut.adglasses.localagent.LocalAgentAction.GlobalBack -> "Press back."
            is com.achyut.adglasses.localagent.LocalAgentAction.GlobalHome -> "Go home."
            is com.achyut.adglasses.localagent.LocalAgentAction.OpenNotifications -> "Open notifications."
            is com.achyut.adglasses.localagent.LocalAgentAction.OpenRecents -> "Open recent apps."
            is com.achyut.adglasses.localagent.LocalAgentAction.OpenContacts -> "Open contacts."
            is com.achyut.adglasses.localagent.LocalAgentAction.MakeCall -> "Call ${action.number}."
            is com.achyut.adglasses.localagent.LocalAgentAction.SendSms -> "Send a message to ${action.number}."
            is com.achyut.adglasses.localagent.LocalAgentAction.SendEmail -> "Send an email to ${action.to}."
            is com.achyut.adglasses.localagent.LocalAgentAction.SetAlarm -> "Set an alarm."
            is com.achyut.adglasses.localagent.LocalAgentAction.ReadScreenAloud -> "Read the screen aloud."
            is com.achyut.adglasses.localagent.LocalAgentAction.ToggleWifi -> "Open Wi-Fi settings."
            is com.achyut.adglasses.localagent.LocalAgentAction.ToggleBluetooth -> "Open Bluetooth settings."
            is com.achyut.adglasses.localagent.LocalAgentAction.ToggleFlashlight -> "Open flashlight settings."
            is com.achyut.adglasses.localagent.LocalAgentAction.Wait -> "Wait briefly."
            is com.achyut.adglasses.localagent.LocalAgentAction.Finish -> "Finish the task."
        }
    }

    private fun rejectCurrent() {
        val p = current ?: return
        lifecycleScope.launch {
            val dao = MyApplication.database.pendingActionDao()
            withContext(Dispatchers.IO) {
                p.status = "rejected"
                p.result = "rejected_by_user"
                dao.update(p)
            }
            notifyServiceResumeAfterApproval(rejected = true)
            Toast.makeText(this@PendingActionsActivity, "Rejected action #${p.id}", Toast.LENGTH_SHORT).show()
            loadPending()
        }
    }

    private fun approveCurrent() {
        val p = current ?: return
        lifecycleScope.launch {
            val dao = MyApplication.database.pendingActionDao()

            // Mark approved
            withContext(Dispatchers.IO) {
                p.status = "approved"
                p.result = null
                dao.update(p)
            }

            val actions = LocalAgentActionParser.parseList(p.actionJson)
            if (actions.isEmpty()) {
                withContext(Dispatchers.IO) {
                    p.status = "executed"
                    p.result = "parse_failed"
                    dao.update(p)
                }
                Toast.makeText(this@PendingActionsActivity, "Could not parse action JSON", Toast.LENGTH_SHORT).show()
                loadPending()
                return@launch
            }

            val results = mutableListOf<String>()
            for (a in actions) {
                val availability = LocalAgentDeviceState.availability(this@PendingActionsActivity)
                if (availability != LocalAgentDeviceState.Availability.READY) {
                    LocalAgentPrefs.setStatus(
                        this@PendingActionsActivity,
                        "Action blocked: ${availability.statusText}",
                    )
                    LocalAgentPrefs.setLastError(this@PendingActionsActivity, availability.errorCode)
                    results += "${a.javaClass.simpleName}: blocked_device_state"
                    break
                }
                val ok = runCatching {
                    val intentOk = LocalAgentActionManager.executeNow(this@PendingActionsActivity, a)
                    if (intentOk) true else LocalAgentAccessibilityBridge.performWithOptionalShizukuFallback(
                        this@PendingActionsActivity,
                        a,
                    )
                }.getOrDefault(false)

                results += "${a.javaClass.simpleName}: ${if (ok) "ok" else "failed"}"
            }

            withContext(Dispatchers.IO) {
                p.status = "executed"
                p.result = results.joinToString("; ")
                dao.update(p)
            }

            Toast.makeText(this@PendingActionsActivity, "Executed action #${p.id}", Toast.LENGTH_SHORT).show()

            // Resume the agent loop after successful approval.
            notifyServiceResumeAfterApproval(rejected = false)

            loadPending()
        }
    }

    /**
     * Tell the [LocalAgentService] to resume its loop after the user approved a pending action.
     */
    private fun notifyServiceResumeAfterApproval(rejected: Boolean) {
        val intent = Intent(this, LocalAgentService::class.java).apply {
            action = LocalAgentIntents.ACTION_RESUME_AFTER_APPROVAL
            putExtra(LocalAgentIntents.EXTRA_REJECTED, rejected)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
