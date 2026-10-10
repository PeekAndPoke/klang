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
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.parallel
import kotlin.math.abs
import kotlin.random.Random

/**
 * The Ignitor `parallel` node (`docs/tasks/in-progress/parallel-serial-bands.md` step 2): the branches SUMMED, the input
 * built once for all of them, and every earlier branch delayed to the latest one ([BuiltIgnitor.latencySamples]).
 *
 * **The oracles are separate renders of the parts.** A source without draws renders the same samples in every build,
 * so a branch rendered on its own is the branch inside the sum; what is left after subtracting it is the other
 * branch, and that is compared with the input itself, shifted by the expected latency.
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
        build(shaped(saw, 2)).latencySamples shouldBe 4.0
        build(shaped(saw, 4)).latencySamples shouldBe 5.5
        build(IgnitorDsl.Distort(inner = saw, oversample = c(8.0))).latencySamples shouldBe 6.25
        build(shaped(shaped(saw, 4), 4)).latencySamples shouldBe 11.0
        build(IgnitorDsl.Distort(inner = shaped(saw, 2), amount = c(0.0), oversample = c(2.0))).latencySamples shouldBe 4.0
    }

    "a parallel reports its latest branch, and a plain sum reports its later operand" {
        build(saw.parallel({ shaped(it, 2) }, { shaped(it, 4) }, { it })).latencySamples shouldBe 5.5
        build(IgnitorDsl.Plus(left = shaped(saw, 2), right = saw)).latencySamples shouldBe 4.0
    }

    "the pad is rounded once, from the exact sum: two 4x stages against a dry branch pad it by 11, not 12" {
        val dry = render(saw)
        val late = render(shaped(shaped(saw, 4), 4))
        val sum = render(saw.parallel({ shaped(shaped(it, 4), 4) }, { it }))

        shouldMatch(DoubleArray(sum.size) { sum[it] - late[it] }) { k -> if (k < 11) 0.0 else dry[k - 11] }
    }

    "the pad follows the voice's windows: a block rendered in two windows is the same as in one" {
        // A voice renders a block in windows (`offset`, `length`) when it starts mid-block or a gate falls inside it;
        // the pad's ring advances once per rendered sample, so the split must not move a sample.
        val dsl = saw.parallel({ shaped(it, 2) }, { it })
        val whole = render(dsl)
        val split = renderSplit(dsl, at = 50)

        shouldMatch(split) { whole[it] }
    }

    "a late branch and a dry one meet aligned: what is left after the late branch is the input, 4 samples late" {
        val dry = render(saw)
        val late = render(shaped(saw, 2))
        val sum = render(saw.parallel({ shaped(it, 2) }, { it }))

        shouldMatch(DoubleArray(sum.size) { sum[it] - late[it] }) { k -> if (k < 4) 0.0 else dry[k - 4] }
    }

    "the branch order does not matter: the dry branch first is padded the same" {
        val dry = render(saw)
        val late = render(shaped(saw, 2))
        val sum = render(saw.parallel({ it }, { shaped(it, 2) }))

        shouldMatch(DoubleArray(sum.size) { sum[it] - late[it] }) { k -> if (k < 4) 0.0 else dry[k - 4] }
    }

    "two late branches are padded to the later one: 2x against 4x, the 2x branch 2 samples later" {
        val twoX = render(shaped(saw, 2))
        val fourX = render(shaped(saw, 4))
        val sum = render(saw.parallel({ shaped(it, 2) }, { shaped(it, 4) }))

        shouldMatch(DoubleArray(sum.size) { sum[it] - fourX[it] }) { k -> if (k < 2) 0.0 else twoX[k - 2] }
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
