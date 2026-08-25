/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.random.Random

/**
 * C4 guard (docs/plans/filter-unification.md): `wet == 0` IS bypass, bit-identically, on
 * every routed additive effect. The helper cannot early-return per sample (`dry*1 + wet*0`
 * flips -0.0 and poisons on a NaN wet), so each CALLER carries an explicit early return;
 * these rows are the tripwires for removing one.
 *
 * The shimmer row is the plan's named red case: pre-C4 it only bypassed when feedback was
 * ALSO zero, so `shimmer(wet = 0, feedback = 0.5)` ran the grain engine for nothing.
 */
class WetZeroBypassSpec : StringSpec({

    val blockFrames = 128
    val blocks = 8

    fun ctx() = IgniteContext(
        sampleRate = 48000,
        voiceDurationFrames = blocks * blockFrames * 2,
        gateEndFrame = blocks * blockFrames * 2,
        releaseFrames = 0,
        voiceEndFrame = blocks * blockFrames * 2,
        scratchBuffers = ScratchBuffers(blockFrames = blockFrames),
        voiceElapsedFrames = 0,
    )

    val noise = DoubleArray(blocks * blockFrames).also {
        val rng = Random(777)
        for (i in it.indices) it[i] = rng.nextDouble() * 2.0 - 1.0
        // Salt with -0.0 at fixed positions: the arithmetic path (dry*1 + wet*0) flips
        // -0.0 to +0.0, so toRawBits catches a REMOVED early return — without the salt
        // this fixture cannot distinguish bypass from the w=0 arithmetic (both exact).
        for (i in it.indices step 17) it[i] = -0.0
    }

    fun noiseSource(): Ignitor = object : Ignitor {
        var pos = 0
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.offset + ctx.length) {
                buffer[i] = noise[pos++]
            }
        }
    }

    fun render(chain: Ignitor): DoubleArray {
        val c = ctx()
        c.offset = 0
        c.length = blockFrames
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)
        repeat(blocks) { blk ->
            chain.generate(buf, 220.0, c)
            buf.copyInto(out, blk * blockFrames, 0, blockFrames)
            c.voiceElapsedFrames += blockFrames
        }
        return out
    }

    fun assertBitIdenticalToSource(chain: Ignitor) {
        val processed = render(chain)
        val plain = render(noiseSource())
        for (i in processed.indices) {
            processed[i].toRawBits() shouldBe plain[i].toRawBits()
        }
    }

    "ignitor phaser at wet 0 is bit-identical bypass" {
        assertBitIdenticalToSource(
            noiseSource().phaser(
                rate = ConstantIgnitor(1.0),
                wet = ConstantIgnitor(0.0),
                center = ConstantIgnitor(1000.0),
                sweep = ConstantIgnitor(500.0),
            )
        )
    }

    "ignitor shimmer at wet 0 is bit-identical bypass EVEN WITH feedback" {
        assertBitIdenticalToSource(
            noiseSource().shimmer(
                wet = ConstantIgnitor(0.0),
                feedback = ConstantIgnitor(0.5),
                tone = ConstantIgnitor(4000.0),
            )
        )
    }
})
