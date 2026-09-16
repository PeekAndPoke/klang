# Figures for "The Promise Is a Margin". Data pasted as literals; the source of each block is named
# in the comment above it. Run from this directory: python3 make_fig.py
# Fig 1 of the post is graphs.dot, rendered with: dot -Tpng graphs.dot -o affine-graphs.png
import math
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: the parity oracle as a picture, SIMULATED here in Python's doubles. One
# 128-sample block; the authored chain (x + 0.1) * 3.0 against its fold x * 3.0 + (3.0 * 0.1),
# the folded constant computed in doubles as the optimizer computes it, so the two differ by
# rounding only. Per sample the relative error |diff| / |y| blows up wherever y crosses
# zero; against the block's loudest sample (the engine's OPTIMIZER_PARITY rule, 1e-12 relative to
# max |y| over the block, capped at 1.0) the same differences stay flat. Not a measurement of the
# engine; a picture of why the oracle is defined the way it is.
# ---------------------------------------------------------------------------------------------
n = 128
# The phase is chosen so that sample 40 lands a hundred-thousandth of a sample from a zero
# crossing, which a 48 kHz render does many times a minute.
phi = math.pi - 2.0 * math.pi * 1.5 * (40.0 + 1e-5) / n
xs = [-0.1 + 0.9 * math.sin(2.0 * math.pi * 1.5 * i / n + phi) for i in range(n)]
add = 3.0 * 0.1
ref = [(x + 0.1) * 3.0 for x in xs]
opt = [x * 3.0 + add for x in xs]
diff = [abs(a - b) for a, b in zip(ref, opt)]
scale = min(max(abs(v) for v in ref), 1.0)
per_sample = [d / abs(y) if y != 0.0 and d > 0.0 else (1e-300 if d == 0.0 else 1.0) for d, y in zip(diff, ref)]
block_scale = [d / scale if d > 0.0 else 1e-300 for d in diff]
fig, (a1, a2) = plt.subplots(2, 1, figsize=(10.5, 5.6), dpi=130, sharex=True, gridspec_kw={"height_ratios": [1, 1.6]})
fig.patch.set_facecolor("white")
for ax in (a1, a2):
    ax.set_facecolor("white"); ax.spines[["top", "right"]].set_visible(False)
a1.plot(range(n), ref, color=INK, lw=1.2)
a1.axhline(0.0, color=GRAY, lw=0.6)
a1.set_ylabel("the block's samples")
a1.set_title("SIMULATION: ONE BLOCK, THE AUTHORED CHAIN AGAINST ITS FOLD, ROUNDING ONLY", loc="left", fontsize=9, color=INK)
a2.scatter(range(n), per_sample, s=9, color=CRIM, label="relative to each sample: |diff| / |y|")
a2.scatter(range(n), block_scale, s=9, color=VERDE, label="relative to the block's loudest sample, capped at full scale (the engine's oracle)")
a2.axhline(1e-12, color=GOLD, lw=1.0, ls=(0, (4, 3)))
a2.text(1, 2.2e-12, "OPTIMIZER_PARITY, 1e-12: a fold is accepted under this line", color=GOLD, fontsize=8.5)
imax = max(range(n), key=lambda i: per_sample[i])
a2.annotate(f"sample {imax}: y is {abs(ref[imax]):.0e}, a rounding difference of {diff[imax]:.0e} reads as {per_sample[imax]:.0e}",
            xy=(imax, per_sample[imax]), xytext=(imax + 6, 3e-9), fontsize=8.5, color=CRIM,
            arrowprops=dict(arrowstyle="-", color=CRIM, lw=0.7))
a2.set_yscale("log"); a2.set_ylim(1e-18, 1e-6)
a2.set_ylabel("error, log scale"); a2.set_xlabel("sample in the block")
a2.legend(frameon=False, loc="upper right", fontsize=8)
fig.savefig("parity-oracle.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("max per-sample", max(v for v in per_sample if v < 1.0), "max block-scale", max(block_scale), "zero diffs", sum(1 for d in diff if d == 0.0))
print("figures written")
