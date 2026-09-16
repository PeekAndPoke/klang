# Figures for "One Engine for Five Waves". Data pasted as literals; the source of each block is named
# in the comment above it. Run from this directory: python3 make_fig.py
# Fig 1 of the post is graphs.dot, rendered with: dot -Tpng graphs.dot -o engines.png
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: microseconds per voice before and after the unification, from the archive
# record docs/tasks-archive/2026-06/20260605-oscillator-engine-unification.md ("Verification"):
# JVM / node after: supersaw 4.65 / 8.57 (unchanged), supersquare 5.00 / 9.60 (was about 8.2 JVM),
# supertri 5.04 / 9.73, supersine 15.68 / 22.56 (was 18.7 JVM). No node "before" was recorded, and
# no "before" for supertri. No benchmark file of that day exists; the record is the source.
# ---------------------------------------------------------------------------------------------
oscs = ["supersaw", "supersquare", "supertri", "supersine"]
jvm_before = [4.65, 8.2, None, 18.7]
jvm_after = [4.65, 5.00, 5.04, 15.68]
node_after = [8.57, 9.60, 9.73, 22.56]
fig, ax = plt.subplots(figsize=(10, 4), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
w = 0.26
for i, o in enumerate(oscs):
    if jvm_before[i] is not None:
        ax.bar(i - w, jvm_before[i], width=w, color=CRIM, label="JVM before" if i == 0 else None)
        ax.text(i - w, jvm_before[i] + 0.3, f"{jvm_before[i]}", ha="center", fontsize=8.5, color=INK)
    else:
        ax.text(i - w, 0.4, "no\nbefore", ha="center", fontsize=7.5, color=GRAY)
    ax.bar(i, jvm_after[i], width=w, color=VERDE, label="JVM after" if i == 0 else None)
    ax.text(i, jvm_after[i] + 0.3, f"{jvm_after[i]}", ha="center", fontsize=8.5, color=INK)
    ax.bar(i + w, node_after[i], width=w, color=GRAY, label="node after (no before recorded)" if i == 0 else None)
    ax.text(i + w, node_after[i] + 0.3, f"{node_after[i]}", ha="center", fontsize=8.5, color=INK)
ax.set_xticks(range(4)); ax.set_xticklabels(oscs)
ax.set_ylabel("us per voice (the record's numbers)"); ax.set_ylim(0, 26)
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False, loc="upper left")
ax.set_title("FIVE SUPER OSCILLATORS ON ONE ENGINE, 2026-06-05 (supersaw and superramp share a bar: unchanged)", loc="left", fontsize=9, color=INK)
fig.savefig("per-voice.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
