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
import io.peekandpoke.klang.audio_be.utils.nanGuard
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

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
 * The next rows pin the two production nodes over the oversampler, the `Shape` node (`ShapeIgnitor`) and the
 * fused `Distort` node (`FusedDistortIgnitor`), through their nodes against the same ring oracle (engine tidy-up step 2,
 * audit B4.1: the shaping loop runs between `Oversampler.upsample` and `Oversampler.decimate`). Both nodes render
 * through one `DistortionCore` since step 11 (audit B2.2), the `Shape` node at drive 1.0 with its own soft cap. The
 * windows are ragged inside a 128-frame block, as a voice sees them (a note starting mid-block, an empty window),
 * and the source carries NaN samples, so the offsets, loop bounds and NaN guards are part of the pin. The next rows
 * pin stage 0, the plain path, on a hostile source (NaN, both infinities, 1e300, a denormal, -0.0); the last pins the
 * core's ramped shape table against its constant one.
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
            random = testRandom,
        )
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(total)
        var at = 0

        for ((offset, length) in windows) {
            ctx.voiceElapsedFrames = at
            ctx.updateOffsetAndLength(offset = offset, length = length)
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

            dc.process(buffer = work, offset = offset, length = length)

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

                sameBits(actual = renderNode(node), expected = oracle(stages, shape, drive = null), clue = "$shape")
            }
        }

        "stages $stages: the fused Distort node renders every shape, oversampled, bit for bit as the ring form" {
            // 0.6 drives; -0.2 is a modulated-style amount at or below 0, which runs at unity drive (never a bypass).
            for (shape in DistortionShape.entries) {
                for ((amount, drive) in listOf(0.6 to DistortionCore.drive(0.6), -0.2 to 1.0)) {
                    val node = ArrayIgnitor(source).fusedDistort(ConstantIgnitor(amount), shape, stages)

                    sameBits(actual = renderNode(node), expected = oracle(stages, shape, drive), clue = "$shape at $amount")
                }
            }
        }
    }

    // ── Stage 0, the plain path, on a hostile source (engine tidy-up step 11, audit B2.2) ─────────────

    /** The source above with a hostile sample every few frames: NaN, both infinities, 1e300, a denormal, -0.0. */
    val hostileValues = doubleArrayOf(
        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e300, -1e300, 4.9e-324, -0.0,
    )
    val hostile = DoubleArray(total) { i -> if (i % 5 == 2) hostileValues[(i / 5) % hostileValues.size] else source[i] }

    /** [node] over the hostile source, one window per `generate`, as [renderNode] does for the clean one. */
    fun renderHostile(build: (Ignitor) -> Ignitor): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = 48000,
            voiceDurationFrames = total,
            gateEndFrame = total,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = testRandom,
        )
        val node = build(ArrayIgnitor(hostile))
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(total)
        var at = 0

        for ((offset, length) in windows) {
            ctx.voiceElapsedFrames = at
            ctx.updateOffsetAndLength(offset = offset, length = length)
            node.generate(buffer, 220.0, ctx)

            for (i in 0 until length) {
                out[at + i] = buffer[offset + i]
            }

            at += length
        }

        return out
    }

    /**
     * The stage-0 law written out: the shape of `x * drive`, NaN-guarded, the DC blocker, then the soft cap for the
     * `Shape` node ([drive] null, no multiply at all) and no cap for the fused `Distort` node.
     */
    fun plainOracle(shape: DistortionShape, drive: Double?): DoubleArray {
        val dc = LowPassHighPassFilters.DcBlocker()
        val work = AudioBuffer(blockFrames)
        val out = DoubleArray(total)
        var at = 0

        for ((offset, length) in windows) {
            for (i in 0 until length) {
                val x = hostile[at + i]

                work[offset + i] = if (drive == null) {
                    applyDistortionShape(shape, x).nanGuard()
                } else {
                    applyDistortionShape(shape, x * drive).nanGuard()
                }
            }

            dc.process(buffer = work, offset = offset, length = length)

            for (i in 0 until length) {
                out[at + i] = if (drive == null) ShapingFuncs.softCap(work[offset + i]) else work[offset + i]
            }

            at += length
        }

        return out
    }

    "stage 0: the Shape node renders every shape on a hostile source bit for bit as the plain law" {
        for (shape in DistortionShape.entries) {
            sameBits(
                actual = renderHostile { it.shape(shape, 0) },
                expected = plainOracle(shape, drive = null),
                clue = "$shape",
            )
        }
    }

    "stage 0: the fused Distort node renders every shape on a hostile source bit for bit as the plain law" {
        for (shape in DistortionShape.entries) {
            for ((amount, drive) in listOf(0.6 to DistortionCore.drive(0.6), -0.2 to 1.0)) {
                sameBits(
                    actual = renderHostile { it.fusedDistort(ConstantIgnitor(amount), shape, 0) },
                    expected = plainOracle(shape, drive),
                    clue = "$shape at $amount",
                )
            }
        }
    }

    // ── The ramped shaping table (engine follow-up item 9) ──────────────────────────────────────────

    /**
     * `DistortionCore` writes its shape table twice, once per loop form (one loop per shape, see its `shapeRun`).
     * The rows above pin the constant-drive table against [applyDistortionShape]; this pins the ramped one against
     * it: a ramp from a drive to the same drive steps by exactly 0, so every sample is driven by exactly that drive,
     * and `processRamped` must render `process`'s bits, shape by shape, on the plain path and oversampled.
     */
    "processRamped at a constant drive renders process's bits for every shape, stages 0 to 4" {
        val drive = DistortionCore.drive(0.5)

        for (stages in 0..4) {
            for (shape in DistortionShape.entries) {
                val constant = DistortionCore(shape = shape, oversampleStages = stages)
                val ramped = DistortionCore(shape = shape, oversampleStages = stages)
                val scratch = ScratchBuffers(blockFrames)
                val a = AudioBuffer(blockFrames)
                val b = AudioBuffer(blockFrames)
                val outA = DoubleArray(total)
                val outB = DoubleArray(total)
                var at = 0

                for ((offset, length) in windows) {
                    for (i in 0 until length) {
                        a[offset + i] = hostile[at + i]
                        b[offset + i] = hostile[at + i]
                    }

                    constant.process(buffer = a, offset = offset, length = length, drive = drive, scratchBuffers = scratch)
                    ramped.processRamped(
                        buffer = b, offset = offset, length = length, driveFrom = drive, driveTo = drive, scratchBuffers = scratch,
                    )

                    for (i in 0 until length) {
                        outA[at + i] = a[offset + i]
                        outB[at + i] = b[offset + i]
                    }

                    at += length
                }

                sameBits(actual = outB, expected = outA, clue = "$shape at stages $stages")
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
            upsample(buffer = buffer, offset = offset, length = length, work = work)
            transformBlock(work, oversampledLen)

            var currentLen = oversampledLen

            for (stage in 0 until stages) {
                currentLen = decimate2x(decimators[stage], work, currentLen)
            }

            work.copyInto(destination = buffer, destinationOffset = offset, startIndex = 0, endIndex = length)
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
