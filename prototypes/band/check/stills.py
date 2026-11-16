#!/usr/bin/env python3
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Takes a fixed set of stills of the show into ignored/stills/<name>/, or compares two such sets pixel by pixel.
The same moments, seeds and cameras every time, so a change that must not change the picture can prove it.

  stills.py take <name>          needs the repo's app-shot tool (headless Chrome with WebGL)
  stills.py compare <a> <b>      needs ImageMagick's `compare`; exit 1 when a pixel differs
"""
import json, os, subprocess, sys, time
band = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
here = os.path.join(band, 'ignored')
if sys.argv[1] == 'compare':
    a, b = (os.path.join(here, 'stills', n) for n in sys.argv[2:4])
    bad = 0
    for f in sorted(os.listdir(a)):
        n = subprocess.run(['compare', '-metric', 'AE', os.path.join(a, f), os.path.join(b, f), 'null:'], capture_output=True, text=True).stderr.split()[0]
        bad += n != '0'
        print(f, n, 'pixels differ' if n != '0' else 'same')
    sys.exit(1 if bad else 0)
name = sys.argv[2]
out = os.path.join(here, 'stills', name)
os.makedirs(out, exist_ok=True)
b = "http://127.0.0.1:18741/web/index.html?nohud&"
views = {
    "walkin": "still&t=-8.0", "walkin2": "still&t=-3.0", "start": "still&t=10.0", "play": "still&t=62.1",
    "front": "still&t=62.1&front=1", "seed3": "still&t=62.1&seed=3", "marimba": "still&t=125.0&seed=2",
    "trumpet": "still&t=280.0", "trumpet2": "still&t=283.4&seed=5", "stroll": "still&t=80.0&seed=1",
    "cam1": "t=70.0&seed=2", "cam2": "t=110.0&seed=3", "cam3": "t=236.0&seed=4",
    "strings": "still&t=62.1&cam=-2.0,1.3,3.2&at=-2.15,1.1,0.4", "drums": "still&t=63.0&cam=0.3,1.9,0.6&at=0.1,1.45,-1.8",
    "head": "still&t=10.0&head=eimer&from=side", "late": "still&t=500.0&seed=7", "test": "test&still&t=5.0",
}
plan = {"serve": band, "serve_port": 18741, "port": 9341,
        "profile": "/tmp/claude-1002/-opt-dev-peekandpoke-klang/ec868304-a02c-43b5-9428-84614e7bed46/scratchpad/chrome-band-%d" % time.time(),
        "width": 900, "height": 520, "scale": 1, "webgl": True,
        "shots": [{"url": b + q, "out": os.path.join(out, k + ".png"), "wait": 7} for k, q in views.items()]}
p = os.path.join(here, 'baseline-plan.json')
json.dump(plan, open(p, 'w'))
repo = os.path.dirname(os.path.dirname(band))
sys.exit(subprocess.call(["python3", os.path.join(repo, "console/tools/app-shot/app_shot.py"), p]))
