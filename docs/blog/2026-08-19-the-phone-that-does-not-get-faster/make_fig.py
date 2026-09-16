# Figures for "The Phone That Does Not Get Faster". Data pasted as literals; the source of each
# block is named in the comment above it. Run from this directory: python3 make_fig.py
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from datetime import date

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: the phone's timeline. Sources: docs/plans/unified-eq.md:225,808; docs/plans/
# resource-warehouse.md:214,321,417; docs/tasks-archive/2026-09/20260916-ignitor-optimizer-
# arithmetic-folds.md:40; audio/MEMORY.md (drift per block, 2026-09-15).
# ---------------------------------------------------------------------------------------------
events = [
    (date(2026, 8, 19), "stalls, then half the song plays", CRIM, "constant folding deployed"),
    (date(2026, 8, 20), "plays at about 75% CPU", VERDE, "EqCore fusion deployed"),
    (date(2026, 9, 4), "first run kills the playback", CRIM, "eight cylinders and four reverbs built cold in the first frame"),
    (date(2026, 9, 4), "first run plays", VERDE, "resource warehouse, warmup vocabulary"),
    (date(2026, 9, 15), "barely runs", CRIM, "the guitars rebuilt as five-stage rigs"),
    (date(2026, 9, 15), "smooth", VERDE, "drift per block, culling, polynomial math"),
]
fig, ax = plt.subplots(figsize=(12.5, 5.2), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
d0, d1 = date(2026, 8, 15), date(2026, 9, 19)
def x(d): return (d - d0).days / (d1 - d0).days
ax.plot([0, 1], [0.5, 0.5], color=INK, lw=1.2, zorder=1)
# the stalls above the line, the runs below; the middle cluster sits on a higher tier so no
# label reaches into its neighbour
layout = [
    (0.28, 0.0, "center"), (-0.28, 0.0, "center"),
    (0.62, -0.012, "center"), (-0.62, 0.012, "center"),
    (0.28, -0.012, "center"), (-0.28, 0.012, "center"),
]
for (d, state, col, why), (off, nx, ha) in zip(events, layout):
    xx = x(d) + nx
    ax.plot([xx], [0.5], marker="o", ms=9, color=col, zorder=3)
    ax.plot([xx, xx], [0.5, 0.5 + off * 0.9], color=GRAY, lw=0.8, zorder=2)
    va = "bottom" if off > 0 else "top"
    ax.text(xx, 0.5 + off, f"{d.strftime('%b %d')}: {state}", ha=ha, va=va, color=col, fontsize=9, fontweight="bold")
    ax.text(xx, 0.5 + off + (0.09 if off > 0 else -0.09), why, ha=ha, va=va, color=GRAY, fontsize=8)
ax.set_xlim(-0.05, 1.05); ax.set_ylim(-0.42, 1.48); ax.axis("off")
ax.text(0.0, 1.46, "DER SCHMETTERLING ON THE FAIRPHONE 4, AUGUST TO SEPTEMBER 2026", ha="left", va="top", color=INK, fontsize=9)
ax.text(0.0, 1.37, "what the phone said each time the song was deployed; red is a stall, green a run", ha="left", va="top", color=GRAY, fontsize=8, style="italic")
fig.savefig("phone-timeline.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 1 of the post: node against the JVM, the same 33 ignitor rows, one machine.
# Source: docs/benchmarks/2026-08-19_202119_compare.md, the "JS/JVM ratio" column.
# ---------------------------------------------------------------------------------------------
ratios = [('pluck+distort_8x', 2.09), ('superpluck', 1.52), ('pluck+coarse_4x', 1.51), ('supersine', 1.48),
          ('pluck+distort_4x', 1.75), ('pluck+crush_4x', 1.63), ('pluck+distort_2x', 1.55), ('pluck', 1.28),
          ('sine+vibrato+tremolo', 1.46), ('pluck+distort', 1.45), ('supersaw+lpf+adsr+reverb', 2.25), ('dust', 1.48),
          ('supersaw_16v', 1.9), ('supersaw+lpf+adsr', 1.94), ('square+fm', 1.59), ('supertri', 1.87), ('supersquare', 1.94),
          ('supersaw_8v', 1.92), ('supersaw', 2.01), ('superramp', 2.01), ('sine', 1.79), ('supersaw_4v', 1.95),
          ('brownnoise', 1.94), ('pinknoise', 2.21), ('zawtooth', 1.86), ('impulse', 1.95), ('whitenoise', 2.14),
          ('triangle', 1.91), ('ramp', 1.92), ('supersaw_1v', 1.95), ('pulze', 1.96), ('square', 1.98), ('sawtooth', 2.01)]
ratios.sort(key=lambda r: r[1])
fig, ax = plt.subplots(figsize=(9.5, 7.2), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
names = [r[0] for r in ratios]; vals = [r[1] for r in ratios]
cols = [GOLD if v >= 2.1 else INK for v in vals]
ax.barh(range(len(vals)), vals, color=cols, height=0.7)
ax.set_yticks(range(len(vals))); ax.set_yticklabels(names, fontsize=8)
ax.axvline(1.0, color=GRAY, lw=1.0, ls=(0, (4, 3)))
ax.axvline(1.92, color=CRIM, lw=1.2)
ax.text(1.93, len(vals) + 0.2, "median 1.92x", color=CRIM, fontsize=8.5, va="bottom")
ax.text(1.02, -0.9, "JVM = 1", color=GRAY, fontsize=8)
for i, v in enumerate(vals):
    # the value inside the bar's end, so the median rule never runs through a label
    ax.text(v - 0.02, i, f"{v:.2f}x", va="center", ha="right", fontsize=7.5, color="white", fontweight="bold")
ax.set_xlim(0, 2.6); ax.set_ylim(-1.2, len(vals) + 1.0)
ax.set_xlabel("render time on node divided by render time on the JVM, same machine, same rows")
ax.spines[["top", "right"]].set_visible(False)
ax.set_title("THE DEPLOYMENT PLATFORM IS THE SLOW ONE", loc="left", fontsize=9, color=INK)
fig.savefig("jvm-vs-node.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 3 of the post: the ledger's first reading, the same frozen pieces on two engines, medians of three.
# Source: docs/benchmarks/ledger.md (v0.3.12 transplant rows; v0.3.14 rows of 2026-09-16 11:02-11:03).
# ---------------------------------------------------------------------------------------------
pieces = ["guitar melody", "guitars rhythm", "marimba", "trommel", "bass", "drums"]
before = [0.01961, 0.04352, 0.03388, 0.06037, 0.01009, 0.03968]
after = [0.01612, 0.03746, 0.02897, 0.03365, 0.00451, 0.01726]
fig, ax = plt.subplots(figsize=(10, 4.2), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
xs = range(len(pieces)); w = 0.38
ax.bar([i - w / 2 for i in xs], before, width=w, color=CRIM, label="v0.3.12 (2026-09-10)")
ax.bar([i + w / 2 for i in xs], after, width=w, color=VERDE, label="v0.3.14 (2026-09-16)")
for i, (b, a) in enumerate(zip(before, after)):
    ax.text(i + w / 2, a + 0.0012, f"{100 * (a - b) / b:+.0f}%", ha="center", fontsize=8.5, color=INK, fontweight="bold")
ax.set_xticks(list(xs)); ax.set_xticklabels(pieces)
ax.set_ylabel("median RTF (JVM, 48 kHz, 128-frame blocks)")
ax.spines[["top", "right"]].set_visible(False)
ax.legend(frameon=False, loc="upper right")
ax.set_title("THE SAME SIX PIECES, TWO ENGINES, ONE MACHINE", loc="left", fontsize=9, color=INK)
fig.savefig("ledger-first-reading.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
