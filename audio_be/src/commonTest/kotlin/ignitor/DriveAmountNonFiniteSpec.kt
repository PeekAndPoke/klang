/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.DistortionCore
import kotlin.random.Random

/**
 * **A non-finite `drive` amount reads as unset, and unset is off** (KLANG-00429, audit finding L5-11, the maintainer's
 * "both parts" of 2026-11-11, part (ii)). The gate switches a LEAF amount off at a non-finite value; an amount that is a
 * signal or an expression is built, and `DriveIgnitor` read a NaN as a drive: `10^(NaN * 1.2)` made every sample NaN,
 * `+Infinity` an infinite gain. The numerical-safety contract reads it as unset at the node's own read
 * (`audio/ref/numerical-safety.md`): the block passes its input through, as at an amount of 0. Not a clamp: every
 * finite amount drives as before.
 */
class DriveAmountNonFiniteSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    fun render(amount: Double?): DoubleArray {
        val saw = Ignitors.saw(analog = ConstantIgnitor(0.0))
        val node = if (amount == null) saw else saw.drive(ConstantIgnitor(amount))
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(1),
        )
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(blockFrames * 4)

        for (b in 0 until 4) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            node.generate(buffer, 220.0, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }
        }

        return out
    }

    "a NaN or an infinite drive amount passes the input through unchanged, bit for bit (L5-11)" {
        val dry = render(amount = null)

        for (amount in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("amount $amount") {
                render(amount = amount).map { it.toRawBits() } shouldBe dry.map { it.toRawBits() }
            }
        }
    }

    "a finite drive amount still drives: the gain of DistortionCore.drive, no clamp" {
        val dry = render(amount = null)
        val gain = DistortionCore.drive(0.5)
        val driven = render(amount = 0.5)

        for (i in dry.indices) {
            withClue("sample $i") { driven[i] shouldBe (dry[i] * gain plusOrMinus 1e-12) }
        }
    }
})
