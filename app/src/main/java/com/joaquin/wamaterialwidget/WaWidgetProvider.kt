package com.joaquin.wamaterialwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Home-screen widget built on classic RemoteViews (not Glance) on purpose:
 * each message row gets the conversation's own notification PendingIntent via
 * setOnClickPendingIntent, so the tap is delivered by the launcher — the same
 * path as tapping the notification itself — and opens that exact chat.
 *
 * Colors are read from the Android 12+ system dynamic palette
 * (android.R.color.system_*) at render time, so the widget follows the
 * wallpaper's Material You tones and the system light/dark mode.
 */
class WaWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WaReconciler.reconcile(context)
        for (id in appWidgetIds) {
            WaWidgetUpdater.updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        WaWidgetUpdater.updateWidget(context, appWidgetManager, appWidgetId)
    }
}

/** One row of the widget feed: a single unread message. */
private data class FeedItem(
    val conversationKey: String,
    val title: String,
    val isGroup: Boolean,
    val sender: String?,
    val text: String,
    val timestamp: Long
)

private data class WaPalette(
    val background: Int,
    val onBackground: Int,
    val onBackgroundVariant: Int,
    val pillBackground: Int,
    val pillText: Int,
    val divider: Int
)

object WaWidgetUpdater {

    fun updateAll(context: Context) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WaWidgetReceiver::class.java))
            for (id in ids) updateWidget(context, manager, id)
        } catch (e: Exception) {
            // Host unavailable: nothing to do.
        }
    }

    fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        try {
            val options = manager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)
            val views = if (minWidth < 190) {
                buildCompact(context)
            } else {
                val maxRows = ((minHeight - 92) / 46).coerceIn(2, 8)
                buildMedium(context, maxRows)
            }
            manager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            // A failed render must never crash the caller (listener, receiver).
        }
    }

    private fun buildMedium(context: Context, maxRows: Int): RemoteViews {
        val palette = palette(context)
        val conversations = WaStore.load(context)
        val feed = feedItems(conversations)
        val openWhatsApp = openWhatsAppPendingIntent(context)

        val views = RemoteViews(context.packageName, R.layout.wa_widget)
        views.setInt(R.id.widget_root, "setBackgroundColor", palette.background)
        views.setOnClickPendingIntent(R.id.widget_root, openWhatsApp)
        views.setOnClickPendingIntent(R.id.widget_header, openWhatsApp)
        views.setTextColor(R.id.header_title, palette.onBackground)

        if (feed.isEmpty()) {
            views.setViewVisibility(R.id.header_pill, View.GONE)
            views.setViewVisibility(R.id.header_subtitle, View.GONE)
            views.setViewVisibility(R.id.rows_container, View.GONE)
            views.setViewVisibility(R.id.empty_text, View.VISIBLE)
            views.setTextColor(R.id.empty_text, palette.onBackgroundVariant)
            views.setOnClickPendingIntent(R.id.empty_text, openWhatsApp)
            return views
        }

        views.setViewVisibility(R.id.empty_text, View.GONE)
        views.setViewVisibility(R.id.rows_container, View.VISIBLE)
        views.setViewVisibility(R.id.header_pill, View.VISIBLE)
        views.setViewVisibility(R.id.header_subtitle, View.VISIBLE)
        views.setTextViewText(R.id.header_pill, "${feed.size} new")
        views.setTextColor(R.id.header_pill, palette.pillText)
        views.setColorStateList(
            R.id.header_pill,
            "setBackgroundTintList",
            ColorStateList.valueOf(palette.pillBackground)
        )
        views.setTextViewText(
            R.id.header_subtitle,
            "${feed.size} unread ${if (feed.size == 1) "message" else "messages"} " +
                "in ${conversations.size} ${if (conversations.size == 1) "chat" else "chats"}"
        )
        views.setTextColor(R.id.header_subtitle, palette.onBackgroundVariant)

        views.removeAllViews(R.id.rows_container)
        val shown = feed.take(maxRows)
        for (item in shown) {
            val row = RemoteViews(context.packageName, R.layout.wa_widget_row)
            row.setTextViewText(R.id.row_title, item.title)
            row.setTextColor(R.id.row_title, palette.onBackground)
            val body = if (item.isGroup && !item.sender.isNullOrBlank()) {
                "${item.sender}: ${item.text}"
            } else {
                item.text
            }
            row.setTextViewText(R.id.row_text, body)
            row.setTextColor(R.id.row_text, palette.onBackgroundVariant)
            row.setTextViewText(R.id.row_time, formatTime(item.timestamp))
            row.setTextColor(R.id.row_time, palette.onBackgroundVariant)
            row.setInt(R.id.row_divider, "setBackgroundColor", palette.divider)
            // The notification's own PendingIntent opens this exact chat; if it
            // is not in memory (e.g. right after a reboot), fall back to the
            // WhatsApp home intent.
            row.setOnClickPendingIntent(R.id.row_root, WaIntents.get(item.conversationKey) ?: openWhatsApp)
            views.addView(R.id.rows_container, row)
        }
        val extra = feed.size - shown.size
        if (extra > 0) {
            val more = RemoteViews(context.packageName, R.layout.wa_widget_more)
            more.setTextViewText(R.id.more_text, "+$extra more")
            more.setTextColor(R.id.more_text, palette.onBackgroundVariant)
            more.setOnClickPendingIntent(R.id.more_text, openWhatsApp)
            views.addView(R.id.rows_container, more)
        }
        return views
    }

    private fun buildCompact(context: Context): RemoteViews {
        val palette = palette(context)
        val conversations = WaStore.load(context)
        val feed = feedItems(conversations)
        val openWhatsApp = openWhatsAppPendingIntent(context)

        val views = RemoteViews(context.packageName, R.layout.wa_widget_compact)
        views.setInt(R.id.compact_root, "setBackgroundColor", palette.background)
        views.setOnClickPendingIntent(R.id.compact_root, openWhatsApp)
        views.setTextColor(R.id.compact_title, palette.onBackground)
        if (feed.isEmpty()) {
            views.setViewVisibility(R.id.compact_main, View.GONE)
            views.setInt(R.id.compact_root, "setGravity", Gravity.CENTER_VERTICAL)
            views.setTextViewText(R.id.compact_sub, "No unread messages")
            views.setTextViewTextSize(R.id.compact_sub, TypedValue.COMPLEX_UNIT_SP, 13f)
            views.setTextColor(R.id.compact_sub, palette.onBackgroundVariant)
        } else {
            views.setTextViewText(R.id.compact_main, "${feed.size} new")
            views.setTextColor(R.id.compact_main, palette.onBackground)
            views.setTextViewText(
                R.id.compact_sub,
                "in ${conversations.size} ${if (conversations.size == 1) "chat" else "chats"}"
            )
            views.setTextColor(R.id.compact_sub, palette.onBackgroundVariant)
        }
        return views
    }

    private fun feedItems(conversations: List<WaConversation>): List<FeedItem> {
        val items = ArrayList<FeedItem>()
        for (conversation in conversations) {
            val messages = if (conversation.messages.isNotEmpty()) {
                conversation.messages
            } else {
                listOf(WaMessage(conversation.lastMessage, conversation.lastSender, conversation.timestamp))
            }
            for (message in messages) {
                items.add(
                    FeedItem(
                        conversationKey = conversation.key,
                        title = conversation.title,
                        isGroup = conversation.isGroup,
                        sender = if (conversation.isGroup) message.sender else null,
                        text = message.text,
                        timestamp = message.timestamp
                    )
                )
            }
        }
        return items.sortedByDescending { it.timestamp }
    }

    private fun palette(context: Context): WaPalette {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        fun color(resId: Int) = context.getColor(resId)
        return if (night) {
            WaPalette(
                background = color(android.R.color.system_accent2_900),
                onBackground = color(android.R.color.system_neutral1_100),
                onBackgroundVariant = color(android.R.color.system_neutral1_300),
                pillBackground = color(android.R.color.system_accent1_800),
                pillText = color(android.R.color.system_accent1_100),
                divider = color(android.R.color.system_neutral1_700)
            )
        } else {
            WaPalette(
                background = color(android.R.color.system_accent2_100),
                onBackground = color(android.R.color.system_neutral1_900),
                onBackgroundVariant = color(android.R.color.system_neutral1_600),
                pillBackground = color(android.R.color.system_accent1_200),
                pillText = color(android.R.color.system_accent1_900),
                divider = color(android.R.color.system_neutral1_300)
            )
        }
    }

    private fun openWhatsAppPendingIntent(context: Context): PendingIntent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.whatsapp")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTime(timestamp: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        return when {
            sameDay -> SimpleDateFormat("h:mm a", Locale.US).format(then.time)
            isYesterday -> "Yesterday"
            else -> SimpleDateFormat("MMM d", Locale.US).format(then.time)
        }
    }
}
