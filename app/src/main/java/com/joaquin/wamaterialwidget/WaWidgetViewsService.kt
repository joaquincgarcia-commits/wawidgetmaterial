package com.joaquin.wamaterialwidget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Backs the widget's scrollable message list (a ListView in the widget
 * layout). The factory re-reads [WaStore] whenever the host asks for fresh
 * data, so the list always mirrors the current unread messages.
 */
class WaWidgetViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WaWidgetFactory(applicationContext)
    }
}

class WaWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<FeedItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        WaReconciler.reconcile(context)
        items = WaWidgetUpdater.feedItems(WaStore.load(context))
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val palette = WaWidgetUpdater.palette(context)
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
        row.setTextViewText(R.id.row_time, WaWidgetUpdater.formatTime(item.timestamp))
        row.setTextColor(R.id.row_time, palette.onBackgroundVariant)
        row.setInt(R.id.row_divider, "setBackgroundColor", palette.divider)
        val fillIn = Intent().putExtra(OpenChatActivity.EXTRA_CONVERSATION_KEY, item.conversationKey)
        row.setOnClickFillInIntent(R.id.row_root, fillIn)
        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
