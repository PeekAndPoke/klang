# Figures for "Sixty-Seven Microseconds to 385 Nanoseconds". Data pasted as literals; the source of
# each block is named in the comment above it. Run from this directory: python3 make_fig.py
# Fig 2 of the post is graphs.dot, rendered with: dot -Tpng graphs.dot -o wire-path.png
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 1 of the post: nanoseconds per ScheduledVoice on node, log scale. Source:
# docs/tasks-archive/2026-06/20260607-worklet-codec-ksp.md: the kotlinx baseline (decode 64,309 ns,
# encode 4,994 ns, the first microbench; 67,001 and 4,904 in the closing measurement), the ProtoBuf
# spike (encode 50 us, the only number recorded; never committed), the hand-rolled proof (decode
# 398, encode 440), the generated codec (decode 385, encode 478). Today's row from the run of
# 2026-09-16 (see the post's table).
# ---------------------------------------------------------------------------------------------
rows = [("kotlinx\ndecodeFromDynamic", 67001, 4904, CRIM), ("ProtoBuf spike\n(encode only)", None, 50000, GRAY),
        ("hand-rolled proof", 398, 440, GOLD), ("generated codec\n(2026-06-07)", 385, 478, VERDE)]
fig, ax = plt.subplots(figsize=(10, 4), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
w = 0.36
for i, (name, dec, enc, col) in enumerate(rows):
    if dec is not None:
        ax.bar(i - w / 2, dec, width=w, color=col, label="decode (the audio thread)" if i == 0 else None)
        ax.text(i - w / 2, dec * 1.25, f"{dec:,}", ha="center", fontsize=8.5, color=INK, fontweight="bold")
    ax.bar(i + w / 2, enc, width=w, color=col, alpha=0.55, label="encode (the main thread)" if i == 0 else None)
    ax.text(i + w / 2, enc * 1.25, f"{enc:,}", ha="center", fontsize=8.5, color=INK)
ax.set_yscale("log"); ax.set_ylim(100, 300000)
ax.yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, p: f"{int(v):,}"))
ax.set_xticks(range(len(rows))); ax.set_xticklabels([r[0] for r in rows], fontsize=8.5)
ax.set_ylabel("ns per ScheduledVoice (node, log scale)")
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False, loc="upper right")
ax.set_title("ONE VOICE ACROSS THE WIRE: FOUR CODECS, 2026-06-07", loc="left", fontsize=9, color=INK)
fig.savefig("codec-ns.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
