# Figures for "Measure Before You Build". Data pasted as literals; the source of each block is
# named in the comment above it. Run from this directory: python3 make_fig.py
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Circle, FancyArrowPatch

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 1: the six guitar-rig rows, node 24, one voice, µs per block. Source: the archive record
# docs/tasks-archive/2026-09/20260916-ignitor-optimizer-arithmetic-folds.md (the table under step 3),
# the runs of 2026-09-15 with KLANG_BENCH_FILTER=guitar-rig.
# ---------------------------------------------------------------------------------------------
rows = [("string only", 18.7, GRAY), ("full rig", 53.2, INK), ("all seven level knobs deleted", 54.3, CRIM),
        ("all five drives deleted", 53.3, CRIM), ("both deleted", 52.3, CRIM), ("every shaper at 1x", 31.6, VERDE)]
fig, ax = plt.subplots(figsize=(10, 4.4), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
ys = list(range(len(rows)))[::-1]
ax.axvline(53.2, color=GRAY, lw=0.8, ls=(0, (4, 3)), zorder=1)
for y, (name, v, col) in zip(ys, rows):
    ax.barh(y, v, color=col, height=0.62, zorder=2)
    ax.text(v + 1.4, y, f"{v:.1f} µs", va="center", fontsize=9, color=INK, zorder=3,
            bbox=dict(facecolor="white", edgecolor="none", pad=1.0))
ax.set_yticks(ys); ax.set_yticklabels([r[0] for r in rows])
ax.text(53.9, len(rows) - 0.55, "the full rig", color=GRAY, fontsize=8, ha="left", va="top")
ax.set_xlim(0, 66); ax.set_xlabel("render time per 128-frame block, one voice, node 24 (µs)")
ax.spines[["top", "right"]].set_visible(False)
ax.set_title("WHAT DELETING A STAGE OUTRIGHT BUYS: THE CEILING OF ANY FOLD", loc="left", fontsize=9, color=INK)
fig.savefig("rig-ablation.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 2: the two decimator reads. Left: the 15-slot ring, two pushes and eight wrapping index
# steps per output. Right: the same taps read straight out of the work buffer at s[2m-13..2m+1].
# A construction, not data: drawn from Oversampler.kt at v0.3.13 and v0.3.14.
# ---------------------------------------------------------------------------------------------
fig, (axl, axr) = plt.subplots(1, 2, figsize=(11, 3.9), dpi=130, gridspec_kw={"width_ratios": [1, 1.35]})
fig.patch.set_facecolor("white")
for a in (axl, axr):
    a.set_facecolor("white"); a.axis("off")
# left: ring
import math
n = 15; R = 1.0
axl.set_xlim(-2.3, 2.3); axl.set_ylim(-2.05, 1.75); axl.set_aspect("equal")
# the arrow points at slot `pos` = 2, so the window's center is (pos - 8) mod 15 = 9 and the taps
# are the center plus its odd offsets modulo 15, exactly as HalfBandState.output() walks them
pos = 2
center = (pos - 8) % n
taps = {center} | {(center + d) % n for d in (1, 3, 5, 7)} | {(center - d) % n for d in (1, 3, 5, 7)}
for k in range(n):
    ang = math.pi / 2 - 2 * math.pi * k / n
    xk, yk = R * math.cos(ang), R * math.sin(ang)
    col = GOLD if k == center else (INK if k in taps else "#DDE3E6")
    axl.add_patch(Circle((xk, yk), 0.16, facecolor=col, edgecolor=INK, lw=0.8))
    axl.text(xk * 1.36, yk * 1.36, f"{k}", ha="center", va="center", fontsize=7.5, color=GRAY)
axl.add_patch(Circle((0, 0), 0.26, facecolor=GOLD, edgecolor=INK, lw=0.8))
axl.text(0, 0, "pos", ha="center", va="center", fontsize=8, color="white", fontweight="bold")
axl.add_patch(FancyArrowPatch((0.28, 0.1), (0.75, 0.62), arrowstyle="-|>", color=GOLD, lw=1.4, mutation_scale=12))
axl.text(0, -1.68, "2 pushes, then 1 center read and 8 index steps", ha="center", fontsize=8, color=INK)
axl.text(0, -1.90, "that wrap at 15; filled = the nine slots one output reads, gold the center", ha="center", fontsize=8, color=INK)
axl.set_title("v0.3.13: a 15-slot ring", loc="left", fontsize=9, color=INK)
# right: linear
axr.set_xlim(-1.5, 17.5); axr.set_ylim(-2.4, 2.6)
cells = list(range(16))  # s[2m-13] .. s[2m+2]
for i in cells:
    j = i - 13  # offset from 2m
    is_center = (j == -6)
    is_tap = j in (-13, -11, -9, -7, -5, -3, -1, 1)
    col = GOLD if is_center else (INK if is_tap else "#DDE3E6")
    axr.add_patch(FancyBboxPatch((i - 0.42, -0.42), 0.84, 0.84, boxstyle="round,pad=0.02", facecolor=col, edgecolor=INK, lw=0.7))
    lbl = f"{j:+d}" if j != 0 else "2m"
    axr.text(i, -0.95, lbl, ha="center", va="top", fontsize=7, color=GRAY)
axr.text(0, 1.55, "history (13)", ha="left", fontsize=8, color=GRAY)
axr.plot([-0.45, 12.45], [1.35, 1.35], color=GRAY, lw=0.8)
axr.text(13.0, 1.55, "this block", ha="left", fontsize=8, color=GRAY)
axr.plot([12.55, 15.45], [1.35, 1.35], color=GRAY, lw=0.8)
axr.text(-0.45, -1.75, "y[m] = 0.5·s[2m-6] + k1(s[2m-5]+s[2m-7]) + k3(s[2m-3]+s[2m-9])",
         ha="left", fontsize=7.2, color=INK)
axr.text(-0.45, -2.15, "       + k5(s[2m-1]+s[2m-11]) + k7(s[2m+1]+s[2m-13])",
         ha="left", fontsize=7.2, color=INK)
axr.text(8, 2.35, "the same nine taps read straight out of the buffer; the center is the gold cell", ha="center", fontsize=8, color=INK)
axr.set_title("v0.3.14: indexed, one read per tap", loc="left", fontsize=9, color=INK)
fig.savefig("decimator-reads.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 3: before and after the polyphase decimator, node 24, µs per block, one voice.
# Source: audio/MEMORY.md "The oversampler's decimator is polyphase and indexed" (2026-09-15).
# ---------------------------------------------------------------------------------------------
names = ["pluck + distort 2x", "pluck + distort 4x", "guitar rig"]
before = [9.4, 13.4, 53.2]; after = [8.6, 10.1, 44.3]
fig, ax = plt.subplots(figsize=(8.5, 3.9), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
xs = range(3); w = 0.38
ax.bar([i - w / 2 for i in xs], before, width=w, color=CRIM, label="ring decimator (v0.3.13)")
ax.bar([i + w / 2 for i in xs], after, width=w, color=VERDE, label="polyphase, indexed (v0.3.14)")
for i, (b, a) in enumerate(zip(before, after)):
    ax.text(i + w / 2, a + 1.0, f"{100 * (a - b) / b:+.0f}%", ha="center", fontsize=9, color=INK, fontweight="bold")
    ax.text(i - w / 2, b + 1.0, f"{b:.1f}", ha="center", fontsize=8, color=GRAY)
    ax.text(i + w / 2, a + 3.6, f"{a:.1f}", ha="center", fontsize=8, color=GRAY)
ax.set_xticks(list(xs)); ax.set_xticklabels(names)
ax.set_ylabel("µs per 128-frame block, one voice, node 24"); ax.set_ylim(0, 62)
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False, loc="upper left")
ax.set_title("THE SAME TAPS, THE SAME SUMS, BIT FOR BIT, IN LESS TIME", loc="left", fontsize=9, color=INK)
fig.savefig("decimator-before-after.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
