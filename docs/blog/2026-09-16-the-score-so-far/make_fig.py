# Figures for "The Score So Far". Data pasted as literals; the source of each block is named in
# the comment above it. Run from this directory: python3 make_fig.py
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from datetime import date

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 1 of the post: the song axis. Every snapshot of Der Schmetterling's text rendered by ONE
# engine (HEAD of 2026-09-16), the `snapshots` suite: docs/benchmarks/2026-09-16_144137_song_jvm.md.
# Columns: work (passes over the block per block, median), medRTF, ns/smp/pass.
# ---------------------------------------------------------------------------------------------
snaps = [("Jul 3\nfrozen", 112, 0.14422, 26.8), ("Sep 8\nv0.3.8.2", 170, 0.10984, 13.5), ("Sep 8\nv0.3.8.3", 170, 0.11267, 13.8),
         ("Sep 8\nv0.3.9", 143, 0.08802, 12.8), ("Sep 9\nv0.3.10", 157, 0.11068, 14.7), ("Sep 10\nv0.3.11", 157, 0.11088, 14.7),
         ("Sep 10\nv0.3.12", 157, 0.11334, 15.0), ("Sep 15\nv0.3.13", 409, 0.17756, 9.0), ("Sep 16\nv0.3.14", 402, 0.17561, 9.1),
         ("Sep 16\nHEAD", 402, 0.17752, 9.2)]
fig, (ax, ax2) = plt.subplots(2, 1, figsize=(11, 6), dpi=130, sharex=True, gridspec_kw={"height_ratios": [1.5, 1]})
fig.patch.set_facecolor("white"); ax.set_facecolor("white"); ax2.set_facecolor("white")
x = np.arange(len(snaps))
work = [s[1] for s in snaps]
ax.bar(x, work, width=0.6, color=[GRAY if i == 0 else INK for i in range(len(snaps))])
for xi, w in zip(x, work):
    ax.text(xi, w + 8, f"{w}", ha="center", fontsize=8, color=INK)
ax.set_ylabel("passes over the block per block")
ax.set_ylim(0, 520)
axr = ax.twinx()
axr.plot(x, [s[2] for s in snaps], color=CRIM, marker="o", ms=5, lw=1.2)
for xi, s in zip(x, snaps):
    axr.text(xi, s[2] + 0.009, f"{s[2]:.3f}", ha="center", fontsize=7, color=CRIM)
axr.set_ylim(0, 0.26); axr.set_ylabel("median RTF on today's engine", color=CRIM)
ax2.plot(x, [s[3] for s in snaps], color=VERDE, marker="s", ms=5, lw=1.2)
for xi, s in zip(x, snaps):
    ax2.text(xi, s[3] + 1.4, f"{s[3]:.1f}", ha="center", fontsize=7, color=VERDE)
ax2.set_ylim(0, 34); ax2.set_ylabel("ns per sample per pass", color=VERDE)
ax2.set_xticks(x); ax2.set_xticklabels([s[0] for s in snaps], fontsize=7.5)
for a in (ax, axr, ax2):
    a.spines[["top"]].set_visible(False)
