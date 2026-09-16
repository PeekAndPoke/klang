# Figures for "Seven Megabytes of Silence". Data pasted as literals; the source of each block is
# named in the comment above it. Run from this directory: python3 make_fig.py
# Fig 2 of the post is graphs.dot, rendered with: dot -Tpng graphs.dot -o shelf.png
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 1 of the post: what one orbit allocated at its first touch, before the warehouse (a 10 s
# stereo delay ring of doubles, 480 000 frames x 2 x 8 bytes = 7.68 MB; the Freeverb network
# 204 KB; buffers and wrappers about 3 KB: docs/plans/resource-warehouse.md, "The problem,
# measured"), against what it allocates after (nothing until the song asks; a class-0 ring of
# 0.5 s plus a 64-frame margin is (24 000 + 64) x 16 bytes = 385 KB at 48 kHz; the reverb network
# only once an orbit asks for room; Der Schmetterling has no delaytime and four orbits with a
# room). Eight orbits per song.
# ---------------------------------------------------------------------------------------------
fig, ax = plt.subplots(figsize=(9, 3.8), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
labels = ["before: every orbit,\nat its first touch", "after: an orbit with a\nhalf-second delay and a room", "after: an orbit of\nDer Schmetterling with a room\n(the song has no delay)"]
ring = [7.68, 0.385, 0.0]
verb = [0.204, 0.204, 0.204]
rest = [0.003, 0.003, 0.003]
xs = range(3)
ax.bar(xs, ring, color=CRIM, width=0.55, label="delay ring")
ax.bar(xs, verb, bottom=ring, color=GOLD, width=0.55, label="reverb network")
ax.bar(xs, rest, bottom=[r + v for r, v in zip(ring, verb)], color=GRAY, width=0.55, label="buffers and wrappers")
for i, tot in enumerate([r + v + s for r, v, s in zip(ring, verb, rest)]):
    ax.text(i, tot + 0.15, f"{tot:.2f} MB" if tot >= 1 else f"{tot * 1000:.0f} KB", ha="center", fontsize=9, color=INK, fontweight="bold")
ax.set_xticks(list(xs)); ax.set_xticklabels(labels, fontsize=8.5)
ax.set_ylabel("MB per orbit, zero-filled on the audio thread"); ax.set_ylim(0, 9)
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False, loc="upper right")
ax.set_title("ONE ORBIT'S FIRST TOUCH: 7.68 MB OF RING FOR DELAYS NOBODY ASKED FOR", loc="left", fontsize=9, color=INK)
fig.savefig("orbit-bytes.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
