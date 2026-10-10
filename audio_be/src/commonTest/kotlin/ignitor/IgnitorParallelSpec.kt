/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.roundTrip
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.parallel
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Ignitor `parallel` node (`docs/tasks-archive/2026-10/20261010-parallel-serial-bands.md` step 2): the branches SUMMED, the input
 * built once for all of them, and every branch matched in phase to the others: by phase twins, an unshaped round trip
 * of every oversampler a branch lacks ([BuiltIgnitor.oversamplers], `docs/tasks/in-progress/iir-oversampler.md`), or,
 * when a branch's oversamplers are unknown, by a whole-sample delay to the latest one ([BuiltIgnitor.latencySamples]).
 *
 * **The oracles are separate renders of the parts.** A source without draws renders the same samples in every build,
 * so a branch rendered on its own is the branch inside the sum; what is left after subtracting it is the other
 * branch, and that is compared with the input itself, through bare [Oversampler] round trips or shifted by whole
 * samples.
 */
class IgnitorParallelSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 12
    val noteHz = 220.0

    fun build(dsl: IgnitorDsl): BuiltIgnitor = dsl.buildExciter(random = Random(7), freqHz = noteHz, sampleRate = sampleRate)

    fun render(dsl: IgnitorDsl): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val ignitor = dsl.buildExciter(random = ctx.random, freqHz = noteHz, sampleRate = sampleRate).ignitor
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ignitor.generate(buffer, noteHz, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            ctx.voiceElapsedFrames += blockFrames
        }

        return out
    }

    /** As [render], every block in two windows, `[0, at)` and `[at, 128)`, as a voice renders around an event. */
    fun renderSplit(dsl: IgnitorDsl, at: Int): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        )
        val ignitor = dsl.buildExciter(random = ctx.random, freqHz = noteHz, sampleRate = sampleRate).ignitor
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = at)
            ignitor.generate(buffer, noteHz, ctx)
            ctx.voiceElapsedFrames += at
            ctx.updateOffsetAndLength(offset = at, length = blockFrames - at)
            ignitor.generate(buffer, noteHz, ctx)
            ctx.voiceElapsedFrames += blockFrames - at

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }
        }

        return out
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    val saw = IgnitorDsl.Saw()

    /** An oversampled shaper: changes the signal AND delays it by its oversampler's group delay. */
    fun shaped(inner: IgnitorDsl, factor: Int) = IgnitorDsl.Shape(inner = inner, oversample = c(factor.toDouble()))

    /** [signal] through an unshaped round trip of a bare [Oversampler] per entry of [stages], block by block. */
    fun twin(signal: DoubleArray, vararg stages: Int): DoubleArray {
        val out = signal.copyOf()
        val scratch = ScratchBuffers(blockFrames)

        for (s in stages) {
            val os = Oversampler(s)

            for (b in 0 until blocks) {
                val block = AudioBuffer(blockFrames) { out[b * blockFrames + it] }

                os.roundTrip(buffer = block, offset = 0, length = blockFrames, scratch = scratch) { _, _ -> }
                block.copyInto(destination = out, destinationOffset = b * blockFrames)
            }
        }

        return out
    }

    fun shouldMatch(actual: DoubleArray, expected: (Int) -> Double) {
        for (k in actual.indices) {
            withClue("sample $k") {
                actual[k] shouldBe (expected(k) plusOrMinus 1e-12)
            }
        }
    }

    // ── The sum ─────────────────────────────────────────────────────────────────────────────────

    "the node is the SUM of its branches: x * 0.5 and x * 0.25 side by side are 0.75 of x" {
        val dry = render(saw)
        val out = render(saw.parallel({ it.mul(0.5) }, { it.mul(0.25) }))

        shouldMatch(out) { 0.75 * dry[it] }
    }

    "the input is built ONCE for every branch: a noise in two plain branches is that noise twice" {
        // Two noise instances would draw two different streams from the voice's random; one instance is the same
        // stream in both branches. The noise alone, built with the same seed, is that one stream.
        val noise = IgnitorDsl.WhiteNoise()
        val alone = render(noise)
        val out = render(noise.parallel({ it }, { it }))

        shouldMatch(out) { 2.0 * alone[it] }
    }

    // ── Latency ─────────────────────────────────────────────────────────────────────────────────

    "an oversampled stage reports its exact latency, a series adds up, a gated-off distort reports none" {
        build(saw).latencySamples shouldBe 0.0
        // The IIR half-band's low-frequency delays: 3.07, 4.40, 5.06 samples at 2x, 4x, 8x (OversamplerGroupDelaySpec).
        build(shaped(saw, 2)).latencySamples shouldBe (3.067 plusOrMinus 0.001)
        build(shaped(saw, 4)).latencySamples shouldBe (4.397 plusOrMinus 0.001)
        build(IgnitorDsl.Distort(inner = saw, oversample = c(8.0))).latencySamples shouldBe (5.062 plusOrMinus 0.001)
        build(shaped(shaped(saw, 4), 4)).latencySamples shouldBe (8.795 plusOrMinus 0.002)
        build(IgnitorDsl.Distort(inner = shaped(saw, 2), amount = c(0.0), oversample = c(2.0))).latencySamples shouldBe (3.067 plusOrMinus 0.001)
    }

    "the oversamplers of the spine: listed in series, shared through a plain sum, unknown when a sum mixes two" {
        build(saw).oversamplers shouldBe emptyList()
        build(shaped(shaped(saw, 4), 2)).oversamplers shouldBe listOf(1, 2)
        build(IgnitorDsl.Plus(left = shaped(saw, 2), right = saw)).oversamplers shouldBe listOf(1)
        build(IgnitorDsl.Plus(left = shaped(saw, 2), right = shaped(saw, 2))).oversamplers shouldBe listOf(1)
        build(IgnitorDsl.Plus(left = shaped(saw, 2), right = shaped(saw, 4))).oversamplers shouldBe null
        build(IgnitorDsl.Distort(inner = shaped(saw, 2), amount = c(0.0), oversample = c(2.0))).oversamplers shouldBe listOf(1)
        // An unknown child stays unknown through a later oversampler.
        build(shaped(IgnitorDsl.Plus(left = shaped(saw, 2), right = shaped(saw, 4)), 2)).oversamplers shouldBe null
    }

    "the union a parallel pads to, and what each branch lacks of it" {
        Oversampler.unionOf(listOf(listOf(1), listOf(2, 2), emptyList())) shouldBe listOf(1, 2, 2)
        Oversampler.unionOf(listOf(listOf(1, 1), listOf(1))) shouldBe listOf(1, 1)
        Oversampler.missingFrom(have = listOf(2), union = listOf(1, 2, 2)) shouldBe listOf(1, 2)
        Oversampler.missingFrom(have = listOf(1, 2, 2), union = listOf(1, 2, 2)) shouldBe emptyList()
    }

    "a parallel reports the union it pads to and its delay, a plain sum its later operand" {
        val node = build(saw.parallel({ shaped(it, 2) }, { shaped(it, 4) }, { it }))

        node.oversamplers shouldBe listOf(1, 2)
        // 3.067 + 4.397: every branch now runs through a 2x and a 4x round trip.
        node.latencySamples shouldBe (7.464 plusOrMinus 0.001)
        build(IgnitorDsl.Plus(left = shaped(saw, 2), right = saw)).latencySamples shouldBe (3.067 plusOrMinus 0.001)
    }

    "an unknown branch falls back to the whole-sample pad, rounded once from the exact sum: 8.79 pads by 9, not 8" {
        // The late branch mixes two 4x stages (8.79 late) with a 2x one in a plain sum, so its oversamplers are
        // unknown; the dry branch is delayed to it by 9 samples, the exact sum rounded, not the 8 two rounded stages say.
        val mixed = { x: IgnitorDsl -> IgnitorDsl.Plus(left = shaped(shaped(x, 4), 4), right = shaped(x, 2)) }
        val dry = render(saw)
        val late = render(mixed(saw))
        val sum = render(saw.parallel({ mixed(it) }, { it }))

        shouldMatch(DoubleArray(sum.size) { sum[it] - late[it] }) { k -> if (k < 9) 0.0 else dry[k - 9] }
    }

    "two 4x stages in series against a dry branch: the dry branch gets both twins" {
        val dry = render(saw)
        val late = render(shaped(shaped(saw, 4), 4))
        val sum = render(saw.parallel({ shaped(shaped(it, 4), 4) }, { it }))

        shouldMatch(DoubleArray(sum.size) { sum[it] - late[it] }) { k -> twin(dry, 2, 2)[k] }
    }

    "the pad follows the voice's windows: a block rendered in two windows is the same as in one" {
        // A voice renders a block in windows (`offset`, `length`) when it starts mid-block or a gate falls inside it;
        // the pad's ring advances once per rendered sample, so the split must not move a sample.
        val dsl = saw.parallel({ shaped(it, 2) }, { it })
        val whole = render(dsl)
        val split = renderSplit(dsl, at = 50)

        shouldMatch(split) { whole[it] }
    }

    "a late branch and a dry one meet in phase: what is left after the late branch is the input through a 2x twin" {
        val dry = render(saw)
        val late = render(shaped(saw, 2))
        val sum = render(saw.parallel({ shaped(it, 2) }, { it }))
        val expected = twin(dry, 1)

        shouldMatch(DoubleArray(sum.size) { sum[it] - late[it] }) { expected[it] }
    }

    "the branch order does not matter: the dry branch first is padded the same" {
        val dry = render(saw)
        val late = render(shaped(saw, 2))
        val sum = render(saw.parallel({ it }, { shaped(it, 2) }))
        val expected = twin(dry, 1)

        shouldMatch(DoubleArray(sum.size) { sum[it] - late[it] }) { expected[it] }
    }

    "2x against 4x: each branch gets the twin of the other's oversampler" {
        val twoX = twin(render(shaped(saw, 2)), 2)
        val fourX = twin(render(shaped(saw, 4)), 1)
        val sum = render(saw.parallel({ shaped(it, 2) }, { shaped(it, 4) }))

        shouldMatch(sum) { twoX[it] + fourX[it] }
    }

    "a clean sum stays flat at the top: an oversampled branch and a dry one, equal in level, are twice the dry" {
        // A hard clip under 1 and the soft cap under 0.95 pass a 0.4 sine untouched, so the shaped branch is the
        // oversampler's round trip and its DC blocker (flat at the top). A whole-sample pad left a notch here, at the
        // frequency where the round trip's extra top delay reaches half a period: -28.5 dB at 18.25 kHz (2x), -31.5
        // dB at 16.75 kHz (4x), -38 dB at 18 kHz (8x). Every frequency repeats in 192 samples, so the RMS from sample
        // 768 on spans whole periods.
        val hard = c(DistortionShapes.indexOf("hard"))

        fun rms(signal: DoubleArray): Double = sqrt((768 until signal.size).sumOf { signal[it] * signal[it] } / (signal.size - 768))

        for ((factors, freq) in listOf(listOf(2) to 18250.0, listOf(4) to 16750.0, listOf(8) to 18000.0, listOf(2, 4) to 18000.0)) {
            val sine = IgnitorDsl.Sine(freq = c(freq), analog = c(0.0)).mul(0.4)
            val shapedBranches = factors.map { IgnitorDsl.Shape(inner = sine, shape = hard, oversample = c(it.toDouble())) }
            val dry = render(sine)
            val sum = render(IgnitorDsl.Parallel(branches = shapedBranches + sine))

            withClue("factors $factors at $freq Hz") {
                rms(sum) / rms(dry) shouldBe ((factors.size + 1).toDouble() plusOrMinus 0.01)
            }
        }
    }

    "control: a plain sum does NOT align, so the same subtraction leaves the input on time" {
        // Guards the alignment rows against a shaper that stopped being late: then they would pass with no padding.
        val dry = render(saw)
        val late = render(shaped(saw, 2))
        val sum = render(IgnitorDsl.Plus(left = shaped(saw, 2), right = saw))

        shouldMatch(DoubleArray(sum.size) { sum[it] - late[it] }) { dry[it] }

        var differs = 0.0

        for (k in 4 until dry.size) {
            differs = maxOf(differs, abs(dry[k] - dry[k - 4]))
        }

        differs shouldBeGreaterThan 0.01
    }
})
