package com.adglasses.app.core.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.adglasses.app.AppGraph
import com.adglasses.app.core.model.CapturedNotification

class ADNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        if (title.isEmpty() && text.isEmpty()) return
        val label = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse { sbn.packageName }
        AppGraph.notifications.upsert(
            CapturedNotification(
                packageName = sbn.packageName,
                appLabel = label,
                title = title,
                text = text,
                postedAtEpochMs = sbn.postTime,
                key = sbn.key,
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        AppGraph.notifications.remove(sbn.key)
    }
}
