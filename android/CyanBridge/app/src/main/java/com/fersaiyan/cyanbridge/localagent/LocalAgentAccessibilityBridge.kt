package com.fersaiyan.cyanbridge.localagent

import android.util.Log
import com.fersaiyan.cyanbridge.localagent.accessibility.LocalAgentAccessibilityService

/**
 * Simple in-process bridge between our foreground [LocalAgentService] and the
 * [LocalAgentAccessibilityService] singleton.
 */
object LocalAgentAccessibilityBridge {
    private const val TAG = "LocalAgentBridge"

    fun isConnected(): Boolean = LocalAgentAccessibilityService.instance != null

    fun snapshotScreenText(maxChars: Int = 12_000): String? {
        val svc = LocalAgentAccessibilityService.instance ?: return null
        return try {
            svc.dumpActiveWindowText()
                ?.take(maxChars)
        } catch (e: Exception) {
            Log.w(TAG, "snapshotScreenText failed: ${e.message}")
            null
        }
    }

    fun snapshotScreen(
        maxNodes: Int = 120,
        maxChars: Int = 12_000,
    ): LocalAgentScreenSnapshot? {
        val svc = LocalAgentAccessibilityService.instance ?: return null
        return try {
            val nodes = svc.dumpScreenNodes(maxNodes = maxNodes)
            val text = svc.dumpActiveWindowText()?.take(maxChars)
            val packageName = svc.getCurrentForegroundPackageName()

            if (nodes.isEmpty() && text.isNullOrBlank() && packageName.isNullOrBlank()) {
                null
            } else {
                LocalAgentScreenSnapshot(
                    packageName = packageName,
                    textSummary = text,
                    nodes = nodes,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "snapshotScreen failed: ${e.message}")
            null
        }
    }

    suspend fun perform(action: LocalAgentAction): Boolean {
        val svc = LocalAgentAccessibilityService.instance ?: return false
        return try {
            when (action) {
                is LocalAgentAction.Wait -> true
                LocalAgentAction.GlobalBack -> svc.pressBack()
                LocalAgentAction.GlobalHome -> svc.pressHome()
                is LocalAgentAction.ClickText -> svc.clickByTextOrDesc(action.text)
                is LocalAgentAction.ClickCoord -> svc.simulateClick(action.x, action.y)
                is LocalAgentAction.TypeText -> svc.typeTextBestEffort(action.text, action.hint)
                is LocalAgentAction.Scroll -> svc.scrollGesture(
                    when (action.direction) {
                        LocalAgentAction.Direction.UP -> LocalAgentAccessibilityService.ScrollDirection.UP
                        LocalAgentAction.Direction.DOWN -> LocalAgentAccessibilityService.ScrollDirection.DOWN
                    }
                )
                is LocalAgentAction.Swipe -> svc.swipe(
                    action.startX, action.startY, action.endX, action.endY, action.durationMs
                )
                is LocalAgentAction.LongPress -> svc.longPress(action.x, action.y, action.durationMs)
                LocalAgentAction.OpenNotifications -> svc.openNotifications()
                LocalAgentAction.OpenRecents -> svc.openRecents()
                is LocalAgentAction.OpenApp -> false
                is LocalAgentAction.Finish -> true
                is LocalAgentAction.SendEmail -> false
                is LocalAgentAction.MakeCall -> false
                is LocalAgentAction.SendSms -> false
                is LocalAgentAction.SetAlarm -> false
                LocalAgentAction.OpenContacts -> false
                LocalAgentAction.ToggleWifi -> false
                LocalAgentAction.ToggleBluetooth -> false
                LocalAgentAction.ToggleFlashlight -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "perform failed: ${e.message}")
            false
        }
    }
}
