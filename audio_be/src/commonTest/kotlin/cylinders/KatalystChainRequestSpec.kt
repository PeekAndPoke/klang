/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The Katalyst-in-pattern path end to end on the backend: a `katalyst(…)` reference rides the
 * voice stream, the scheduler consumes it at promotion, and the cylinder for that event's ORBIT
 * installs the chain (Katalyst step 3a).
 *
 * The twin of the master's `MasterBusTest` rows for the same three properties: it applies whether
 * or not the event sounds, it applies before the late-sound guards, and it reaches the right host.
 * See `docs/tasks/katalyst-dsl.md` §6.
 */
class KatalystChainRequestSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    /** A chain with neither delay, reverb, body nor duck, so the stage list says which one runs. */
    val gainOnly = KatalystDsl.of(KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(1.5)))

    fun newDispatcher(): PlaybackEngineDispatcher =
        PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }

    /** A short note on [orbit], optionally carrying a chain reference. */
    fun note(orbit: Int, katalyst: String? = null, startTime: Double = 0.0) = ScheduledVoice(
        playbackId = "song",
        startTime = startTime,
        gateEndTime = startTime + 0.05,
        data = VoiceData.empty.copy(
            sound = "sine",
            freqHz = 440.0,
            gain = 0.2,
            cylinder = orbit,
            katalyst = katalyst,
        ),
        playbackStartTime = 0.0,
    )

    /** The control-only carrier a top-level `katalyst(…)` emits: a chain reference, no sound. */
    fun chainEvent(name: String, orbit: Int, startTime: Double = 0.0) = ScheduledVoice(
        playbackId = "song",
        startTime = startTime,
        gateEndTime = startTime + 1.0,
        data = VoiceData.empty.copy(katalyst = name, cylinder = orbit, control = true),
        playbackStartTime = 0.0,
    )

    fun render(d: PlaybackEngineDispatcher, blocks: Int, from: Int = 0) {
        val out = ShortArray(blockFrames * 2)

        for (b in from until from + blocks) {
            d.renderBlock(cursorFrame = (b * blockFrames).toDouble(), out = out)
        }
    }

    fun cylinder(d: PlaybackEngineDispatcher, orbit: Int): Cylinder? =
        d.engine("song")?.cylinders?.cylinders?.firstOrNull { it.id == orbit }

    /** True while the cylinder runs the classic chain, which is the only one with all seven stages. */
    fun Cylinder.runsClassic(): Boolean = reverb != null && delay != null && body != null && duck != null

    "a control-only chain event installs the chain on ITS orbit, and creates no voice" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "bus", dsl = gainOnly))
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(chainEvent("bus", orbit = 2)))
        )

        render(d, blocks = 4)

        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 0

        val orbit2 = cylinder(d, 2).shouldNotBeNull()

        orbit2.runsClassic() shouldBe false
        orbit2.reverb.shouldBeNull()
        orbit2.pipeline.size shouldBe 1
        withClue("a chain declared on orbit 2 must not reach any other orbit") {
            cylinder(d, 0).shouldBeNull()
        }
    }

    "a sounding event carries the chain too, and the note still sounds" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "bus", dsl = gainOnly))
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(note(orbit = 2, katalyst = "bus"), note(orbit = 0)),
            )
        )

        render(d, blocks = 4)

        d.engine("song")?.scheduler?.getActiveVoiceCount() shouldBe 2

        cylinder(d, 2).shouldNotBeNull().runsClassic() shouldBe false
        withClue("the orbit that declared nothing keeps the historical chain") {
            cylinder(d, 0).shouldNotBeNull().runsClassic() shouldBe true
        }
    }

    "a registration that arrives AFTER the reference lands on the next block, unasked" {
        val d = newDispatcher()
        // The reference first, with no chain registered: a one-shot `.katalyst(…)` emits once, so
        // nothing will ask again. The orbit is silent, and the per-block poll in
        // `Cylinders.processAndMix` is what has to close the gap.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(chainEvent("bus", orbit = 2)))
        )

        render(d, blocks = 2)

        withClue("an unknown name installs nothing, and is not resolved to classic either") {
            cylinder(d, 2).shouldNotBeNull().runsClassic() shouldBe true
        }

        d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "bus", dsl = gainOnly))

        render(d, blocks = 1, from = 2)

        cylinder(d, 2).shouldNotBeNull().runsClassic() shouldBe false
    }

    "a LATE event still installs its chain, though its note is dropped" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.RegisterKatalyst(playbackId = "song", name = "bus", dsl = gainOnly))
        // The first note fixes the playback epoch at t = 0.
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(note(orbit = 0))))

        render(d, blocks = 344)

        // A second note on orbit 2, stamped about a second in the past: the note is rightly
        // dropped (no late voices, ever), the STATE it carries must still land.
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(note(orbit = 2, katalyst = "bus", startTime = 0.0)),
            )
        )

        render(d, blocks = 4, from = 344)

        d.engine("song")?.scheduler?.droppedVoiceCount("song") shouldBe 1
        cylinder(d, 2).shouldNotBeNull().runsClassic() shouldBe false
    }
})
