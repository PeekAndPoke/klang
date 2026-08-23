/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * C0.2 wire guard: the five per-param compressor fields must arrive on [Voice.compressor]
 * in the RIGHT slots. Five DISTINCT values pin the whole mapping — a transposed named arg
 * in VoiceFactory (e.g. attack = data.compressorRelease) fails here and nowhere else.
 */
class VoiceFactoryCompressorWireSpec : StringSpec({

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
        val scheduled = ScheduledVoice(
            playbackId = "test",
            data = data,
            startTime = 0.0,
            gateEndTime = 1.0,
            playbackStartTime = 0.0,
        )
        return factory.makeVoice(
            scheduled = scheduled,
            nowFrame = 0.0,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null")
    }

    "five distinct wire values land in the right compressor slots" {
        val voice = voiceOf(
            VoiceData.empty.copy(
                freqHz = 440.0,
                sound = "triangle",
                compressorThreshold = -11.0,
                compressorRatio = 3.5,
                compressorKnee = 2.5,
                compressorAttack = 0.017,
                compressorRelease = 0.23,
            )
        )

        val c = voice.compressor.shouldNotBeNull()
        c.thresholdDb shouldBe -11.0
        c.ratio shouldBe 3.5
        c.kneeDb shouldBe 2.5
        c.attackSeconds shouldBe 0.017
        c.releaseSeconds shouldBe 0.23
    }

    "no compressor fields set means no compressor on the voice" {
        val voice = voiceOf(
            VoiceData.empty.copy(freqHz = 440.0, sound = "triangle")
        )
        voice.compressor shouldBe null
    }
})
