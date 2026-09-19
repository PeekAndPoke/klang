/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sin

/**
 * The orbit reverb's size GLIDES (`docs/plans/knob-glide.md`, the pilot). The oracles
 * are independent of the code under test: the per-block values of a linear glide written out by
 * hand and handed to a bare [Reverb], the Freeverb feedback law `size * 0.28 + 0.7`, and the drain
 * countdown's formula evaluated by hand.
 *
 * 44.1 kHz and 128 frames throughout, so one glide is 17 blocks (2205 samples, 17.2 blocks,
 * rounded), and the longest comb revolution is 1617 + 23 = 1640 samples.
 */
class KatalystReverbGlideSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val glideBlocks = 17

    fun ctx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    /** Two partials, not a DC fill: the combs hold a signal whose every cell differs. */
    fun fillInput(buffer: StereoBuffer, block: Int) {
        for (i in 0 until blockFrames) {
            val n = (block * blockFrames + i).toDouble()

            buffer.left[i] = 0.3 * sin(2.0 * PI * 440.0 * n / sampleRate) + 0.2 * sin(2.0 * PI * 97.0 * n / sampleRate)
            buffer.right[i] = 0.3 * sin(2.0 * PI * 330.0 * n / sampleRate) + 0.2 * sin(2.0 * PI * 61.0 * n / sampleRate)
        }
    }

    /**
     * Drives the orbit stage and a bare [Reverb] on the same input for [blocks] blocks. [configure]
     * is the owner's call, made every block like `KatalystChain.applyParams` makes it; [reference]
     * sets the bare network by hand before its block. Returns the largest sample difference.
     */
    fun drive(
        effect: KatalystReverbEffect,
        effectCtx: KatalystContext,
        reference: Reverb,
        referenceIn: StereoBuffer,
        referenceOut: StereoBuffer,
        firstBlock: Int,
        blocks: Int,
        configure: (Int) -> Unit,
        setReference: (Int) -> Unit,
        peak: DoubleArray,
    ): Double {
        var worst = 0.0

        for (k in 1..blocks) {
            val block = firstBlock + k - 1

            configure(k)
            setReference(k)

            fillInput(effectCtx.reverbSendBuffer, block)
            fillInput(referenceIn, block)
            effectCtx.mixBuffer.clear()
            referenceOut.clear()

            effect.process(effectCtx)
            reference.process(referenceIn, referenceOut, blockFrames)

            for (i in 0 until blockFrames) {
                worst = maxOf(worst, abs(effectCtx.mixBuffer.left[i] - referenceOut.left[i]))
                worst = maxOf(worst, abs(effectCtx.mixBuffer.right[i] - referenceOut.right[i]))
                peak[0] = maxOf(peak[0], abs(effectCtx.mixBuffer.left[i]))
            }
        }

        return worst
    }

    "a size change on a running orbit reaches the new feedback after the glide, and not before" {
        val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
        val c = ctx()

        repeat(30) { block ->
            effect.configure(size = 0.2, lowpass = null)
            fillInput(c.reverbSendBuffer, block)
            c.mixBuffer.clear()
            effect.process(c)
        }

        val unit = effect.reverb!!

        // The owner re-applies its settings every block, as the chain does in production: the
        // same target again must not restart the glide. (Between a configure and the next block the
        // unit holds the configured value, HEAD's write; the glide overwrites it before the block
        // processes, so the value checked is the one each block RUNS at.)
        var previous = 0.2

        for (k in 1..glideBlocks) {
            effect.configure(size = 0.8, lowpass = null)
            fillInput(c.reverbSendBuffer, 30 + k)
            c.mixBuffer.clear()
            effect.process(c)

            withClue("block $k") {
                if (k < glideBlocks) {
                    (unit.size > previous && unit.size < 0.8) shouldBe true
                } else {
                    unit.size.toRawBits() shouldBe 0.8.toRawBits()
                }
            }

            previous = unit.size
        }

        // The Freeverb law by hand: 0.8 * 0.28 + 0.7.
        (abs(unit.tailFeedback - 0.924) <= 1e-12) shouldBe true
    }

    "the gliding room is the network a hand-set linear glide of the size produces" {
        val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
        val effectCtx = ctx()
        val reference = Reverb(sampleRate)
        val refIn = StereoBuffer(blockFrames)
        val refOut = StereoBuffer(blockFrames)
        val peak = DoubleArray(1)

        reference.size = 0.2

        val settled = drive(
            effect, effectCtx, reference, refIn, refOut, firstBlock = 0, blocks = 30,
            configure = { effect.configure(size = 0.2, lowpass = null) },
            setReference = {},
            peak = peak,
        )

        // A steady room is the bare network, bit for bit.
        settled shouldBe 0.0

        // The glide and 30 blocks after it, against the straight line by hand.
        val gliding = drive(
            effect, effectCtx, reference, refIn, refOut, firstBlock = 30, blocks = glideBlocks + 30,
            configure = { effect.configure(size = 0.8, lowpass = null) },
            setReference = { k -> reference.size = if (k >= glideBlocks) 0.8 else 0.2 + 0.6 * k / glideBlocks },
            peak = peak,
        )

        (gliding <= 1e-12) shouldBe true
        (peak[0] > 1e-3) shouldBe true

        // The control: the hand-set glide against the same bare network JUMPING to the new room,
        // on the same input. If this gap were small, the identity above would prove nothing.
        val glideRef = Reverb(sampleRate)
        val jumpRef = Reverb(sampleRate)
        val glideOut = StereoBuffer(blockFrames)
        val jumpOut = StereoBuffer(blockFrames)
        var snapGap = 0.0

        glideRef.size = 0.2
        jumpRef.size = 0.2

        for (block in 0 until 30 + glideBlocks + 30) {
            val k = block - 29

            if (k >= 1) {
                glideRef.size = if (k >= glideBlocks) 0.8 else 0.2 + 0.6 * k / glideBlocks
                jumpRef.size = 0.8
            }

            fillInput(refIn, block)
            glideOut.clear()
            jumpOut.clear()
            glideRef.process(refIn, glideOut, blockFrames)
            jumpRef.process(refIn, jumpOut, blockFrames)

            for (i in 0 until blockFrames) {
                snapGap = maxOf(snapGap, abs(glideOut.left[i] - jumpOut.left[i]))
            }
        }

        (snapGap > 1e-4) shouldBe true
    }

    "an off-config during a RISING glide drains for the feedback the glide is heading to" {
        val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
        // The law Draining keeps: it is Active on silent input, mid-glide too.
        val active = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
        val c = ctx()
        val a = ctx()

        effect.configure(size = 0.05, lowpass = null)
        active.configure(size = 0.05, lowpass = null)
        c.reverbSendBuffer.left[0] = 1.0
        c.reverbSendBuffer.right[0] = 1.0
        a.reverbSendBuffer.left[0] = 1.0
        a.reverbSendBuffer.right[0] = 1.0
        effect.process(c)
        active.process(a)

        // The room starts to grow, two blocks into a glide to the largest one...
        repeat(2) {
            effect.configure(size = 1.0, lowpass = null)
            active.configure(size = 1.0, lowpass = null)
            c.reverbSendBuffer.clear()
            a.reverbSendBuffer.clear()
            c.mixBuffer.clear()
            a.mixBuffer.clear()
            effect.process(c)
            active.process(a)
        }

        val peak = effect.reverb!!.combPeakAbs()
        val sizeNow = 0.05 + 0.95 * 2 / glideBlocks

        (abs(effect.reverb!!.size - sizeNow) <= 1e-12) shouldBe true

        // ...and the owner turns it off.
        effect.configure(size = 0.0, lowpass = null)

        // The countdown by hand, from the drain's formula: revolutions of the 1640-sample longest
        // comb, plus the spare one. Too short: the feedback in force at the off-config. Long
        // enough: the feedback the glide is heading to, which the drain reaches 15 blocks later.
        fun countdown(feedback: Double): Double = (ceil(ln(1e-5 / peak) / ln(feedback)) + 1.0) * 1640.0

        val tooShort = countdown(sizeNow * 0.28 + 0.7)
        val enough = countdown(1.0 * 0.28 + 0.7)

        (enough > tooShort) shouldBe true

        val tooShortBlocks = ceil(tooShort / blockFrames).toInt() + 2
        val enoughBlocks = ceil(enough / blockFrames).toInt() + 2
        var worstLaw = 0.0

        for (block in 0 until tooShortBlocks) {
            c.reverbSendBuffer.fill(0.5) // a drain discards live sends
            a.reverbSendBuffer.clear()
            c.mixBuffer.clear()
            a.mixBuffer.clear()
            active.configure(size = 1.0, lowpass = null)
            effect.process(c)
            active.process(a)

            for (i in 0 until blockFrames) {
                worstLaw = maxOf(worstLaw, abs(c.mixBuffer.left[i] - a.mixBuffer.left[i]))
            }
        }

        // Draining kept gliding exactly as the Active side did (a law of the engine, not an oracle).
        worstLaw shouldBe 0.0
        effect.reverb!!.size shouldBe 1.0

        // Where the short countdown would have cut, the network still holds a tail well above the
        // silence threshold, and the stage still reports it.
        (effect.reverb!!.combPeakAbs() > 1e-3) shouldBe true
        effect.hasTail() shouldBe true

        repeat(enoughBlocks - tooShortBlocks) {
            c.reverbSendBuffer.clear()
            c.mixBuffer.clear()
            effect.process(c)
        }

        effect.hasTail() shouldBe false
    }

    "an off-config during a FALLING glide drains for the feedback still in force, not the target's" {
        val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
        val c = ctx()

        // The largest room, charged...
        repeat(20) { block ->
            effect.configure(size = 1.0, lowpass = null)
            fillInput(c.reverbSendBuffer, block)
            c.mixBuffer.clear()
            effect.process(c)
        }

        // ...two blocks into a glide down to the smallest room that is still on...
        repeat(2) { block ->
            effect.configure(size = 0.01, lowpass = null)
            fillInput(c.reverbSendBuffer, 20 + block)
            c.mixBuffer.clear()
            effect.process(c)
        }

        val sizeNow = 1.0 + (0.01 - 1.0) * 2 / glideBlocks

        (abs(effect.reverb!!.size - sizeNow) <= 1e-12) shouldBe true

        val peak = effect.reverb!!.combPeakAbs()

        // ...and the owner turns it off.
        effect.configure(size = 0.0, lowpass = null)

        // The countdown by hand from the size IN FORCE, the larger of the two while falling (the
        // accepted over-hold: the room decays faster than this from here on). Off lands on the
        // block that takes the countdown to zero or below.
        val countdown = (ceil(ln(1e-5 / peak) / ln(sizeNow * 0.28 + 0.7)) + 1.0) * 1640.0
        val offBlock = ceil(countdown / blockFrames).toInt()

        repeat(offBlock - 1) {
            c.reverbSendBuffer.clear()
            c.mixBuffer.clear()
            effect.process(c)
        }

        effect.hasTail() shouldBe true

        c.mixBuffer.clear()
        effect.process(c)

        effect.hasTail() shouldBe false
    }

    "reset and retire mid-glide forget the glide: the next room arrives at once" {
        for (retire in listOf(false, true)) {
            withClue(if (retire) "retire" else "reset") {
                val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
                val c = ctx()

                effect.configure(size = 0.2, lowpass = null)

                repeat(3) { block ->
                    fillInput(c.reverbSendBuffer, block)
                    c.mixBuffer.clear()
                    effect.process(c)
                }

                repeat(3) { block ->
                    effect.configure(size = 0.8, lowpass = null)
                    fillInput(c.reverbSendBuffer, block)
                    c.mixBuffer.clear()
                    effect.process(c)
                }

                if (retire) {
                    effect.retire()
                } else {
                    effect.reset()
                }

                // Not 0.5: that is a fresh unit's factory size, which a retired stage's next rent
                // would show whether or not the glide was forgotten.
                effect.configure(size = 0.45, lowpass = null)

                effect.reverb!!.size shouldBe 0.45

                fillInput(c.reverbSendBuffer, 0)
                c.mixBuffer.clear()
                effect.process(c)

                effect.reverb!!.size shouldBe 0.45
            }
        }
    }
})
