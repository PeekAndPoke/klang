/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * The wire from `FilterDef.envelope` to an audible filter sweep, through the real `VoiceFactory`.
 *
 * Audit finding [F5]'s surviving bullet: `VoiceFactory.toModulator()` was **unreached by any spec**.
 * Every spec that calls `makeVoice` omits `FilterDef.envelope`, so the function took its
 * `envData == null && drift == null` early return every time and none of its real branches ran.
 * `FilterEnvSemitoneSpec` covers the semitone law thoroughly, but it constructs
 * `Voice.FilterModulator` by hand — it starts *after* the wire this spec is about.
 *
 * That is the F18 shape exactly: both ends covered, the join empty, and a parameter that silently
 * never arrives is invisible to inspection because the code at both ends reads correctly.
 *
 * **Measured as energy, not as a cutoff**, because `makeVoice` builds the filter internally and there
 * is no spy to inject. A sawtooth at 220 Hz through a 300 Hz lowpass is dark; opening that cutoff by
 * +24 semitones (×4, to 1200 Hz) lets four more harmonics through, which is a large and unambiguous
 * energy change in a direction the semitone law fixes in advance.
 */
class VoiceFactoryFilterEnvWireSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val blocks = 24

    fun energyOf(envelope: FilterEnvDef?): Double {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val voiceBuffer = DoubleArray(blockFrames)
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = voiceBuffer,
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "wire",
                data = VoiceData.empty.copy(
                    freqHz = 220.0,
                    sound = "sawtooth",
                    // VCA off: the amplitude envelope must not be what moves the energy.
                    adsr = AdsrDef.Std(release = 0.05, on = false),
                    filters = FilterDefs(listOf(FilterDef.LowPass(freq = 300.0, q = 0.707, envelope = envelope))),
                ),
                startTime = 0.0,
                gateEndTime = 1.0,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "wire", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")

        val rc = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = voiceBuffer,
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        var energy = 0.0
        for (b in 0 until blocks) {
            voiceBuffer.fill(0.0)
            rc.blockStart = (b * blockFrames).toDouble()
            if (!voice.render(rc)) break
            for (v in voiceBuffer) energy += v * v
        }

        return energy
    }

    // Zero attack and full sustain, so the sweep is at its target from the first frame and the
    // comparison is not about attack timing.
    fun env(depth: Double) = FilterEnvDef(attack = 0.0, decay = 0.0, sustain = 1.0, release = 0.0, depth = depth)

    "the filter envelope reaches the voice at all — a positive depth opens the cutoff" {
        val closed = energyOf(null)
        val opened = energyOf(env(+24.0))

        // Positive control: without it, two silent renders would also satisfy "opened > closed".
        (closed > 1e-9) shouldBe true

        // +24 semitones is 300 Hz -> 1200 Hz. If `envelope` never reached toModulator, no modulator
        // is built, no FilterModRenderer runs, and these two are byte-identical.
        (opened > closed * 1.5) shouldBe true
    }

    "the depth's SIGN is carried through — a negative depth closes it further" {
        val closed = energyOf(null)
        val darker = energyOf(env(-24.0))

        // 300 Hz -> 75 Hz. A sign flip anywhere on the wire would brighten instead, and the row
        // above would still pass on its own.
        (darker < closed) shouldBe true
    }
})