ax2.spines[["right"]].set_visible(False)
ax.set_title("THE SONG AXIS: EVERY SNAPSHOT OF DER SCHMETTERLING ON ONE ENGINE, JVM, 2026-09-16", loc="left", fontsize=9, color=INK)
ax2.set_title("the same run: the engine's cost per unit of that work", loc="left", fontsize=8, color=GRAY, style="italic")
fig.tight_layout()
fig.savefig("song-axis.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: the engine axis on fixed work. The FROZEN Der Schmetterling of July 3 (the
# same text every time, door renames aside) in every song benchmark file that rendered it, by
# date, JVM, one machine. Files: docs/benchmarks/2026-07-03_{091035,105515,105742,121753,142021,
# 143911,153844}_song_jvm.md (512-frame blocks), 2026-08-06_171456 (512), 2026-08-10_174658,
# 2026-08-11_{114942,115323,115642}, 2026-08-19_121412, 2026-09-15_{095213,132340,132518,141658,
# 141813,150617,151101,163346,163520,175711,180013,203656,203953}, 2026-09-16_144535 (128 frames).
# The two July 3 runs before the body moved to the orbit are marked; they are the engine of that
# morning, and the frozen text is the same.
# ---------------------------------------------------------------------------------------------
runs = [(date(2026, 7, 3), [0.09819, 0.10228], 512, "body per voice"), (date(2026, 7, 3), [0.08265, 0.08349, 0.08095, 0.09860, 0.07940], 512, None),
        (date(2026, 8, 6), [0.08383], 512, None), (date(2026, 8, 10), [0.08761], 128, None),
        (date(2026, 8, 11), [0.08593, 0.08765, 0.09083], 128, None), (date(2026, 8, 19), [0.08704], 128, None),
        (date(2026, 9, 15), [0.10107, 0.08895, 0.08987, 0.08479, 0.08836, 0.10253, 0.09882, 0.09703, 0.09490, 0.09873, 0.08186, 0.07568, 0.07562], 128, None),
        (date(2026, 9, 16), [0.10959], 128, None)]
fig, ax = plt.subplots(figsize=(11, 4.2), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
d0 = date(2026, 6, 28)
for d, vals, block, note in runs:
    xs = [(d - d0).days + (0.6 * (i - (len(vals) - 1) / 2) / max(1, len(vals) - 1)) for i in range(len(vals))]
    col = CRIM if note else (GOLD if block == 512 else INK)
    ax.scatter(xs, vals, s=22, color=col, zorder=3)
    if not note:
        med = float(np.median(vals))
        ax.plot([xs[0] - 1.2, xs[-1] + 1.2], [med, med], color=col, lw=1.4, zorder=2)
        up = d not in (date(2026, 8, 10), date(2026, 9, 15))
        ax.text((d - d0).days + (0 if up else 2.2), med + (0.0045 if up else -0.0045), f"{med:.4f}", ha="center" if up else "left", fontsize=7.5, color=col)
    else:
        ax.text((d - d0).days + 1.5, max(vals), note, ha="left", va="center", fontsize=7.5, color=CRIM)
ticks = [date(2026, 7, 3), date(2026, 8, 6), date(2026, 8, 11), date(2026, 8, 19), date(2026, 9, 15)]
ax.set_xticks([(t - d0).days for t in ticks]); ax.set_xticklabels([t.strftime("%b %d") for t in ticks], fontsize=8)
ax.set_xlim(0, (date(2026, 9, 20) - d0).days); ax.set_ylim(0.06, 0.12); ax.set_ylabel("median RTF of the frozen July 3 song")
ax.spines[["top", "right"]].set_visible(False)
ax.text((date(2026, 7, 3) - d0).days, 0.0625, "gold: 512-frame blocks", color=GOLD, fontsize=7.5)
ax.text((date(2026, 8, 19) - d0).days, 0.0625, "ink: 128-frame blocks (the tone parameter, pinned since August 7)", color=INK, fontsize=7.5)
ax.set_title("THE ENGINE AXIS ON FIXED WORK: THE FROZEN SONG IN EVERY RUN THAT RENDERED IT, JVM, ONE MACHINE", loc="left", fontsize=9, color=INK)
fig.savefig("engine-axis.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 3 of the post: the phone timeline with the song's work underneath. Events: the timeline of
# docs/blog/2026-08-19-the-phone-that-does-not-get-faster/make_fig.py (its sources: docs/plans/
# unified-eq.md:225,808; docs/plans/resource-warehouse.md:214,321,417; docs/tasks-archive/2026-09/
# 20260916-ignitor-optimizer-arithmetic-folds.md:40; audio/MEMORY.md). The work per block per
# snapshot: docs/benchmarks/2026-09-16_144137_song_jvm.md (Fig 1); the song texts between July 3
# and September 8 do not parse on today's doors (console/song-snapshots.sh), so that span is
# dashed and unmeasured.
# ---------------------------------------------------------------------------------------------
events = [(date(2026, 8, 19), "stalls, then half the song", CRIM), (date(2026, 8, 20), "about 75% CPU", VERDE),
          (date(2026, 9, 4), "first run kills playback", CRIM), (date(2026, 9, 4), "first run plays", VERDE),
          (date(2026, 9, 15), "barely runs", CRIM), (date(2026, 9, 15), "smooth", VERDE)]
work_pts = [(date(2026, 7, 3), 112), (date(2026, 9, 8), 170), (date(2026, 9, 8), 143), (date(2026, 9, 9), 157),
            (date(2026, 9, 10), 157), (date(2026, 9, 15), 409), (date(2026, 9, 16), 402)]
fig, (top, bot) = plt.subplots(2, 1, figsize=(12, 5.6), dpi=130, sharex=True, gridspec_kw={"height_ratios": [1.1, 1.6]})
fig.patch.set_facecolor("white")
d0 = date(2026, 6, 28); dx = lambda d: (d - d0).days
top.set_facecolor("white"); top.axis("off")
top.plot([dx(date(2026, 7, 1)), dx(date(2026, 9, 22))], [0.5, 0.5], color=INK, lw=1.2)
layout = [0.3, -0.3, 0.62, -0.62, 0.3, -0.3]
for (d, state, col), off in zip(events, layout):
    top.plot([dx(d)], [0.5], marker="o", ms=8, color=col, zorder=3)
    top.plot([dx(d), dx(d)], [0.5, 0.5 + off * 0.9], color=GRAY, lw=0.8)
    top.text(dx(d), 0.5 + off, f"{d.strftime('%b %d')}: {state}", ha="center", va="bottom" if off > 0 else "top", color=col, fontsize=8.5, fontweight="bold")
top.set_ylim(-0.6, 1.5)
top.text(dx(date(2026, 7, 1)), 1.45, "WHAT THE PHONE SAID (red a stall, green a run) AND WHAT THE SONG ASKED FOR", ha="left", va="top", color=INK, fontsize=9)
bot.set_facecolor("white")
bot.plot([dx(date(2026, 7, 3)), dx(date(2026, 9, 8))], [112, 112], color=GRAY, lw=1.2, linestyle="--")
bot.text(dx(date(2026, 8, 5)), 122, "snapshots of these weeks do not parse on today's doors: unmeasured", ha="center", fontsize=7.5, color=GRAY)
xs = [dx(d) for d, _ in work_pts]; ys = [w for _, w in work_pts]
bot.step(xs, ys, where="post", color=INK, lw=1.4)
bot.scatter(xs, ys, s=24, color=INK, zorder=3)
for (d, w), (ddx, dy) in zip(work_pts, [(0, -22), (0, 14), (-2.2, -22), (1.2, 14), (1.2, -22), (-1.5, 14), (1.5, -22)]):
    bot.text(dx(d) + ddx, w + dy, f"{w}", ha="center", fontsize=7.5, color=INK)
bot.set_ylim(0, 480); bot.set_ylabel("passes per block, the song's work")
bot.spines[["top", "right"]].set_visible(False)
ticks = [date(2026, 7, 3), date(2026, 8, 1), date(2026, 8, 19), date(2026, 9, 4), date(2026, 9, 15)]
bot.set_xticks([dx(t) for t in ticks]); bot.set_xticklabels([t.strftime("%b %d") for t in ticks], fontsize=8)
bot.set_xlim(dx(date(2026, 6, 30)), dx(date(2026, 9, 24)))
fig.tight_layout()
fig.savefig("phone-and-work.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
