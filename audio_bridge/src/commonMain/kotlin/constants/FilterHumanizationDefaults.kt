/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// Shared "filter feel" constants — analog-character tuning parameters used
// across the filter pipeline. Co-located so a single edit retunes the engine.
//
// These live in `audio_bridge` because they are **wire defaults**: each one is
// the default of a field on `StageDsl.Filter`, which crosses the wire. The
// engine (`audio_be`) reads them from here, so authoring side and engine side
// cannot drift apart. They did drift apart while they were duplicated — see
// `docs/tasks/audio-bridge-constants.md` §1.
//
// Engine-internal filter tuning with no DSL field (`FILTER_SMOOTH_SAMPLES`,
// the oscillator drift depths in `AnalogDriftCoeffs`) deliberately stays in
// `audio_be`. The rule is: a constant belongs here iff it is a wire default.
//
// All scaled by the `analog` parameter. `analog = 0` is exactly no humanization —
// bit-identical to the textbook filter, and the saturated branch is skipped entirely.
//
// The old "1 = mild / 3 = Memorymoog / 10 = broken VCO" ladder that used to head this
// block was written for the pre-2026-08-11 values and overstated today's by 2-10x (cutoff
// offset 5x, drive 2x, drift 10x); at
// the current cutoff offset, `analog = 10` is ±3.5 cents, which is not a broken VCO.
// Deliberately not replaced with a new ladder: the values are mid-retune (see
// docs/tasks/audio-bridge-constants.md §6) and a fresh set of adjectives would go stale
// the same way. Per-constant magnitudes are given below and are derived, not guessed.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Per-voice cutoff offset scale, per unit `analog`. Each filter instance gets a
 * uniform random multiplier in `1 ± FILTER_CUTOFF_OFFSET_PER_ANALOG × analog`
 * applied at construction and at every runtime `setCutoff` call.
 *
 * At `analog=1` ≈ ±0.35 cents per voice; at `analog=3` ≈ ±1 cent; at `analog=10`
 * ≈ ±3.5 cents. Tuned by ear — larger values smear the filter's character
 * noticeably across unison voices, especially with long filter chains where
 * each filter draws independently (e.g. `notch + lpf + hpf`). This offset is
 * *frozen per note*, so on fast melodic lines through a high-Q filter it becomes
 * a per-note re-pitch of the resonant peak — keep it small.
 *
 * Consumer: `VoiceFactory.perVoiceCutoffOffsetMul`, via `StageDsl.Filter.cutoffOffsetPerAnalog`.
 */
const val FILTER_CUTOFF_OFFSET_PER_ANALOG: Double = 0.0002

/**
 * Humanization-amount scale for the analog-style state-dependent damping in
 * `SvfLPF` / `SvfHPF`. `driveScale = analog × FILTER_DRIVE_PER_ANALOG` multiplies
 * the `tCfb` term in `kEff = k + 2·driveScale·tCfb`, where `tCfb` is the
 * diode-pair polynomial (`diodePairResistanceApprox`) evaluated at the BP
 * integrator state. Higher values → stronger resonance compression at hot
 * drive, more "OB-X bite".
 *
 * At `analog = 0` the saturated branch is skipped — linear filter, no cost.
 *
 * Magnitudes measured at unit amplitude, Q=5 (the adjectives this ladder used to carry were
 * written for the old 0.5 and did not survive the halving — "crushed" at driveScale 2.5 is
 * really about -2.2 dB of peak compression at Q=5, and -0.10 dB at Q=2):
 * `analog = 1` → driveScale 0.25; `analog = 3` → 0.75; `analog = 10` → 2.5.
 *
 * The effect is strongly Q-dependent AND level-dependent (`tCfb` is driven by the integrator
 * state), so a single adjective per `analog` value cannot be honest. See
 * `docs/tasks/audio-bridge-constants.md` §6.2 for the measured table.
 *
 * Consumers: `SvfLPF`, `SvfHPF` (via `StageDsl.Filter.drivePerAnalog`) and
 * `IgnitorFilters`, which reads this constant directly — the ignitor filter
 * path has no pipeline stage to carry the value.
 */
const val FILTER_DRIVE_PER_ANALOG: Double = 0.25

/**
 * Filter cutoff drift magnitude, expressed as a multiplier on the oscillator
 * drift scale. Filters get their own `AnalogDrift` instance (constructed with
 * `analog × FILTER_DRIFT_RELATIVE_TO_OSC` so the two-layer OU produces a drift
 * trajectory scaled relative to oscillator pitch drift).
 *
 * The oscillator side runs at `ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS`
 * = 1.0 cent per unit `analog`, so this constant *is* the FILTER-to-pitch drift
 * ratio: at 0.25 the filter wanders a quarter as much as pitch; above 1.0 it
 * wanders more. Whether that is the right way round is an open tuning question
 * — real hardware arguably wanders more in cutoff than in pitch. See
 * `docs/tasks/audio-bridge-constants.md` §6.
 *
 * Consumer: `VoiceFactory.toModulator`, via `StageDsl.Filter.driftRelToOsc`.
 */
const val FILTER_DRIFT_RELATIVE_TO_OSC: Double = 0.25
