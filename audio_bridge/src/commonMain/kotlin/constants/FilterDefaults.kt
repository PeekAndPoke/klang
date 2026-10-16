/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// The filter and equalizer section defaults every door and node falls back to: the four SVF filters'
// `q`, and the eq's `band` and `tap` (`/dsl-design` section 4: defaults are the same on every surface
// and live in ONE place). The Kotlin doors take them as their default symbols (`lowpass(freq, q =
// FILTER_Q.ign, configure)`); the KlangScript door keeps the same number as its literal default
// (KSP takes literals only), and a default-node row per door pins that the two agree. The cutoff
// ENVELOPE's defaults live in `FilterEnvelopeDefaults.kt`.
// ─────────────────────────────────────────────────────────────────────────────

/** The filters' and the eq sections' default resonance: the Butterworth `q`, flat at the cutoff. */
const val FILTER_Q: Double = 0.707

/** The lowpass's and highpass's default cascade count: one pass, 12 dB/oct (a knob, so a slot can carry it). */
const val FILTER_PASSES: Double = 1.0

/** The eq `band`'s default gain in decibels: `0` is exactly transparent. */
const val EQ_BAND_DB: Double = 0.0

/** The eq `tap`'s default linear gain: `1` mixes the band back in at full strength (about +6 dB at its frequency). */
const val EQ_TAP_GAIN: Double = 1.0
