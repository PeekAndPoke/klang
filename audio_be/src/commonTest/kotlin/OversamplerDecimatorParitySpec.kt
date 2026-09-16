/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import kotlin.math.PI
import kotlin.math.sin

/**
 * The polyphase decimator against the ring-buffer form it replaced, bit for bit.
 *
 * [RingOversampler] is the previous implementation kept as the oracle, its arithmetic unchanged: a 15-tap
 * circular delay line per stage, two pushes and one FIR evaluation per output. The new pass
 * indexes the work buffer directly and reads its first outputs from a prefix view; the ragged
 * block lengths below cross every boundary that view has (a block shorter than the history, a
 * block shorter than the prefix, a block that ends inside the in-place region, an empty block).
 * Samples are compared with `Double.equals`, which keeps `-0.0` and `0.0` apart where `==` would
 * not: the claim is the same bits, not the same value.
 */
class OversamplerDecimatorParitySpec : StringSpec({

    val blockFrames = 128

    fun signal(i: Int): Double {
        // A sine with seeded hash noise on top (Int multiply wraps, the constant is 2^32 minus
        // Knuth's 2654435761), so the shaper below clips on both sides and never sits on a rail.
        var x = i * -1640531535
        x = x xor (x ushr 13)
        val noise = ((x and 0xFFFF) / 65535.0 - 0.5) * 0.6

        return 0.8 * sin(2.0 * PI * 441.0 * i / 48000.0) + noise
    }

    // Ragged: shorter than the history, shorter than the prefix, on and around both edges, full blocks.
    val lengths = listOf(1, 3, 5, 6, 7, 12, 13, 14, 25, 26, 27, 28, 29, 64, 128, 2, 0, 128, 128, 9, 128)

    for (stages in 1..3) {
        "stages $stages: every output equals the ring form across ragged block lengths" {
            val scratch = ScratchBuffers(blockFrames)
            val fast = Oversampler(stages)
            val ring = RingOversampler(stages)
            var pos = 0

            for (length in lengths) {
                val offset = (pos * 7) % (blockFrames - length + 1)
                val a = AudioBuffer(blockFrames) { i -> signal(pos + i - offset) }
                val b = a.copyOf()

                fast.process(a, offset, length, scratch) { w, count ->
                    for (i in 0 until count) {
                        w[i] = ShapingFuncs.hardClip(w[i] * 1.7)
                    }
                }
                ring.process(b, offset, length, scratch) { w, count ->
                    for (i in 0 until count) {
                        w[i] = ShapingFuncs.hardClip(w[i] * 1.7)
                    }
                }

                for (i in 0 until blockFrames) {
                    withClue("block at $pos, length $length, sample $i") {
                        a[i].equals(b[i]) shouldBe true
                    }
                }

                pos += length
            }
        }
    }
})

/** The ring-buffer oversampler as it was before the polyphase pass, the oracle above. */
private class RingOversampler(stages: Int) {
    val stages: Int = stages.coerceAtLeast(0)
    val factor: Int = 1 shl this.stages

    private val decimators = Array(this.stages) { HalfBandState() }
    private var lastSample: Double = 0.0

    fun process(
        buffer: AudioBuffer,
        offset: Int,
        length: Int,
        scratchBuffers: ScratchBuffers,
        transformBlock: (work: AudioBuffer, count: Int) -> Unit,
    ) {
        if (stages == 0) {
            return
        }

        val oversampledLen = length * factor

        scratchBuffers.oversample(factor).use { work ->
            upsample(buffer, offset, length, work)
            transformBlock(work, oversampledLen)

            var currentLen = oversampledLen

            for (stage in 0 until stages) {
                currentLen = decimate2x(decimators[stage], work, currentLen)
            }

            work.copyInto(buffer, offset, 0, length)
        }
    }

    private fun upsample(buffer: AudioBuffer, offset: Int, length: Int, work: AudioBuffer) {
        val f = factor
        var prev = lastSample

        for (i in 0 until length) {
            val curr = buffer[offset + i]
            val base = i * f
            val step = (curr - prev) / f

            for (j in 0 until f) {
                work[base + j] = (prev + step * j)
            }

            prev = curr
        }

        lastSample = prev
    }

    private fun decimate2x(state: HalfBandState, work: AudioBuffer, currentLen: Int): Int {
        val outLen = currentLen ushr 1
        var outIdx = 0
        var i = 0

        while (i < currentLen) {
            state.push(work[i])
            state.push(work[i + 1])
            work[outIdx] = state.output()
            outIdx++
            i += 2
        }

        return outLen
    }

    private class HalfBandState {
        val delay = DoubleArray(TAPS)
        var pos: Int = 0

        fun push(sample: Double) {
            delay[pos] = sample
            pos++

            if (pos >= TAPS) {
                pos = 0
            }
        }

        fun output(): Double {
            var centerIdx = pos - HALF_LEN - 1

            if (centerIdx < 0) {
                centerIdx += TAPS
            }

            var sum = CENTER_TAP * delay[centerIdx]
            var idxPlus = centerIdx + 1

            if (idxPlus >= TAPS) {
                idxPlus -= TAPS
            }

            var idxMinus = centerIdx - 1

            if (idxMinus < 0) {
                idxMinus += TAPS
            }

            for (k in KERNEL.indices) {
                sum += KERNEL[k] * (delay[idxPlus] + delay[idxMinus])
                idxPlus += 2

                if (idxPlus >= TAPS) {
                    idxPlus -= TAPS
                }

                idxMinus -= 2

                if (idxMinus < 0) {
                    idxMinus += TAPS
                }
            }

            return sum
        }
    }

    companion object {
        private val KERNEL = doubleArrayOf(
            0.33261825699561426,
            -0.11553340575436945,
            0.046063814906802995,
            -0.013148666148047813,
        )
        private const val CENTER_TAP = 0.5
        private const val TAPS = 15
        private const val HALF_LEN = 7
    }
}
