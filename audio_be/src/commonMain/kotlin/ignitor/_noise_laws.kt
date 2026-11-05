/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

// ═══════════════════════════════════════════════════════════════════════════════
// The fBm noise walk's two numbers, each written once
// ═══════════════════════════════════════════════════════════════════════════════
//
// The perlin and berlin noises walk their noise function (`PerlinNoise.fbm`, `BerlinNoise.fbm` in `common`) by a fixed
// speed per second, scaled by the `rate` knob, over at most a fixed number of octaves. Both numbers were `private` in
// `Ignitors` until katalyst knob expressions step 1 (2026-10-11), which moved them here so a second host (the
// Katalyst's own `perlin` and `berlin` modulators) reads the same two numbers rather than a copy.

/**
 * The speed of the perlin and berlin walks at `rate = 1`, in noise units per SECOND, so `rate` is a speed in seconds
 * like every other timed knob (maintainer, 2026-11-05, KLANG-00290 (engine follow-ups) item 37, option A: rate 1 is the
 * old speed at 48 kHz). Until then the walk took a fixed 0.003 per SAMPLE, so it followed the host's sample rate: 144
 * units per second at 48 kHz, 132 at 44.1 kHz. (Inside an oversampling region it never sped up: a noise is a source,
 * rendered at the base rate, and a Katalyst knob keeps the orbit's clock.) A host takes its per-sample
 * step as `PERLIN_UNITS_PER_SECOND / sampleRate`, divided FIRST: at 48 kHz that is the old 0.003 bit for bit, so a
 * 48 kHz render is unchanged.
 */
internal const val PERLIN_UNITS_PER_SECOND: Double = 144.0

/** The fBm octave cap of the perlin and berlin noises: bounds their per-sample cost, which is linear in the octaves. */
internal const val PERLIN_FBM_MAX_OCTAVES: Int = 8
