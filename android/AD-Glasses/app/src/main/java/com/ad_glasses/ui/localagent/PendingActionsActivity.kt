package com.ad_glasses.ui.localagent

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
import com.ad_glasses.data.local.entity.PendingAction
import com.ad_glasses.localagent.LocalAgentAccessibilityBridge
import com.ad_glasses.localagent.LocalAgentActionParser
import com.ad_glasses.localagent.LocalAgentDeviceState
import com.ad_glasses.localagent.LocalAgentIntents
import com.ad_glasses.localagent.LocalAgentPrefs
import com.ad_glasses.localagent.LocalAgentService
import com.ad_glasses.localagent.actions.LocalAgentActionManager
import com.ad_glasses.shared.ui.localagent.PendingActionsScreen
import com.ad_glasses.ui.MyApplication
import com.ad_glasses.ui.appearance.AppearancePreferences
import com.ad_glasses.ui.appearance.rememberAppearanceSettings
import com.ad_glasses.ui.theme.ADGlassesTheme
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
            ADGlassesTheme(appearance) {
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

    private fun describeAction(action: com.ad_glasses.localagent.LocalAgentAction): String {
        return when (action) {
            is com.ad_glasses.localagent.LocalAgentAction.OpenApp -> "Open ${action.appName}."
            is com.ad_glasses.localagent.LocalAgentAction.ClickText -> "Click ${action.text}."
            is com.ad_glasses.localagent.LocalAgentAction.ClickCoord -> "Tap the highlighted control."
            is com.ad_glasses.localagent.LocalAgentAction.TypeText -> "Type ${action.text}."
            is com.ad_glasses.localagent.LocalAgentAction.PressEnter -> "Press enter."
            is com.ad_glasses.localagent.LocalAgentAction.Scroll -> "Scroll the screen."
            is com.ad_glasses.localagent.LocalAgentAction.Swipe -> "Swipe the screen."
            is com.ad_glasses.localagent.LocalAgentAction.LongPress -> "Long press the selected control."
            is com.ad_glasses.localagent.LocalAgentAction.GlobalBack -> "Press back."
            is com.ad_glasses.localagent.LocalAgentAction.GlobalHome -> "Go home."
            is com.ad_glasses.localagent.LocalAgentAction.OpenNotifications -> "Open notifications."
            is com.ad_glasses.localagent.LocalAgentAction.OpenRecents -> "Open recent apps."
            is com.ad_glasses.localagent.LocalAgentAction.OpenContacts -> "Open contacts."
            is com.ad_glasses.localagent.LocalAgentAction.MakeCall -> "Call ${action.number}."
            is com.ad_glasses.localagent.LocalAgentAction.SendSms -> "Send a message to ${action.number}."
            is com.ad_glasses.localagent.LocalAgentAction.SendEmail -> "Send an email to ${action.to}."
            is com.ad_glasses.localagent.LocalAgentAction.SetAlarm -> "Set an alarm."
            is com.ad_glasses.localagent.LocalAgentAction.ReadScreenAloud -> "Read the screen aloud."
            is com.ad_glasses.localagent.LocalAgentAction.ToggleWifi -> "Open Wi-Fi settings."
            is com.ad_glasses.localagent.LocalAgentAction.ToggleBluetooth -> "Open Bluetooth settings."
            is com.ad_glasses.localagent.LocalAgentAction.ToggleFlashlight -> "Open flashlight settings."
            is com.ad_glasses.localagent.LocalAgentAction.Wait -> "Wait briefly."
            is com.ad_glasses.localagent.LocalAgentAction.Finish -> "Finish the task."
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
