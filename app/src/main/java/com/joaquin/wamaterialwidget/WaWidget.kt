package com.joaquin.wamaterialwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.LocalSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CompactSize = DpSize(110.dp, 110.dp)
private val MediumSize = DpSize(250.dp, 140.dp)

private val ConversationKey = ActionParameters.Key<String>("conversation_key")

/** Opens WhatsApp's main screen (header tap / universal fallback). */
class OpenWhatsAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (WaReconciler.reconcile(context)) WaReconciler.refreshWidget(context)
        openWhatsAppHome(context)
    }
}

/**
 * Opens one exact conversation by sending the PendingIntent that WhatsApp
 * attached to that conversation's notification. If there is no live
 * PendingIntent (e.g. after a reboot, before any new notification for that
 * chat arrives) or sending it fails, falls back to WhatsApp's main screen.
 */
class OpenChatAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // If the chat was already read, its notification is gone: clean the
        // store and fall back to WhatsApp's main screen instead of trying to
        // send a dead PendingIntent.
        val changed = WaReconciler.reconcile(context)
        if (changed) WaReconciler.refreshWidget(context)
        val key = parameters[ConversationKey]
        val stillUnread = key != null && WaStore.load(context).any { it.key == key }
        val pendingIntent = if (stillUnread) WaIntents.get(key!!) else null
        if (pendingIntent != null) {
            try {
                pendingIntent.send()
                return
            } catch (e: PendingIntent.CanceledException) {
                // Fall through to the home-screen fallback.
            } catch (e: Exception) {
                // Fall through to the home-screen fallback.
            }
        }
        openWhatsAppHome(context)
    }
}

internal fun openWhatsAppHome(context: Context) {
    try {
        val launch = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
            ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(launch)
    } catch (e: Exception) {
        // WhatsApp not installed: nothing sensible to open.
    }
}

class WaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaWidget()
}

class WaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(CompactSize, MediumSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Self-heal against the system's current notifications before
        // rendering: a chat that was already read must not render as unread.
        WaReconciler.reconcile(context)
        val conversations = WaStore.load(context)
        provideContent {
            GlanceTheme {
                WidgetContent(conversations)
            }
        }
    }

}

@Composable
private fun WidgetContent(conversations: List<WaConversation>) {
    val size = LocalSize.current
    val totalUnread = conversations.sumOf { it.unreadCount }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .padding(16.dp)
    ) {
        if (size.width < 180.dp) {
            CompactContent(conversations.size, totalUnread)
        } else {
            MediumContent(conversations, totalUnread)
        }
    }
}

@Composable
private fun CompactContent(chatCount: Int, totalUnread: Int) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .clickable(actionRunCallback<OpenWhatsAppAction>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "WhatsApp",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(GlanceModifier.height(4.dp))
        if (totalUnread > 0) {
            Text(
                text = totalUnread.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = if (chatCount == 1) "unread in 1 chat" else "unread in $chatCount chats",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        } else {
            Text(
                text = "✓",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "No new messages",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MediumContent(conversations: List<WaConversation>, totalUnread: Int) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header: text only (no logo), plus the status pill.
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .clickable(actionRunCallback<OpenWhatsAppAction>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WhatsApp",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            if (totalUnread > 0) {
                Pill(
                    text = if (totalUnread == 1) "1 new" else "$totalUnread new",
                    background = { GlanceTheme.colors.primaryContainer },
                    foreground = { GlanceTheme.colors.onPrimaryContainer }
                )
            } else {
                Pill(
                    text = "Up to date",
                    background = { GlanceTheme.colors.secondaryContainer },
                    foreground = { GlanceTheme.colors.onSurfaceVariant }
                )
            }
        }

        Spacer(GlanceModifier.height(10.dp))

        if (conversations.isEmpty()) {
            EmptyState()
        } else {
            conversations.take(3).forEachIndexed { index, conversation ->
                if (index > 0) Spacer(GlanceModifier.height(6.dp))
                ConversationRow(conversation)
            }
        }
    }
}

@Composable
private fun Pill(
    text: String,
    background: @Composable () -> androidx.glance.unit.ColorProvider,
    foreground: @Composable () -> androidx.glance.unit.ColorProvider
) {
    Box(
        modifier = GlanceModifier
            .background(background())
            .cornerRadius(12.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = foreground(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun EmptyState() {
    Row(
        modifier = GlanceModifier.clickable(actionRunCallback<OpenWhatsAppAction>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(44.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(GlanceModifier.width(12.dp))
        Column {
            Text(
                text = "No new messages",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = "New messages will show up here.",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
private fun ConversationRow(conversation: WaConversation) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(16.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clickable(
                actionRunCallback<OpenChatAction>(
                    actionParametersOf(ConversationKey to conversation.key)
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = conversation.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = formatTime(conversation.timestamp),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
            val preview = if (conversation.isGroup && !conversation.lastSender.isNullOrBlank()) {
                "${conversation.lastSender}: ${conversation.lastMessage}"
            } else {
                conversation.lastMessage
            }
            Text(
                text = preview,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        // Circular unread-count badge.
        Box(
            modifier = GlanceModifier
                .size(22.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "h:mm a" else "EEE"
    return SimpleDateFormat(pattern, Locale.US).format(Date(timestamp))
}
