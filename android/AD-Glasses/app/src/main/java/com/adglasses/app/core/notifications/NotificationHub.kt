package com.adglasses.app.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.adglasses.app.core.model.CapturedNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

sealed interface NotificationReplyResult {
    data class Sent(val notification: CapturedNotification) : NotificationReplyResult
    data object NoReplyableNotification : NotificationReplyResult
}

class NotificationHub(context: Context) {
    private val appContext = context.applicationContext
    private val interactions = ConcurrentHashMap<String, NotificationInteraction>()

    private val _items = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val items: StateFlow<List<CapturedNotification>> = _items.asStateFlow()

    /**
     * Keeps only lightweight notification text in StateFlow while retaining live PendingIntent /
     * RemoteInput handles in an in-memory map. Handles disappear when the source notification is
     * removed, so stale actions cannot be replayed after the system says the notification is gone.
     */
    fun capture(item: CapturedNotification, notification: Notification) {
        val replyAction = notification.actions
            ?.asSequence()
            ?.mapNotNull { action ->
                // Android's Java API exposes a platform-typed RemoteInput array. filterNotNull()
                // closes that interop boundary so ReplyAction always owns an exact Array<RemoteInput>.
                val inputs: Array<RemoteInput> = action.remoteInputs
                    ?.filterNotNull()
                    ?.filter { it.allowFreeFormInput }
                    ?.toTypedArray()
                    ?: emptyArray()
                if (inputs.isEmpty()) null else ReplyAction(action.actionIntent, inputs)
            }
            ?.firstOrNull()

        val interaction = NotificationInteraction(
            contentIntent = notification.contentIntent,
            replyAction = replyAction,
        )
        if (interaction.contentIntent != null || interaction.replyAction != null) {
            interactions[item.key] = interaction
        } else {
            interactions.remove(item.key)
        }

        val exposed = item.copy(
            canReply = replyAction != null,
            canOpen = notification.contentIntent != null,
        )
        _items.value = (listOf(exposed) + _items.value.filterNot { it.key == exposed.key }).take(50)
    }

    fun remove(key: String) {
        interactions.remove(key)
        _items.value = _items.value.filterNot { it.key == key }
    }

    fun reply(target: String?, message: String): NotificationReplyResult {
        val body = message.trim()
        require(body.isNotEmpty()) { "The reply is empty" }

        val candidate = findReplyable(target) ?: return NotificationReplyResult.NoReplyableNotification
        val action = interactions[candidate.key]?.replyAction
            ?: return NotificationReplyResult.NoReplyableNotification

        val fillIn = Intent()
        val results = Bundle()
        action.remoteInputs.forEach { input -> results.putCharSequence(input.resultKey, body) }
        RemoteInput.addResultsToIntent(action.remoteInputs, fillIn, results)
        RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
        action.pendingIntent.send(appContext, 0, fillIn)
        return NotificationReplyResult.Sent(candidate)
    }

    fun open(key: String): Boolean {
        val action = interactions[key]?.contentIntent ?: return false
        action.send()
        return true
    }

    private fun findReplyable(target: String?): CapturedNotification? {
        val replyable = _items.value.filter { it.canReply && interactions[it.key]?.replyAction != null }
        if (replyable.isEmpty()) return null
        val wanted = target?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return replyable.first()
        return replyable.firstOrNull { item ->
            item.title.lowercase().contains(wanted) ||
                item.appLabel.lowercase().contains(wanted) ||
                item.text.lowercase().contains(wanted)
        }
    }

    private data class NotificationInteraction(
        val contentIntent: PendingIntent?,
        val replyAction: ReplyAction?,
    )

    private data class ReplyAction(
        val pendingIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>,
    )
}
