# Figures for "Loop Shape Beats Pass Count". Data pasted as literals; the source of each block is
# named in the comment above it. Run from this directory: python3 make_fig.py
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, FancyArrowPatch

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: the two loop shapes of a partial bank, a DIAGRAM. Sample-major: for each
# sample, for each partial (every partial's phase and gain reloaded per sample). Partial-major:
# for each partial, for each sample (one phase and one gain held in registers for a whole block,
# the buffer accumulated once per partial).
# ---------------------------------------------------------------------------------------------
P, N = 4, 10
fig, (a1, a2) = plt.subplots(1, 2, figsize=(11, 4.2), dpi=130)
fig.patch.set_facecolor("white")
def grid(ax, title, order_color, order):
    ax.set_facecolor("white")
    for p in range(P):
        for i in range(N):
            ax.add_patch(Rectangle((i, P - 1 - p), 1, 1, facecolor="white", edgecolor=GRAY, lw=0.6))
    # the traversal order as a thin line through cell centers
    xs = [c[0] + 0.5 for c in order]; ys = [P - 1 - c[1] + 0.5 for c in order]
    ax.plot(xs, ys, color=order_color, lw=1.4)
    ax.plot(xs[0], ys[0], "o", color=order_color, ms=4)
    ax.set_xlim(-0.2, N + 0.2); ax.set_ylim(-1.7, P + 0.9)
    ax.set_xticks([])
    for i in range(N):
        ax.text(i + 0.5, -0.4, f"s{i}", ha="center", fontsize=8, color=INK)
    ax.set_yticks([P - 1 - p + 0.5 for p in range(P)]); ax.set_yticklabels([f"partial {p + 1}" for p in range(P)], fontsize=8)
    for s in ("top", "right", "left", "bottom"):
        ax.spines[s].set_visible(False)
    ax.tick_params(length=0)
    ax.set_title(title, loc="left", fontsize=9, color=INK)
sample_major = [(i, p) for i in range(N) for p in range(P)]
partial_major = [(i, p) for p in range(P) for i in range(N)]
grid(a1, "SAMPLE-MAJOR: for each sample, for each partial", CRIM, sample_major)
a1.text(0, -1.1, "per cell: load this partial's phase, increment and gain,\none sin, one multiply-add into the sample; 41 us per block", fontsize=8, color=INK)
grid(a2, "PARTIAL-MAJOR: for each partial, for each sample", VERDE, partial_major)
a2.text(0, -1.1, "per row: phase, increment and gain stay in registers,\nthe buffer is accumulated once per partial; 22 us per block", fontsize=8, color=INK)
fig.suptitle("THE SAME 40 SINE EVALUATIONS, TWO ORDERS", x=0.125, ha="left", fontsize=9, color=INK)
fig.savefig("loop-shapes.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")

