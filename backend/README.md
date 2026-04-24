# Keep Quick Add — Backend

A lightweight FastAPI proxy that wraps `gkeepapi` to provide REST access to Google Keep lists.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| GET | `/lists` | Get all Keep lists |
| POST | `/lists/{id}/items` | Add item to a list |

## Getting Your Google Master Token

Run this Docker one-liner to get your master token:

```bash
docker run --rm -it python:3.12 sh -c \
  'pip install gpsoauth -q && python3 -c "
import gpsoauth, getpass
email = input(\"Email: \")
oauth_token = input(\"OAuth Token (from https://accounts.google.com/EmbeddedSetup): \")
android_id = input(\"Android ID (16 hex chars, e.g. 1234567890abcdef): \")
res = gpsoauth.perform_master_login(email, oauth_token, android_id)
print(\"Master Token:\", res.get(\"Token\", \"ERROR: \" + str(res)))
"'
```

Or more simply, use an App Password if you have 2FA enabled:

```bash
pip install gpsoauth
python3 -c "
import gpsoauth
res = gpsoauth.perform_master_login('your@gmail.com', 'your_app_password', '0000000000000000')
print(res.get('Token'))
"
```

### What is the Android ID?

The Android ID is a 16-character hexadecimal string that uniquely identifies a (real or virtual) Android device. Google uses it as part of the device registration process when obtaining a master token.

**You can use a fake/placeholder ID** (`0000000000000000`) when using an App Password — Google does not strictly validate it in that flow.

If you need a real Android ID (e.g. for OAuth token flows), you can get it from:

- **From a real Android device:**  
  Install [Device ID](https://play.google.com/store/apps/details?id=com.evozi.deviceid) from the Play Store, or run via ADB:
  ```bash
  adb shell settings get secure android_id
  ```

- **From an emulator:**  
  ```bash
  adb shell settings get secure android_id
  ```

- **Generate a random valid one** (16 hex chars):
  ```bash
  python3 -c "import secrets; print(secrets.token_hex(8))"
  ```

## Running with Docker Compose

```bash
# 1. Copy and fill in credentials
cp .env.example .env
nano .env

# 2. Start the server
docker compose up -d

# 3. Test it
curl http://localhost:8000/lists
curl -X POST http://localhost:8000/lists/{list_id}/items \
  -H "Content-Type: application/json" \
  -d '{"text": "Milk"}'
```

## Running Locally (without Docker)

```bash
pip install -r requirements.txt
export GOOGLE_EMAIL=your@gmail.com
export GOOGLE_MASTER_TOKEN=your_token
uvicorn main:app --reload --port 8000
```

## Mock Server (for local testing, no credentials needed)

A zero-dependency mock server is included for testing the Android app without real Google credentials.

```bash
# No pip install needed - uses Python stdlib only
python3 mock_server.py
```

The mock server:
- Runs on `http://localhost:8000` (same port as real server)
- Returns 6 pre-configured mock lists (Groceries, Ideas, Inbox, etc.)
- Accepts `POST /lists/{id}/items` and logs added items to console
- Prints a session summary of all added items on exit (Ctrl+C)

**Android emulator:** set backend URL to `http://10.0.2.2:8000` in the app's Settings screen.  
**Real device:** set backend URL to `http://<your-machine-ip>:8000`.
