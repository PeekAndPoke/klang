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
// step per sample, scaled by the `rate` knob, over at most a fixed number of octaves. Both numbers were `private` in
// `Ignitors` until katalyst knob expressions step 1 (2026-10-11), which moved them here so a second host (the
// Katalyst's own `perlin` and `berlin` modulators) reads the same two numbers rather than a copy.

/**
 * The step per sample of the perlin and berlin walks at `rate = 1`, in noise units. Per SAMPLE, so the walk's speed
 * follows the sample rate: about 144 units per second at 48 kHz, 132 at 44.1 kHz.
 */
internal const val PERLIN_STEP: Double = 0.003

/** The fBm octave cap of the perlin and berlin noises: bounds their per-sample cost, which is linear in the octaves. */
internal const val PERLIN_FBM_MAX_OCTAVES: Int = 8
