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
