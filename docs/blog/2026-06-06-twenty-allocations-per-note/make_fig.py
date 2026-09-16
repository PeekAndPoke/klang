# Figures for "Twenty Allocations per Note". Data pasted as literals; the source of each block is
# named in the comment above it. Run from this directory: python3 make_fig.py
# Fig 1 of the post is graphs.dot, rendered with: dot -Tpng graphs.dot -o copy-chain.png
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: nanoseconds per copy of one voice data object on node, log scale. Sources:
# docs/tasks-archive/2026-06/20260607-mutable-voicedata-optimization.md ("Rejected"): the flat
# 105-field copy() at about 820 ns/op and the Object.assign fastCopy at 18,390 ns/op, both
# 2026-06-06 on JS; the grouped leaf clone today from
# docs/benchmarks/2026-09-16_voicedata-copy.md (node: leaf 78.33, voice 179.89, full 541.10).
# ---------------------------------------------------------------------------------------------
rows = [("flat, 105 fields\ncopy() (June 6)", 820, CRIM), ("flat, fastCopy via\nObject.assign (rejected)", 18390, GRAY),
        ("grouped, leaf\n(0 groups set, today)", 78.33, VERDE), ("grouped, typical voice\n(4 groups, today)", 179.89, VERDE), ("grouped, every group\n(15 groups, today)", 541.10, GOLD)]
fig, ax = plt.subplots(figsize=(10.5, 4), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
for i, (name, v, col) in enumerate(rows):
    ax.bar(i, v, width=0.55, color=col)
    ax.text(i, v * 1.25, f"{v:,.0f}", ha="center", fontsize=9, color=INK, fontweight="bold")
ax.set_yscale("log"); ax.set_ylim(30, 60000)
ax.yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, p: f"{int(v):,}"))
ax.set_xticks(range(len(rows))); ax.set_xticklabels([r[0] for r in rows], fontsize=8)
ax.set_ylabel("ns per clone of one voice data (node, log scale)")
ax.spines[["top", "right"]].set_visible(False)
ax.set_title("ONE COPY OF THE VOICE DATA: THE FLOOR, THE DEAD END, AND THE GROUPED OBJECT", loc="left", fontsize=9, color=INK)
fig.savefig("copy-ns.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
