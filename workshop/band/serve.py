#!/usr/bin/env python3
"""Serves this folder on http://localhost:18750/web/ and tells the browser never to keep a file, so a reload always
shows the latest scripts and models. three.js is not kept here: where ignored/three is missing, it is served from
the app's own copy (build/js/node_modules/three, there after any Kotlin/JS build of the app)."""
import functools
import http.server
import os

here = os.path.dirname(os.path.abspath(__file__))
repo = os.path.dirname(os.path.dirname(here))
THREE = "/ignored/three/"


class Handler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def translate_path(self, path):
        found = super().translate_path(path)
        clean = path.split("?", 1)[0]
        if clean.startswith(THREE) and not os.path.exists(found):
            for build in ("build", os.path.join(".build-claude", "_root")):
                other = os.path.normpath(os.path.join(repo, build, "js", "node_modules", "three", clean[len(THREE):]))
                if other.startswith(repo) and os.path.exists(other):
                    return other
        return found


print("http://localhost:18750/web/")
http.server.ThreadingHTTPServer(("127.0.0.1", 18750), functools.partial(Handler, directory=here)).serve_forever()
