# Keep Quick Add — Android App

A floating widget Android app for quickly adding items to Google Keep lists.

## Opening in Android Studio

1. Open **Android Studio**
2. Choose **File → Open**
3. Navigate to and select the **`android/`** folder (this folder, not the repo root)
4. Wait for Gradle sync to complete (downloads dependencies automatically)
5. Create or select an emulator: **Tools → Device Manager → Create Device** (API 26+)
6. Press **Run ▶** to build and install

> ⚠️ Open the `android/` subfolder directly, not the repo root `keep-quick-add/`

## Features

- **Floating Widget**: Dark Todoist-style overlay anchored above the keyboard
- **List Selection**: Dropdown showing your Google Keep lists (fetched from backend)
- **30-minute Cache**: Lists are cached locally, with manual refresh in the toolbar
- **Quick Input**: Type item name, press Enter or tap send to add
- **Launcher Shortcut**: Long-press the app icon for a "Quick Add" shortcut
- **Dismiss**: Tap outside or press Back to close

## Architecture

- `MainActivity` — Permission handling, service control, cache status + refresh
- `FloatingWidgetService` — Foreground service managing the overlay widget
- `KeepRepository` — Networking (OkHttp) + 30-minute SharedPreferences cache
- `QuickAddShortcutActivity` — Transparent launcher for shortcut

## Backend Configuration

The app connects to the Python backend. Default URL (emulator → host machine):
```
http://10.0.2.2:8000
```

To use a real device or Tailscale, update `BASE_URL` in `KeepRepository.kt`:
```kotlin
private const val BASE_URL = "http://YOUR_IP_OR_TAILSCALE_IP:8000"
```

## Permissions Required

- `INTERNET` — Backend API calls
- `SYSTEM_ALERT_WINDOW` — Draw over other apps
- `FOREGROUND_SERVICE` — Keep widget running
- `POST_NOTIFICATIONS` — Foreground service notification

## Usage

1. Start the backend (`cd ../backend && docker compose up -d`)
2. Launch the app and grant overlay permission
3. Tap **Start Floating Widget** (or use the launcher shortcut)
4. Select a Keep list from the dropdown
5. Type your item → press Enter or tap the send button
6. Toast confirms the item was added → widget closes
