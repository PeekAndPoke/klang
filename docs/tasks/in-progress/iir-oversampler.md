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
   chain read it as before (`BuiltIgnitor.latencySamples`, `KatalystLatentEffect`).
4. **Listening:** loudness-matched pairs of the songs that oversample (Kokon, Der Schmetterling, Tetris, TetrisRemix,
   IrishLamentTechno, A Truth Worth Lying For, Sandsturm), old kernel against new.
5. **The bus default:** whether the Katalyst `distort` now oversamples by default (it does not today because the old
   kernel dulled the mix).

Credits land with the code: the all-pass polyphase half-band (Valenzuela and Constantinides, 1983).
