package com.fersaiyan.cyanbridge.ui.localagent

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
import com.fersaiyan.cyanbridge.data.local.entity.PendingAction
import com.fersaiyan.cyanbridge.localagent.LocalAgentAccessibilityBridge
import com.fersaiyan.cyanbridge.localagent.LocalAgentActionParser
import com.fersaiyan.cyanbridge.localagent.LocalAgentIntents
import com.fersaiyan.cyanbridge.localagent.LocalAgentService
import com.fersaiyan.cyanbridge.localagent.actions.LocalAgentActionManager
import com.fersaiyan.cyanbridge.ui.MyApplication
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme
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
            appendLine(prettyJson)
        }.trimEnd()
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
                val ok = runCatching {
                    val intentOk = LocalAgentActionManager.executeNow(this@PendingActionsActivity, a)
                    if (intentOk) true else LocalAgentAccessibilityBridge.perform(a)
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
            notifyServiceResumeAfterApproval()

            loadPending()
        }
    }

    /**
     * Tell the [LocalAgentService] to resume its loop after the user approved a pending action.
     */
    private fun notifyServiceResumeAfterApproval() {
        val intent = Intent(this, LocalAgentService::class.java).apply {
            action = LocalAgentIntents.ACTION_RESUME_AFTER_APPROVAL
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
