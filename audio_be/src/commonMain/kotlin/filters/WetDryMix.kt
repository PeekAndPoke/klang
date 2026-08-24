/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * THE wet/dry law (C4 of the filter unification): one cos/sin crossfade for every additive
 * effect, with a per-EFFECT exponent and a minimum dry coefficient (`floor`).
 *
 * ```
 * out = max(floor, cos(w*pi/2)^p) * dry  +  sin(w*pi/2)^p * wet
 *
 *   p = 1  ->  equal-POWER      (decorrelated wet: reverb tail, delay, shimmer tail)
 *   p = 2  ->  equal-AMPLITUDE  (correlated wet: body, vowel, both phasers)
 * ```
 *
 * The exponent is a property of the EFFECT, declared once at the call site, never a user
 * knob: for DEcorrelated signals powers add, so `p = 1` holds 0 dB across the knob; for
 * CORRELATED signals amplitudes add, and the same curve at `p = 2` (a linear crossfade,
 * re-expressed) holds constant amplitude instead. Pretending the two statistics are one law
 * is the +3 dB midpoint bump this helper exists to remove.
 *
 * `floor` is the minimum dry coefficient (what each effect IS: crossfades use 0, the body's
 * physical floor is 0.4, the vowel's 0.2, the orbit phaser is additive with floor 1). Above
 * `w* = (2/pi) * acos(floor^(1/p))` the dry is PINNED at the floor and total power rises
 * with `w` by construction — that is what a floor is for; the law and the floor are mutually
 * exclusive in that region, not composed.
 *
 * Callers precompute both coefficients once per block (the functions are pure and cheap but
 * `pow` does not belong in a per-sample loop) and MUST early-return on `wet == 0` themselves:
 * `dry*1 + wet*0` is NOT a bit-identical bypass (it flips -0.0 and poisons on a NaN wet).
 *
 * Inputs are coerced, never thrown on (house rule): non-finite w -> 0, w clamped to [0, 1],
 * non-finite floor -> 0, floor clamped to [0, 1].
 */
object WetDryMix {

    /** The dry coefficient: `max(floor, cos(w*pi/2)^p)`. */
    fun dryCoeff(w: Double, floor: Double, p: Int): Double {
        val safeW = if (w.isFinite()) w.coerceIn(0.0, 1.0) else 0.0
        val safeFloor = if (floor.isFinite()) floor.coerceIn(0.0, 1.0) else 0.0
        return max(safeFloor, cos(safeW * PI / 2.0).pow(p))
    }

    /** The wet coefficient: `sin(w*pi/2)^p`. */
    fun wetCoeff(w: Double, p: Int): Double {
        val safeW = if (w.isFinite()) w.coerceIn(0.0, 1.0) else 0.0
        return sin(safeW * PI / 2.0).pow(p)
    }
}
