/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.sin

class DuckingSpec : StringSpec({
    val sampleRate = 44100

    /**
     * One block of a mono [input] through the door the cylinder duck calls, [Ducking.processStereo]: the signal on both
     * sides (a copy as the right channel) and [sidechain] as both trigger channels. The linked detector's
     * `max(|L|, |R|)` over two equal channels is the mono level, so [input] ends as the mono render.
     */
    fun Ducking.duckMono(input: AudioBuffer, sidechain: AudioBuffer, blockSize: Int) {
        processStereo(inputL = input, inputR = input.copyOf(), sidechainL = sidechain, sidechainR = sidechain, blockSize = blockSize)
    }

    "Ducking reduces volume when sidechain is active" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            releaseSeconds = 0.01,
            depth = 0.8
        )

        // Create input signal (constant level)
        val input = AudioBuffer(100) { 1.0 }

        // Create sidechain signal (loud trigger)
        val sidechain = AudioBuffer(100) { 0.8 }

        ducking.duckMono(input = input, sidechain = sidechain, blockSize = 100)

        // Input should be reduced significantly
        val avgLevel = input.map { abs(it) }.average()
        avgLevel shouldBeLessThan 0.5
    }

    "Ducking returns to normal when sidechain stops" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            releaseSeconds = 0.001, // Very fast return
            depth = 1.0
        )

        // Process with active sidechain
        val input1 = AudioBuffer(100) { 1.0 }
        val sidechain1 = AudioBuffer(100) { 1.0 }
        ducking.duckMono(input = input1, sidechain = sidechain1, blockSize = 100)

        // Process with silent sidechain (should return to normal)
        val input2 = AudioBuffer(1000) { 1.0 }
        val sidechain2 = AudioBuffer(1000) { 0.0 }
        ducking.duckMono(input = input2, sidechain = sidechain2, blockSize = 1000)

        // Should return close to full volume
        val endLevel = input2.takeLast(100).map { abs(it) }.average()
        endLevel shouldBe (1.0 plusOrMinus 0.1)
    }

    "Depth parameter controls ducking amount" {
        val input1 = AudioBuffer(100) { 1.0 }
        val input2 = AudioBuffer(100) { 1.0 }
        val sidechain = AudioBuffer(100) { 1.0 }

        val duckingLight = Ducking(sampleRate, 0.01, depth = 0.3)
        val duckingHeavy = Ducking(sampleRate, 0.01, depth = 0.9)

        duckingLight.duckMono(input = input1, sidechain = sidechain, blockSize = 100)
        duckingHeavy.duckMono(input = input2, sidechain = sidechain, blockSize = 100)

        val avgLight = input1.map { abs(it) }.average()
        val avgHeavy = input2.map { abs(it) }.average()

        // Heavy ducking should reduce more
        avgHeavy shouldBeLessThan avgLight
    }

    "Reset clears internal state" {
        val ducking = Ducking(sampleRate = sampleRate, releaseSeconds = 0.01, depth = 1.0)

        // Duck the signal
        val input = AudioBuffer(100) { 1.0 }
        val sidechain = AudioBuffer(100) { 1.0 }
        ducking.duckMono(input = input, sidechain = sidechain, blockSize = 100)

        // Reset
        ducking.reset()

        // Should process at full volume immediately
        val input2 = AudioBuffer(10) { 1.0 }
        val sidechain2 = AudioBuffer(10) { 0.0 }
        ducking.duckMono(input = input2, sidechain = sidechain2, blockSize = 10)

        input2[0] shouldBe (1.0 plusOrMinus 0.01)
    }

    "Ducking with zero depth has no effect" {
        val ducking = Ducking(
            sampleRate = sampleRate,
            releaseSeconds = 0.1,
            depth = 0.0
        )

        val input = AudioBuffer(100) { 1.0 }
        val sidechain = AudioBuffer(100) { 1.0 }

        ducking.duckMono(input = input, sidechain = sidechain, blockSize = 100)

        // Should remain at full volume
        val avgLevel = input.map { abs(it) }.average()
        avgLevel shouldBe (1.0 plusOrMinus 0.01)
    }

    "processStereo ducks both channels equally (linked stereo)" {
        val ducking = Ducking(sampleRate = sampleRate, releaseSeconds = 0.01, depth = 0.8)

        val inputLeft = AudioBuffer(100) { 1.0 }
        val inputRight = AudioBuffer(100) { 1.0 }
        val sidechainLeft = AudioBuffer(100) { 0.8 }
        val sidechainRight = AudioBuffer(100) { 0.8 }

        ducking.processStereo(inputL = inputLeft, inputR = inputRight, sidechainL = sidechainLeft, sidechainR = sidechainRight, blockSize = 100)

        val avgLeft = inputLeft.map { abs(it) }.average()
        val avgRight = inputRight.map { abs(it) }.average()

        // Both channels should be ducked
        avgLeft shouldBeLessThan 0.5
        avgRight shouldBeLessThan 0.5

        // Both channels should have identical gain reduction (linked stereo)
        avgLeft shouldBe (avgRight plusOrMinus 0.001)
    }

    "processStereo preserves stereo image with asymmetric sidechain" {
        val ducking = Ducking(sampleRate = sampleRate, releaseSeconds = 0.01, depth = 0.8)

        val inputLeft = AudioBuffer(100) { 1.0 }
        val inputRight = AudioBuffer(100) { 1.0 }
        // Sidechain is louder on left — but linked detection should apply equal ducking
        val sidechainLeft = AudioBuffer(100) { 0.9 }
        val sidechainRight = AudioBuffer(100) { 0.1 }

        ducking.processStereo(inputL = inputLeft, inputR = inputRight, sidechainL = sidechainLeft, sidechainR = sidechainRight, blockSize = 100)

        val avgLeft = inputLeft.map { abs(it) }.average()
        val avgRight = inputRight.map { abs(it) }.average()

        // Both channels should have identical gain reduction despite asymmetric sidechain
        avgLeft shouldBe (avgRight plusOrMinus 0.001)
    }

    "Recovery time affects return speed" {
        // Use separate buffers for active/silent phases, process in-place
        val duckingFast = Ducking(sampleRate, releaseSeconds = 0.001, depth = 0.8)
        val duckingSlow = Ducking(sampleRate, releaseSeconds = 0.1, depth = 0.8)

        // Phase 1: Duck both with active sidechain
        val active1 = AudioBuffer(100) { 1.0 }
        val active2 = AudioBuffer(100) { 1.0 }
        val sidechainActive = AudioBuffer(100) { 1.0 }
        duckingFast.duckMono(input = active1, sidechain = sidechainActive, blockSize = 100)
        duckingSlow.duckMono(input = active2, sidechain = sidechainActive, blockSize = 100)

        // Phase 2: Release with silent sidechain
        val recover1 = AudioBuffer(400) { 1.0 }
        val recover2 = AudioBuffer(400) { 1.0 }
        val sidechainSilent = AudioBuffer(400) { 0.0 }
        duckingFast.duckMono(input = recover1, sidechain = sidechainSilent, blockSize = 400)
        duckingSlow.duckMono(input = recover2, sidechain = sidechainSilent, blockSize = 400)

        // Fast should recover more by sample 50-100 of the recovery phase
        val fastLevel = recover1.sliceArray(50 until 100).map { abs(it) }.average()
        val slowLevel = recover2.sliceArray(50 until 100).map { abs(it) }.average()

        // Fast recovery should be closer to 1.0 than slow recovery
        (fastLevel > slowLevel) shouldBe true
    }

    "applyKnobArgs leaves the instance where the two setters leave it, bit for bit" {
        // Engine follow-up 45: the cylinder duck hands a moving release or depth over through `knobArgs` and one
        // `applyKnobArgs`, where it called the two setters. Both instances hear the same blocks and take the same
        // knobs, one through the setters (release, then depth, the order the stage used), the other through the
        // holder; every stored knob and every output sample must match bit for bit. The steps reach every guard:
        // non-finite knobs (each kept at its stored value), a depth below 0 and above 1 (coerced), a depth of -0.0
        // (kept, `coerceIn` does not touch it), a release under the 1 ms floor and a negative one (the coefficient's
        // own clamp), and a step that keeps both.
        val nan = Double.NaN
        val inf = Double.POSITIVE_INFINITY
        val steps = listOf(
            doubleArrayOf(0.02, 0.6),
            doubleArrayOf(0.011, 0.25),
            doubleArrayOf(nan, 0.75),
            doubleArrayOf(0.047, nan),
            doubleArrayOf(inf, -0.3),
            doubleArrayOf(0.0004, 1.7),
            doubleArrayOf(-0.5, -0.0),
            doubleArrayOf(nan, nan),
            doubleArrayOf(0.3, 0.0),
            doubleArrayOf(0.005, 1.0),
        )
        val block = 64
        val bySetters = Ducking(sampleRate = sampleRate, releaseSeconds = 0.1, depth = 0.5)
        val byHolder = Ducking(sampleRate = sampleRate, releaseSeconds = 0.1, depth = 0.5)

        steps.forEachIndexed { n, knobs ->
            bySetters.releaseSeconds = knobs[0]
            bySetters.depth = knobs[1]

            byHolder.knobArgs[Ducking.KNOB_RELEASE] = knobs[0]
            byHolder.knobArgs[Ducking.KNOB_DEPTH] = knobs[1]
            byHolder.applyKnobArgs()

            withClue("step $n, the stored knobs") {
                byHolder.releaseSeconds.toRawBits() shouldBe bySetters.releaseSeconds.toRawBits()
                byHolder.depth.toRawBits() shouldBe bySetters.depth.toRawBits()
            }

            // A sidechain that hits for 12 samples in every 40, so the instant duck and the release (the
            // coefficient) both act in every block.
            val start = n * block
            val sideL = AudioBuffer(block) { if ((start + it) % 40 < 12) 0.9 * sin(0.3 * (start + it)) else 0.0 }
            val sideR = AudioBuffer(block) { if ((start + it) % 40 < 12) 0.6 * sin(0.2 * (start + it) + 1.0) else 0.0 }
            val l1 = AudioBuffer(block) { 0.7 * sin(0.031 * (start + it)) }
            val r1 = AudioBuffer(block) { 0.5 * sin(0.047 * (start + it) + 0.5) }
            val l2 = l1.copyOf()
            val r2 = r1.copyOf()

            bySetters.processStereo(inputL = l1, inputR = r1, sidechainL = sideL, sidechainR = sideR, blockSize = block)
            byHolder.processStereo(inputL = l2, inputR = r2, sidechainL = sideL, sidechainR = sideR, blockSize = block)

            for (i in 0 until block) {
                withClue("step $n, sample $i") {
                    l2[i].toRawBits() shouldBe l1[i].toRawBits()
                    r2[i].toRawBits() shouldBe r1[i].toRawBits()
                }
            }
        }
    }

    // ── One NaN on the side chain (audit L7b-01) ─────────────────────────────────────────────────────────────────────
    // Through `min(1.0, x)` a NaN level made the target NaN, the release arm STORED it, and the duck's gain stayed NaN
    // for the duck's whole life: the ducked cylinder, and through the shared mix every playback, went silent. Now a NaN
    // level reads as a full duck for that sample, as an infinity does, and the state stays finite.

    /** One block of [frames] through [ducking]'s settled loop with a sidechain of [side] on the LEFT only; the output. */
    fun settledBlock(ducking: Ducking, side: (Int) -> Double, frames: Int = 64): Pair<AudioBuffer, AudioBuffer> {
        val l = AudioBuffer(frames) { 1.0 }
        val r = AudioBuffer(frames) { 1.0 }

        ducking.processStereo(inputL = l, inputR = r, sidechainL = AudioBuffer(frames) { side(it) }, sidechainR = AudioBuffer(frames) { 0.0 }, blockSize = frames)

        return l to r
    }

    "one NaN on the side chain ducks that sample fully, as an infinity does, and the duck does not latch" {
        val poisoned = Ducking(sampleRate = sampleRate, releaseSeconds = 0.01, depth = 0.8)
        val infinite = Ducking(sampleRate = sampleRate, releaseSeconds = 0.01, depth = 0.8)

        val (nanL, nanR) = settledBlock(poisoned, side = { if (it == 0) Double.NaN else 0.0 })
        val (infL, _) = settledBlock(infinite, side = { if (it == 0) Double.POSITIVE_INFINITY else 0.0 })

        withClue("the NaN sample is a full duck, 1 - depth") {
            nanL[0] shouldBe (1.0 - 0.8 plusOrMinus 1e-12)
        }

        withClue("both channels of the block are finite (one shared gain)") {
            nanL.all { it.isFinite() } shouldBe true
            nanR.all { it.isFinite() } shouldBe true
        }

        for (i in nanL.indices) {
            withClue("the NaN reads exactly as an infinity, sample $i") {
                nanL[i].toRawBits() shouldBe infL[i].toRawBits()
            }
        }

        // The next clean block: finite, and the gain recovers toward 1
        val (cleanL, _) = settledBlock(poisoned, side = { 0.0 })

        withClue("the block after: finite, and recovering") {
            cleanL.all { it.isFinite() } shouldBe true
            (cleanL.last() > cleanL.first()) shouldBe true
        }
    }

    "one NaN on the side chain does not latch the gliding loop either" {
        val ducking = Ducking(sampleRate = sampleRate, releaseSeconds = 0.01, depth = 0.8)
        val frames = 64
        val l = AudioBuffer(frames) { 1.0 }
        val r = AudioBuffer(frames) { 1.0 }

        // The switch-off fade: the weight rides from 1 to 0 across the block
        ducking.processStereoGliding(
            inputL = l, inputR = r,
            sidechainL = AudioBuffer(frames) { if (it == 0) Double.NaN else 0.0 }, sidechainR = AudioBuffer(frames) { 0.0 },
            blockSize = frames, depthFrom = 0.8, depthTo = 0.8, weightFrom = 1.0, weightTo = 0.0,
        )

        withClue("the gliding block is finite") {
            l.all { it.isFinite() } shouldBe true
            r.all { it.isFinite() } shouldBe true
        }

        val (cleanL, _) = settledBlock(ducking, side = { 0.0 })

        withClue("the settled block after it is finite") {
            cleanL.all { it.isFinite() } shouldBe true
        }
    }
})
