package com.fersaiyan.cyanbridge.localagent.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import com.fersaiyan.cyanbridge.data.local.entity.PendingAction
import com.fersaiyan.cyanbridge.localagent.LocalAgentAction
import com.fersaiyan.cyanbridge.localagent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ui.MyApplication
import org.json.JSONObject

/**
 * Manages action enqueuing, approval logic, and execution of system intents.
 */
object LocalAgentActionManager {

    enum class Risk { LOW, MEDIUM, HIGH }

    fun classifyRisk(action: LocalAgentAction): Risk {
        return when (action) {
            is LocalAgentAction.Wait,
            is LocalAgentAction.GlobalBack,
            is LocalAgentAction.GlobalHome,
            is LocalAgentAction.Finish,
            is LocalAgentAction.Scroll,
            is LocalAgentAction.OpenApp,
            is LocalAgentAction.OpenNotifications,
            is LocalAgentAction.OpenRecents,
            is LocalAgentAction.OpenContacts,
            is LocalAgentAction.ToggleFlashlight -> Risk.LOW

            is LocalAgentAction.ClickText,
            is LocalAgentAction.ClickCoord,
            is LocalAgentAction.TypeText,
            is LocalAgentAction.Swipe,
            is LocalAgentAction.LongPress,
            is LocalAgentAction.ToggleWifi,
            is LocalAgentAction.ToggleBluetooth -> Risk.MEDIUM

            is LocalAgentAction.MakeCall,
            is LocalAgentAction.SendSms,
            is LocalAgentAction.SetAlarm,
            is LocalAgentAction.SendEmail -> Risk.HIGH
        }
    }

    /**
     * Enqueue a planned action for approval or auto-execution.
     */
    suspend fun processPlannedAction(context: Context, action: LocalAgentAction, source: String = "agent"): Boolean {
        val risk = classifyRisk(action)
        val requireConfirm = LocalAgentPrefs.isRequireActionConfirmationEnabled(context)
        val autoLowRisk = LocalAgentPrefs.isAutoExecuteLowRiskEnabled(context)

        val shouldAutoExecute = !requireConfirm || (risk == Risk.LOW && autoLowRisk)

        if (shouldAutoExecute) {
            return executeNow(context, action)
        }

        // Store as pending
        val dao = MyApplication.database.pendingActionDao()
        dao.insert(
            PendingAction(
                ts = System.currentTimeMillis(),
                source = source,
                actionJson = actionToJson(action).toString(),
                status = "pending"
            )
        )
        return false
    }

    /**
     * Executes the action immediately (system intents or accessibility).
     * Accessibility actions are handled by LocalAgentAccessibilityBridge.
     * System-intent actions are handled here and return true on success.
     */
    suspend fun executeNow(context: Context, action: LocalAgentAction): Boolean {
        return when (action) {
            is LocalAgentAction.OpenApp -> openAppIntent(context, action)
            is LocalAgentAction.Finish -> true
            is LocalAgentAction.SendEmail -> {
                sendEmailIntent(context, action)
                true
            }
            is LocalAgentAction.MakeCall -> {
                makeCallIntent(context, action)
                true
            }
            is LocalAgentAction.SendSms -> {
                sendSmsIntent(context, action)
                true
            }
            is LocalAgentAction.SetAlarm -> {
                setAlarmIntent(context, action)
                true
            }
            LocalAgentAction.OpenContacts -> {
                openContactsIntent(context)
                true
            }
            LocalAgentAction.ToggleWifi -> {
                toggleWifiIntent(context)
                true
            }
            LocalAgentAction.ToggleBluetooth -> {
                toggleBluetoothIntent(context)
                true
            }
            LocalAgentAction.ToggleFlashlight -> {
                toggleFlashlightIntent(context)
                true
            }
            else -> {
                // For a11y-only actions (click, type, swipe, scroll, etc.),
                // the service loop delegates to LocalAgentAccessibilityBridge.
                false
            }
        }
    }

    // --- System intent handlers ---

