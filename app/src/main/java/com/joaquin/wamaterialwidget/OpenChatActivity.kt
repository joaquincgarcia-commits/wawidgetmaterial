package com.joaquin.wamaterialwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible trampoline for widget message taps. The launcher delivers the
 * tap here (a user-initiated, foreground start), and from this foreground
 * context we send the tapped conversation's notification PendingIntent,
 * which opens that exact WhatsApp chat. Falls back to WhatsApp's main
 * screen when the conversation is no longer unread or its intent is gone.
 */
class OpenChatActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent?.getStringExtra(EXTRA_CONVERSATION_KEY)
        var opened = false
        if (!key.isNullOrBlank()) {
            WaReconciler.reconcile(this)
            val stillUnread = WaStore.load(this).any { it.key == key }
            val pendingIntent = if (stillUnread) WaIntents.get(key) else null
            if (pendingIntent != null) {
                try {
                    pendingIntent.send()
                    opened = true
                } catch (e: Exception) {
                    // Dead intent: fall through to WhatsApp's main screen.
                }
            }
        }
        if (!opened) {
            try {
                val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
                    ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
                if (launch != null) {
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (e: Exception) {
                // WhatsApp not installed / not launchable: nothing to do.
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_CONVERSATION_KEY = "conversation_key"
    }
}
