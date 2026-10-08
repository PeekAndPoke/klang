/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

/**
 * The weight of a linear fade from [weightFrom] to [weightTo], [remaining] samples before it lands:
 * `weightTo + (weightFrom - weightTo) * (remaining * invLength)`, where [invLength] is one over the fade's length in
 * samples.
 *
 * The weight counts DOWN to the landing, so at `remaining == 0` it is `weightTo + (weightFrom - weightTo) * 0.0`,
 * which is [weightTo] exactly for finite ends; a fade that counted up from the start would land on a rounding
 * instead. At `remaining == length` it is [weightFrom] exactly when `length * invLength` rounds to 1.0, which holds
 * for the engine's fade lengths (882 and 960 samples for 20 ms, 2205 and 2400 for 50 ms, at 44.1 and 48 kHz) but not
 * for every length (49 is the smallest that misses, by one ulp).
 *
 * With `weightTo = 0.0` the law is `0.0 + weightFrom * (remaining * invLength)`, the same double as
 * `weightFrom * (remaining * invLength)` for every [weightFrom] whose product is not -0.0 (`0.0 + -0.0` is +0.0):
 * that is -0.0 itself and a negative subnormal small enough to round to -0.0; and but for a NaN's payload. That is
 * the one-ended fade [io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystFilterSwap] wrote before tidy-up step
 * 12 (b); its weights are 1.0 or a target weight in `[0, 1]`, never negative.
 *
 * No guard: a non-finite end or [invLength] makes the weight non-finite (an infinite end at `remaining == 0` gives
 * NaN, `Inf * 0.0`).
 */
@Suppress("NOTHING_TO_INLINE")
inline fun linearFadeWeight(weightFrom: Double, weightTo: Double, remaining: Int, invLength: Double): Double =
    weightTo + (weightFrom - weightTo) * (remaining * invLength)

/**
 * One channel of a linear crossfade: `target[i] = base[i] + w * (other[i] - base[i])` for `i` in `0 until count`,
 * the weight `w` of sample `i` being [linearFadeWeight] at `remaining - i`. At `w = 0` the output is [base], at
 * `w = 1` it is [other].
 *
 * [target] may be the same array as [base] or as [other]: each sample reads both before it writes. The two hosts
 * use one form each:
 * - [io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystFilterSwap] fades an outgoing bank out over the
 *   target: `base = target = mix` (the target bank's output, or dry), `other` the outgoing entry's output,
 *   `weightTo = 0.0`, `weightFrom` the weight the entry left at.
 * - [io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystCompressorEffect] fades its compressed mix against
 *   the dry one: `base` the dry mix, `other = target = mix` (compressed), from where the weight stands to 0 or 1.
 *
 * The caller chooses [count] so the window stops at or before the landing (`count <= remaining`); past it the
 * weight would extrapolate beyond [weightTo]. Each host handles the landing itself (what the rest of the block
 * carries, which state comes next).
 *
 * **Not twins of this law, on purpose:**
 * - The chain swap's [io.peekandpoke.klang.audio_be.Crossfade] ramps the leaving chain's INPUT down and the
 *   arriving chain's OUTPUT up, so a drain's tail is never scaled; two chains, not one blend.
 * - The duck's glide ([io.peekandpoke.klang.audio_be.effects.Ducking.processStereoGliding]) scales the
 *   REDUCTION, `1 + w * (gain - 1)`, with weights written from the block's END, one ramp per block; no dry copy
 *   and no landing count.
 *
 * Allocates nothing; no lambda. No guard: non-finite samples or weights pass through the arithmetic as they are.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun crossfadeLinear(
    target: DoubleArray,
    base: DoubleArray,
    other: DoubleArray,
    count: Int,
    weightFrom: Double,
    weightTo: Double,
    remaining: Int,
    invLength: Double,
) {
    for (i in 0 until count) {
        val w = linearFadeWeight(
            weightFrom = weightFrom,
            weightTo = weightTo,
            remaining = remaining - i,
            invLength = invLength,
        )
        val b = base[i]

        target[i] = b + w * (other[i] - b)
    }
}
