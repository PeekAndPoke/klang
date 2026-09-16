# Figures for "Eleven Digits of Sine". Data pasted as literals; the source of each block is named
# in the comment above it. Run from this directory: python3 make_fig.py
import math
import matplotlib
import matplotlib.ticker
matplotlib.use("Agg")
import matplotlib.pyplot as plt

INK, GRAY, CRIM, VERDE, GOLD = "#14202B", "#8D9BA3", "#B23A32", "#2F7A4F", "#A8791C"
plt.rcParams.update({"font.family": "monospace", "font.size": 9})

# ---------------------------------------------------------------------------------------------
# Fig 1: nanoseconds per call, library against polynomial, JVM and node 24, Ryzen 9 PRO 7940HS,
# 2026-09-15. Source: audio/MEMORY.md ("Polynomial e^x in the envelopes", the per-call paragraph),
# from audio_benchmark's runMathBenchmark.
# ---------------------------------------------------------------------------------------------
funcs = ["sin", "2^x", "e^x"]
jvm_lib, jvm_poly = [6.1, 8.9, 3.7], [1.7, 2.7, 3.5]
node_lib, node_poly = [7.2, 12.1, 7.1], [1.8, 3.8, 4.5]
fig, (a1, a2) = plt.subplots(1, 2, figsize=(10.5, 3.9), dpi=130, sharey=True)
fig.patch.set_facecolor("white")
for ax, lib, poly, title in ((a1, jvm_lib, jvm_poly, "JVM 17"), (a2, node_lib, node_poly, "node 24 (V8, the worklet's engine)")):
    ax.set_facecolor("white")
    w = 0.38
    ax.bar([i - w / 2 for i in range(3)], lib, width=w, color=CRIM, label="library")
    ax.bar([i + w / 2 for i in range(3)], poly, width=w, color=VERDE, label="polynomial")
    for i, (l, p) in enumerate(zip(lib, poly)):
        ax.text(i - w / 2, l + 0.2, f"{l}", ha="center", fontsize=8.5, color=INK)
        ax.text(i + w / 2, p + 0.2, f"{p}", ha="center", fontsize=8.5, color=INK)
        ax.text(i + w / 2, p + 1.4, f"{l / p:.1f}x", ha="center", fontsize=8.5, color=INK, fontweight="bold")
    ax.set_xticks(range(3)); ax.set_xticklabels(funcs)
    ax.spines[["top", "right"]].set_visible(False)
    ax.set_title(title, loc="left", fontsize=9, color=INK)
a1.set_ylabel("ns per call (median)"); a1.set_ylim(0, 14.5)
a1.legend(frameon=False, loc="upper left")
fig.suptitle("ONE CALL, LIBRARY AGAINST POLYNOMIAL", x=0.125, ha="left", fontsize=9, color=INK)
fig.savefig("ns-per-call.png", bbox_inches="tight", facecolor="white")
plt.close(fig)

# ---------------------------------------------------------------------------------------------
# Fig 2: the error of fastSin over one period, computed here in Python with the coefficients and
# the fold copied from audio_be/src/commonMain/kotlin/DspUtil.kt (SIN_S1..SIN_S11), against
# Python's math.sin (a correctly rounded libm). Not a measurement of the engine; a check of its
# polynomial.
# ---------------------------------------------------------------------------------------------
S1, S3, S5, S7, S9, S11 = (0.9999999998893945, -0.1666666654102997, 0.00833332925508402,
                           -0.00019840702003847582, 2.7518821382393724e-06, -2.37942173904353e-08)
def fast_sin(phase):
    x = phase - math.pi
    if x > math.pi / 2:
        x = math.pi - x
    elif x < -math.pi / 2:
        x = -math.pi - x
    x2 = x * x
    return -x * (S1 + x2 * (S3 + x2 * (S5 + x2 * (S7 + x2 * (S9 + x2 * S11)))))
