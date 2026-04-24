# Keep Quick Add

Quickly add items to your Google Keep lists from anywhere on your Android device using a floating widget.

## Architecture

```
keep-quick-add/
├── android/    # Android app (floating widget)
└── backend/    # Python proxy server (wraps gkeepapi)
```

```
Phone                        Your machine / home server
┌─────────────────┐          ┌──────────────────────────┐
│  Android App    │  HTTP    │  Python FastAPI Backend   │
│                 │ ───────► │  (gkeepapi wrapper)      │
│  Floating       │ ◄─────── │                          │
│  widget UI      │  JSON    │  GET  /lists             │
│                 │          │  POST /lists/{id}/items   │
└─────────────────┘          └──────────┬───────────────┘
                                        │ private API
                                        ▼
                              ┌──────────────────────┐
                              │    Google Keep        │
                              └──────────────────────┘
```

## Modules

### `android/`
The Android app. Open this folder in Android Studio.

- **Floating widget** — dark Todoist-style overlay anchored above the keyboard
- **List dropdown** — fetched from backend, cached locally for 30 minutes
- **Launcher shortcut** — long-press the app icon for a "Quick Add" shortcut
- **Settings screen** — configure the backend URL from within the app

See [`android/README.md`](android/README.md) for setup and build instructions.

### `backend/`
A lightweight FastAPI server that wraps [`gkeepapi`](https://github.com/kiwiz/gkeepapi) to expose Google Keep lists via REST.

- `GET /health` — health check
- `GET /lists` — returns all Keep lists
- `POST /lists/{id}/items` — adds an item to a list
- Dockerized for easy deployment (home server, Tailscale, Cloud Run)
- Includes a **mock server** for local testing without Google credentials

See [`backend/README.md`](backend/README.md) for setup and deployment instructions.

## Quick Start

**1. Start the backend (mock mode for testing):**
```bash
cd backend
python3 mock_server.py
```

**2. Open the Android app in Android Studio:**
```
File → Open → select the android/ folder
```

**3. Configure the backend URL in the app:**
```
Main screen → ⚙ Configure Backend URL → http://10.0.2.2:8000 → Test → Save
```

**4. Grant overlay permission and start the widget.**

**5. Long-press the app icon to add the "Quick Add" shortcut to your home screen.**
