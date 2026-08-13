/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
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
            MasterStageDsl.Reverb(wet = 0.0, roomSize = 9.0),       // nothing sent into it
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
            MasterStageDsl.Reverb(wet = 0.4, roomSize = 7.0),
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
        val chain = build(MasterStageDsl.Reverb(wet = 0.9, roomSize = 9.0, damp = 0.1))
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

    "roomSize is the sprudel scale — the number a user writes means the same on both buses" {
        // The regression this whole change exists for: authored 3 must be a ~1 s tail (0.3), not the
        // 12.5 s maximum it became when the master skipped the /10.
        build(MasterStageDsl.Reverb(wet = 0.5, roomSize = 3.0)).reverbs[0].roomSize shouldBe 0.3
        build(MasterStageDsl.Reverb(wet = 0.5, roomSize = 8.0)).reverbs[0].roomSize shouldBe 0.8
    }

    "an authored roomSize under the audible floor drops the stage" {
        // 0.05 authored -> 0.005 normalized, below MIN_TIME_FX. The comparison must happen AFTER
        // normalization, or the master would build a Freeverb the orbit would have skipped.
        val chain = build(MasterStageDsl.Reverb(wet = 0.5, roomSize = 0.05))

        chain.isActive shouldBe false
        chain.hasTail shouldBe false
    }

    "a NaN roomSize still builds a reverb — it must not silently delete the stage" {
        // The trap: if the finite() fallback were the NORMALIZED default (0.5) instead of the
        // authored one (5.0), it would normalize to 0.05, fall under MIN_TIME_FX, and the reverb
        // would vanish with no error.
        val chain = build(MasterStageDsl.Reverb(wet = 0.5, roomSize = Double.NaN))

        chain.isActive shouldBe true
        chain.reverbs[0].roomSize shouldBe 0.5
    }

    "roomFade, roomLp and cap reach the DSP unchanged" {
        val reverb = build(
            MasterStageDsl.Reverb(wet = 0.5, roomSize = 8.0, damp = 0.3, roomFade = 0.12, roomLp = 9000.0)
        ).reverbs[0]

        reverb.roomFade shouldBe 0.12
        reverb.roomLp shouldBe 9000.0
        reverb.damp shouldBe 0.3
    }

    "a non-finite roomLp is ignored rather than written" {
        // Assigned through the property setter, which drops non-finite. A constructor initializer
        // would bypass that guard — the same trap the delay KDoc flags.
        build(MasterStageDsl.Reverb(wet = 0.5, roomSize = 8.0, roomLp = Double.NaN)).reverbs[0].roomLp shouldBe null
    }

    "the delay feedback ceiling reaches the DSP" {
        build(
            MasterStageDsl.Delay(wet = 0.5, timeSeconds = 0.25, feedback = 1.0, cap = 3.0)
        ).delays[0].feedbackCap shouldBe 3.0
    }

    "an authored lookahead reaches the Compressor — the wire-to-DSP hop, asserted" {
        // The parameter-parity rule is only worth something if the value actually arrives. Before
        // this, `limiters` was private and this hop could not be checked at all.
        build(MasterStageDsl.Limiter(lookaheadSeconds = 0.004)).limiters[0].lookaheadSeconds shouldBe 0.004
    }

    "an authored limiter defaults to NO lookahead — the cross-playback desync guard" {
        // A master chain is per playback. Any default latency here would delay one playback against
        // every other one; the house safety limiter can afford 5 ms only because it runs once, on
        // the summed mix. See AUTHORED_LIMITER_LOOKAHEAD_SECONDS in audio_bridge/constants.
        build(MasterStageDsl.Limiter()).limiters[0].lookaheadSeconds shouldBe 0.0
    }

    "a hostile lookahead cannot throw or exhaust memory on the audio thread" {
        // This is the one stage parameter that sizes an array, and chains are built on the audio
        // thread. Negative -> NegativeArraySizeException; Infinity -> Int.MAX_VALUE doubles.
        // Non-finite falls back to "off" via finite(), like every sibling parameter...
        build(MasterStageDsl.Limiter(lookaheadSeconds = Double.NaN)).limiters[0].lookaheadSeconds shouldBe 0.0
        build(MasterStageDsl.Limiter(lookaheadSeconds = Double.POSITIVE_INFINITY))
            .limiters[0].lookaheadSeconds shouldBe 0.0
        // ...a negative value is finite, so the range bound is what catches it...
        build(MasterStageDsl.Limiter(lookaheadSeconds = -1.0)).limiters[0].lookaheadSeconds shouldBe 0.0
        // ...and a merely absurd one is capped rather than rejected.
        build(MasterStageDsl.Limiter(lookaheadSeconds = 10.0)).limiters[0].lookaheadSeconds shouldBe 0.05
    }
})
