/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl

/**
 * Build-time behaviour of a master chain: which stages survive, and what the chain claims about
 * itself. These are the properties `MasterBus` and `PlaybackEngine` steer on (fast path, disposal),
 * so they are asserted directly rather than inferred from rendered audio.
 */
class MasterChainSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun build(vararg stages: MasterStageDsl): MasterChain =
        MasterChain.build(MasterDsl.of(*stages), sampleRate = sampleRate, blockFrames = blockFrames)

    "an empty chain is inactive and tail-free" {
        val chain = build()

        chain.isActive shouldBe false
        chain.hasTail shouldBe false
    }

    "stages that cannot be heard are dropped at build time" {
        val chain = build(
            MasterStageDsl.Gain(gain = 1.0),                        // unity — nothing to do
            MasterStageDsl.Reverb(wet = 0.0, roomSize = 0.9),       // nothing sent into it
            MasterStageDsl.Delay(wet = 0.5, timeSeconds = 0.0),     // no delay time
        )

        // Nothing survives, so the engine keeps its zero-copy fast path and the chain can never
        // hold a drained engine open. (Freeverb is the priciest DSP unit in the engine — building
        // one for a wet=0 reverb would cost every block, forever.)
        chain.isActive shouldBe false
        chain.hasTail shouldBe false
    }

    "audible stages survive and a time-based one is marked as having a tail" {
        val chain = build(
            MasterStageDsl.Gain(gain = 2.0),
            MasterStageDsl.Reverb(wet = 0.4, roomSize = 0.7),
        )

        chain.isActive shouldBe true
        chain.hasTail shouldBe true
        // Nothing has been processed yet, so there is no energy to ring.
        chain.hasActiveTail() shouldBe false
    }

    "a limiter-only chain is active but has no tail" {
        val chain = build(MasterStageDsl.Limiter())

        chain.isActive shouldBe true
        chain.hasTail shouldBe false
    }

    "non-finite parameters fall back instead of throwing or poisoning the bus" {
        // NaN in a delay time used to reach DelayLine with maxDelaySeconds = NaN → a zero-length
        // ring → `coerceIn(min > max)` throwing inside the render callback, which kills the worklet.
        val chain = build(
            MasterStageDsl.Gain(gain = Double.NaN),
            MasterStageDsl.Delay(wet = 0.5, timeSeconds = Double.NaN, feedback = Double.NaN),
            MasterStageDsl.Reverb(wet = Double.NaN, roomSize = Double.POSITIVE_INFINITY),
        )

        val bus = StereoBuffer(blockFrames).also { buffer ->
            for (i in 0 until blockFrames) {
                buffer.left[i] = 0.5
                buffer.right[i] = 0.5
            }
        }

        chain.process(bus, blockFrames)

        // Whatever survived must leave the signal finite — a NaN here would flood the shared mix
        // for every playback, permanently.
        for (i in 0 until blockFrames) {
            bus.left[i].isFinite() shouldBe true
            bus.right[i].isFinite() shouldBe true
        }
    }

    "reset clears a chain so a re-adopted one cannot replay an old tail" {
        val chain = build(MasterStageDsl.Reverb(wet = 0.9, roomSize = 0.9, damp = 0.1))
        val bus = StereoBuffer(blockFrames)

        // Push a burst through it, then let it run dry for a moment.
        for (i in 0 until blockFrames) {
            bus.left[i] = 0.8
            bus.right[i] = 0.8
        }
        chain.process(bus, blockFrames)
        chain.hasActiveTail() shouldBe true

        chain.reset()

        chain.hasActiveTail() shouldBe false
    }
})
