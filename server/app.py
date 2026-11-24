#!/usr/bin/env python3
"""mcc-app server: recipe uploads from the apps, and video transcripts for the AI recipe import.

  GET  /  and  /health   no auth, for Docker and uptime monitors
  POST /v1/recipes       add a recipe (pt + en) to the recipe repository   (docs/RECIPE-UPLOAD.md)
  POST /v1/transcript    what is said in a cooking video                   (server/README.md)

Both POSTs need "Authorization: Bearer <MCC_TOKEN>". Settings: README.md and .env.example.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from common import Problem, log
from settings import Settings
from transcript import Transcriber, video_key
from upload import Uploader

VERSION = "2.0.0"


class Handler(BaseHTTPRequestHandler):
    server_version = f"mcc-server/{VERSION}"
    settings: Settings
    uploader: Uploader
    transcriber: Transcriber

    def log_message(self, *args):  # requests are logged by reply(), without headers or bodies
        pass

    def route(self) -> str:
        """The path without query or trailing slash; '//v1/recipes' (an address typed with a trailing slash) too."""
        return re.sub(r"/+", "/", self.path.split("?")[0]).rstrip("/")

    def reply(self, status: int, obj: dict, note: str = "-"):
        data = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        log(f"{self.command} {self.route() or '/'} {status} {note}")

    def drain(self, length: int):
        """Reads and drops an unwanted body, so closing the connection doesn't reset it before the client has
        read the error reply."""
        left = min(length, 16 * self.settings.max_body)
        while left > 0:
            chunk = self.rfile.read(min(left, 65536))
            if not chunk:
                break
            left -= len(chunk)

    def do_GET(self):
        if self.route() in ("", "/health"):
            return self.reply(200, {"ok": True, "version": VERSION,
                                    "transcripts": {"whisper": bool(self.settings.whisper_host)}})
        self.reply(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        route = self.route()
        if route not in ("/v1/recipes", "/v1/transcript"):
            self.drain(length)
            return self.reply(404, {"error": "not found"})
        m = re.fullmatch(r"Bearer\s+(\S+)", self.headers.get("Authorization") or "", re.I)
        if not m or not hmac.compare_digest(hashlib.sha256(m.group(1).encode()).hexdigest(),
                                            self.settings.token_sha256):
            self.drain(length)
            return self.reply(401, {"error": "missing or wrong token"})
        if length > self.settings.max_body:
            self.drain(length)
            return self.reply(413, {"error": f"the request is larger than {self.settings.max_body // 1024} KB"})
        try:
            body = json.loads(self.rfile.read(length) or b"null")
        except ValueError:
            return self.reply(422, {"error": "the body is not valid JSON"})
        if route == "/v1/recipes":
            note = body.get("key", "-") if isinstance(body, dict) else "-"
            work = lambda: (201, self.uploader.add(body))  # noqa: E731
        else:
            url = body.get("url") if isinstance(body, dict) else None
            note = video_key(url) if isinstance(url, str) else "-"
            work = lambda: (200, self.transcriber.get(url))  # noqa: E731
        try:
            status, result = work()
            return self.reply(status, result, note)
        except Problem as e:
            return self.reply(e.status, {"error": str(e)}, note)
        except Exception as e:  # noqa: BLE001
            log(f"{route} failed for {note}: {type(e).__name__}: {str(e)[:300]}")
            return self.reply(502, {"error": "the server could not finish this; try again later"}, note)


def main():
    os.umask(0o077)
    s = Settings.from_env()
    Handler.settings = s
    Handler.uploader = Uploader(s)
    Handler.transcriber = Transcriber(s)
    try:
        Handler.uploader.sync()
    except Exception as e:  # noqa: BLE001 - the remote may be briefly unreachable; every upload syncs again
        log(f"initial sync of the recipe repository failed: {e}")
    log(f"mcc-server {VERSION} on :{s.port}; repository {s.repo_dir} ({s.git_branch}); "
        f"deploy command {'set' if s.deploy_cmd else 'none'}; "
        f"speech-to-text {'on' if s.whisper_host else 'off'}")
    ThreadingHTTPServer(("", s.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