n = 40000
ph = [2 * math.pi * i / n for i in range(n)]
err = [fast_sin(p) - math.sin(p) for p in ph]
fig, ax = plt.subplots(figsize=(10.5, 3.6), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
ax.plot([p / (2 * math.pi) for p in ph], [e * 1e11 for e in err], color=INK, lw=0.9)
ax.axhline(1.3, color=CRIM, lw=0.8, ls=(0, (4, 3))); ax.axhline(-1.3, color=CRIM, lw=0.8, ls=(0, (4, 3)))
ax.text(0.005, 1.45, "1.3e-11, the fitted maximum error", color=CRIM, fontsize=8)
ax.set_xlabel("phase, in periods"); ax.set_ylabel("fastSin(x) - sin(x), in units of 1e-11")
ax.set_ylim(-2.2, 2.2)
ax.spines[["top", "right"]].set_visible(False)
ax.set_title("THE POLYNOMIAL'S ERROR OVER ONE PERIOD (computed from the coefficients, not measured in the engine)", loc="left", fontsize=9, color=INK)
fig.savefig("fastsin-error.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("max abs error", max(abs(e) for e in err))

# ---------------------------------------------------------------------------------------------
# Fig 3: the voices that carry a sine, a 2^x or an e^x per sample, before and after, medRTF, JVM.
# Sources: the sine in the modulators, docs/benchmarks/2026-09-15_153754_song_jvm.md (library)
# and 2026-09-15_153812_song_jvm.md (polynomial), rows FM BELL, LFO PAD, LEAD; the 2^x in the
# pitch paths, 2026-09-15_150939 (pow) and 2026-09-15_150806 (fastExp2), row trommel; the e^x in
# the envelopes and the compressor, 2026-09-15_163229 (library) and 2026-09-15_163405
# (polynomial), rows PINK, HATS, BASS.
# ---------------------------------------------------------------------------------------------
rows = [
    ("FM bell\n(sine)", 0.00257, 0.00215), ("LFO pad\n(sine)", 0.00636, 0.00416), ("lead, superramp\n(sine)", 0.02242, 0.01889),
    ("Orchestertrommel\n(2^x)", 0.04619, 0.04364),
    ("pink noise\n(e^x)", 0.00246, 0.00222), ("hats\n(e^x)", 0.00289, 0.00271), ("bass\n(e^x)", 0.00219, 0.00212),
]
fig, ax = plt.subplots(figsize=(11, 4.2), dpi=130)
fig.patch.set_facecolor("white"); ax.set_facecolor("white")
w = 0.38
for i, (name, b, a) in enumerate(rows):
    ax.bar(i - w / 2, b, width=w, color=CRIM, label="library" if i == 0 else None)
    ax.bar(i + w / 2, a, width=w, color=VERDE, label="polynomial" if i == 0 else None)
    ax.text(i + w / 2, a * 1.06, f"{100 * (a - b) / b:+.1f}%", ha="center", fontsize=8.5, color=INK, fontweight="bold")
ax.set_yscale("log"); ax.set_ylim(0.0015, 0.08)
ax.set_yticks([0.002, 0.003, 0.005, 0.01, 0.02, 0.03, 0.05]); ax.set_yticklabels(["0.002", "0.003", "0.005", "0.01", "0.02", "0.03", "0.05"])
ax.yaxis.set_minor_formatter(matplotlib.ticker.NullFormatter())
ax.set_xticks(range(len(rows))); ax.set_xticklabels([r[0] for r in rows], fontsize=8)
ax.set_ylabel("median RTF (JVM, log scale)")
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False, loc="upper left")
ax.set_title("THE VOICES THAT PAY PER SAMPLE, BEFORE AND AFTER EACH SWAP", loc="left", fontsize=9, color=INK)
fig.savefig("voices-before-after.png", bbox_inches="tight", facecolor="white")
plt.close(fig)
print("figures written")
