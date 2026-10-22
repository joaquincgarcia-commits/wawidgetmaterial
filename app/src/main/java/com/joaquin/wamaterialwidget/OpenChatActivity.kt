package com.joaquin.wamaterialwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Trampoline for widget message taps. It lives in its own task (separate
 * taskAffinity, singleInstance) and is fully transparent. The work happens
 * in onResume - when this activity is genuinely in the foreground - because
 * sending another app's notification PendingIntent from a background or
 * not-yet-resumed context is silently blocked by Android's background
 * activity-start rules. From here, the send opens that exact WhatsApp chat;
 * if the conversation is gone or its intent died, WhatsApp's main screen
 * opens instead. Either way, finish() returns to the launcher, never to
 * this app's own screens.
 */
class OpenChatActivity : Activity() {

    private var handled = false
    private var conversationKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conversationKey = intent?.getStringExtra(EXTRA_CONVERSATION_KEY)
    }

    override fun onResume() {
        super.onResume()
        if (handled) return
        handled = true
        openTarget()
        finish()
    }

    private fun openTarget() {
        val key = conversationKey
        if (!key.isNullOrBlank()) {
            WaReconciler.reconcile(this)
            val stillUnread = WaStore.load(this).any { it.key == key }
            val pendingIntent = if (stillUnread) WaIntents.get(key) else null
            if (pendingIntent != null) {
                try {
                    pendingIntent.send()
                    return
                } catch (e: Exception) {
                    // Dead intent: fall through to WhatsApp's main screen.
                }
            }
        }
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

    companion object {
        const val EXTRA_CONVERSATION_KEY = "conversation_key"
    }
}
