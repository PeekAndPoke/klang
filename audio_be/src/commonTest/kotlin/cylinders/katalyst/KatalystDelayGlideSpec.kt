/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sin

/**
 * The orbit delay's `feedback` and `wet` GLIDE (`docs/plans/knob-glide.md`, Katalyst step 5b-2):
 * the feedback per block (a COEFFICIENT), the wet per sample (a LEVEL). The oracles are independent
 * of the code under test: a bare [DelayLine] configured BY HAND with the straight-line values and
 * fed a feed computed by hand, and the drain countdown's formula evaluated by hand.
 *
 * 44.1 kHz and 128 frames throughout, so one glide is 17 blocks.
 */
class KatalystDelayGlideSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val glideBlocks = 17

    fun ctx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
    )

    fun effect() = KatalystDelayEffect(
        delayLine = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate),
        blockFrames = blockFrames,
    )

    fun bare(feedback: Double) = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate, time = 0.02, feedback = feedback)

    /** Two partials: the ring holds a signal whose every cell differs. */
    fun input(block: Int, i: Int): Double {
        val n = (block * blockFrames + i).toDouble()

        return 0.3 * sin(2.0 * PI * 440.0 * n / sampleRate) + 0.2 * sin(2.0 * PI * 97.0 * n / sampleRate)
    }

    /**
     * One block of the stage (fed the orbit mix, which holds the input) against one block of the bare
     * line (fed [feedAt] per sample, into an output that starts from the same input, since the stage
     * is an insert and `DelayLine.process` adds). Returns the largest difference.
     */
    fun block(
        effect: KatalystDelayEffect,
        c: KatalystContext,
        reference: DelayLine,
        block: Int,
        feedAt: (Int) -> Double,
    ): Double {
        val refIn = StereoBuffer(blockFrames)
        val refOut = StereoBuffer(blockFrames)

        for (i in 0 until blockFrames) {
            val x = input(block, i)

            c.mixBuffer.left[i] = x
            c.mixBuffer.right[i] = x
            refOut.left[i] = x
            refOut.right[i] = x
            refIn.left[i] = x * feedAt(i)
            refIn.right[i] = x * feedAt(i)
        }

        effect.process(c)
        reference.process(refIn, refOut, blockFrames)

        var worst = 0.0

        for (i in 0 until blockFrames) {
            worst = maxOf(worst, abs(c.mixBuffer.left[i] - refOut.left[i]))
        }

        return worst
    }

    "a feedback change glides per block along the straight line, and lands" {
        val fx = effect()
        val c = ctx()
        val reference = bare(feedback = 0.3)

        // Settled first: a steady delay is the bare line, bit for bit.
        var settled = 0.0

        for (b in 0 until 30) {
            fx.configure(time = 0.02, feedback = 0.3, cap = 1.0, wet = 1.0)
            settled = maxOf(settled, block(fx, c, reference, b) { 1.0 })
        }

        settled shouldBe 0.0

        // The owner re-applies the new feedback every block, as the chain does.
        var gliding = 0.0

        for (k in 1..glideBlocks + 20) {
            fx.configure(time = 0.02, feedback = 0.8, cap = 1.0, wet = 1.0)
            reference.feedback = if (k >= glideBlocks) 0.8 else 0.3 + 0.5 * k / glideBlocks
            gliding = maxOf(gliding, block(fx, c, reference, 29 + k) { 1.0 })
        }

        (gliding <= 1e-12) shouldBe true
        fx.delayLine!!.feedback shouldBe 0.8

        // The control, from the bare line alone: the same input with the feedback JUMPING differs.
        val glideRef = bare(0.3)
        val jumpRef = bare(0.3)
        var gap = 0.0

        for (b in 0 until 30 + glideBlocks + 20) {
            val k = b - 29

            if (k >= 1) {
                glideRef.feedback = if (k >= glideBlocks) 0.8 else 0.3 + 0.5 * k / glideBlocks
                jumpRef.feedback = 0.8
            }

            val inBuf = StereoBuffer(blockFrames)
            val g = StereoBuffer(blockFrames)
            val j = StereoBuffer(blockFrames)

            for (i in 0 until blockFrames) {
                inBuf.left[i] = input(b, i)
                inBuf.right[i] = input(b, i)
            }

            glideRef.process(inBuf, g, blockFrames)
            jumpRef.process(inBuf, j, blockFrames)

            for (i in 0 until blockFrames) {
                gap = maxOf(gap, abs(g.left[i] - j.left[i]))
            }
        }

        (gap > 1e-3) shouldBe true
    }

    "a wet change glides per SAMPLE into the feed along the straight line, and lands" {
        val fx = effect()
        val c = ctx()
        val reference = bare(feedback = 0.4)

        for (b in 0 until 20) {
            fx.configure(time = 0.02, feedback = 0.4, cap = 1.0, wet = 0.2)
            block(fx, c, reference, b) { 0.2 } shouldBe 0.0
        }

        // The straight line from 0.2 to 0.9 over the glide's 17 * 128 samples, by hand.
        val total = glideBlocks * blockFrames
        var gliding = 0.0

        for (k in 1..glideBlocks + 20) {
            fx.configure(time = 0.02, feedback = 0.4, cap = 1.0, wet = 0.9)
            gliding = maxOf(
                gliding,
                block(fx, c, reference, 19 + k) { i ->
                    val j = (k - 1) * blockFrames + i + 1

                    if (j >= total) 0.9 else 0.2 + 0.7 * j / total
                },
            )
        }

        (gliding <= 1e-12) shouldBe true
    }

    "an off-config during a RISING feedback glide drains for the feedback the glide is heading to" {
        val fx = effect()
        val c = ctx()

        fx.configure(time = 0.02, feedback = 0.2, cap = 1.0, wet = 1.0)
        c.mixBuffer.left[0] = 1.0
        c.mixBuffer.right[0] = 1.0
        fx.process(c)

        repeat(2) {
            fx.configure(time = 0.02, feedback = 0.9, cap = 1.0, wet = 1.0)
            c.mixBuffer.clear()
            fx.process(c)
        }

        val peak = fx.delayLine!!.tapWindowPeakAbs()

        fx.configure(time = 0.0, feedback = 0.0, cap = 1.0, wet = 1.0)

        // The countdown by hand, from the drain's formula: periods of the 882-sample tap, plus the
        // spare one, at the LARGER magnitude of the feedback in force and the one it glides to.
        fun countdown(fb: Double): Double = (ceil(ln(1e-5 / peak) / ln(fb)) + 1.0) * 0.02 * sampleRate

        val inForce = 0.2 + 0.7 * 2 / glideBlocks
        val offBlock = ceil(countdown(0.9) / blockFrames).toInt()

        (countdown(0.9) > countdown(inForce)) shouldBe true

        repeat(offBlock - 1) {
            c.mixBuffer.clear()
            fx.process(c)
        }

        withClue("still draining one block before the target's countdown ends") { fx.hasTail() shouldBe true }

        c.mixBuffer.clear()
        fx.process(c)

        fx.hasTail() shouldBe false
    }

    "an off-config during a FALLING feedback glide drains for the feedback still in force" {
        // The other direction, guarded on its own (pilot log entry 6): with a rising-only row the
        // mutant "count down from the target" passes.
        val fx = effect()
        val c = ctx()

        fx.configure(time = 0.02, feedback = 0.9, cap = 1.0, wet = 1.0)
        c.mixBuffer.left[0] = 1.0
        c.mixBuffer.right[0] = 1.0
        fx.process(c)

        repeat(2) {
            fx.configure(time = 0.02, feedback = 0.2, cap = 1.0, wet = 1.0)
            c.mixBuffer.clear()
            fx.process(c)
        }

        val peak = fx.delayLine!!.tapWindowPeakAbs()

        fx.configure(time = 0.0, feedback = 0.0, cap = 1.0, wet = 1.0)

        val inForce = 0.9 + (0.2 - 0.9) * 2 / glideBlocks
        val countdown = (ceil(ln(1e-5 / peak) / ln(inForce)) + 1.0) * 0.02 * sampleRate
        val offBlock = ceil(countdown / blockFrames).toInt()

        repeat(offBlock - 1) {
            c.mixBuffer.clear()
            fx.process(c)
        }

        withClue("still draining one block before the in-force countdown ends") { fx.hasTail() shouldBe true }

        c.mixBuffer.clear()
        fx.process(c)

        fx.hasTail() shouldBe false
    }

    "reset mid-glide forgets BOTH glides: the next life's feedback and wet are in force at once" {
        val fx = effect()
        val c = ctx()

        fx.configure(time = 0.02, feedback = 0.2, cap = 1.0, wet = 0.2)

        for (b in 0 until 3) {
            for (i in 0 until blockFrames) {
                c.mixBuffer.left[i] = input(b, i)
                c.mixBuffer.right[i] = input(b, i)
            }

            fx.process(c)
        }

        repeat(3) { b ->
            fx.configure(time = 0.02, feedback = 0.9, cap = 1.0, wet = 0.9)

            for (i in 0 until blockFrames) {
                c.mixBuffer.left[i] = input(3 + b, i)
                c.mixBuffer.right[i] = input(3 + b, i)
            }

            fx.process(c)
        }

        fx.reset()

        // The next life: its own numbers, from the first block on. The oracle is a bare line that
        // never knew another setting, fed the same input times the new wet.
        val reference = bare(feedback = 0.5)

        for (b in 0 until 20) {
            fx.configure(time = 0.02, feedback = 0.5, cap = 1.0, wet = 0.6)

            withClue("block $b") {
                block(fx, c, reference, 100 + b) { 0.6 } shouldBe 0.0
            }
        }
    }

    /**
     * A 1 s echo fed loud for 0.2 s, then silence, up to 1.1 s: the 1 s tap now reads the loud
     * part, while the ring's recent samples hold nothing (feedback 0, so the ring holds only the
     * input). Then the time is shortened to 0.01 s, which starts a 50 ms crossfade away from the
     * loud 1 s tap.
     */
    fun chargedLongEcho(): Pair<KatalystDelayEffect, KatalystContext> {
        val fx = effect()
        val c = ctx()
        val loudBlocks = (0.2 * sampleRate / blockFrames).toInt()
        val totalBlocks = (1.1 * sampleRate / blockFrames).toInt()

        for (b in 0 until totalBlocks) {
            fx.configure(time = 1.0, feedback = 0.0, cap = 1.0, wet = 1.0)

            for (i in 0 until blockFrames) {
                val x = if (b < loudBlocks) input(b, i) else 0.0

                c.mixBuffer.left[i] = x
                c.mixBuffer.right[i] = x
            }

            fx.process(c)
        }

        return fx to c
    }

    /** Blocks the crossfade still sounds the old tap in: 2205 samples, the last block excluded. */
    val fadeBlocks = 2205 / blockFrames

    "an off-config around a tap crossfade drains for the tap still sounding, not the shorter new one" {
        // `DelayLine.reachSamples` in the scan and in the countdown: switched off in the same block
        // as the time change (the fade has not started, the 1 s tap is still in force) and one
        // block later (the fade runs, the 1 s tap is the one it leaves). A countdown from the new
        // 0.01 s tap ends after two of its periods, seven blocks, and resets the ring under a fade
        // that still sounds the old tap for seventeen.
        for (offAfterBlocks in listOf(0, 1)) {
            withClue("off $offAfterBlocks block(s) after the time change") {
                val (fx, c) = chargedLongEcho()

                fx.configure(time = 0.01, feedback = 0.0, cap = 1.0, wet = 1.0)

                repeat(offAfterBlocks) {
                    c.mixBuffer.clear()
                    fx.process(c)
                }

                fx.configure(time = 0.0, feedback = 0.0, cap = 1.0, wet = 1.0)

                withClue("the orbit is told there is a tail") { fx.hasTail() shouldBe true }

                for (b in offAfterBlocks until fadeBlocks - 1) {
                    c.mixBuffer.clear()
                    fx.process(c)

                    withClue("the old tap is still heard fading out, block $b of the fade") {
                        c.mixBuffer.left.any { abs(it) > 1e-3 } shouldBe true
                    }
                }
            }
        }
    }

    "a running delay mid-crossfade reports the tail of the tap still sounding" {
        // `DelayLine.reachSamples` in the ceiling's window: the stage stays ON, the input is
        // silent, and the orbit asks for a tail every block of the fade. A window cut to the new
        // 0.01 s tap closes over silence at once and answers "no tail" while the 1 s tap sounds.
        val (fx, c) = chargedLongEcho()

        for (b in 0 until fadeBlocks - 1) {
            fx.configure(time = 0.01, feedback = 0.0, cap = 1.0, wet = 1.0)
            c.mixBuffer.clear()
            fx.process(c)

            withClue("block $b of the fade: heard, and reported") {
                c.mixBuffer.left.any { abs(it) > 1e-3 } shouldBe true
                fx.hasTail() shouldBe true
            }
        }
    }
})
