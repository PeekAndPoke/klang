# Figures for "Zombies". Data pasted as literals; the source of each block is named in the comment
# above it. Run from this directory: python3 make_fig.py
import math
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 1 of the post: the life of one voice under culling, a DIAGRAM, not a render. The envelope
# is a stylized ADSR (gate 120 ms, release an exponential decay); the constants are the engine's
# (VOICE_CULL_FLOOR -100 dBFS, VOICE_CULL_SECONDS 50 ms, audio/MEMORY.md "Silence culling").
# ---------------------------------------------------------------------------------------------
gate_ms, sched_end_ms = 120.0, 700.0
floor_db = -100.0
def env_db(t):
    if t < 8.0:
        return -60.0 + 60.0 * t / 8.0
    if t < gate_ms:
        return -3.0 * (t - 8.0) / (gate_ms - 8.0)
    # release: -3 dB at the gate end, then 0.55 dB per ms
    return -3.0 - 0.55 * (t - gate_ms)
ts = [i * 0.5 for i in range(int(sched_end_ms * 2) + 1)]
db = [env_db(t) for t in ts]
cross_ms = gate_ms + (abs(floor_db) - 3.0) / 0.55
cull_ms = cross_ms + 50.0
fig, ax = plt.subplots(figsize=(11, 4.4), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
ax.plot([t for t in ts if t <= cull_ms], [d for t, d in zip(ts, db) if t <= cull_ms], color=INK, lw=1.6)
ax.plot([t for t in ts if t >= cull_ms and env_db(t) >= -140], [d for t, d in zip(ts, db) if t >= cull_ms and d >= -140], color=GRAY, lw=1.0, ls=(0, (2, 2)))
ax.axhline(floor_db, color=CRIM, lw=0.9, ls=(0, (4, 3)))
ax.text(4, floor_db + 3, "the floor, -100 dBFS", color=CRIM, fontsize=8.5)
ax.axvspan(0, gate_ms, color=VERDE, alpha=0.08)
ax.axvspan(cross_ms, cull_ms, color=GOLD, alpha=0.25)
ax.axvspan(cull_ms, sched_end_ms, color=GRAY, alpha=0.15, hatch="//")
for x, label, y, ha in ((gate_ms, "gate ends: culling allowed", -118, "left"), (cross_ms, "output under the floor", -60, "left"), (cull_ms, "culled: the chain stops rendering", -84, "left"), (sched_end_ms, "scheduled end: the slot is freed", -40, "right")):
    ax.axvline(x, color=INK, lw=0.6)
    ax.text(x + (4 if ha == "left" else -4), y, label, fontsize=8.5, color=INK, ha=ha)
ax.text((cross_ms + cull_ms) / 2, -92, "50 ms", ha="center", fontsize=8.5, color=INK)
ax.text((cull_ms + sched_end_ms) / 2, -150, "ZOMBIE: renders nothing, keeps its slot and its orbit lease", ha="center", fontsize=8.5, color=INK)
ax.text(gate_ms / 2, -150, "gate: never culled", ha="center", fontsize=8.5, color=INK)
ax.set_xlim(0, sched_end_ms); ax.set_ylim(-165, 8)
ax.set_xlabel("time from note-on (ms)"); ax.set_ylabel("the voice's own output peak (dBFS)")
ax.spines[["top", "right"]].set_visible(False)
ax.set_title("DIAGRAM: ONE VOICE'S LIFE UNDER CULLING (a stylized envelope, the engine's constants)", loc="left", fontsize=9, color=INK)
fig.savefig("voice-timeline.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: the culled share per piece, onsets and culled voices from the rig suite of
# 2026-09-15 (docs/benchmarks/2026-09-15_172412_song_jvm.md, the "part" table), the day of the
# change and the same song text.
# ---------------------------------------------------------------------------------------------
pieces = ["drums (samples)", "Orchestertrommel", "marimba", "bass", "rhythm guitars", "melody guitar"]
onsets = [303, 34, 128, 32, 272, 136]
culled = [257, 27, 0, 0, 0, 0]
fig, ax = plt.subplots(figsize=(9, 3.6), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
ys = list(range(len(pieces)))[::-1]
share = [100.0 * c / o for c, o in zip(culled, onsets)]
ax.barh(ys, share, color=[VERDE if v > 0 else GRAY for v in share], height=0.6)
for y, v, c, o in zip(ys, share, culled, onsets):
    ax.text(v + 1.0, y, f"{c} of {o} voices, {v:.0f}%", va="center", fontsize=8.5, color=INK)
ax.set_yticks(ys); ax.set_yticklabels(pieces)
ax.set_xlim(0, 118); ax.set_xlabel("voices that turned into zombies before their scheduled end (%)")
ax.spines[["top", "right"]].set_visible(False)
ax.set_title("WHERE CULLING FINDS SILENCE, AND WHERE IT CANNOT", loc="left", fontsize=9, color=INK)
fig.savefig("culled-share.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 3 of the post: the same engine with culling off and on, back to back on 2026-09-16
# (docs/benchmarks/2026-09-16_132213_song_jvm.md, culling off by a one-line edit of the default
# window; 2026-09-16_132457_song_jvm.md, culling on), "part" and "song" tables; and the commit's
# own pair on 2026-09-15 (2026-09-15_132518_song_jvm.md off, 2026-09-15_132340_song_jvm.md on),
# the live song of that day and the frozen song of July 3, 48 cycles.
# ---------------------------------------------------------------------------------------------
rows = [
    ("drums, 8 cycles\n25 -> 6 voices per block", 0.04107, 0.01943),
    ("whole song, 8 cycles\n41 -> 22 voices per block", 0.22652, 0.18787),
    ("live song, Sep 15, 48 cycles\n1180 of 4024 culled", 0.12770, 0.10280),
    ("frozen song, Jul 3, 48 cycles\n481 of 11195 culled", 0.08987, 0.08895),
]
fig, ax = plt.subplots(figsize=(11.5, 4), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
w = 0.36
for i, (name, off, on) in enumerate(rows):
    ax.bar(i - w / 2, off, width=w, color=CRIM, label="culling off" if i == 0 else None)
    ax.bar(i + w / 2, on, width=w, color=VERDE, label="culling on" if i == 0 else None)
    ax.text(i + w / 2, on + 0.004, f"{100 * (on - off) / off:+.0f}%", ha="center", fontsize=9, color=INK, fontweight="bold")
ax.set_xticks(range(len(rows))); ax.set_xticklabels([r[0] for r in rows], fontsize=8.5)
ax.set_ylabel("median RTF (JVM)"); ax.set_ylim(0, 0.26)
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False, loc="upper left")
ax.set_title("THE SAME ENGINE, CULLING OFF AND ON, BACK TO BACK", loc="left", fontsize=9, color=INK)
fig.savefig("cull-off-on.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("cross", cross_ms, "cull", cull_ms)
print("figures written")
