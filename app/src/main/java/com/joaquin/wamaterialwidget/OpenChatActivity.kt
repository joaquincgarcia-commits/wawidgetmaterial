package com.joaquin.wamaterialwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Trampoline for widget message taps. It lives in its own task (separate
 * taskAffinity, singleInstance) and is fully transparent; in onResume -
 * genuinely in the foreground - it simply opens WhatsApp's main screen
 * with a plain activity start, which is always allowed.
 *
 * Opening the exact chat by sending the conversation's notification
 * PendingIntent was dropped: on this device those sends fail silently
 * (no exception, no activity), and treating them as success prevented
 * any fallback from running.
 */
class OpenChatActivity : Activity() {

    private var handled = false

    override fun onResume() {
        super.onResume()
        if (handled) return
        handled = true
        try {
            val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
            if (launch != null) {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) {
            // WhatsApp not installed / not launchable: nothing to do.
        }
        finish()
    }

    companion object {
        const val EXTRA_CONVERSATION_KEY = "conversation_key"
    }
}
