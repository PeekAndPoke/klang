# Figures for "Half a Song". Data pasted as literals; the source of each block is named in the
# comment above it. Run from this directory: python3 make_fig.py
# Fig 1 of the post is graphs.dot, rendered with: dot -Tpng graphs.dot -o tone-chain.png
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: the GTR-FX ladder, each rung's marginal medRTF over the rung before it, on
# 2026-08-19 before the fold (docs/benchmarks/2026-08-19_130650_song_jvm.md, the D0 baseline),
# after D1a (2026-08-19_152633_song_jvm.md) and after D1b (2026-08-19_161817_song_jvm.md). The
# "Δ medRTF" column of each file; the bare-signal rung is the delta over the floor.
# ---------------------------------------------------------------------------------------------
rungs = ["bare signal\n(over the floor)", "+ mids tap", "+ presence tap", "+ notch", "+ tracking\nhighpass", "+ double\nlowpass"]
d0 = [0.00470, 0.00030, 0.00015, 0.00036, 0.00035, 0.00038]
d1a = [0.00455, 0.00014, 0.00028, 0.00021, 0.00020, 0.00057]
d1b = [0.00512, 0.00044, 0.00013, 0.00010, 0.00020, 0.00057]
fig, (a1, a2) = plt.subplots(1, 2, figsize=(11.5, 4), dpi=130, gridspec_kw={"width_ratios": [1, 3.2]})
fig.patch.set_facecolor("white")
w = 0.26
for ax, idx in ((a1, [0]), (a2, [1, 2, 3, 4, 5])):
    ax.set_facecolor("white")
    for j, i in enumerate(idx):
        ax.bar(j - w, d0[i] * 1e5, width=w, color=CRIM, label="before (D0 baseline)" if (ax is a2 and j == 0) else None)
        ax.bar(j, d1a[i] * 1e5, width=w, color=VERDE, label="after D1a (Times and Plus fold)" if (ax is a2 and j == 0) else None)
        ax.bar(j + w, d1b[i] * 1e5, width=w, color=GOLD, label="after D1b (every combinator)" if (ax is a2 and j == 0) else None)
    ax.set_xticks(range(len(idx))); ax.set_xticklabels([rungs[i] for i in idx], fontsize=8.5)
    ax.spines[["top", "right"]].set_visible(False)
a1.set_ylabel("marginal medRTF of the rung, x 1e-5"); a1.set_title("the string itself", loc="left", fontsize=9, color=INK)
a2.set_title("the tone chain, rung by rung (the mids tap is where the fold lands: 30 to 14)", loc="left", fontsize=9, color=INK)
a2.legend(frameon=False, loc="upper left", fontsize=8); a2.set_ylim(0, 65)
fig.suptitle("THE GUITAR LADDER ON AUGUST 19, THREE RUNS OF THE SAME RUNGS (JVM, one run each, noisy at this scale)", x=0.125, ha="left", fontsize=9, color=INK)
fig.savefig("ladder-rungs.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
