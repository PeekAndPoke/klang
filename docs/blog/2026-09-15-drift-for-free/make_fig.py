# Figures for "Drift for Free". Data pasted as literals; the source of each block is named in the
# comment above it. Run from this directory: python3 make_fig.py
import math
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 2 of the post: the rig suite before and after, medRTF, JVM. Source: docs/benchmarks/
# 2026-09-15_175556_song_jvm.md (drift per sample; its "no analog" rows 0.03355 rhythm, 0.02300
# marimba, 0.03426 trommel) and 2026-09-15_175907_song_jvm.md (drift per block; its "no analog"
# rows 0.03491, 0.02413, 0.03750). Each floor is drawn next to the run it was measured in.
# ---------------------------------------------------------------------------------------------
pieces = ["rhythm guitars", "marimba", "Orchestertrommel", "bass", "melody guitar"]
per_sample = [0.04009, 0.02626, 0.04132, 0.00867, 0.01977]
per_block = [0.03555, 0.02368, 0.03409, 0.00625, 0.01653]
floor_before = [0.03355, 0.02300, 0.03426, None, None]
floor_after = [0.03491, 0.02413, 0.03750, None, None]
fig, ax = plt.subplots(figsize=(11, 4.4), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
w = 0.2
for i, p in enumerate(pieces):
    ax.bar(i - 1.5 * w, per_sample[i], width=w, color=CRIM, label="drift per sample" if i == 0 else None)
    if floor_before[i] is not None:
        ax.bar(i - 0.5 * w, floor_before[i], width=w, color=GRAY, label="no drift, measured in the per-sample run" if i == 0 else None)
    ax.bar(i + 0.5 * w, per_block[i], width=w, color=VERDE, label="drift per block, ramped" if i == 0 else None)
    if floor_after[i] is not None:
        ax.bar(i + 1.5 * w, floor_after[i], width=w, color=GOLD, label="no drift, measured in the per-block run" if i == 0 else None)
    ax.text(i + 0.5 * w, per_block[i] + 0.0008, f"{100 * (per_block[i] - per_sample[i]) / per_sample[i]:+.0f}%", ha="center", fontsize=8.5, color=INK, fontweight="bold")
ax.set_xticks(range(len(pieces))); ax.set_xticklabels(pieces)
ax.set_ylabel("median RTF (JVM, 48 kHz, 128-frame blocks)")
ax.spines[["top", "right"]].set_visible(False)
ax.legend(frameon=False, loc="upper center", bbox_to_anchor=(0.5, -0.08), ncol=2)
ax.set_title("THE SAME PIECES, THE DRIFT STEPPED PER SAMPLE, PER BLOCK, AND NOT AT ALL", loc="left", fontsize=9, color=INK)
fig.savefig("rig-before-after.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 1 of the post: one drift lane's multiplier, the fast layer only, SIMULATED with the engine's constants
# (AnalogDriftCoeffs.kt: fast tau 0.05 s, sigma_x = 1/sqrt(3), 0.2 cents peak per unit analog at
# 3 sigma, analog = 15 as in the song): per sample at 48 kHz against per block at 375 Hz with a
# linear ramp, each lane drawing its own noise from one seed (the two consume the stream at
# different rates, so they are two walks of the same character, not the same walk), over four
# 128-frame blocks.
# ---------------------------------------------------------------------------------------------
def coeffs(rate):
    a = 1.0 / (0.05 * rate)
    sigma_y = math.sqrt(a * a / (1.0 - (1.0 - a) ** 2)) * (1.0 / math.sqrt(3.0))
    scale = 15.0 * 0.2 * 5.7780e-4 / (3.0 * sigma_y)
    return a, scale

def xorshift(seed):
    x = seed & 0xFFFFFFFF
    while True:
        x ^= (x << 13) & 0xFFFFFFFF; x ^= x >> 17; x ^= (x << 5) & 0xFFFFFFFF
        yield (x / 2147483647.0) - 1.0  # uniform in about [-1, 1], as the engine maps its draw

blocks, n = 4, 128
# per sample
a_s, k_s = coeffs(48000)
rng = xorshift(20260915); y = 0.0; per_sample_curve = []
for i in range(blocks * n):
    y = (1 - a_s) * y + a_s * next(rng)
    per_sample_curve.append(1.0 + k_s * y)
# per block: one step per block, the multiplier ramps linearly from the previous end to the new one
a_b, k_b = coeffs(375)
rng = xorshift(20260915); y = 0.0; ends = [1.0]
for b in range(blocks + 1):
    y = (1 - a_b) * y + a_b * next(rng)
    ends.append(1.0 + k_b * y)
ramp = []
for b in range(blocks):
    s, e = ends[b], ends[b + 1]
    for i in range(n):
        ramp.append(s + (e - s) * i / n)
cents = lambda m: [1200.0 * math.log2(v) for v in m]
fig, ax = plt.subplots(figsize=(10.5, 3.8), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
t = [i / 48000.0 * 1000.0 for i in range(blocks * n)]
ax.plot(t, cents(per_sample_curve), color=CRIM, lw=1.0, label="stepped every sample (48 000 steps per second)")
ax.plot(t, cents(ramp), color=VERDE, lw=1.6, label="stepped every block (375 per second), ramped across the block")
for b in range(1, blocks):
    ax.axvline(b * n / 48000.0 * 1000.0, color=GRAY, lw=0.7, ls=(0, (3, 3)))
ax.set_xlabel("time (ms); the dashed lines are block boundaries, 2.67 ms apart")
ax.set_ylabel("pitch offset (cents), fast layer only")
ax.spines[["top", "right"]].set_visible(False)
ax.legend(frameon=False, loc="upper center", bbox_to_anchor=(0.5, -0.2), ncol=2, fontsize=8)
ax.set_title("SIMULATION: ONE LANE'S FAST LAYER, ITS TIME CONSTANT 50 MS, OVER FOUR BLOCKS", loc="left", fontsize=9, color=INK)
fig.savefig("lane-simulation.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 3 of the post: the whole song, live and frozen, before and after. Source: docs/benchmarks/
# 2026-09-15_175711_song_jvm.md (per sample) and 2026-09-15_180013_song_jvm.md (per block).
# ---------------------------------------------------------------------------------------------
names = ["Der Schmetterling, live (Sep 15)", "Der Schmetterling, frozen (Jul 3)"]
before = [0.10575, 0.09873]; after = [0.09734, 0.08186]
fig, ax = plt.subplots(figsize=(8, 3.6), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
w = 0.36
for i in range(2):
    ax.bar(i - w / 2, before[i], width=w, color=CRIM, label="drift per sample" if i == 0 else None)
    ax.bar(i + w / 2, after[i], width=w, color=VERDE, label="drift per block" if i == 0 else None)
    ax.text(i + w / 2, after[i] + 0.002, f"{100 * (after[i] - before[i]) / before[i]:+.0f}%", ha="center", fontsize=9, color=INK, fontweight="bold")
ax.set_xticks([0, 1]); ax.set_xticklabels(names)
ax.set_ylabel("median RTF (JVM)"); ax.set_ylim(0, 0.125)
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False, loc="upper right")
ax.set_title("THE WHOLE SONG, 48 CYCLES, THREE PASSES", loc="left", fontsize=9, color=INK)
fig.savefig("song-before-after.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
