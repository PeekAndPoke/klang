import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
SCALE_MAX, MEASURED = 30000, 149721

fig, ax = plt.subplots(figsize=(10.5, 3.5), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")

# printed scale: 0..30000 occupies x 0..1 ; beyond that is "off the plate"
PLATE = 0.62
def xf(v): return v / SCALE_MAX * PLATE

ax.add_patch(plt.Rectangle((0, 0.46), PLATE, 0.14, facecolor="#EDF1F3", edgecolor=INK, lw=1.2, zorder=2))
for v in range(0, SCALE_MAX + 1, 5000):
    ax.plot([xf(v)] * 2, [0.46, 0.40], color=INK, lw=1.0, zorder=3)
    ax.text(xf(v), 0.345, f"{v:,}", ha="center", va="top", color=INK, fontsize=8.5, family="monospace")
ax.text(0.0, 0.985, "S L I M   P R O D U C T I V I T Y   I N D E X", ha="left", va="top",
        color=INK, fontsize=8.5, family="monospace")
ax.text(0.0, 0.905, "printed range 0 to 30,000, and where this project actually falls", ha="left", va="top",
        color=GRAY, fontsize=8.5, family="monospace", style="italic")

# typical environments, inside the scale
for v, lbl, col in [(6000, "business\nsystems", GRAY), (12000, "complex\nreal-time", GRAY), (28000, "best\ninstrumented", VERDE)]:
    ax.plot([xf(v)] * 2, [0.46, 0.60], color=col, lw=2.0, zorder=4)
    ax.text(xf(v), 0.70, lbl, ha="center", va="bottom", color=col, fontsize=8.5, family="monospace", linespacing=1.4)

# the plate ends; the measured value is far beyond it
ax.plot([PLATE, 0.965], [0.53, 0.53], color=INK, lw=1.0, ls=(0, (5, 4)), zorder=1)
ax.add_patch(plt.Rectangle((PLATE, 0.46), 0.02, 0.14, facecolor="white", edgecolor="none", zorder=3))
ax.text(PLATE + 0.035, 0.44, "end of the instrument", ha="left", va="top", color=GRAY,
        fontsize=8.5, family="monospace", style="italic")

ax.plot([0.965] * 2, [0.46, 0.60], color=CRIM, lw=3.0, zorder=5, solid_capstyle="butt")
ax.text(0.965, 0.70, "Klangmotor\nE = 149,721", ha="right", va="bottom", color=CRIM,
        fontsize=11, family="monospace", fontweight="bold", linespacing=1.5)

arr = FancyArrowPatch((xf(28000) + 0.012, 0.30), (0.955, 0.30), arrowstyle="-|>",
                      mutation_scale=13, color=GOLD, lw=1.6, shrinkA=0, shrinkB=0, zorder=4)
ax.add_patch(arr)
ax.text((xf(28000) + 0.955) / 2, 0.255, "5.0x past the top of the scale", ha="center", va="top",
        color=GOLD, fontsize=9.5, family="monospace")

ax.set_xlim(-0.035, 1.0); ax.set_ylim(0.17, 1.03); ax.axis("off")
fig.tight_layout(rect=(0, 0, 1, 1))
fig.savefig("productivity-index-scale.png", facecolor="white", bbox_inches="tight", pad_inches=0.25)
print("written")
