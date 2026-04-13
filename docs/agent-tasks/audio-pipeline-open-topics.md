# Audio Pipeline — Open Topics

Last updated: 2026-04-13.

Recent progress:

- Distortion oversampling (`distos()`) landed — see archived
  `2026-04/20260409-distortion-oversampling.md`.
- New "pedal" engine mode landed (commit `3c32351f`). Voice pipeline now has a Motör-branded
  engine DSL with `modern` and `pedal` flavors (see `engines/AudioEngine.kt`).

The topics below are still open.

---

## 1. Bus-Level Configuration

Voice currently carries cylinder config (delay.time, reverb.roomSize, phaser.*, compressor.*,
ducking.*) that should move to Bus-level configuration. This would decouple voice data from
bus parameters and allow per-cylinder effect settings independent of voice scheduling.

## 2. Master Configuration & Analog Saturation

Enhance `KlangAudioRenderer` to support configurable mastering chains (e.g., "Transparent" vs
"Analog Warmth") with parameters for compression, saturation drive, and asymmetric bias.
See archived `klang-audio-master-configuration.md` for full implementation plan.

## 3. New Oscillator Candidates

High priority:

- **Phase Distortion** (Casio CZ-style) — alias-free warped sine, 8+ shapes
- **Waveshaping** (Chebyshev polynomial harmonics)
- **Hard Sync** — PolyBLEP at sync points (infrastructure exists)

Medium priority:

- **Formant / Vocal** oscillator
- **Waveguide Reed / Clarinet**

See archived `new-oscillator-implementations.md` for full specs and references.

## 4. Modulated Delay Effects

Flanger and Chorus effects using the existing `DelayLine` with LFO modulation.
Karplus-Strong is already implemented. See archived `modulated-delay-line-effects.md`.

## 5. Oversampler DSP Quality (deferred)

Current Oversampler (15-tap half-band FIR + linear interpolation) works but has known quality gaps:

- **A1/A2/A5:** Linear interpolation creates images that intermodulate through the waveshaper.
  Fix: replace with zero-stuffing + half-band FIR on both upsample and downsample paths.
  Upgrade to 31-43 tap half-band for 50-60 dB rejection (vs ~20 dB current).
- **S7:** No `reset()` on Oversampler — stale filter state may bleed into recycled voice attacks.
- **S8:** `(Float) -> Float` lambda boxes per sample in Kotlin/JS. Inline `process()` to fix.

See archived `20260409-distortion-oversampling.md` for full review findings (A1-A9, S1-S11).

## 6. Minor Items

- **PitchEnvelopeRenderer per-sample `pow()`** — could optimize sustain phase (constant value).
- **Triangle oscillator not band-limited** — aliasing at -12dB/oct, acceptable for most use.
- **Cylinder "last writer wins"** — by design (strudel convention), can cause parameter flickering.
- **VoiceScheduler cut group TODO** — "Use a fade out / release phase instead of hard cut?"
- **VoiceScheduler scheduling tests** — only diagnostics tested, promotion logic untested.
