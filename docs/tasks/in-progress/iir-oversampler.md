# A better oversampler: polyphase IIR half-band, everywhere

Status: **in progress since 2026-10-10, on the branch `iir-oversampler` (worktree `klang-worktrees/iir-oversampler`).**
Part of [`../oversampling-regions.md`](../oversampling-regions.md) (its §6 "the resampler's quality becomes the sound
lever", §8a, §8b), done first and on its own: the sound part, no DSL change.

## Why

Today's oversampler (`Oversampler.kt`: linear-interpolation upsampling, a 15-tap half-band FIR decimator per 2x stage)
was built cheap for waveshapers. Measured 2026-10-09 on a clean signal at 48 kHz and 2x: -0.7 dB at 10 kHz, -2 dB at
16 kHz, -4 dB at 20 kHz; its alias rejection is about 20 dB where 50 to 60 is the target (review findings A1, A2,
A5). On a bus it dulls the whole mix, so the Katalyst `distort` defaults to no oversampling. Its latency (4, 5.5, 6.25
samples) is what `parallel` has to pad.

## Decided (maintainer, 2026-10-10)

- **A better oversampler everywhere**, sound changes accepted: "the songs are 'just' benchmarks still"; the maintainer
  re-tunes them if needed (O4).
- **Low latency** matters (O7): a plain `plus` of a clean and an oversampled path should not comb.
- **The kernel: a polyphase IIR half-band** (all-pass based), chosen over first-order ADAA alone, which at 48 kHz
  dulls the top more than today (measured: -2 dB at 10 kHz, -6 dB at 16 kHz, -11.7 dB at 20 kHz on a quiet sine).
- **The IIR is the standard for now, other methods come later as types** (maintainer, after the measurements: 8
  coefficients for the first 2x stage, flat to 0.00 dB, about 100 dB alias rejection; a linear-phase FIR with a
  constant delay is a later type).
- **6 coefficients for every stage above the first** (maintainer, choosing "6 coefs, as built" after the implementation had
  moved from 4 without asking; about 95 dB of rejection there). The low-frequency latency: 3.07 / 4.40 / 5.06 / 5.39
  samples at 2x / 4x / 8x / 16x, against the FIR's 4.0 / 5.5 / 6.25.
- **Phase-matched pads** (maintainer: "IIR + phase-matched pads", with "the sound quality is priority 1. Performance
  number 2."). The IIR's delay rises toward the top (at 2x: 3.07 at 1 kHz, 3.33 at 10 kHz, 3.89 at 16 kHz, 4.74 at
  20 kHz), so a whole-sample pad against a clean oversampled path NOTCHES the top: -28.5 dB at 18.25 kHz (2x), -31.5
  dB at 16.75 kHz (4x), -38 dB at 18 kHz (8x). The first report said "a dip of about 4 dB", measured at only two
  frequencies; that was wrong. The fix: a dry path runs through an unshaped round trip of the same oversampler, its
  PHASE TWIN, and then matches exactly. Built in the Katalyst `distort` stage's dry path, the voice `parallel` (the
  build lists each branch's oversamplers, `BuiltIgnitor.oversamplers`; a branch that mixes two in a plain `plus`
  falls back to the whole-sample pad) and the bus `parallel` (`KatalystLatentEffect.oversamplers`; a lookahead is
  still padded by a ring).
- **The CPU cost is accepted** (maintainer, 2026-10-10: "The slower performance is expected and will be offset by
  the new oversampler dsl when multiple steps share the same oversampling instead up and down sampling again and
  again"). Measured on the JVM, a 128-frame round trip with a soft shaper: the FIR 2.97 us at 2x and 3.77 us at 4x,
  the IIR 3.32 us and 5.03 us (its states flushed once per block instead of per sample, its sections unrolled with
  the states in locals); Kokon's whole render 20.6 s against the FIR's 18.2 s (before the phase twins).
- **ADAA later, as a "type"** of the oversample DSL (with the oversampling regions), where it pairs with oversampling.
- **No DSL change now** (the other agent's DSL work must not be crossed): the `oversample` knobs stay as they are.
- Files owned while this runs (agreed with the engine follow-ups coordinator, 2026-10-10): `Oversampler.kt`,
  `DistortionCore.kt`, `ShapingFuncs.kt`, the `DistortionShape` catalogue, the Shape and Distort build arms and
  `IgnitorEffects.kt`, `KatalystDistortEffect.kt`, their specs. Its item 13 (`ScratchBuffers.oversample`) stays theirs.

## Steps

1. **The bench:** a spec that measures any oversampler round trip: passband flatness, alias rejection (a hard shape
   driven by a high sine, energy at the non-harmonic frequencies) and the group delay per frequency. Today's kernel's
   numbers become the reference row.
2. **The kernel:** the IIR half-band design (coefficients derived and documented), upsampler and decimator per 2x
   stage, the same API (`upsample`, `decimate`, `reset`, `factor`, the declared latency), allocation-free.
3. **The latency:** the declared delay becomes the new kernel's low-frequency group delay; `parallel` and the Katalyst
   chain read it as before (`BuiltIgnitor.latencySamples`, `KatalystLatentEffect`), and pad an oversampled path with
   its phase twin rather than whole samples (decided above).
4. **Listening:** loudness-matched pairs of the songs that oversample (Kokon, Der Schmetterling, Tetris, TetrisRemix,
   IrishLamentTechno, A Truth Worth Lying For, Sandsturm), old kernel against new.
5. **The bus default:** whether the Katalyst `distort` now oversamples by default (it does not today because the old
   kernel dulled the mix).

Credits land with the code: the all-pass polyphase half-band (Valenzuela and Constantinides, 1983).
