/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// The Ignitor shimmer's defaults, read by the Kotlin door (`shimmer(wet = SHIMMER_WET.ign, ...)`) and the node's
// fields (`IgnitorDsl.Shimmer`), the same numbers (`/dsl-design` section 4: defaults live in ONE place). The
// KlangScript door keeps the same numbers as its literal defaults (KSP takes literals only), and a default-node row
// pins that the two agree (typed-doors rollout S3c, `docs/tasks-archive/2026-10/20261016-kotlin-typed-doors.md`).
// ─────────────────────────────────────────────────────────────────────────────

/** The shimmer's wet/dry balance, 0..1: an equal-power half mix. */
const val SHIMMER_WET: Double = 0.5

/** The shimmer's cascade feedback, 0..0.95. */
const val SHIMMER_FEEDBACK: Double = 0.5

/** The cutoff of the one-pole lowpass in the shimmer's feedback path, in Hz. */
const val SHIMMER_TONE_HZ: Double = 4000.0

/** The shimmer's grain transpositions in semitones: root, fifth and octave. */
val SHIMMER_PITCHES: List<Double> = listOf(0.0, 7.0, 12.0)
