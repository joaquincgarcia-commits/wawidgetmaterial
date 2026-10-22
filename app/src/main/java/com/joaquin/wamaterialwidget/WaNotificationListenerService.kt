package com.joaquin.wamaterialwidget

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Listens ONLY to WhatsApp notifications and mirrors the currently-unread
 * conversations into [WaStore] so the widget can render them.
 * The app declares no INTERNET permission: nothing ever leaves the device.
 */
class WaNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        WaListenerRef.instance = this
        if (WaReconciler.reconcile(applicationContext)) refreshWidget()
    }

    override fun onListenerDisconnected() {
        if (WaListenerRef.instance === this) WaListenerRef.instance = null
        try {
            requestRebind(ComponentName(applicationContext, WaNotificationListenerService::class.java))
        } catch (e: Exception) {
            // System will rebind on its own schedule.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val parsed = parseIfMessage(sbn) ?: return
        sbn.notification?.contentIntent?.let { WaIntents.put(parsed.key, it) }
        WaStore.upsert(applicationContext, parsed)
        WaReconciler.reconcile(applicationContext)
        refreshWidget()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        val key = conversationKey(sbn.packageName, titleOf(sbn.notification?.extras))
        if (key != null) {
            WaIntents.remove(key)
            WaStore.remove(applicationContext, key)
        }
        // Self-heal even when the removed notification's key does not match the
        // stored one: rebuild from what the system says is still active.
        WaReconciler.reconcile(applicationContext)
        refreshWidget()
    }

    /**
     * Parses one notification into a conversation, or returns null when the
     * notification is not a chat message (group summary, progress/service
     * notice, system text, or empty).
     */
    internal fun parseIfMessage(sbn: StatusBarNotification): WaConversation? {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return null
        val notification = sbn.notification ?: return null
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = notification.extras
        if (extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return null
        if (notification.category == Notification.CATEGORY_SERVICE) return null
        val parsed = parseConversation(sbn, extras) ?: return null
        if (parsed.lastMessage.lowercase() in SYSTEM_TEXTS) return null
        if (parsed.title == "WhatsApp" && parsed.lastMessage.isBlank()) return null
        return parsed
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

        val parsedMessages = ArrayList<WaMessage>()

        @Suppress("DEPRECATION")
        val rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!rawMessages.isNullOrEmpty()) {
            for (item in rawMessages) {
                val bundle = item as? Bundle ?: continue
                val text = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                if (text.isBlank()) continue
                val sender = bundle.getCharSequence("sender")?.toString()?.trim()?.ifBlank { null }
                val time = bundle.getLong("time", sbn.postTime).takeIf { it > 0 } ?: sbn.postTime
                parsedMessages.add(WaMessage(text = text, sender = if (isGroup) sender else null, timestamp = time))
            }
        }
        if (parsedMessages.isEmpty()) {
            // WhatsApp also uses InboxStyle for some notifications: the unread
            // messages arrive as plain text lines instead of MessagingStyle
            // bundles. Read those too, so no message is dropped.
            @Suppress("DEPRECATION")
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!lines.isNullOrEmpty()) {
                lines.forEachIndexed { index, line ->
                    val text = line?.toString()?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        parsedMessages.add(
                            WaMessage(text = text, sender = null, timestamp = sbn.postTime + index)
                        )
                    }
                }
            }
        }
        if (parsedMessages.isEmpty()) {
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()?.trim().orEmpty()
            if (text.isBlank()) return null
            // Non-MessagingStyle fallback: in groups, EXTRA_TITLE is the sender.
            parsedMessages.add(
                WaMessage(
                    text = text,
                    sender = if (isGroup) title?.ifBlank { null } else null,
                    timestamp = sbn.postTime
                )
            )
        }

        val last = parsedMessages.last()
        return WaConversation(
            key = conversationKey(sbn.packageName, displayTitle) ?: sbn.key,
            title = displayTitle,
            lastMessage = last.text,
            lastSender = if (isGroup) last.sender else null,
            isGroup = isGroup,
            unreadCount = parsedMessages.size.coerceAtLeast(1),
            timestamp = last.timestamp,
            messages = parsedMessages
        )
    }

    private fun refreshWidget() {
        WaWidgetUpdater.updateAll(applicationContext)
    }

    companion object {
        internal val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val SYSTEM_TEXTS = setOf(
            "checking for new messages",
            "waiting for this message. this may take a while.",
            "backing up messages",
            "restoring messages"
        )
    }
}

/** Process-wide reference to the currently bound listener instance, if any. */
object WaListenerRef {
    @Volatile
    var instance: WaNotificationListenerService? = null
}

/**
 * Rebuilds [WaStore] from the notifications the system reports as active
 * right now. This is the self-healing path: a removal event that was missed
 * (or whose key did not match) can no longer leave a read chat stuck in the
 * widget, because any reconcile pass drops whatever is not active anymore.
 * Returns true when the stored list changed.
 */
object WaReconciler {
    fun reconcile(context: Context): Boolean {
        val service = WaListenerRef.instance ?: return false
        val actives = try {
            service.activeNotifications
        } catch (e: Exception) {
            null
        } ?: return false

        val parsed = ArrayList<WaConversation>()
        for (sbn in actives) {
            val conversation = service.parseIfMessage(sbn) ?: continue
            parsed.add(conversation)
            sbn.notification?.contentIntent?.let { WaIntents.put(conversation.key, it) }
        }
        WaIntents.retainKeys(parsed.map { it.key }.toSet())

        val sorted = parsed.sortedByDescending { it.timestamp }
        val changed = WaStore.load(context) != sorted
        if (changed) WaStore.replaceAll(context, sorted)
        return changed
    }

    /** Asks the widget host to re-render every placed widget. */
    fun refreshWidget(context: Context) {
        WaWidgetUpdater.updateAll(context)
    }
}