# ---------------------------------------------------------------------------------------------
# Fig 1 of the post: the three shapes of a seven-harmonic voice on 2026-09-07, JVM, µs per block.
# Source: the commit message of 6d4056f9 and audio/MEMORY.md ("Loop shape beats block-pass
# count"): sample-major 41, the tree 24, partial-major 22. That day's harness rendered every voice
# twice (fixed in 39119aef), so these are relative numbers; the tree and bank rows are in
# docs/benchmarks/2026-09-07_sine-partial-banks_jvm.md (23.66 and 22.20).
# ---------------------------------------------------------------------------------------------
fig, ax = plt.subplots(figsize=(8.5, 3.8), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
names = ["the bank, sample-major\n(never committed)", "the tree\n7 Sine, 7 Times, 7 Plus", "the bank, partial-major\n(what shipped)"]
vals = [41, 24, 22]
ax.bar(range(3), vals, color=[CRIM, GRAY, VERDE], width=0.55)
for i, v in enumerate(vals):
    ax.text(i, v + 0.8, f"{v} us", ha="center", fontsize=9, color=INK, fontweight="bold")
ax.set_xticks(range(3)); ax.set_xticklabels(names, fontsize=8.5)
ax.set_ylabel("us per block, one voice (relative)")
ax.set_ylim(0, 48)
ax.spines[["top", "right"]].set_visible(False)
ax.set_title("ONE LOOP LOST TO TWENTY-ONE PASSES, THEN WON, ON 2026-09-07 (JVM)", loc="left", fontsize=9, color=INK)
fig.savefig("three-shapes.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 3 of the post: the tree against the bank on both platforms, then and now. Sources:
# docs/benchmarks/2026-09-10_drift-lanes_jvm.md and _nodejs.md (the 2026-09-10 rows, after the
# harness fix) and docs/benchmarks/2026-09-16_sine-bank-drift-lanes_jvm.md and _nodejs.md
# (today, with the polynomial sine and the per-block drift in the engine).
# ---------------------------------------------------------------------------------------------
groups = [("JVM, Sep 10", 14.11, 13.32), ("node, Sep 10", 25.27, 29.06), ("JVM, today", 8.49, 7.65), ("node, today", 18.19, 20.45)]
fig, ax = plt.subplots(figsize=(9.5, 3.8), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
w = 0.36
for i, (name, tree, bank) in enumerate(groups):
    ax.bar(i - w / 2, tree, width=w, color=GRAY, label="the tree (21 passes)" if i == 0 else None)
    ax.bar(i + w / 2, bank, width=w, color=VERDE if bank <= tree else CRIM, label="the bank (one loop per partial)" if i == 0 else None)
    ax.text(i + w / 2, bank + 0.5, f"{100 * (bank - tree) / tree:+.0f}%", ha="center", fontsize=8.5, color=INK, fontweight="bold")
ax.set_xticks(range(len(groups))); ax.set_xticklabels([g[0] for g in groups])
ax.set_ylabel("us per block, one voice"); ax.set_ylim(0, 38)
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False, loc="upper left")
ax.set_title("THE TREE AGAINST THE BANK, TWO PLATFORMS, TWO DATES (green: the bank is cheaper; red: it is not)", loc="left", fontsize=9, color=INK)
fig.savefig("tree-vs-bank.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 4 of the post: DriftLanes on node, each drift row divided by the sine+analog control of its
# own run. Source: docs/benchmarks/2026-09-10_drift-lanes_nodejs.md (the ratio table in its note:
# the pre-change engine twice, the first cut, the hoisted build twice) and today's
# 2026-09-16_sine-bank-drift-lanes_nodejs.md (supersaw_8v+analog 13.68, sine-harmonics7+analog
# 19.97, sine+analog 10.24).
# ---------------------------------------------------------------------------------------------
rows = [("supersaw, 8 voices", [1.40, 1.56], 1.63, [1.39, 1.47], 13.6845 / 10.2419),
        ("sine bank, 7 harmonics", [3.66, 4.05], 3.75, [3.05, 3.16], 19.9671 / 10.2419)]
fig, axes = plt.subplots(1, 2, figsize=(12, 3.8), dpi=130)
fig.patch.set_facecolor("white")
for ax, (name, pre, first, hoisted, today) in zip(axes, rows):
    ax.set_facecolor("white")
    xs = [0, 0, 1, 2, 2, 3]; ys = pre + [first] + hoisted + [today]
    cols = [GRAY, GRAY, CRIM, VERDE, VERDE, INK]
    ax.scatter(xs, ys, s=[60] * 6, color=cols, zorder=3)
    ax.hlines([min(pre), max(pre)], -0.3, 3.3, color=GRAY, lw=0.6, ls=(0, (3, 3)))
    ax.set_xticks(range(4)); ax.set_xticklabels(["before\n(two runs)", "first cut\n(per sample)", "hoisted\n(two runs)", "today"], fontsize=8.5)
    ax.set_ylabel("cost relative to a plain drifting sine")
    ax.spines[["top", "right"]].set_visible(False)
    ax.set_title(name, loc="left", fontsize=9, color=INK)
    ax.set_xlim(-0.4, 3.4)
fig.suptitle("DRIFTLANES ON NODE: THE FIRST CUT READ ITS PER-BLOCK VALUES INSIDE THE SAMPLE LOOP", x=0.125, ha="left", fontsize=9, color=INK)
fig.savefig("driftlanes-node.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("more figures written")
