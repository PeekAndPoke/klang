/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * **A pluck's tail carries no DC** (KLANG-00426, audit finding L3-06). The loop passes DC at the feedback gain alone
 * (the one-pole and the allpass have unity gain at 0 Hz), so a burst whose random mean stayed in the line outlived its
 * tone: after 1.9 s the mean was 0.08 to 1.84 times the AC level left (the pluck at 220 Hz, 8 voice seeds), 0.27 to
 * 4.26 (the superpluck). The excitation is now free of DC: its pick comb sums to zero over the string (`KarplusString.excite`).
 *
 * The measurement of the audit's reproduction: the bare node at 220 Hz with its defaults, analog 0, 2 s, the mean and
 * the AC level over 1.9 to 2.0 s, for 8 voice seeds.
 */
class PluckDcSpec : StringSpec({
    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 2 * sampleRate / blockFrames

    fun render(node: Ignitor, seed: Int): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = blocks * blockFrames,
            gateEndFrame = blocks * blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(seed),
        )
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            node.generate(buf, 220.0, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }
        }

        return out
    }

    fun checkTail(name: String, make: (Random) -> Ignitor) {
        for (seed in 1..8) {
            val out = render(node = make(Random(seed)), seed = seed)
            val from = 19 * sampleRate / 10
            val to = out.size
            var sum = 0.0

            for (n in from until to) {
                sum += out[n]
            }

            val mean = sum / (to - from)
            var acPower = 0.0

            for (n in from until to) {
                acPower += (out[n] - mean) * (out[n] - mean)
            }

            val ac = sqrt(acPower / (to - from))

            println("PLUCK-DC $name seed $seed: tail mean $mean, AC level $ac, |mean| / AC ${abs(mean) / ac}")

            withClue("$name, seed $seed: the tail still rings") {
                ac shouldBeGreaterThan 1e-4
            }

            // The window's own floor: a decaying tone's mean over 0.1 s is not zero. With the pick comb's excitation
            // (KLANG-00426, L3-08; its sum over the string is zero by construction, `KarplusStringSpec`) it reaches 0.017
            // (the superpluck, seed 6), where the burst stayed under 0.01; the DC the loop kept before L3-06 read 0.08 to
            // 4.26. The bound sits between the two.
            withClue("$name, seed $seed: |mean| over the AC level in the tail") {
                abs(mean) / ac shouldBeLessThan 0.04
            }
        }
    }

    "the pluck's tail after 1.9 s is AC: its mean is under 4 percent of its level, 8 seeds" {
        checkTail(name = "pluck") { rng -> Ignitors.karplusStrong(analog = ConstantIgnitor(0.0), rng = rng) }
    }

    "the superpluck's tail after 1.9 s is AC: its mean is under 4 percent of its level, 8 seeds" {
        checkTail(name = "superpluck") { rng -> Ignitors.superKarplusStrong(analog = ConstantIgnitor(0.0), rng = rng) }
    }
})
