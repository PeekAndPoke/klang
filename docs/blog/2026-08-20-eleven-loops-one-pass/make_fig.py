# Figures for "Eleven Loops, One Pass". Data pasted as literals; the source of each block is named
# in the comment above it. Run from this directory: python3 make_fig.py
# Fig 1 of the post is graphs.dot, rendered with: dot -Tpng graphs.dot -o guitar-tail-graphs.png
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: the three loop shapes of EqCore at one, four and six sections, against the
# chained per-filter nodes, µs per block, raw rows of the reversed-order bake-off run of
# 2026-08-19 (docs/benchmarks/2026-08-19_202119_jvm.md and _nodejs.md, effect tables). The
# Ignitor chain rows include a bare sine source (0.92 µs JVM, 1.23 node, the "Ignitor sine"
# row); the EqCore rows run on a pre-filled buffer. Shown raw; the plan's copy-corrected
# figures are in the text.
# ---------------------------------------------------------------------------------------------
shapes = ["sample-major", "section-major", "section-major, locals"]
colors = [GOLD, CRIM, VERDE]
jvm = {"1 section": [0.7198, None, 0.4486], "4 serial": [1.3718, 2.6761, 1.7223], "2 taps + 4 serial": [2.1428, 4.0881, 2.6108]}
node = {"1 section": [0.7776, None, 0.4816], "4 serial": [2.4224, 2.8275, 1.8125], "2 taps + 4 serial": [3.4160, 4.2790, 2.8227]}
chain_jvm = {"4 serial": 2.7334, "2 taps + 4 serial": 2.9897}
chain_node = {"4 serial": 4.0244, "2 taps + 4 serial": 4.7376}
fig, (a1, a2) = plt.subplots(1, 2, figsize=(11.5, 4.2), dpi=130, sharey=True)
fig.patch.set_facecolor("white")
for ax, data, chain, title in ((a1, jvm, chain_jvm, "JVM"), (a2, node, chain_node, "node 24 (V8, the worklet's engine)")):
    ax.set_facecolor("white")
    groups = list(data.keys()); w = 0.2
    for gi, g in enumerate(groups):
        vals = data[g]
        for si, v in enumerate(vals):
            if v is None:
                continue
            ax.bar(gi + (si - 1) * w, v, width=w, color=colors[si], label=shapes[si] if gi == 1 else None)
            ax.text(gi + (si - 1) * w, v + 0.06, f"{v:.2f}", ha="center", fontsize=7.5, color=INK)
        if g in chain:
            ax.bar(gi + 2 * w, chain[g], width=w, color=GRAY, label="chained nodes (incl. a sine source)" if gi == 1 else None)
            ax.text(gi + 2 * w, chain[g] + 0.06, f"{chain[g]:.2f}", ha="center", fontsize=7.5, color=INK)
    ax.set_xticks(range(len(groups))); ax.set_xticklabels(groups)
    ax.spines[["top", "right"]].set_visible(False)
    ax.set_title(title, loc="left", fontsize=9, color=INK)
a1.set_ylabel("µs per block"); a1.set_ylim(0, 5.4)
a2.legend(frameon=False, loc="upper left", fontsize=8)
fig.suptitle("THE THREE LOOP SHAPES AGAINST THE CHAIN, 2026-08-19 (raw rows of one run)", x=0.125, ha="left", fontsize=9, color=INK)
fig.savefig("loop-shapes-bakeoff.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
