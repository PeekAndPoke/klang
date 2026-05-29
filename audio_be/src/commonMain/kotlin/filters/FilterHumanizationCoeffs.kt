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
 * At `analog=1` ≈ ±5 cents per voice; at `analog=3` ≈ ±15 cents; at `analog=10`
 * ≈ ±50 cents. Tuned by ear — larger values smear the filter's character
 * noticeably across unison voices, especially with long filter chains where
 * each filter draws independently (e.g. `notch + lpf + hpf`).
 *
 * Consumer: `VoiceFactory.perVoiceCutoffOffsetMul`.
 */
internal const val FILTER_CUTOFF_OFFSET_PER_ANALOG: Double = 0.003

/**
 * Humanization-amount scale for the Obxd-style state-dependent damping in
 * `SvfLPF` / `SvfHPF`. `driveScale = analog × FILTER_DRIVE_PER_ANALOG` multiplies
 * the `tCfb` term in `kEff = k + 2·driveScale·tCfb`, where `tCfb` is the
 * diode-pair polynomial ([diodePairResistanceApprox]) evaluated at the BP
 * integrator state. Higher values → stronger resonance compression at hot
 * drive, more "OB-X bite".
 *
 * At `analog = 0` the saturated branch is skipped — linear filter, no cost.
 * At `analog = 1` → driveScale = 0.75 (subtle). At `analog = 3` → 1.5
 * (noticeable). At `analog = 10` → 7.5 (crushed resonance, lots of bite).
 *
 * Consumers: `SvfLPF`, `SvfHPF` (and future `SvfBPF` / `SvfNotch` if extended).
 */
internal const val FILTER_DRIVE_PER_ANALOG: Double = 0.75

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

/**
 * Filter cutoff drift magnitude, expressed as a multiplier on the oscillator
 * drift scale. Filters get their own `AnalogDrift` instance (constructed with
 * `analog × FILTER_DRIFT_RELATIVE_TO_OSC` so the two-layer OU produces a
 * proportionally bigger drift trajectory than oscillator pitch drift).
 *
 * Tuned by ear: filter cutoff is less perceptually sensitive than pitch, so
 * drift can be a few times wider before sounding "out of tune". At `analog=1`
 * with this constant = `5.0`, the slow layer wanders ±4 cents over ~10 s and
 * the fast layer adds ±1 cent of micro-wobble — Diva-default territory.
 *
 * Consumer: `VoiceFactory.toModulator` (creates the per-filter drift).
 */
internal const val FILTER_DRIFT_RELATIVE_TO_OSC: Double = 5.0
