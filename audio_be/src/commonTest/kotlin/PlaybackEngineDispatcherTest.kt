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
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.voices.TestSamples
import io.peekandpoke.klang.audio_bridge.PipelineDsl
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

    "RegisterIgnitor lands on the per-playback engine fork, not the shared parent" {
        val d = newDispatcher()
        val dsl = d.ignitorRegistry.get("sine").shouldNotBeNull()   // a built-in dsl to re-register under a new name

        d.handle(KlangCommLink.Cmd.RegisterIgnitor(playbackId = "song", name = "myosc", dsl = dsl))

        // Resolvable on the engine's fork (custom locally + built-ins via the parent)...
        val scheduler = d.engine("song").shouldNotBeNull().scheduler
        scheduler.containsIgnitor("myosc") shouldBe true
        scheduler.containsIgnitor("sine") shouldBe true
        // ...but NOT on the shared parent — it dies with the engine.
        d.ignitorRegistry.get("myosc") shouldBe null
    }

    "RegisterPipeline lands on the per-playback engine fork, not the shared parent" {
        val d = newDispatcher()

        d.handle(KlangCommLink.Cmd.RegisterPipeline(playbackId = "song", name = "myeng", dsl = PipelineDsl.pedal))

        // Resolvable on the engine's fork...
        d.engine("song").shouldNotBeNull().scheduler.resolvePipeline("myeng") shouldBe PipelineDsl.pedal
        // ...but the shared parent doesn't know "myeng" (falls back to the default engine).
        d.pipelineRegistry.get("myeng") shouldNotBe PipelineDsl.pedal
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

    "ReplaceVoices does not double a voice already promoted to active (live-update race)" {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)
        val far = (sampleRate * 10).toDouble() // frame where the startTime=10s voice becomes due

        // 1. Schedule a future voice — stays in the heap at frame 0.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(voice("song", startTime = 10.0, gateEndTime = 20.0))
            )
        )
        // 2. Advance the clock so it is promoted to active (the race precondition).
        d.renderBlock(far, out)
        d.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBe 1

        // 3. A live update re-sends the SAME voice (its replacement) while it is already active.
        d.handle(
            KlangCommLink.Cmd.ReplaceVoices(
                playbackId = "song",
                voices = listOf(voice("song", startTime = 10.0, gateEndTime = 20.0)),
                afterTimeSec = null
            )
        )
        d.renderBlock(far + blockFrames, out)

        // Without the identity dedup the resend promotes alongside the active one → 2 (the bug).
        d.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBe 1
    }

    "ReplaceVoices keeps legit simultaneous same-time voices (chord/superimpose safety)" {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)
        val far = (sampleRate * 10).toDouble()

        // Two voices at the SAME time differing only in payload — like two chord tones / layers.
        val a = voice("song", startTime = 10.0, gateEndTime = 20.0)
        val b = a.copy(data = a.data.copy(freqHz = 660.0))
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(a, b)))
        d.renderBlock(far, out)
        d.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBe 2

        // Re-send both. Full-identity 1-to-1 matching drops each against its own twin — both are kept,
        // never collapsed to one (which a sourceId+time key would wrongly do).
        d.handle(KlangCommLink.Cmd.ReplaceVoices(playbackId = "song", voices = listOf(a, b), afterTimeSec = null))
        d.renderBlock(far + blockFrames, out)
        d.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBe 2
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
        d.renderBlock(0.0, out)

        d.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBe 0
    }

    "Cleanup drains then disposes the engine once idle" {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)

        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(futureVoice("song"))))
        d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
        d.renderBlock(0.0, out)   // cleared future voice → engine fully idle → disposed

        d.engine("song") shouldBe null
    }

    "two playbacks on the same orbit get independent cylinders (the isolation fix)" {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)

        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "A", voice = voice("A", cylinder = 0)))
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "B", voice = voice("B", cylinder = 0)))
        d.renderBlock(0.0, out)   // each voice touches orbit 0 in its own engine

        d.activePlaybackIds shouldContainAll setOf("A", "B")
        val cylA = d.engine("A").shouldNotBeNull().cylinders.cylinders.first { it.id == 0 }
        val cylB = d.engine("B").shouldNotBeNull().cylinders.cylinders.first { it.id == 0 }

        // With the old global cylinder pool these would be the SAME object (last-writer-wins).
        (cylA === cylB) shouldBe false
    }

    "a late-created engine's first voice is not judged in the past (clock snap)" {
        val d = newDispatcher()   // startTimeSec = 0 via setBackendStartTime(0.0)
        val out = ShortArray(blockFrames * 2)

        // The backend has been running ~10s (global cursor advanced); no engines exist yet.
        val lateFrame = sampleRate * 10
        d.renderBlock(lateFrame.toDouble(), out)

        // The frontend stamped playbackStartTime 100ms "ago" (delivery latency) — well past the
        // ~13ms past-cutoff. A fresh engine starting at frame 0 would compute its epoch against the
        // backend START time and discard this; the shared clock makes the epoch snap to "now".
        val nowSec = lateFrame.toDouble() / sampleRate
        val late = ScheduledVoice(
            playbackId = "late",
            startTime = 0.0,
            gateEndTime = 1.0,
            data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0, cylinder = 0),
            playbackStartTime = nowSec - 0.1,
        )
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "late", voice = late))

        d.engine("late").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBeAtLeast 1
    }
})
