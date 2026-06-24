/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeAtLeast
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.TestSamples
import io.peekandpoke.klang.audio_bridge.EngineDsl
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * D1 gate: every [KlangCommLink.Cmd] subtype dispatched through [PlaybackEngineDispatcher.handle]
 * reaches the right collaborator. Asserts observable side effects on real collaborators (the
 * scheduler/registries are final classes, so there is nothing to spy — we verify the effect).
 */
class PlaybackEngineDispatcherTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    // Drives the real factory so create() itself is under test (cylinders stay lazy — nothing heavy
    // is allocated until a voice touches an orbit).
    fun newDispatcher(): PlaybackEngineDispatcher =
        PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.voices.setBackendStartTime(0.0) }

    // A voice that promotes immediately (start at frame 0).
    fun nowVoice(pid: String = "test") = ScheduledVoice(
        playbackId = pid,
        startTime = 0.0,
        gateEndTime = 1.0,
        data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0),
        playbackStartTime = 0.0,
    )

    // A voice scheduled far in the future — stays in the scheduled heap, never promoted at frame 0.
    fun futureVoice(pid: String = "test") = nowVoice(pid).copy(startTime = 10.0, gateEndTime = 11.0)

    "RegisterIgnitor routes to the ignitor registry" {
        val d = newDispatcher()
        val dsl = d.ignitorRegistry.get("sine").shouldNotBeNull()
        d.ignitorRegistry.get("myosc") shouldBe null

        d.handle(KlangCommLink.Cmd.RegisterIgnitor(playbackId = "test", name = "myosc", dsl = dsl))

        d.ignitorRegistry.get("myosc") shouldBe dsl
    }

    "RegisterEngine routes to the engine registry" {
        val d = newDispatcher()

        d.handle(KlangCommLink.Cmd.RegisterEngine(playbackId = "test", name = "myeng", dsl = EngineDsl.pedal))

        d.engineRegistry.get("myeng") shouldBe EngineDsl.pedal
    }

    "Sample routes to the sample store" {
        val d = newDispatcher()
        val req = SampleRequest(bank = null, sound = "test", index = null, note = null)
        d.voices.getCompleteSample(req) shouldBe null

        d.handle(
            KlangCommLink.Cmd.Sample.Complete(
                req = req,
                note = null,
                pitchHz = 440.0,
                sample = TestSamples.silence(64, sampleRate),
            )
        )

        d.voices.getCompleteSample(req).shouldNotBeNull()
    }

    "ScheduleVoice routes to the scheduler" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "test", voice = nowVoice()))
        d.voices.process(0)
        d.voices.getActiveVoiceCount() shouldBeAtLeast 1
    }

    "ScheduleVoices routes to the scheduler" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "test", voices = listOf(nowVoice())))
        d.voices.process(0)
        d.voices.getActiveVoiceCount() shouldBeAtLeast 1
    }

    "ReplaceVoices routes to the scheduler" {
        val d = newDispatcher()
        d.handle(
            KlangCommLink.Cmd.ReplaceVoices(playbackId = "test", voices = listOf(nowVoice()), afterTimeSec = null)
        )
        d.voices.process(0)
        d.voices.getActiveVoiceCount() shouldBeAtLeast 1
    }

    "ClearScheduled routes to the scheduler" {
        val d = newDispatcher()
        // Future voice stays scheduled (not promoted at frame 0), so ClearScheduled can remove it.
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "test", voices = listOf(futureVoice())))
        d.handle(KlangCommLink.Cmd.ClearScheduled(playbackId = "test"))
        d.voices.process(0)
        d.voices.getActiveVoiceCount() shouldBe 0
    }

    "Cleanup routes to the scheduler" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "test", voices = listOf(futureVoice())))
        d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "test"))
        d.voices.process(0)
        d.voices.getActiveVoiceCount() shouldBe 0
    }
})
