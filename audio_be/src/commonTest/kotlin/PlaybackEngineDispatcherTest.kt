/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
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
 * D2·b gate: every [KlangCommLink.Cmd] subtype is routed to the right place, engines are created
 * lazily per playback and disposed on drain, and — the headline — two playbacks on the same orbit
 * id get **independent** cylinders (the orbit-collision fix).
 */
class PlaybackEngineDispatcherTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    fun newDispatcher(): PlaybackEngineDispatcher =
        PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }

    fun voice(pid: String, cylinder: Int = 0, startTime: Double = 0.0, gateEndTime: Double = 1.0) =
        ScheduledVoice(
            playbackId = pid,
            startTime = startTime,
            gateEndTime = gateEndTime,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, cylinder = cylinder),
            playbackStartTime = 0.0,
        )

    // Scheduled far in the future — stays in the heap, never promoted at frame 0.
    fun futureVoice(pid: String) = voice(pid, startTime = 10.0, gateEndTime = 11.0)

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

    "Sample routes to the shared sample store" {
        val d = newDispatcher()
        val req = SampleRequest(bank = null, sound = "test", index = null, note = null)
        d.sampleStore.getComplete(req) shouldBe null

        d.handle(
            KlangCommLink.Cmd.Sample.Complete(
                req = req, note = null, pitchHz = 440.0, sample = TestSamples.silence(64, sampleRate),
            )
        )

        d.sampleStore.getComplete(req).shouldNotBeNull()
    }

    "ScheduleVoice lazily creates the engine for its playbackId" {
        val d = newDispatcher()
        d.activePlaybackIds.size shouldBe 0

        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "song", voice = voice("song")))

        d.activePlaybackIds shouldContainAll setOf("song")
        d.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBeAtLeast 1
    }

    "ReplaceVoices replaces voices on the existing engine" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "song", voice = voice("song")))
        d.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBeAtLeast 1

        d.handle(
            KlangCommLink.Cmd.ReplaceVoices(playbackId = "song", voices = listOf(voice("song")), afterTimeSec = null)
        )

        d.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBeAtLeast 1
    }

    "ReplaceVoices for an unknown playback does not create an engine (no leak)" {
        val d = newDispatcher()

        d.handle(
            KlangCommLink.Cmd.ReplaceVoices(playbackId = "ghost", voices = listOf(voice("ghost")), afterTimeSec = null)
        )

        d.engine("ghost") shouldBe null
        d.activePlaybackIds.size shouldBe 0
    }

    "ClearScheduled drops not-yet-played voices" {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)

        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(futureVoice("song"))))
        d.handle(KlangCommLink.Cmd.ClearScheduled(playbackId = "song"))
        d.renderBlock(0, out)

        d.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBe 0
    }

    "Cleanup drains then disposes the engine once idle" {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)

        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(futureVoice("song"))))
        d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
        d.renderBlock(0, out)   // cleared future voice → engine fully idle → disposed

        d.engine("song") shouldBe null
    }

    "two playbacks on the same orbit get independent cylinders (the isolation fix)" {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)

        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "A", voice = voice("A", cylinder = 0)))
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "B", voice = voice("B", cylinder = 0)))
        d.renderBlock(0, out)   // each voice touches orbit 0 in its own engine

        d.activePlaybackIds shouldContainAll setOf("A", "B")
        val cylA = d.engine("A").shouldNotBeNull().cylinders.cylinders.first { it.id == 0 }
        val cylB = d.engine("B").shouldNotBeNull().cylinders.cylinders.first { it.id == 0 }

        // With the old global cylinder pool these would be the SAME object (last-writer-wins).
        (cylA === cylB) shouldBe false
    }
})
