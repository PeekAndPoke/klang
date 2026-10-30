#!/usr/bin/env python3
"""Screenshots of a locally served app through headless Chrome and its DevTools protocol.

  app_shot.py <plan.json>

plan:
  {"serve": "<built app folder>",   optional: served on 127.0.0.1:<serve_port>, any path that is not
                                    a file gets index.html (path-routed single page apps)
   "serve_port": 18731,
   "port": 9333,                    Chrome's DevTools port
   "profile": "<scratch dir>",      Chrome's profile folder, a fresh one per plan
   "width": 1600, "height": 1000,
   "scale": 1,                      deviceScaleFactor; 2 shows what a fine display shows
   "webgl": false,                  true: a software WebGL context (swiftshader) under xvfb-run
   "shots": [{"url": "http://127.0.0.1:18731/", "out": "<file>.png", "wait": 6,
              "steps": [{"js": "..."}, {"click": "css selector"}, {"wait": 2}]}]}

Traps it guards against (2026-10-28 to 30, the dev history screenshots and Klang-Research's visuals):
- Headless Chrome has no window focus, so a UI that rests on blur stops drawing: focus is emulated.
- Headless Chrome has no WebGL, not even with the swiftshader flags alone: "webgl" adds them and runs Chrome under
  xvfb-run (tried 2026-10-30), in a process group of its own that is stopped as a whole.
- Only 127.0.0.1 and localhost are visited; any other host is refused.
"""
import base64
import functools
import http.server
import json
import os
import signal
import socket
import struct
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request

LOCAL_HOSTS = {"127.0.0.1", "localhost"}


class DevToolsSocket:
    """The least of a WebSocket client that Chrome's DevTools protocol needs: text frames, masked when sent."""

    def __init__(self, url):
        parts = urllib.parse.urlparse(url)
        self.sock = socket.create_connection((parts.hostname, parts.port), timeout=60)
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((
            f"GET {parts.path} HTTP/1.1\r\nHost: {parts.netloc}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
        head = b""
        while b"\r\n\r\n" not in head:
            head += self.sock.recv(1)
        if b" 101 " not in head.split(b"\r\n")[0]:
            raise RuntimeError(f"app-shot: no WebSocket upgrade: {head[:80]!r}")

    def _read(self, n):
        data = b""
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise RuntimeError("app-shot: DevTools socket closed")
            data += chunk
        return data

    def send(self, text):
        payload = text.encode()
        mask = os.urandom(4)
        n = len(payload)
        if n < 126:
            header = struct.pack("!BB", 0x81, 0x80 | n)
        elif n < 65536:
            header = struct.pack("!BBH", 0x81, 0x80 | 126, n)
        else:
            header = struct.pack("!BBQ", 0x81, 0x80 | 127, n)
        self.sock.sendall(header + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))

    def recv(self):
        message = b""
        while True:
            first, second = self._read(2)
            n = second & 0x7F
            if n == 126:
                n = struct.unpack("!H", self._read(2))[0]
            elif n == 127:
                n = struct.unpack("!Q", self._read(8))[0]
            payload = self._read(n)
            if first & 0x0F in (0x1, 0x0):
                message += payload
                if first & 0x80:
                    return message.decode()


class SinglePageHandler(http.server.SimpleHTTPRequestHandler):
    def send_head(self):
        path = self.translate_path(self.path.split("?")[0])
        if not os.path.exists(path):
            self.path = "/index.html"
        return super().send_head()

    def log_message(self, *args):
        pass


def serve(folder, port):
    handler = functools.partial(SinglePageHandler, directory=folder)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def chrome_command(plan):
    width, height = plan.get("width", 1600), plan.get("height", 1000)
    flags = [
        "--headless=new", "--no-first-run", "--hide-scrollbars",
        f"--remote-debugging-port={plan.get('port', 9333)}", f"--user-data-dir={plan['profile']}",
        f"--window-size={width},{height}", "--autoplay-policy=no-user-gesture-required",
    ]
    if plan.get("webgl"):
        return ["xvfb-run", "-a", "google-chrome", *flags,
                "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "about:blank"]

    return ["google-chrome", *flags, "--disable-gpu", "about:blank"]


def check_local(url):
    host = urllib.parse.urlparse(url).hostname
    if host not in LOCAL_HOSTS:
        sys.exit(f"app-shot: refusing {url}: only 127.0.0.1 and localhost")


def main():
    plan = json.load(open(sys.argv[1]))
    for shot in plan["shots"]:
        check_local(shot["url"])

    server = serve(plan["serve"], plan.get("serve_port", 18731)) if plan.get("serve") else None
    port = plan.get("port", 9333)
    # its own process group: xvfb-run leaves Chrome and Xvfb running when only the wrapper is stopped
    chrome = subprocess.Popen(chrome_command(plan), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                              start_new_session=True)
    try:
        page = None
        for _ in range(100):
            try:
                targets = json.load(urllib.request.urlopen(f"http://127.0.0.1:{port}/json"))
                page = next(t for t in targets if t["type"] == "page")
                break
            except Exception:
                time.sleep(0.2)

        if page is None:
            sys.exit("app-shot: Chrome did not answer on its DevTools port")

        ws = DevToolsSocket(page["webSocketDebuggerUrl"])
        ids = iter(range(1, 10**6))

        def call(method, **params):
            i = next(ids)
            ws.send(json.dumps({"id": i, "method": method, "params": params}))
            while True:
                msg = json.loads(ws.recv())
                if msg.get("id") == i:
                    return msg.get("result", msg.get("error"))

        call("Emulation.setFocusEmulationEnabled", enabled=True)
        call("Emulation.setDeviceMetricsOverride", width=plan.get("width", 1600), height=plan.get("height", 1000),
             deviceScaleFactor=plan.get("scale", 1), mobile=False)

        for shot in plan["shots"]:
            call("Page.navigate", url=shot["url"])
            time.sleep(shot.get("wait", 6))
            for step in shot.get("steps", []):
                if "js" in step:
                    r = call("Runtime.evaluate", expression=step["js"], awaitPromise=True, returnByValue=True)
                    print("  js:", json.dumps(r)[:200])
                if "click" in step:
                    sel = json.dumps(step["click"])
                    r = call("Runtime.evaluate", expression=(
                        f"(() => {{ const e = document.querySelector({sel}); if (!e) return 'missing';"
                        f" e.click(); return 'clicked'; }})()"))
                    print("  click", step["click"], r.get("result", {}).get("value"))
                if "wait" in step:
                    time.sleep(step["wait"])
            data = call("Page.captureScreenshot", format="png")["data"]
            with open(shot["out"], "wb") as f:
                f.write(base64.b64decode(data))
            print("shot", shot["out"])
    finally:
        os.killpg(chrome.pid, signal.SIGTERM)
        chrome.wait(timeout=10)
        if server:
            server.shutdown()


if __name__ == "__main__":
    main()
