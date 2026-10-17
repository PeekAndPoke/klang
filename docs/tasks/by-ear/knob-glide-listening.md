# Knob glide: two ear items

Status: **both decided by ear (maintainer, 2026-10-17) and done; ready to archive.** Opened 2026-10-14 (C13) from
[`../../../audio/ref/knob-glide.md`](../../../audio/ref/knob-glide.md).

1. **A body material change below 60 Hz:** at a 20 ms crossfade it reads -39.6 dB re the signal, at 50 ms -49.0 dB,
   a gap of 9.4 dB, loudest where a low mode starts cold (`croon` to `tube`, `wood` to `cedar`, `bell` to `wood`).
   The level and class of the phaser thump heard before. Listen: 20 ms or 50 ms for the low body modes?
2. **The compressor's rising-threshold swing:** accepted as it is, or a fix? Listen on a bus whose threshold moves up.

Pairs: `tmp/listening/knob-glide/` (local, not in git; start at `tmp/listening/00-index.md`).

## Decisions (maintainer, 2026-10-17)

1. **50 ms.** "i cannot really hear a difference between the 20ms and the 50ms, so i would say to be on the safe side
   we take the 50ms."
2. **Fix it.** "the hard swell is not really acceptable, or at least not that fast."

## What was done (2026-10-17)

1. `BANK_CROSSFADE_SECONDS` is 0.05 (`audio_bridge/.../constants/BusEffectDefaults.kt`, its KDoc rewritten with the
   measurement and the true rate limit). It shares the value of `KNOB_GLIDE_SECONDS` and stays its own constant: the
   swap counts samples (2205 at 44.1 kHz, 2400 at 48 kHz), the knob glide whole blocks. A material can now change once
   every 18 blocks (52.2 ms) at 44.1 kHz, 19 (50.7 ms) at 48 kHz: 16ths pass, 32nds park above about 143 BPM at
   44.1 kHz (148 at 48 kHz), 64ths park above about 72 BPM (at 20 ms they passed up to about 184 BPM at 44.1 kHz,
   176 at 48 kHz). The specs that pinned the 20 ms landing (882 and 960
   samples) pin 2205 and 2400 as literals; the modulated body rows were re-timed to the longer fade.
2. A compressor move that RELAXES the compressor on balance (review rounds 2 and 3: its net reduction at a fixed
   loud input of 0 dBFS is less, the maintainer's decision of 2026-10-17, "judge at a fixed loud level"; so a rising threshold, a lower ratio, a narrower knee, or a mix that relaxes; a tie glides)
   now RELEASES at the compressor's own release time constant instead of gliding over 50 ms: the three knobs travel as
   ONE move on one shared fraction (so the path is the glide's path and mixed directions cannot dip below both ends,
   review round 1), the fraction a one-pole sampled at block ends but never faster than the linear glide (the click
   safety net; the release value itself is untouched), linear per sample inside a block
   (`audio_be/.../cylinders/katalyst/CompressorKnobMove.kt`, used by `KatalystCompressorEffect`). A move that adds reduction
   on balance, or ties, keeps the 50 ms linear glide (a compressor-to-limiter swap whose knee narrows glides now); judged
   at the follower's envelope instead, a move after a rest read as a tie and glided, and the next note swelled 16 dB
   in 20 ms (pair: `tmp/listening/knob-glide/compressor-rest-*.wav`); a new target starts a new move from the values in force; a modulated knob stays outside the move with its one-block ramp. The sprudel `compressor(...)` door, the
   Katalyst `compressor` and `limiter` all reach this one stage. Why the knob and not the gain: the gain computer is
   memoryless and linear in the threshold and the inverse ratio above the knee, so a one-pole on the knob IS the
   envelope's release on the reduction, and a falling knob afterwards starts from where it stands, with no
   over-compression (a variant that shifts the envelope by the threshold step was considered and not taken for that reason).
   Measured: on the listening pair the 0.25 s takeover now swells about 24 dB with 10 to 90 percent in about 183 ms,
   against 32.2 dB with 10 to 90 percent in 46 ms before (`tmp/listening/knob-glide/compressor-swing-after.wav`).

Corpus: three rows moved, all by item 1 (a run with item 2 disabled gives the same hashes): Seltsamere Dinge, The
Synthsale Piper's Last Rave, frozen strangerThings_2026_07_03; nothing accepted.

The OFF edge and a falling duck `depth` keep the 50 ms fade (maintainer, 2026-10-17); the decision lives in
[`../../../audio/ref/knob-glide.md`](../../../audio/ref/knob-glide.md).
