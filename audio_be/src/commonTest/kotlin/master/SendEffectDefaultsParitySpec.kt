/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.master

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * One set of send-effect defaults on every surface (`constants/SendEffectDefaults.kt`, maintainer
 * 2026-09-16): an orbit voice that touches the delay or the reverb gets, for every slot it leaves
 * unset, exactly what an unconfigured master stage gets. A non-finite slot reads as unset on both
 * buses. A voice that does not touch an effect sends nothing and configures nothing.
 *
 * Both sides go through their real production path, `VoiceFactory` + `Cylinder` for the orbit and
 * `MasterChain.build` for the master, so a default that drifts on either side fails here.
 */
class SendEffectDefaultsParitySpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun voiceOf(data: VoiceData): Voice {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        return factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data.copy(freqHz = 440.0, sound = "triangle"),
                startTime = 0.0,
                gateEndTime = 1.0,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")
    }

    /** The orbit's DSP after one voice claimed it, through the cylinder that writes the DSP. */
    fun orbitOf(data: VoiceData): Pair<Voice, Cylinder> {
        val voice = voiceOf(data)
        val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)
        cylinder.updateFromVoice(voice, blockStart = 0.0)

        return voice to cylinder
    }

    fun masterOf(stage: MasterStageDsl) = MasterChain.build(
        dsl = MasterDsl.of(stage),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
    )

    // ── Delay ────────────────────────────────────────────────────────────────────────────────────

    "delay: an orbit voice that sets only the send gets the master's time, feedback and cap" {
        val (_, cylinder) = orbitOf(VoiceData.empty.copy(delay = 0.4))
        val master = masterOf(MasterStageDsl.Delay(wet = 0.4)).delays.firstOrNull().shouldNotBeNull()

        val orbit = cylinder.delay.delayLine.shouldNotBeNull()
        orbit.time shouldBe master.time
        orbit.feedback shouldBe master.feedback
        orbit.cap shouldBe master.cap
    }

    "delay: an orbit voice that sets any one slot but the send sends the master's default wet" {
        listOf(
            "time" to VoiceData.empty.copy(delayTime = 0.5),
            "feedback" to VoiceData.empty.copy(delayFeedback = 0.6),
            "cap" to VoiceData.empty.copy(delayCap = 2.0),
        ).forEach { (slot, data) ->
            withClue("only $slot") {
                orbitOf(data).first.delay.amount shouldBe MasterStageDsl.Delay().wet
            }
        }
    }

    "delay: a non-finite slot reads as unset, the same on both buses" {
        val (voice, cylinder) = orbitOf(
            VoiceData.empty.copy(delay = Double.NaN, delayTime = Double.POSITIVE_INFINITY, delayFeedback = Double.NaN),
        )
        val master = masterOf(
            MasterStageDsl.Delay(wet = Double.NaN, time = Double.POSITIVE_INFINITY, feedback = Double.NaN),
        ).delays.firstOrNull().shouldNotBeNull()

        val orbit = cylinder.delay.delayLine.shouldNotBeNull()
        orbit.time shouldBe master.time
        orbit.feedback shouldBe master.feedback
        voice.delay.amount shouldBe MasterStageDsl.Delay().wet
    }

    "delay: a voice that does not touch it sends nothing and leaves the orbit's delay off" {
        val (voice, cylinder) = orbitOf(VoiceData.empty)

        voice.delay.amount shouldBe 0.0
        cylinder.delay.delayLine.shouldBeNull()
    }

    // ── Reverb ───────────────────────────────────────────────────────────────────────────────────

    "reverb: an orbit voice that sets only the send gets the master's size" {
        val (_, cylinder) = orbitOf(VoiceData.empty.copy(reverb = 0.4))
        val master = masterOf(MasterStageDsl.Reverb(wet = 0.4)).reverbs.firstOrNull().shouldNotBeNull()

        cylinder.reverb.reverb.shouldNotBeNull().size shouldBe master.size
    }

    "reverb: an orbit voice that sets any one slot but the send sends the master's default wet" {
        listOf(
            "size" to VoiceData.empty.copy(reverbSize = 4.0),
            "lowpass" to VoiceData.empty.copy(reverbLowpass = 3000.0),
        ).forEach { (slot, data) ->
            withClue("only $slot") {
                orbitOf(data).first.reverb.amount shouldBe MasterStageDsl.Reverb().wet
            }
        }
    }

    "reverb: a non-finite slot reads as unset, the same on both buses" {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            val (voice, cylinder) = orbitOf(VoiceData.empty.copy(reverb = bad, reverbSize = bad))
            val master = masterOf(MasterStageDsl.Reverb(wet = bad, size = bad)).reverbs.firstOrNull().shouldNotBeNull()

            withClue("slot = $bad") {
                cylinder.reverb.reverb.shouldNotBeNull().size shouldBe master.size
                voice.reverb.amount shouldBe MasterStageDsl.Reverb().wet
            }
        }
    }

    "reverb: a voice that does not touch it sends nothing and leaves the orbit's reverb off" {
        val (voice, cylinder) = orbitOf(VoiceData.empty)

        voice.reverb.amount shouldBe 0.0
        cylinder.reverb.reverb.shouldBeNull()
    }
})
