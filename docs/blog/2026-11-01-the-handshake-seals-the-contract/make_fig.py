# Figures for "The Handshake Seals the Contract". Run from this directory: python3 make_fig.py
import csv
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 3 of the post: the clock trace (and the hero). Source: clock-trace.csv beside this file, recorded from an
# OfflineBackend at 48 kHz with the real PlaybackClock (a scratch spec, not committed). Columns:
# t (wall seconds), backend (the backend's timeline), old (a clock on the wall, what the frontend
# used before), new (PlaybackClock.nowSec()), reported (the last report), phase.
# Phases: play 0 to 2 s, stall 2 to 4 s (the audio thread starves), play 4 to 6 s, burst 6 to 9 s
# (a phone's process() in bursts: about 100 ms of audio every 100 ms).
# ---------------------------------------------------------------------------------------------
rows = list(csv.DictReader(open("clock-trace.csv")))
t = [float(r["t"]) for r in rows]
backend = [float(r["backend"]) for r in rows]
old = [float(r["old"]) for r in rows]
new = [float(r["new"]) for r in rows]
phase = [r["phase"] for r in rows]

fig, (ax, ex) = plt.subplots(2, 1, figsize=(10, 6.6), dpi=130, sharex=True,
                             gridspec_kw={"height_ratios": [3, 2], "hspace": 0.10})
fig.patch.set_facecolor("white")
for a in (ax, ex):
    a.set_facecolor("white")
    a.spines[["top", "right"]].set_visible(False)
    # the stall and the bursts, behind everything
    a.axvspan(2.0, 4.0, color=CRIM, alpha=0.07, lw=0)
    a.axvspan(6.0, 9.01, color=GOLD, alpha=0.10, lw=0)
    a.set_xlim(0, 9.01)

ax.plot(t, backend, color=GRAY, lw=5.5, alpha=0.55, label="the backend's timeline", solid_capstyle="butt")
ax.plot(t, old, color=CRIM, lw=1.8, label="old clock: the wall")
ax.plot(t, new, color=VERDE, lw=1.8, label="new clock: PlaybackClock")
ax.set_ylim(0, 9.4)
ax.set_ylabel("position on the backend's timeline (s)")
ax.legend(loc="lower right", frameon=False, fontsize=8.5)
ax.text(1.0, 2.6, "all three agree", ha="center", va="center", color=GRAY, fontsize=8.5)
ax.annotate("", xy=(1.0, 1.3), xytext=(1.0, 2.25), arrowprops=dict(arrowstyle="-", color=GRAY, lw=0.8))
trans = ax.get_xaxis_transform()
ax.text(1.0, 1.035, "play", transform=trans, ha="center", va="bottom", color=GRAY)
ax.text(3.0, 1.035, "the audio thread starves", transform=trans, ha="center", va="bottom", color=CRIM)
ax.text(5.0, 1.035, "play", transform=trans, ha="center", va="bottom", color=GRAY)
ax.text(7.5, 1.035, "bursts of about 100 ms", transform=trans, ha="center", va="bottom", color=GOLD)

