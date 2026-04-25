#!/usr/bin/env python3
"""
Mock PubNub Publish API server for local testing of Keep Quick Add.

Mimics the PubNub publish endpoint:
    POST https://ps.pndsn.com/publish/{pub_key}/{sub_key}/0/{channel}/0
    Body: {"message": "..."}

Returns the same JSON-array response format as PubNub:
    [1, "Sent", "<timetoken>"]

Run with: python3 mock_server.py
"""

import json
import logging
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

PORT = 8000

# In-memory store of published messages: list of {channel, message, timetoken}
PUBLISHED: list[dict] = []


class MockHandler(BaseHTTPRequestHandler):

    def log_message(self, format, *args):
        log.info(f"{self.address_string()} - {format % args}")

    def send_json(self, code: int, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_json_body(self):
        length = int(self.headers.get("Content-Length", 0))
        if length == 0:
            return {}
        try:
            return json.loads(self.rfile.read(length))
        except json.JSONDecodeError:
            return {}

    def _parse_publish_path(self, path: str):
        """
        PubNub publish URL format:
            /publish/{pub_key}/{sub_key}/{callback}/{channel}/{seq}[/...]
        Returns (pub_key, sub_key, channel) or None.
        """
        parts = path.strip("/").split("/")
        if len(parts) < 6 or parts[0] != "publish":
            return None
        pub_key, sub_key, _callback, channel, _seq = parts[1:6]
        return pub_key, sub_key, channel

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/health":
            self.send_json(200, {"status": "ok", "mode": "mock-pubnub"})
            return

        # GET-style publish: /publish/PUB/SUB/0/CHANNEL/0/{json}
        if path.startswith("/publish/"):
            parts = path.strip("/").split("/")
            if len(parts) >= 7:
                parsed_path = self._parse_publish_path("/" + "/".join(parts[:6]))
                if parsed_path:
                    pub_key, sub_key, channel = parsed_path
                    try:
                        message = json.loads(parts[6])
                    except json.JSONDecodeError:
                        message = parts[6]
                    return self._handle_publish(pub_key, sub_key, channel, message)
            self.send_json(400, [0, "Invalid publish URL", "0"])
            return

        self.send_json(404, [0, f"Not found: {path}", "0"])

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path.startswith("/publish/"):
            parsed_path = self._parse_publish_path(path)
            if parsed_path is None:
                self.send_json(400, [0, "Invalid publish URL", "0"])
                return
            pub_key, sub_key, channel = parsed_path
            message = self.read_json_body()
            return self._handle_publish(pub_key, sub_key, channel, message)

        self.send_json(404, [0, f"Not found: {path}", "0"])

    def _handle_publish(self, pub_key: str, sub_key: str, channel: str, message):
        # Validate publish key looks vaguely like PubNub
        if not pub_key or not sub_key:
            self.send_json(400, [0, "Missing pub/sub key", "0"])
            return

        if not pub_key.startswith("pub-") and pub_key != "demo":
            log.warning(f"⚠ Suspicious publish key: {pub_key}")

        timetoken = str(int(time.time() * 10_000_000))
        PUBLISHED.append({
            "pub_key": pub_key,
            "sub_key": sub_key,
            "channel": channel,
            "message": message,
            "timetoken": timetoken,
        })

        log.info(f"✓ Published to '{channel}': {json.dumps(message)}")
        # Match PubNub's response: [1, "Sent", "<timetoken>"]
        self.send_json(200, [1, "Sent", timetoken])


def main():
    server = HTTPServer(("0.0.0.0", PORT), MockHandler)
    log.info(f"🟢 Mock PubNub publish server running at http://localhost:{PORT}")
    log.info(f"   GET  /health                                       → health check")
    log.info(f"   POST /publish/{{pub}}/{{sub}}/0/{{channel}}/0       → publish message")
    log.info(f"")
    log.info(f"Configure your Android app with any 'pub-c-...' / 'sub-c-...' keys.")
    log.info(f"Set the channel to whatever you want (default: keep-quick-add).")
    log.info(f"")
    log.info(f"Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log.info("\n🔴 Mock server stopped.")
        log.info(f"\nMessages published during this session ({len(PUBLISHED)} total):")
        for msg in PUBLISHED:
            log.info(f"  [{msg['channel']}] {json.dumps(msg['message'])}")


if __name__ == "__main__":
    main()