    private fun sendEmailIntent(context: Context, action: LocalAgentAction.SendEmail) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${action.to}")
            putExtra(Intent.EXTRA_SUBJECT, action.subject)
            putExtra(Intent.EXTRA_TEXT, action.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun makeCallIntent(context: Context, action: LocalAgentAction.MakeCall) {
        val number = action.number.trim()
        if (number.isBlank()) return
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$number")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun sendSmsIntent(context: Context, action: LocalAgentAction.SendSms) {
        val number = action.number.trim()
        if (number.isBlank()) return
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", action.message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun setAlarmIntent(context: Context, action: LocalAgentAction.SetAlarm) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openContactsIntent(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = ContactsContract.Contacts.CONTENT_URI
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun toggleWifiIntent(context: Context) {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun toggleBluetoothIntent(context: Context) {
        // Open Bluetooth settings; direct toggle requires system permissions on modern Android.
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun toggleFlashlightIntent(context: Context) {
        // Open camera/flashlight settings; direct toggle requires CameraManager API.
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openAppIntent(context: Context, action: LocalAgentAction.OpenApp): Boolean {
        val pm = context.packageManager
        val raw = action.appName.trim()
        if (raw.isBlank()) return false

        val packageCandidates = buildList {
            add(raw)
            if (!raw.contains('.')) add(raw.lowercase())
        }
        packageCandidates.forEach { pkg ->
            pm.getLaunchIntentForPackage(pkg)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val candidates = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        val normalized = raw.lowercase()

        val best = candidates.firstOrNull { resolveInfo ->
            resolveInfo.loadLabel(pm).toString().trim().equals(raw, ignoreCase = true)
        } ?: candidates.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString().trim().lowercase()
            val pkg = resolveInfo.activityInfo.packageName.orEmpty().lowercase()
            label.contains(normalized) || pkg.contains(normalized)
        }

        val pkg = best?.activityInfo?.packageName ?: return false
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun actionToJson(action: LocalAgentAction): JSONObject {
        val obj = JSONObject()
        when (action) {
            is LocalAgentAction.Wait -> {
                obj.put("type", "wait")
                obj.put("ms", action.ms)
            }
            is LocalAgentAction.GlobalBack -> obj.put("type", "back")
            is LocalAgentAction.GlobalHome -> obj.put("type", "home")
            is LocalAgentAction.ClickText -> {
                obj.put("type", "click_text")
                obj.put("text", action.text)
            }
            is LocalAgentAction.ClickCoord -> {
                obj.put("type", "click_coord")
                obj.put("x", action.x)
                obj.put("y", action.y)
            }
            is LocalAgentAction.TypeText -> {
                obj.put("type", "type_text")
                obj.put("text", action.text)
                action.hint?.let { obj.put("hint", it) }
            }
            is LocalAgentAction.Scroll -> {
                obj.put("type", "scroll")
                obj.put("direction", action.direction.name.lowercase())
            }
            is LocalAgentAction.Swipe -> {
                obj.put("type", "swipe")
                obj.put("start_x", action.startX)
                obj.put("start_y", action.startY)
                obj.put("end_x", action.endX)
                obj.put("end_y", action.endY)
                obj.put("duration_ms", action.durationMs)
            }
            is LocalAgentAction.LongPress -> {
                obj.put("type", "long_press")
                obj.put("x", action.x)
                obj.put("y", action.y)
                obj.put("duration_ms", action.durationMs)
            }
            is LocalAgentAction.OpenNotifications -> obj.put("type", "open_notifications")
            is LocalAgentAction.OpenRecents -> obj.put("type", "open_recents")
            is LocalAgentAction.OpenApp -> {
                obj.put("type", "open_app")
                obj.put("app_name", action.appName)
            }
            is LocalAgentAction.Finish -> {
                obj.put("type", "finish")
                action.message?.let { obj.put("message", it) }
            }
            is LocalAgentAction.MakeCall -> {
                obj.put("type", "make_call")
                obj.put("number", action.number)
            }
            is LocalAgentAction.SendSms -> {
                obj.put("type", "send_sms")
                obj.put("number", action.number)
                obj.put("message", action.message)
            }
            is LocalAgentAction.SetAlarm -> {
                obj.put("type", "set_alarm")
                obj.put("hour", action.hour)
                obj.put("minute", action.minute)
                action.label?.let { obj.put("label", it) }
            }
            is LocalAgentAction.OpenContacts -> obj.put("type", "open_contacts")
            is LocalAgentAction.ToggleWifi -> obj.put("type", "toggle_wifi")
            is LocalAgentAction.ToggleBluetooth -> obj.put("type", "toggle_bluetooth")
            is LocalAgentAction.ToggleFlashlight -> obj.put("type", "toggle_flashlight")
            is LocalAgentAction.SendEmail -> {
                obj.put("type", "send_email")
                obj.put("to", action.to)
                obj.put("subject", action.subject)
                obj.put("body", action.body)
            }
        }
        return obj
    }
}
