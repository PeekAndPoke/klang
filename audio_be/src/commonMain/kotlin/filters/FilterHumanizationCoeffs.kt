package io.peekandpoke.klang.audio_be.filters

// ─────────────────────────────────────────────────────────────────────────────
// Shared "filter feel" constants — analog-character tuning parameters used
// across the filter pipeline. Co-located so a single edit retunes the engine.
//
// Sibling of [io.peekandpoke.klang.audio_be.ignitor.AnalogDriftCoeffs] which
// houses the oscillator drift constants.
//
// All scaled by the `analog` parameter:
//   - `analog = 0` → no humanization, bit-identical to the textbook filter
//   - `analog = 1` → mild (Diva-default territory)
//   - `analog = 3` → noticeable (Memorymoog warm-up — "Der Schmetterling")
//   - `analog = 10` → extreme (broken VCO)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Per-voice cutoff offset scale, per unit `analog`. Each filter instance gets a
 * uniform random multiplier in `1 ± FILTER_CUTOFF_OFFSET_PER_ANALOG × analog`
 * applied at construction and at every runtime `setCutoff` call.
 *
 * At `analog=1` ≈ ±1.7 cents per voice; at `analog=3` ≈ ±5 cents; at `analog=10`
 * ≈ ±17 cents. Tuned by ear — larger values smear the filter's character
 * noticeably across unison voices, especially with long filter chains where
 * each filter draws independently (e.g. `notch + lpf + hpf`).
 *
 * Consumer: `VoiceFactory.perVoiceCutoffOffsetMul`.
 */
internal const val FILTER_CUTOFF_OFFSET_PER_ANALOG: Double = 0.001

/**
 * Saturation drive scale per unit `analog` for `SvfLPF` / `SvfHPF` tanh
 * feedback. `driveK = 1.0 + analog × FILTER_DRIVE_PER_ANALOG`. So `analog=1`
 * → driveK=1.5 (mild); `analog=3` → 2.5 (noticeable); `analog=10` → 6.0
 * (heavy compression of the resonance peak).
 *
 * Same value for LPF and HPF — the SVF state-update math is identical, so the
 * saturation character is identical too.
 *
 * Consumers: `SvfLPF`, `SvfHPF` (and future `SvfBPF` / `SvfNotch` when
 * saturation extends to them).
 */
internal const val FILTER_DRIVE_PER_ANALOG: Double = 0.5

/**
 * Coefficient ramp length in samples after each `setCutoff` call. The `BaseSvf`
 * subclasses lerp their coefficients linearly over this many samples to mask
 * the block-boundary discontinuity that `FilterModRenderer` would otherwise
 * introduce on every envelope-modulated block.
 *
 * 32 samples ≈ 0.67 ms at 48 kHz. Long enough to mask the click, short enough
 * to add no audible lag to a swept envelope.
 *
 * If fast-attack `lpe` patches start to feel "soft" / lagging, drop to 8 or 16
 * — but verify with a click test on a static-cutoff patch first to make sure
 * the discontinuity is still masked.
 *
 * Consumer: `BaseSvf.setCutoff`.
 */
internal const val FILTER_SMOOTH_SAMPLES: Int = 32

/** `1 / FILTER_SMOOTH_SAMPLES` — pre-divided so the per-sample loop does muls. */
internal const val FILTER_INV_SMOOTH_SAMPLES: Double = 1.0 / FILTER_SMOOTH_SAMPLES
