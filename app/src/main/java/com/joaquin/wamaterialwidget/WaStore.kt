package com.joaquin.wamaterialwidget

import android.app.PendingIntent
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One unread WhatsApp conversation, as reconstructed from its notification. */
data class WaConversation(
    val key: String,
    val title: String,
    val lastMessage: String,
    val lastSender: String?,
    val isGroup: Boolean,
    val unreadCount: Int,
    val timestamp: Long
)

/**
 * Process-wide, in-memory map from conversation key to the PendingIntent that
 * WhatsApp attached to that conversation's notification (its contentIntent).
 * Tapping a widget row sends this PendingIntent, which is what lets the row
 * open that exact chat. The map is intentionally NOT persisted: PendingIntents
 * cannot be serialized, so after a process death / reboot the map is empty
 * until new notifications arrive, and taps fall back to WhatsApp's main screen.
 */
object WaIntents {
    private val map = ConcurrentHashMap<String, PendingIntent>()
    fun put(key: String, intent: PendingIntent) { map[key] = intent }
    fun get(key: String): PendingIntent? = map[key]
    fun remove(key: String) { map.remove(key) }
}

/** Tiny JSON-file-backed store for the current unread conversations. */
object WaStore {
    private const val FILE_NAME = "wa_conversations.json"
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<WaConversation> = synchronized(lock) {
        val f = file(context)
        if (!f.exists()) return emptyList()
        try {
            val arr = JSONArray(f.readText())
            val out = ArrayList<WaConversation>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    WaConversation(
                        key = o.getString("key"),
                        title = o.getString("title"),
                        lastMessage = o.optString("lastMessage"),
                        lastSender = o.optString("lastSender").ifBlank { null },
                        isGroup = o.optBoolean("isGroup"),
                        unreadCount = o.optInt("unreadCount", 1),
                        timestamp = o.optLong("timestamp")
                    )
                )
            }
            out.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun upsert(context: Context, conversation: WaConversation) {
        val current = load(context).toMutableList()
        current.removeAll { it.key == conversation.key }
        current.add(conversation)
        save(context, current)
    }

    fun remove(context: Context, key: String) {
        val current = load(context).toMutableList()
        if (current.removeAll { it.key == key }) save(context, current)
    }

    private fun save(context: Context, list: List<WaConversation>) = synchronized(lock) {
        val arr = JSONArray()
        for (c in list) {
            arr.put(
                JSONObject()
                    .put("key", c.key)
                    .put("title", c.title)
                    .put("lastMessage", c.lastMessage)
                    .put("lastSender", c.lastSender ?: "")
                    .put("isGroup", c.isGroup)
                    .put("unreadCount", c.unreadCount)
                    .put("timestamp", c.timestamp)
            )
        }
        try {
            file(context).writeText(arr.toString())
        } catch (e: Exception) {
            // Storage failures must never crash the listener.
        }
    }
}
