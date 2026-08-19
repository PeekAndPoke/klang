/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

// ─────────────────────────────────────────────────────────────────────────────
// Engine-internal filter coefficients.
//
// The analog-character constants that used to live here — FILTER_CUTOFF_OFFSET_PER_ANALOG,
// FILTER_DRIVE_PER_ANALOG, FILTER_DRIFT_RELATIVE_TO_OSC — moved to
// `audio_bridge/constants/FilterHumanizationDefaults.kt`, because each is the default
// of a `StageDsl.Filter` field and the two copies had already drifted apart. What stays
// here is the coefficient smoothing, which has no DSL field and is not tunable from a song.
//
// Sibling of [io.peekandpoke.klang.audio_be.ignitor.AnalogDriftCoeffs], which houses the
// oscillator drift constants — also engine-internal, also not (yet) authorable.
// ─────────────────────────────────────────────────────────────────────────────

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