ex.axhline(0.0, color=GRAY, lw=5.5, alpha=0.55, solid_capstyle="butt")
ex.plot(t, [o - b for o, b in zip(old, backend)], color=CRIM, lw=1.8)
ex.plot(t, [n - b for n, b in zip(new, backend)], color=VERDE, lw=1.8)
ex.set_ylim(-0.5, 2.4)
ex.set_ylabel("clock minus backend (s)")
ex.set_xlabel("wall time (s)")
ex.text(5.0, 2.12, "2.0 s ahead, and it stays there", ha="center", va="bottom", color=CRIM, fontsize=8.5)
ex.text(2.08, -0.31, "held 0.1 s past the last report, 84 ms past the backend", ha="left", va="center", color=VERDE, fontsize=8.5)
ex.text(6.1, 0.46, "within 0.1 s of the backend", ha="left", va="center", color=VERDE, fontsize=8.5)
fig.savefig("clock-trace.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: a tempo change under the old moving origin. A SIMULATION from the formula of
# the ticket's problem 1 (KLANG-00399): the frontend moved its origin so the current cycle kept its
# wall time under the new tempo, the backend's epoch stayed, so every voice after the change landed
# displaced by (cycle of the change) x (old seconds per cycle - new seconds per cycle).
# 30 to 33 rpm: 2.000 s per cycle to 1.818 s per cycle. The tempo map displaces nothing.
# Left: the mechanism for a change at cycle 20. Right: the displacement by the cycle of the change.
# ---------------------------------------------------------------------------------------------
old_spc, new_spc = 60 / 30, 60 / 33
per_cycle = old_spc - new_spc
lookahead = 2 * new_spc  # two cycles at the new tempo
N = 20  # the cycle of the change in the left panel

fig, (lx, ax) = plt.subplots(1, 2, figsize=(11, 4.6), dpi=130, gridspec_kw={"width_ratios": [1, 1.15], "wspace": 0.28})
fig.patch.set_facecolor("white")
for a in (lx, ax):
    a.set_facecolor("white")
    a.spines[["top", "right"]].set_visible(False)

# left: where the voices of each cycle land, in seconds after the playback's epoch
def planned(c):      # the tempo map: the new piece starts where the old one stood
    return old_spc * c if c <= N else old_spc * N + new_spc * (c - N)

def moved(c):        # the old way: timed from the moved origin, added to the old epoch
    return old_spc * c if c <= N else new_spc * c

cs = [16 + i / 20 for i in range(0, 201)]
lx.plot([c for c in cs if c <= N], [planned(c) for c in cs if c <= N], color=GRAY, lw=2.4)
lx.plot([c for c in cs if c >= N], [planned(c) for c in cs if c >= N], color=VERDE, lw=2.4)
lx.plot([c for c in cs if c >= N], [moved(c) for c in cs if c >= N], color=CRIM, lw=2.4)
lx.plot([N], [planned(N)], marker="o", ms=6, color=GRAY, zorder=3)
lx.plot([N], [moved(N)], marker="o", ms=6, color=CRIM, zorder=3)
lx.annotate("", xy=(N, moved(N)), xytext=(N, planned(N)), arrowprops=dict(arrowstyle="-|>", color=CRIM, lw=1.2))
lx.annotate("knob turned at cycle 20,\n30 to 33 rpm", xy=(N - 0.1, planned(N) + 0.3), xytext=(16.2, 47.5), ha="left", va="center",
            color="#5C6B74", fontsize=8.5, arrowprops=dict(arrowstyle="-", color=GRAY, lw=0.8))
gap_c = 26
lx.annotate("", xy=(gap_c, moved(gap_c)), xytext=(gap_c, planned(gap_c)), arrowprops=dict(arrowstyle="<->", color=CRIM, lw=1.2))
lx.text(gap_c + 0.3, (moved(gap_c) + planned(gap_c)) / 2, f"{N * per_cycle:.1f} s early\n(20 x {per_cycle:.3f} s)", ha="left", va="center",
        color=CRIM, fontsize=8.5)
lx.text(20.6, planned(26) + 0.8, "tempo map: the new piece\nstarts where the old one stood", ha="left", va="bottom", color=VERDE, fontsize=8.5,
        rotation=0)
lx.text(21.6, 35.2, "old: timed from the moved\norigin, added to the old epoch", ha="left", va="top", color=CRIM, fontsize=8.5)
lx.set_xlim(16, 29.8); lx.set_ylim(30, 58)
lx.set_xlabel("cycle")
lx.set_ylabel("where the voice sounds: seconds after the epoch")
lx.set_title("what one change does", loc="left", fontsize=9, color=INK)

# right: the displacement by the cycle at which the knob is turned
cycles = [c / 4 for c in range(0, 4 * 60 + 1)]
displaced = [c * per_cycle for c in cycles]
ax.plot(cycles, displaced, color=CRIM, lw=2.0)
ax.plot([0, 60], [0, 0], color=VERDE, lw=2.0)
ax.axhline(lookahead, color=GRAY, lw=1.0, ls=(0, (4, 3)))
ax.text(60.5, lookahead + 0.2, f"the look-ahead: two cycles, {lookahead:.1f} s", color=GRAY, fontsize=8.5,
        ha="right", va="bottom")
for c in (10, 20):
    ax.plot([c], [c * per_cycle], marker="o", ms=6, color=CRIM, zorder=3)
    ax.text(c + 1.0, c * per_cycle - 0.3, f"cycle {c}: {c * per_cycle:.1f} s", color=CRIM, fontsize=8.5, ha="left", va="top")
ax.plot([60], [60 * per_cycle], marker="o", ms=6, color=CRIM, zorder=3)
ax.text(60.0, 60 * per_cycle + 0.5, f"cycle 60: {60 * per_cycle:.1f} s", color=CRIM, fontsize=8.5, ha="right", va="bottom")
ax.text(40.0, 0.5, "tempo map: nothing moves", color=VERDE, fontsize=8.5, va="bottom", ha="center")
ax.set_xlim(0, 61); ax.set_ylim(-0.6, 12.4)
ax.set_xlabel("the cycle at which the knob is turned, 30 to 33 rpm")
ax.set_ylabel("every later voice, early by (s)")
ax.set_title("how far, by the cycle of the change", loc="left", fontsize=9, color=INK)
fig.savefig("tempo-change.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 1 of the post: the two threads, what crosses between them, and the clocks each side reads,
# before the work and now. A sketch, not data. Facts: KLANG-00399 "How it works today" (three
# clocks, the diagnostics' backendNowMs as the worklet's Date.now()) and "The design".
# ---------------------------------------------------------------------------------------------
from matplotlib.patches import FancyBboxPatch


def chip(a, x0, y0, x1, y1, text, color, fs=8.3):
    a.add_patch(FancyBboxPatch((x0, y0), x1 - x0, y1 - y0, boxstyle="round,pad=0,rounding_size=0.9",
                               fc="white", ec=color, lw=1.6, zorder=3))
    a.text((x0 + x1) / 2, (y0 + y1) / 2, text, ha="center", va="center", color=color, fontsize=fs, zorder=4)


def thread(a, x0, x1, title, sub):
    a.add_patch(FancyBboxPatch((x0, 0.8), x1 - x0, 29.6, boxstyle="round,pad=0,rounding_size=1.4",
                               fc="#F3F5F6", ec=GRAY, lw=1.2, zorder=1))
    a.text((x0 + x1) / 2, 27.4, title, ha="center", va="center", color=INK, fontsize=9.5, fontweight="bold", zorder=4)
    a.text((x0 + x1) / 2, 24.3, sub, ha="center", va="center", color="#5C6B74", fontsize=8.2, zorder=4)


def msg(a, y, x_from, x_to, color, head, tail):
    a.annotate("", xy=(x_to, y), xytext=(x_from, y), arrowprops=dict(arrowstyle="-|>", color=color, lw=1.8, shrinkA=0, shrinkB=0), zorder=2)
    cx = (x_from + x_to) / 2
    a.text(cx, y + 0.9, head, ha="center", va="bottom", color=color, fontsize=8.5, fontweight="bold")
    a.text(cx, y - 0.9, tail, ha="center", va="top", color=color, fontsize=8.0)


fig, (pa, pb) = plt.subplots(2, 1, figsize=(10.5, 7.4), dpi=130, gridspec_kw={"hspace": 0.16})
fig.patch.set_facecolor("white")
for a in (pa, pb):
    a.set_xlim(0, 100); a.set_ylim(0, 31); a.axis("off")

FX0, FX1, BX0, BX1 = 1, 35, 65, 99
# before
pa.set_title("before: three clocks, and a sync that compared the wall with the wall", loc="left", fontsize=9.5, color=INK)
thread(pa, FX0, FX1, "frontend, the main thread", "the scheduler asks the pattern\nfor the next cycles")
thread(pa, BX0, BX1, "backend, the audio thread", "renders 128 frames at a time")
chip(pa, 3, 11.6, 33, 18.0, "KlangTime: Date.now()\n+ performance.now() since", CRIM)
chip(pa, 3, 3.0, 33, 8.4, "UI: Date.now()", CRIM)
chip(pa, 67, 11.6, 97, 18.0, "timeline: frames rendered\n/ sample rate", "#5C6B74")
msg(pa, 25.6, FX1 + 0.6, BX0 - 0.6, GRAY, "voices", "start time in seconds,\nplaybackStartTime: its own guess")
msg(pa, 12.4, BX0 - 0.6, FX1 + 0.6, CRIM, "diagnostics", "backendNowMs = the worklet's\nDate.now(), a wall clock")
pa.text(50, 2.6, "the sync compared wall with wall", ha="center", va="center", color=CRIM, fontsize=8.4, style="italic")

# now
pb.set_title("now: one timeline, the backend's, and an estimate of it", loc="left", fontsize=9.5, color=INK)
thread(pb, FX0, FX1, "frontend, the main thread", "the scheduler asks the pattern\nfor the next cycles")
thread(pb, BX0, BX1, "backend, the audio thread", "renders 128 frames at a time")
chip(pb, 3, 11.6, 33, 18.0, "PlaybackClock: the last report\n+ time since, capped", VERDE)
chip(pb, 3, 3.0, 33, 8.4, "UI: Date.now(), its times\nset from PlaybackClock", VERDE)
chip(pb, 67, 11.6, 97, 18.0, "timeline: frames rendered\n/ sample rate", "#5C6B74")
msg(pb, 25.6, FX1 + 0.6, BX0 - 0.6, GRAY, "voices", "start time in seconds from\nthe epoch; the second asked for")
msg(pb, 16.0, BX0 - 0.6, FX1 + 0.6, VERDE, "reports", "backendNowMs = the timeline at the\nend of a block, every 21 ms")
msg(pb, 7.2, BX0 - 0.6, FX1 + 0.6, VERDE, "PlaybackAnchored", "once per playback: the epoch\nthe backend chose")
fig.savefig("threads-and-clocks.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
