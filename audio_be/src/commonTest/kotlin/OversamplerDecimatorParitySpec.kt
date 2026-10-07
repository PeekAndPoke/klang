/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.ArrayIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ConstantIgnitor
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.fusedDistort
import io.peekandpoke.klang.audio_be.ignitor.shape
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
 *
 * The last rows pin the two production callers of the oversampler, the `Shape` node (`ShapeIgnitor`) and the
 * fused `Distort` node (`DistortionCore`), through their nodes against the same ring oracle (engine tidy-up step 2,
 * audit B4.1: the callers run their shaping loop between `Oversampler.upsample` and `Oversampler.decimate`). The
 * windows are ragged inside a 128-frame block, as a voice sees them (a note starting mid-block, an empty window),
 * and the source carries NaN samples, so the callers' offsets, loop bounds and NaN guards are part of the pin.
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

                fast.roundTrip(buffer = a, offset = offset, length = length, scratch = scratch) { w, count ->
                    for (i in 0 until count) {
                        w[i] = ShapingFuncs.hardClip(w[i] * 1.7)
                    }
                }
                ring.process(buffer = b, offset = offset, length = length, scratchBuffers = scratch) { w, count ->
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

    // ── The two production callers, through their nodes ─────────────────────────────────────────────

    /** (offset, length) inside one 128-frame block per call: full blocks, mid-block starts, short and empty windows. */
    val windows = listOf(
        0 to 128, 37 to 91, 0 to 1, 64 to 0, 100 to 28, 0 to 13, 5 to 7, 64 to 64, 3 to 125, 0 to 128, 90 to 38, 0 to 128,
    )
    val total = windows.sumOf { it.second }
    val source = DoubleArray(total) { i -> if (i == 200 || i == 418) Double.NaN else signal(i) }

    /** [node] rendered one window per `generate`, at the window's offset in the block, the windows concatenated. */
    fun renderNode(node: Ignitor): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = 48000,
            voiceDurationFrames = total,
            gateEndFrame = total,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(total)
        var at = 0

        for ((offset, length) in windows) {
            ctx.voiceElapsedFrames = at
            ctx.updateOffsetAndLength(offset, length)
            node.generate(buffer, 220.0, ctx)

            for (i in 0 until length) {
                out[at + i] = buffer[offset + i]
            }

            at += length
        }

        return out
    }

    /**
     * The callers' laws on the ring oracle. [drive] null is the `Shape` node: the shape, the DC blocker, the soft cap.
     * Otherwise the fused `Distort` node: the shape of `x * drive` inside the oversampler, the DC blocker, no cap.
     */
    fun oracle(stages: Int, shape: DistortionShape, drive: Double?): DoubleArray {
        val ring = RingOversampler(stages)
        val dc = LowPassHighPassFilters.DcBlocker()
        val scratch = ScratchBuffers(blockFrames)
        val work = AudioBuffer(blockFrames)
        val out = DoubleArray(total)
        var at = 0

        for ((offset, length) in windows) {
            for (i in 0 until length) {
                work[offset + i] = source[at + i]
            }

            ring.process(buffer = work, offset = offset, length = length, scratchBuffers = scratch) { w, count ->
                for (i in 0 until count) {
                    w[i] = if (drive == null) {
                        applyDistortionShape(shape, w[i]).nanGuard()
                    } else {
                        applyDistortionShape(shape, w[i] * drive).nanGuard()
                    }
                }
            }

            dc.process(work, offset, length)

            for (i in 0 until length) {
                out[at + i] = if (drive == null) ShapingFuncs.softCap(work[offset + i]) else work[offset + i]
            }

            at += length
        }

        return out
    }

    fun sameBits(actual: DoubleArray, expected: DoubleArray, clue: String) {
        for (i in 0 until total) {
            withClue("$clue, sample $i") {
                actual[i].equals(expected[i]) shouldBe true
            }
        }
    }

    for (stages in 1..4) {
        "stages $stages: the Shape node renders every shape, oversampled, bit for bit as the ring form" {
            for (shape in DistortionShape.entries) {
                val node = ArrayIgnitor(source).shape(shape, stages)

                sameBits(renderNode(node), oracle(stages, shape, drive = null), "$shape")
            }
        }

        "stages $stages: the fused Distort node renders every shape, oversampled, bit for bit as the ring form" {
            // 0.6 drives; -0.2 is a modulated-style amount at or below 0, which runs at unity drive (never a bypass).
            for (shape in DistortionShape.entries) {
                for ((amount, drive) in listOf(0.6 to DistortionCore.drive(0.6), -0.2 to 1.0)) {
                    val node = ArrayIgnitor(source).fusedDistort(ConstantIgnitor(amount), shape, stages)

                    sameBits(renderNode(node), oracle(stages, shape, drive), "$shape at $amount")
                }
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
