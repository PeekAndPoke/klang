/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.ConstantIgnitor
import io.peekandpoke.klang.audio_be.ignitor.crush
import io.peekandpoke.klang.audio_be.ignitor.renderThroughNode
import kotlin.math.abs

/**
 * Two properties of the crush law on its host, the Ignitor `Crush` node (`CrushCore`, decision D1: FLOOR), that
 * reach past the oracle rows: the sub-1 bypass across its whole range (a named regression) and the magnitude bound
 * over 1 to 16 bits. The law itself (the FLOOR grid, continuous levels, zero in and zero out, the NaN and bypass
 * rules) is pinned bit for bit against an oracle in `StripLawCoresSpec`. The strip's oversampled crush rows went
 * with the strip (phase 3 step 9): the engine has no oversampled crush (`docs/tasks/oversampling-regions.md`).
 */
class CrushLawSpec : StringSpec({

    /** [input] through the crush node at [bits], one window. */
    fun crushed(input: DoubleArray, bits: Double): DoubleArray = renderThroughNode(input) { it.crush(ConstantIgnitor(bits)) }

    "the crush node bypasses for 0 < bits < 1 (sub-2-level grid)" {
        // Regression: previously `bits = 0.5` would activate with halfLevels ≈ 0.707
        // and produce outputs of ≈ ±1.414, a 3 dB gain bump. Now it must bypass.
        for (bits in listOf(0.01, 0.25, 0.5, 0.7, 0.99)) {
            val original = doubleArrayOf(0.1, -0.3, 0.6, -0.9, 0.99, -0.99)
            val buffer = crushed(original, bits)
            for (i in buffer.indices) {
                buffer[i] shouldBe original[i]
            }
        }
    }

    "the crush node never inflates magnitude for any bit depth in [1, 16]" {
        // Stronger property test: for any musically valid bit depth, no input sample
        // in [-1, 1] should produce an output with magnitude > 1.
        val inputs = doubleArrayOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99, 1.0)
        val depths = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.5, 8.0, 16.0)
        for (bits in depths) {
            for (sign in intArrayOf(1, -1)) {
                val buffer = crushed(AudioBuffer(inputs.size) { i -> sign * inputs[i] }, bits)
                for (s in buffer) {
                    abs(s) shouldBeLessThan 1.0001
                }
            }
        }
    }
})
