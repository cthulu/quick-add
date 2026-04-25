#!/usr/bin/env python3
"""
Mock backend server for local testing of Keep Quick Add.

Simulates the real backend API without requiring Google credentials.
Run with: python3 mock_server.py
"""

import json
import logging
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

PORT = 8000

# --- Mock data ---
MOCK_LISTS = [
    {"id": "list-001", "title": "Shopping"},
    {"id": "list-003", "title": "Inbox"},
    {"id": "list-005", "title": "Todo"},
]

# In-memory store of added items: {list_title: [item_text, ...]}
ADDED_ITEMS: dict[str, list[str]] = {lst["title"]: [] for lst in MOCK_LISTS}


class MockHandler(BaseHTTPRequestHandler):

    def log_message(self, format, *args):
        log.info(f"{self.address_string()} - {format % args}")

    def send_json(self, code: int, data):
        body = json.dumps(data, indent=2).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_json_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length == 0:
            return {}
        return json.loads(self.rfile.read(length))

    def do_GET(self):
        path = urlparse(self.path).path

        if path == "/health":
            self.send_json(200, {"status": "ok", "mode": "mock"})
        else:
            self.send_json(404, {"detail": f"Not found: {path}"})

    def do_POST(self):
        path = urlparse(self.path).path

        # POST /items
        if path == "/items":
            body = self.read_json_body()
            text = body.get("text", "").strip()
            list_name = body.get("list_name", "").strip()

            if not text:
                self.send_json(400, {"detail": "text field is required"})
                return
            if not list_name:
                self.send_json(400, {"detail": "list_name field is required"})
                return

            # Find the list by name (case-insensitive)
            lst = next(
                (l for l in MOCK_LISTS if l["title"].lower() == list_name.lower()),
                None
            )
            if lst is None:
                self.send_json(404, {"detail": f"List '{list_name}' not found"})
                return

            ADDED_ITEMS[lst["title"]].append(text)
            log.info(f"✓ Added '{text}' to '{lst['title']}' (total: {len(ADDED_ITEMS[lst['title']])} items)")
            self.send_json(201, {"message": f"Added '{text}' to '{lst['title']}'"})

        else:
            self.send_json(404, {"detail": f"Not found: {path}"})


def main():
    server = HTTPServer(("0.0.0.0", PORT), MockHandler)
    log.info(f"🟢 Mock Keep API server running at http://localhost:{PORT}")
    log.info(f"   GET  /health         → health check")
    log.info(f"   GET  /lists          → returns {len(MOCK_LISTS)} mock lists")
    log.info(f"   POST /lists/{{id}}/items → add item to a list")
    log.info(f"")
    log.info(f"Mock lists available:")
    for lst in MOCK_LISTS:
        log.info(f"   [{lst['id']}] {lst['title']}")
    log.info(f"")
    log.info(f"Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log.info("\n🔴 Mock server stopped.")
        log.info("\nItems added during this session:")
        for lst in MOCK_LISTS:
            items = ADDED_ITEMS[lst["title"]]
            if items:
                log.info(f"  {lst['title']}: {items}")


if __name__ == "__main__":
    main()
