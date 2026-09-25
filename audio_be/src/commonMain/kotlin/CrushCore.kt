/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.math.floor
import kotlin.math.pow

/**
 * THE crush law, one copy for both hosts (phase 3 step 4, decision D1, 2026-09-25: FLOOR everywhere):
 * the voice strip's `CrushRenderer` and the Ignitor's `Crush` node are thin hosts that only adapt their
 * block contract (the strip reads its amount once per note, the node once per block). Before this the
 * node carried its own symmetric `round` quantizer; now both render the strip's quantizer, which is
 * what `classic()` needs to rebuild the strip crush bit for bit (`ClassicStripParitySpec`).
 *
 * **The quantizer** is `floor(x * halfLevels) / halfLevels`, clamped to `[-1, 1]`. Floor biases every
 * sample DOWN to the next grid step, so a symmetric input comes out with a DC offset of roughly
 * `-0.5 / halfLevels` (-0.5 at amount 1), which moves with a modulated amount: this asymmetry IS the classic audible character of a
 * bit crusher. A symmetric `round` quantizer is cleaner and sounds almost the same at every amount,
 * which is why it lost (D1).
 *
 * - **The clamp** catches a non-integer `halfLevels`, where `floor(x * hl) / hl` can pass `-1` (at
 *   amount 1.5, `hl` is about 1.414 and `x = -1` maps to `-2 / 1.414`, about -1.414).
 * - **Continuous levels**: `levels = 2^amount` is not rounded to an integer, so modulating the amount
 *   sweeps the grid smoothly instead of stepping across `log2(N)` boundaries.
 * - **The NaN guard** is fused into the loop: a NaN sample comes out as 0.0, and so does every sample
 *   of an infinite amount (`x * Inf / Inf` is NaN).
 */
internal object CrushCore {

    /**
     * The half level count an [amount] asks for: `2^amount / 2`, or [BYPASS] below 1.0. With fewer
     * than 2 levels the grid step exceeds the input range entirely, so bypass is the only sensible
     * behaviour, and 1.0 (two grid steps plus the clamp) is the lowest musically meaningful amount.
     * A NaN amount is a bypass too (`NaN >= 1.0` is false).
     */
    fun halfLevels(amount: Double): Double =
        if (amount >= 1.0) {
            2.0.pow(amount) / 2.0
        } else {
            BYPASS
        }

    /** What [halfLevels] answers for an amount that does not engage the quantizer. */
    const val BYPASS: Double = 0.0

    /**
     * Quantizes `input[i]` into `output[i]` for `i` in `[from, to)`. [input] and [output] may be the
     * same buffer (the strip renders in place). The caller has checked that [halfLevels] is not
     * [BYPASS].
     */
    fun quantize(input: AudioBuffer, output: AudioBuffer, from: Int, to: Int, halfLevels: Double) {
        val hl = halfLevels

        for (i in from until to) {
            val q = floor(input[i] * hl) / hl
            output[i] = q.coerceIn(-1.0, 1.0).nanGuard()
        }
    }
}
