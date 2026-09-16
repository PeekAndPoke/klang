# Figures for "The Triplet That Never Drifted". Data pasted as literals; the source of each block is
# named in the comment above it. Run from this directory: python3 make_fig.py
# Fig 1 of the post is graphs.dot, rendered with: dot -Tpng graphs.dot -o representations.png
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker
import numpy as np

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: ns per operation for the three transplanted representations, node and JVM,
# from docs/benchmarks/2026-09-16_rational-transplant.md (medians of 5 trials of 300,000 ops).
# The dashed line is the harness floor of that run (an empty op behind the same interface).
# ---------------------------------------------------------------------------------------------
ops = ["plus\n(walk 1/4, 1/3,\n1/8, 1/2)", "compareTo", "times 3", "step map\n(t-a)/size+c", "from Double"]
node = {"floor": 2.82,
        "fixed 32.32 in a Long (Jan 8)": [18.43, 45.17, 34.10, 79.50, 38.01],
        "Rational, BigInt on JS (Mar 16)": [92.16, 13.97, 62.44, 186.06, 113.38],
        "CycleTime (May 29)": [5.74, 5.93, 5.00, 5.35, 5.38]}
jvm = {"floor": 3.11,
       "fixed 32.32 in a Long (Jan 8)": [2.00, 2.38, 3.59, 3.69, 3.74],
       "Rational, Long on the JVM (Mar 16)": [25.03, 4.53, 6.20, 23.21, 14.16],
       "CycleTime (May 29)": [3.21, 3.68, 3.20, 3.40, 3.22]}
cols = [GOLD, CRIM, VERDE]
fig, axes = plt.subplots(1, 2, figsize=(11, 4.2), dpi=130, sharey=True)
fig.patch.set_facecolor("white")
for ax, data, title in ((axes[0], node, "NODE 24, THE PLATFORM THE PHONE RUNS"), (axes[1], jvm, "JVM 17, THE SAME RUN")):
    ax.set_facecolor("white")
    names = [k for k in data if k != "floor"]
    x = np.arange(len(ops)); w = 0.26
    for j, name in enumerate(names):
        vals = data[name]
        ax.bar(x + (j - 1) * w, vals, width=w, color=cols[j], label=name)
        for xi, v in zip(x + (j - 1) * w, vals):
            ax.text(xi, v * 1.12, f"{v:.0f}", ha="center", fontsize=7, color=INK)
    ax.axhline(data["floor"], color=GRAY, linestyle="--", linewidth=0.8)
    ax.text(len(ops) - 0.5, data["floor"] * 0.62, f"harness floor {data['floor']:.1f} ns", ha="right", fontsize=7, color=GRAY)
    ax.set_yscale("log"); ax.set_ylim(1, 400)
    ax.yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, p: f"{int(v)}"))
    ax.set_xticks(x); ax.set_xticklabels(ops, fontsize=7.5)
    ax.spines[["top", "right"]].set_visible(False)
    ax.set_title(title, loc="left", fontsize=9, color=INK)
    ax.legend(fontsize=7, frameon=False, loc="upper left")
axes[0].set_ylabel("ns per operation (log scale)")
fig.suptitle("THREE REPRESENTATIONS OF MUSICAL TIME, TRANSPLANTED INTO ONE BENCHMARK", x=0.01, ha="left", fontsize=9, color=INK)
fig.tight_layout(rect=(0, 0, 1, 0.95))
fig.savefig("transplant-ns.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
