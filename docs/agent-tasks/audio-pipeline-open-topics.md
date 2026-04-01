# Audio Pipeline — Open Topics

Last updated: 2026-03-24. Final review: 3x SHIP (SW, QA, Audio).

~426 tests across 35 files. All pass.

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

## 5. Minor Items

- **PitchEnvelopeRenderer per-sample `pow()`** — could optimize sustain phase (constant value).
- **Triangle oscillator not band-limited** — aliasing at -12dB/oct, acceptable for most use.
- **Cylinder "last writer wins"** — by design (strudel convention), can cause parameter flickering.
- **VoiceScheduler cut group TODO** — "Use a fade out / release phase instead of hard cut?"
- **VoiceScheduler scheduling tests** — only diagnostics tested, promotion logic untested.
