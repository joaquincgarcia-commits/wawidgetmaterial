package com.joaquin.wamaterialwidget

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens ONLY to WhatsApp notifications and mirrors the currently-unread
 * conversations into [WaStore] so the Glance widget can render them.
 * The app declares no INTERNET permission: nothing ever leaves the device.
 */
class WaNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        val notification = sbn.notification ?: return

        // Group summaries only aggregate other notifications; skip them.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // Progress / service notices (backup, restore, verification…) are not messages.
        val extras = notification.extras
        if (extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return
        if (notification.category == Notification.CATEGORY_SERVICE) return

        val parsed = parseConversation(sbn, extras) ?: return
        if (parsed.lastMessage.lowercase() in SYSTEM_TEXTS) return
        if (parsed.title == "WhatsApp" && parsed.lastMessage.isBlank()) return

        notification.contentIntent?.let { WaIntents.put(parsed.key, it) }
        WaStore.upsert(applicationContext, parsed)
        refreshWidget()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        val key = conversationKey(sbn.packageName, titleOf(sbn.notification?.extras)) ?: return
        WaIntents.remove(key)
        WaStore.remove(applicationContext, key)
        refreshWidget()
    }

    private fun titleOf(extras: Bundle?): String? {
        if (extras == null) return null
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        return (conversationTitle ?: title)?.trim()?.ifBlank { null }
    }

    private fun conversationKey(packageName: String, title: String?): String? =
        title?.let { "$packageName:$it" }

    private fun parseConversation(sbn: StatusBarNotification, extras: Bundle): WaConversation? {
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val displayTitle = (conversationTitle ?: title)?.ifBlank { null } ?: return null
        val isGroup = !conversationTitle.isNullOrBlank()

        var lastMessage = ""
        var lastSender: String? = null
        var lastTime = sbn.postTime
        var count = 1

        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!messages.isNullOrEmpty()) {
            count = messages.size
            val last = messages.last() as? Bundle
            if (last != null) {
                lastMessage = last.getCharSequence("text")?.toString()?.trim().orEmpty()
                lastSender = last.getCharSequence("sender")?.toString()?.trim()?.ifBlank { null }
                lastTime = last.getLong("time", sbn.postTime).takeIf { it > 0 } ?: sbn.postTime
            }
        }
        if (lastMessage.isBlank()) {
            lastMessage = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()?.trim().orEmpty()
        }
        if (lastMessage.isBlank()) return null

        return WaConversation(
            key = conversationKey(sbn.packageName, displayTitle) ?: sbn.key,
            title = displayTitle,
            lastMessage = lastMessage,
            lastSender = if (isGroup) lastSender else null,
            isGroup = isGroup,
            unreadCount = count.coerceAtLeast(1),
            timestamp = lastTime
        )
    }

    private fun refreshWidget() {
        scope.launch {
            try {
                val manager = GlanceAppWidgetManager(applicationContext)
                val widget = WaWidget()
                manager.getGlanceIds(WaWidget::class.java).forEach { glanceId ->
                    widget.update(applicationContext, glanceId)
                }
            } catch (e: Exception) {
                // Widget not placed yet, or host unavailable: nothing to do.
            }
        }
    }

    companion object {
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val SYSTEM_TEXTS = setOf(
            "checking for new messages",
            "waiting for this message. this may take a while.",
            "backing up messages",
            "restoring messages"
        )
    }
}
