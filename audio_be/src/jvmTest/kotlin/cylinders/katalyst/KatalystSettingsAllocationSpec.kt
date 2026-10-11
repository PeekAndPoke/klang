/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.common.infra.klangMapOf
import java.lang.management.ManagementFactory

/**
 * Katalyst knob expressions step 2: re-resolving a CHANGED param map allocates no settings object. The compressor's and
 * the duck's writers built a new `CompressorSettings` / `DuckSettings` per resolve until then, which a knob resolved
 * every block (a modulated knob, a later step) would make an allocation per block; now each refills its one holder.
 * Measured by the bytes this thread allocates (`com.sun.management.ThreadMXBean`), the
 * [KatalystResonatorAllocationSpec] way.
 *
 * Every block resolves the next of a few maps built up front (each value differs from the last block's, so the
 * compressor writes its knobs and its glides move), applies, and processes. The first runs warm the JIT; over the later
 * runs the fewest bytes any run took must be 0. The fewest, because a one-off (a JIT recompilation in the middle of a
 * run) lands in one run, while a resolve that allocates does so in every run.
 */
class KatalystSettingsAllocationSpec : StringSpec({

    val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    val sampleRate = 48000
    val blockFrames = 128

    fun bytes(): Double = mx.currentThreadAllocatedBytes.toDouble()

    fun slot(name: String) = IgnitorDsl.Param(name, SLOT_UNSET)

    fun ctx(): KatalystContext = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames)).apply {
        sidechainBuffer = StereoBuffer(blockFrames)
    }

    /**
     * Runs [block] 400 times per run, ten runs, and returns the fewest bytes one of the runs from the fifth on took.
     * No argument: an `Int` handed to a lambda is boxed on the JVM, above 127 an allocation of the harness's own.
     */
    fun fewestBytes(block: () -> Unit): Double {
        var fewest = Double.MAX_VALUE

        for (run in 0 until 10) {
            val b0 = bytes()

            repeat(400) { block() }

            val b1 = bytes()

            if (run >= 4) {
                fewest = minOf(fewest, b1 - b0)
            }
        }

        return fewest
    }

    fun fill(ctx: KatalystContext, b: Int) {
        for (i in 0 until blockFrames) {
            ctx.mixBuffer.left[i] = ((i * 37 + b) % 101) / 101.0 - 0.5
            ctx.mixBuffer.right[i] = ((i * 53 + b) % 89) / 89.0 - 0.5
        }
    }

    "the compressor: re-resolving a changed map every block allocates nothing" {
        val fx = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val writer = KatalystCompressorWriter(
            fx = fx,
            threshold = KatalystKnob(slot("compressor.threshold"), COMPRESSOR_THRESHOLD_DB),
            ratio = KatalystKnob(slot("compressor.ratio"), COMPRESSOR_RATIO),
            knee = KatalystKnob(slot("compressor.knee"), COMPRESSOR_KNEE_DB),
            attack = KatalystKnob(slot("compressor.attack"), COMPRESSOR_ATTACK_SECONDS),
            release = KatalystKnob(slot("compressor.release"), COMPRESSOR_RELEASE_SECONDS),
        )
        val maps = Array(5) {
            klangMapOf(
                "compressor.threshold" to -30.0 + 2.0 * it,
                "compressor.ratio" to 2.0 + it,
                "compressor.knee" to 1.0 + it,
                "compressor.attack" to 0.002 + 0.001 * it,
                "compressor.release" to 0.1 + 0.05 * it,
            )
        }
        val ctx = ctx()
        var b = 0

        val fewest = fewestBytes {
            writer.resolve(maps[b % maps.size])
            writer.apply()
            fill(ctx, b)
            fx.process(ctx)
            b++
        }

        withClue("the fewest bytes a run of 400 changed resolves took was $fewest") {
            fewest shouldBe 0.0
        }
    }

    "the duck: re-resolving a changed map every block allocates nothing" {
        val fx = KatalystDuckEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val writer = KatalystDuckWriter(
            fx = fx,
            orbit = KatalystKnob(slot("duck.orbit"), SLOT_UNSET),
            depth = KatalystKnob(slot("duck.depth"), DUCK_DEPTH),
            attack = KatalystKnob(slot("duck.attack"), DUCK_ATTACK_SECONDS),
        )
        val maps = Array(5) {
            klangMapOf(
                "duck.orbit" to 1.0 + it,
                "duck.depth" to 0.3 + 0.1 * it,
                "duck.attack" to 0.05 + 0.02 * it,
            )
        }
        val ctx = ctx()
        var b = 0

        val fewest = fewestBytes {
            writer.resolve(maps[b % maps.size])
            writer.apply()
            fill(ctx, b)
            fx.orbitBlockRan()
            fx.process(ctx)
            b++
        }

        withClue("the fewest bytes a run of 400 changed resolves took was $fewest") {
            fewest shouldBe 0.0
        }
    }
})
