/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter

/**
 * Drives a stage that switches through [KatalystFilterSwap] (body, vowel) block by block on a mono
 * [input] (both channels), next to [FilterSwapLaw]. The law's banks are REFERENCE filters built
 * from the bare DSP by the spec ([reference]) and run on the dry input from the block they are
 * installed in, so the oracle is the decided law applied to the DSP, never a run of the stage.
 */
internal class SwapHostScript(
    private val n: Int,
    fadeLen: Int,
    private val input: DoubleArray,
    private val stage: (KatalystContext) -> Unit,
) {
    val law = FilterSwapLaw(fadeLen, KatalystFilterSwap.MAX_BANKS)

    private val refs = mutableListOf<DoubleArray>()
    private val mix = StereoBuffer(n)
    private val ctx = KatalystContext(blockFrames = n, mixBuffer = mix)

    var block = 0
        private set

    /** Runs [filter] (fresh, zero state) on the input from the current block on; returns its law id. */
    fun reference(filter: AudioFilter): Int {
        val out = DoubleArray(input.size)
        val buf = DoubleArray(n)

        for (b in block until input.size / n) {
            input.copyInto(buf, 0, b * n, b * n + n)
            filter.process(buf, 0, n)
            buf.copyInto(out, b * n)
        }

        refs += out

        return refs.size - 1
    }

    /** One block through the stage, every sample of both channels against the law. */
    fun step(label: String) {
        val base = block * n

        for (k in 0 until n) {
            mix.left[k] = input[base + k]
            mix.right[k] = input[base + k]
        }

        stage(ctx)

        val expected = law.block(n) { id, k -> if (id == FilterSwapLaw.DRY) input[base + k] else refs[id][base + k] }

        for (k in 0 until n) {
            withClue("$label, block $block, sample $k") {
                mix.left[k] shouldBe expected[k].plusOrMinus(1e-12)
                mix.right[k] shouldBe expected[k].plusOrMinus(1e-12)
            }
        }

        block++
    }

    /** One block through the stage alone, returned (left channel). */
    fun raw(): DoubleArray {
        val base = block * n

        for (k in 0 until n) {
            mix.left[k] = input[base + k]
            mix.right[k] = input[base + k]
        }

        stage(ctx)
        block++

        return DoubleArray(n) { mix.left[it] }
    }

    /** The input of block [b], for a dry comparison. */
    fun inputBlock(b: Int): DoubleArray = input.copyOfRange(b * n, b * n + n)
}
