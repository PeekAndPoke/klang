/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The cylinder reverb's `lowpass` GLIDES on the comb damping's pole (KLANG-00420, the closing run of 2026-11-11): since
 * the knob is a real cutoff (KLANG-00428) a jump moved the pole across most of its range at once (1000 Hz is 0.87 at
 * 44.1 kHz, unset 0.2), a kink in every comb's signal (the click probe's `reverb.lowpass` rows, guarded with their
 * thresholds in `ReverbLowpassClickSpec`). The oracles are written here: the pole of a one-pole whose -3 dB corner is
 * `fc` (`p = 1 + c - sqrt(c (c + 2))`, `c = 2 sin^2(pi fc / fs)`), the unset pole `0.5 * 0.4`, and the per-block values
 * of a linear glide over 17 blocks handed to a bare [Reverb] by hand.
 *
 * 44.1 kHz and 128 frames: one glide is 17 blocks.
 */
class KatalystReverbDampingGlideSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val glideBlocks = 17

    /** The pole of a one-pole lowpass whose -3 dB corner is [fc] at [sampleRate], by hand. */
    fun poleOf(fc: Double): Double {
        val half = sin(PI * fc / sampleRate)
        val c = 2.0 * half * half

        return 1.0 + c - sqrt(c * (c + 2.0))
    }

    /** The cutoff whose pole is [p] at [sampleRate]: [poleOf] inverted by hand, `c = (p^2 + 1) / (2 p) - 1`. */
    fun cutoffOf(p: Double): Double {
        val c = (p * p + 1.0) / (2.0 * p) - 1.0

        return sampleRate / PI * asin(sqrt(c / 2.0))
    }

    /** Freeverb's own damping, the pole of an unset `lowpass`. */
    val unsetPole = 0.5 * 0.4

    fun ctx() = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

    /** A 110 Hz tone, the click probe's, at 0.5. */
    fun fill(buffer: StereoBuffer, block: Int) {
        for (i in 0 until blockFrames) {
            val v = 0.5 * sin(2.0 * PI * 110.0 * (block * blockFrames + i) / sampleRate)

            buffer.left[i] = v
            buffer.right[i] = v
        }
    }

    /** The stage at [lowpass] (null: unset), configured and run for [blocks] blocks from block [first]. */
    fun KatalystReverbEffect.run(c: KatalystContext, lowpass: Double?, first: Int, blocks: Int, each: (Int) -> Unit = {}) {
        for (k in 1..blocks) {
            configure(size = 0.6, lowpass = lowpass, wet = 1.0)
            fill(c.mixBuffer, first + k - 1)
            process(c)
            each(k)
        }
    }

    "a lowpass change on a sounding room glides the comb pole linearly to the new one over the glide, and lands on it exactly" {
        val p0 = poleOf(1000.0)

        for ((from, to) in listOf(1000.0 to null, null to 1000.0, 1000.0 to 5000.0)) {
            val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
            val c = ctx()
            val start = if (from == null) unsetPole else poleOf(from)
            val end = if (to == null) unsetPole else poleOf(to)

            effect.run(c = c, lowpass = from, first = 0, blocks = 30)

            val unit = effect.reverb!!

            withClue("$from: the first configure snapped; settled, no pole is held") {
                unit.dampingHeld shouldBe false
                (abs(unit.dampingInForce - start) <= 1e-12) shouldBe true
            }

            effect.run(c = c, lowpass = to, first = 30, blocks = glideBlocks) { k ->
                withClue("$from -> $to, block $k") {
                    if (k < glideBlocks) {
                        unit.dampingHeld shouldBe true
                        (abs(unit.dampingInForce - (start + (end - start) * k / glideBlocks)) <= 1e-12) shouldBe true
                    } else {
                        unit.dampingHeld shouldBe false
                        (abs(unit.dampingInForce - end) <= 1e-12) shouldBe true
                    }
                }
            }
        }

        // An engagement row: the jump is most of the pole's range
        (p0 - unsetPole > 0.6) shouldBe true
    }

    "the gliding room is the network a hand-set linear glide of the pole produces, the reference run on the cutoffs of those poles" {
        val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
        val c = ctx()
        val reference = Reverb(sampleRate)
        val refIn = StereoBuffer(blockFrames)
        val refOut = StereoBuffer(blockFrames)
        val start = poleOf(1000.0)
        var worst = 0.0
        var moved = 0.0

        reference.size = 0.6
        reference.lowpass = 1000.0

        for (block in 0 until 30 + glideBlocks + 10) {
            val k = block - 29
            val lowpass = if (block < 30) 1000.0 else null

            effect.configure(size = 0.6, lowpass = lowpass, wet = 1.0)

            // The reference is never held: it runs on the CUTOFF whose pole the glide holds (the pole law inverted by
            // hand), so a room that ignored its held pole would not match it
            if (block >= 30) {
                reference.lowpass = if (k < glideBlocks) cutoffOf(start + (unsetPole - start) * k / glideBlocks) else Double.NaN
            }

            fill(c.mixBuffer, block)
            fill(refIn, block)
            fill(refOut, block)
            effect.process(c)
            reference.process(input = refIn, output = refOut, length = blockFrames)

            for (i in 0 until blockFrames) {
                worst = maxOf(worst, abs(c.mixBuffer.left[i] - refOut.left[i]))
                moved = maxOf(moved, abs(c.mixBuffer.left[i] - refIn.left[i]))
            }
        }

        withClue("the room sounds") { (moved > 1e-3) shouldBe true }
        // The hand glide divides by 17 where the glide multiplies by its reciprocal, and the cutoff's round trip rounds:
        // the poles differ in their last bits
        withClue("largest difference to the hand-glided network: $worst") { (worst <= 1e-9) shouldBe true }
    }

    "a room whose lowpass holds still runs on its own pole, never a held one, bit for bit the bare network" {
        for (lowpass in listOf(1000.0, null)) {
            val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
            val c = ctx()
            val reference = Reverb(sampleRate)
            val refIn = StereoBuffer(blockFrames)
            val refOut = StereoBuffer(blockFrames)
            var worst = 0.0

            reference.size = 0.6
            reference.lowpass = lowpass ?: Double.NaN

            for (block in 0 until 60) {
                effect.configure(size = 0.6, lowpass = lowpass, wet = 1.0)
                fill(c.mixBuffer, block)
                fill(refIn, block)
                fill(refOut, block)
                effect.process(c)
                reference.process(input = refIn, output = refOut, length = blockFrames)
                effect.reverb!!.dampingHeld shouldBe false

                for (i in 0 until blockFrames) {
                    worst = maxOf(worst, abs(c.mixBuffer.left[i] - refOut.left[i]))
                }
            }

            withClue("lowpass $lowpass") { worst shouldBe 0.0 }
        }
    }

    "a modulated lowpass glides over one block: it lands on every new cutoff at once, as before the glide" {
        val effect = KatalystReverbEffect(units = ReverbUnits(sampleRate), blockFrames = blockFrames, lowpassModulated = true)
        val c = ctx()

        effect.run(c = c, lowpass = 1000.0, first = 0, blocks = 5)
        effect.run(c = c, lowpass = null, first = 5, blocks = 1)

        val unit = effect.reverb!!

        unit.dampingHeld shouldBe false
        unit.dampingInForce shouldBe unsetPole
    }

    "a drain that starts mid-glide towards a darker room counts the darker room's lag" {
        val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
        val c = ctx()

        effect.run(c = c, lowpass = null, first = 0, blocks = 30)
        effect.run(c = c, lowpass = 5.0, first = 30, blocks = 3)

        val unit = effect.reverb!!

        unit.dampingHeld shouldBe true

        // The bare network at the glide's target (a pole near 1, a lag of about 1400 samples), one block run
        val dark = Reverb(sampleRate)

        dark.size = 0.6
        dark.lowpass = 5.0
        dark.process(input = StereoBuffer(blockFrames), output = StereoBuffer(blockFrames), length = blockFrames)

        unit.drainSamplesUntilSilent(peak = 1.0, size = 0.6) shouldBe dark.drainSamplesUntilSilent(peak = 1.0, size = 0.6)
    }

    "reset and the way into Off forget a glide mid-way: no pole is left held for the next life" {
        val effect = KatalystReverbEffect(reverb = Reverb(sampleRate), blockFrames = blockFrames)
        val c = ctx()

        effect.run(c = c, lowpass = 1000.0, first = 0, blocks = 30)
        effect.run(c = c, lowpass = null, first = 30, blocks = 3)

        val unit = effect.reverb!!

        unit.dampingHeld shouldBe true
        effect.reset()
        unit.dampingHeld shouldBe false

        // The next life's first configure snaps to its cutoff
        effect.run(c = c, lowpass = 1000.0, first = 33, blocks = 1)
        unit.dampingHeld shouldBe false
        (abs(unit.dampingInForce - poleOf(1000.0)) <= 1e-12) shouldBe true
    }
})
