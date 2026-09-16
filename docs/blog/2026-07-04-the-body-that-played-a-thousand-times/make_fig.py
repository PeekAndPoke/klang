# Figures for "The Body That Played a Thousand Times". Data pasted as literals; the source of each
# block is named in the comment above it. Run from this directory: python3 make_fig.py
# Fig 1 of the post is graphs.dot, rendered with: dot -Tpng graphs.dot -o voice-vs-orbit.png
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: the 2 x 2 interaction, the marginal cost of the body with and without a
# superimpose that doubles the voices, before the move (docs/benchmarks/
# 2026-07-03_der-schmetterling-cpu-analysis.md, section 4, "2x2 interaction": base 0.00317, +body
# 0.00499, +super 0.00524, +super +body 0.00797) and after (docs/benchmarks/
# 2026-07-04_233042_song_jvm.md, "exp-interaction": 0.00293, 0.00595, 0.00536, 0.00813). JVM;
# the July files ran 512-frame blocks, before the block size was pinned to 128.
# ---------------------------------------------------------------------------------------------
before = {"no super": 0.00499 - 0.00317, "under a superimpose": 0.00797 - 0.00524}
after = {"no super": 0.00595 - 0.00293, "under a superimpose": 0.00813 - 0.00536}
fig, (a1, a2) = plt.subplots(1, 2, figsize=(11, 3.8), dpi=130)
fig.patch.set_facecolor("white")
w = 0.36
for ax, title in ((a1, "before: the body per voice (2026-07-03)"), (a2, "after: the body per orbit (2026-07-04)")):
    ax.set_facecolor("white")
    data = before if ax is a1 else after
    for i, (k, v) in enumerate(data.items()):
        ax.bar(i, v * 1e4, width=0.5, color=CRIM if ax is a1 else VERDE)
        ax.text(i, v * 1e4 + 0.6, f"+{v:.4f}", ha="center", fontsize=9, color=INK, fontweight="bold")
    ax.set_xticks([0, 1]); ax.set_xticklabels(list(data.keys()))
    ax.set_ylim(0, 36); ax.set_ylabel("marginal medRTF of the body, x 1e-4")
    ax.spines[["top", "right"]].set_visible(False)
    ax.set_title(title, loc="left", fontsize=9, color=INK)
fig.suptitle("WHAT THE BODY COSTS WHEN A SUPERIMPOSE DOUBLES THE VOICES (JVM)", x=0.125, ha="left", fontsize=9, color=INK)
fig.savefig("body-interaction.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 3 of the post: the frozen song's A/B, same machine, JVM: docs/tasks-archive/2026-07/
# 20260703-body-vowel-orbit-katalyst.md, "Result": medRTF 0.10228 -> 0.08265, peakRTF 0.936 -> 0.256.
# ---------------------------------------------------------------------------------------------
fig, (a1, a2) = plt.subplots(1, 2, figsize=(9, 3.6), dpi=130)
fig.patch.set_facecolor("white")
for ax, name, b, a in ((a1, "median RTF", 0.10228, 0.08265), (a2, "peak RTF (the busiest block)", 0.936, 0.256)):
    ax.set_facecolor("white")
    ax.bar([0, 1], [b, a], width=0.5, color=[CRIM, VERDE])
    for i, v in enumerate([b, a]):
        ax.text(i, v * 1.03, f"{v:.3f}" if v < 0.5 else f"{v:.2f}", ha="center", fontsize=9, color=INK, fontweight="bold")
    ax.text(1, a * 0.5, f"{100 * (a - b) / b:+.0f}%", ha="center", fontsize=9, color="white", fontweight="bold")
    ax.set_xticks([0, 1]); ax.set_xticklabels(["body per voice", "body per orbit"])
    ax.set_ylim(0, max(b, a) * 1.18); ax.set_title(name, loc="left", fontsize=9, color=INK)
    ax.spines[["top", "right"]].set_visible(False)
fig.suptitle("THE FROZEN DER SCHMETTERLING, SAME MACHINE, 2026-07-03 (JVM)", x=0.125, ha="left", fontsize=9, color=INK)
fig.savefig("song-ab.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
