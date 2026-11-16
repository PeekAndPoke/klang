#!/usr/bin/env python3
# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

"""Measures the recording's loudness over time (overall and in three bands, a value every 20 ms) and writes it beside
the audio: schmetterling.wav -> schmetterling-energy.json. Run after every new recording."""
import json
import os
import wave

import numpy as np

here = os.path.dirname(os.path.abspath(__file__))
w = wave.open(os.path.join(here, 'ignored', 'schmetterling.wav'), 'rb')
sr, ch, sw, n = w.getframerate(), w.getnchannels(), w.getsampwidth(), w.getnframes()
dt = {2: np.int16, 4: np.int32}[sw]
x = np.frombuffer(w.readframes(n), dtype=dt).astype(np.float64) / np.iinfo(dt).max
x = x.reshape(-1, ch).mean(axis=1)
hop, win = 960, 2048
frames = (len(x) - win) // hop
window = np.hanning(win)
freqs = np.fft.rfftfreq(win, 1 / sr)
bands = {'low': (20, 150), 'mid': (150, 2500), 'high': (2500, 16000)}
out = {k: np.zeros(frames) for k in bands}
total = np.zeros(frames)
for i in range(frames):
    seg = x[i * hop:i * hop + win]
    total[i] = np.sqrt(np.mean(seg * seg))
    sp = np.abs(np.fft.rfft(seg * window)) ** 2
    for k, (a, b) in bands.items():
        out[k][i] = np.sqrt(sp[(freqs >= a) & (freqs < b)].sum())


def shape(v):
    """0 to about 1; follows a rise at once and falls slowly (0.25 s), as a meter does."""
    v = np.clip(v / np.percentile(v, 98), 0, 1.3)
    rel = np.exp(-(hop / sr) / 0.25)
    s = np.zeros_like(v)
    cur = 0.0
    for i, a in enumerate(v):
        cur = max(a, cur * rel)
        s[i] = cur
    return s


level = shape(total)
k = int(1.5 / (hop / sr))
slow = np.convolve(level, np.ones(k) / k, mode='same')
r = lambda v: [round(float(a), 3) for a in v]
data = {'step': hop / sr, 'offset': win / 2 / sr, 'level': r(level), 'slow': r(slow), **{k: r(shape(v)) for k, v in out.items()}}
json.dump(data, open(os.path.join(here, 'ignored', 'schmetterling-energy.json'), 'w'))
print('energy:', frames, 'values,', round(frames * hop / sr, 1), 's')
