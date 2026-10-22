# WhatsApp Material Widget

A personal-use Android home-screen widget that shows your unread WhatsApp
messages in a proper Material You / Material 3 design — the way the rest of
your home screen already looks.

The official WhatsApp widget does not follow your wallpaper's dynamic color
or the system light/dark setting. This one does.

## Features

- **Dynamic color**: built with Jetpack Glance + Material 3 theme colors
  (`widgetBackground`, `primaryContainer`, `secondaryContainer`, ...), so the
  widget re-tints when your wallpaper changes — a green wallpaper gives you a
  green widget — and it follows the system's light/dark mode automatically.
  No colors are hard-coded.
- **No branding clutter**: the header is just the word "WhatsApp" and a pill
  ("N new" / "Up to date"). All UI text is in English.
- **Tap a chat, open that chat**: each row reuses the notification's own
  intent, so tapping "Flavia" opens the conversation with Flavia, not just
  WhatsApp's home screen.
- **Private by design**: the app has **no INTERNET permission**. Everything
  happens on the phone. It only reads WhatsApp (`com.whatsapp` /
  `com.whatsapp.w4b`) notifications, on-device.
- Two sizes: a medium 4x2 list (up to 3 chats) and a compact count widget.

## Get the APK (GitHub Actions — no Android Studio needed)

This repo includes a workflow (`.github/workflows/build-apk.yml`) that builds
the APK in the cloud:

1. Create a new repository on GitHub (private is fine).
2. Upload the **contents** of this folder to the repo — web upload, GitHub
   Desktop, or `git push`, whatever you prefer — and commit to `main`.
3. Open the repo's **Actions** tab. The "Build APK" workflow runs on every
   push (you can also start it manually with "Run workflow").
4. When it finishes green, open the run and download the
   **WA-Material-Widget-debug** artifact (a zip containing `app-debug.apk`).
5. Copy the APK to your phone and install it.

## Build locally (optional)

Requires JDK 17 and the Android SDK (or just open the project in
Android Studio and let it sync):

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Set it up on the phone

1. Install the APK. If Android blocks the file manager from installing it,
   allow "Install unknown apps" for that app when prompted.
2. Open **WhatsApp Material Widget** and tap the button to grant
   **Notification Access**. On newer Android versions, sideloaded apps first
   require: Settings → Apps → WhatsApp Material Widget → ⋮ →
   "Allow restricted settings".
3. Long-press your home screen → Widgets → add **WhatsApp Material Widget**.

## How it works / limitations

- Android gives apps no API into WhatsApp, so the widget is fed by WhatsApp's
  own notifications (`NotificationListenerService`, `MessagingStyle` data).
- It only shows messages that arrive as notifications **after** the app is
  installed and access is granted. There is no chat history import.
- Muted chats or chats whose notifications are hidden won't appear.
- The per-chat tap uses each notification's stored open-intent, kept in
  memory. After a reboot, before any new notification arrives for a chat,
  tapping its row falls back to opening WhatsApp's main screen.
- Reading a chat in WhatsApp dismisses its notification, which also clears
  it from the widget.

## Tech

Kotlin · Jetpack Glance 1.1.1 (`glance-appwidget`, `glance-material3`) ·
Compose Material 3 · minSdk 31 · targetSdk 35 · AGP 8.7.3 · Gradle 8.11.1
