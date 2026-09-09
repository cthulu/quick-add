# Quick Add

Quickly add items to your Google Keep lists from anywhere on your Android device using a floating widget.
The app publishes "add X to list Y" messages to **PubNub**, where any subscribing client (e.g. a server-side
script, a Zapier workflow, or your own automation) can consume them and update Google Keep.

## Architecture

```
Phone                                    PubNub Cloud
┌─────────────────┐                      ┌────────────────────┐
│  Android App    │   POST publish       │   ps.pndsn.com     │
│                 │ ───────────────────► │   (publish API)    │
│  Floating       │   message:           │                    │
│  widget UI      │   "add Milk to       │  Channel:          │
│                 │    the list          │  keep-quick-add    │
│                 │    Groceries"        └─────────┬──────────┘
└─────────────────┘                                │ subscribe
                                                   ▼
                                       ┌──────────────────────┐
                                       │  Your subscriber      │
                                       │  → Google Keep        │
                                       └──────────────────────┘
```

## Project layout

```
quick-add/
├── app/                  # Android application module
├── mock-server/          # Local mock of PubNub publish endpoint
├── build.gradle.kts      # Root Gradle build
├── settings.gradle.kts   # Gradle settings
└── README.md
```

## Features

- **Floating widget** — dark Todoist-style overlay anchored above the keyboard
- **Configurable lists** — list names are configured directly in the app's Settings
- **PubNub publish** — submitting an item posts a JSON message to PubNub
  with body `{"message": "add <item> to the list <list>"}`
- **API key auth** — optional `x-api-key` header for Access Manager / function-based auth
- **Persisted last list** — the last selected list is remembered across submissions
- **Launcher shortcut** — long-press the app icon for a "Quick Add" shortcut
- **Auto permission prompt** — overlay permission is requested on first launch
- **Light & dark mode** — adapts to system theme

## Building & running the Android app

1. Open the project root (`quick-add/`) in **Android Studio**
2. Let Gradle sync and download dependencies
3. Run on a device or emulator (minSdk 26, targetSdk 36)

The app intentionally permits cleartext HTTP globally for local, LAN, emulator, and
Tailscale backends. Keep `app/src/main/res/xml/network_security_config.xml` enabled
when using those backends; this is required for local development and private-network
deployments.


## PubNub setup

1. Sign up at [PubNub](https://www.pubnub.com/) and create a keyset
2. In the app, open **Settings** and enter:
   - **Publish key** — `pub-c-...` from PubNub Admin Portal
   - **Subscribe key** — `sub-c-...` from PubNub Admin Portal
   - **API key** *(optional)* — sent as the `x-api-key` HTTP header
   - **Channel** — defaults to `quick-add`
   - **List names** — first entry is required, additional ones can be added/removed
3. Tap **Test Connection** → **Save**
4. Tap the floating widget → type an item → press send

The published message will arrive on your PubNub channel and can be consumed by any subscriber
(e.g. a serverless function that talks to the unofficial Google Keep API).

## Mock server

A zero-dependency Python script that mimics the PubNub publish endpoint locally
for testing the Android app without hitting the real PubNub service.

```bash
python3 mock-server/mock_server.py
```

Then in the Android app **Settings**:
- **Publish key**: `pub-c-test` (anything starting with `pub-`)
- **Subscribe key**: `sub-c-test`
- **Channel**: `keep-quick-add` (or whatever you want)
- **API key**: leave blank

If you want the app to hit the local mock server instead of `ps.pndsn.com`, you must
either:

1. **Change `PUBNUB_BASE_URL` in `AppSettings.kt`** to `http://10.0.2.2:8000`
   for testing, or
2. **Add a hosts override** on your test device pointing `ps.pndsn.com` to
   your machine's IP.

## Subscribing to messages from PubNub

Once published, any PubNub client can subscribe and consume the messages.
Quick test from the command line:

```bash
curl "https://ps.pndsn.com/v2/subscribe/{sub_key}/{channel}/0?tt=0"
```

## Testing

Run the host-side unit tests with:

```bash
./gradlew testDebugUnitTest
```

Device-only coverage is unavailable in this environment. Runtime permission dialogs,
the Android calendar provider, and physical activity recreation still require a device
or emulator.
