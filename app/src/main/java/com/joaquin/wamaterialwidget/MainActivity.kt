package com.joaquin.wamaterialwidget

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {

    private var accessEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WaMaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetupScreen(accessEnabled = accessEnabled)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)
        WaReconciler.reconcile(this)
        WaWidgetUpdater.updateAll(this)
    }
}

@Composable
private fun WaMaterialTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
private fun SetupScreen(accessEnabled: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "WhatsApp Material Widget",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "A home-screen widget that shows your unread WhatsApp messages " +
                "in Material You style. It takes its colors from your wallpaper " +
                "(dynamic color) and follows your system light/dark setting automatically.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Everything stays on this phone: the app has no internet permission, " +
                "no account, and no ads. It only reads WhatsApp notifications — never " +
                "your chat database — and tapping a conversation in the widget opens " +
                "that exact chat in WhatsApp.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (accessEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = if (accessEnabled) "Notification Access is ON" else "Notification Access is OFF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (accessEnabled) {
                        "The widget can now show new WhatsApp messages as they arrive."
                    } else {
                        "This is the one permission the app needs. Without it, the widget " +
                            "cannot see incoming WhatsApp messages."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Notification Access settings")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "In the list, find “WhatsApp Material Widget” and turn it on. " +
                "If Android says the setting is restricted, open this app's App info, " +
                "tap the ⋮ menu and choose “Allow restricted settings” first.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Add the widget",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "1. Long-press an empty area of your home screen.\n" +
                "2. Tap Widgets.\n" +
                "3. Find “WhatsApp Material Widget” and drag it onto your home screen.\n" +
                "4. Resize it: small shows the unread count, wide shows your latest chats.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Good to know: the widget can only show messages that arrive as " +
                "notifications after Notification Access is on. Muted chats and chats " +
                "whose notifications are hidden will not appear.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
