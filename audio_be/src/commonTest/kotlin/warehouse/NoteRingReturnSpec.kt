/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.peak
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.klangMapOf

/**
 * A voice gives its per-note rings back on every way out of its engine (KLANG-00429, audit finding L5-03): the end of
 * its render, a cut, a stop that drops it before it sounded, a hard cleanup, and the engine's disposal with the voice
 * still listed (a released engine). Each ring once: the shelf counts no double return. Through the real dispatcher,
 * whose voices rent from its warehouse's note-ring shelf; nothing is stocked, so every ring a voice rents is an
 * allocation the test can count.
 */
class NoteRingReturnSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES

    class Fixture(val dispatcher: PlaybackEngineDispatcher, val shelf: NoteRings, val out: StereoBuffer)

    fun fixture(): Fixture {
        val clock = BackendClock(sampleRate)
        val commLink = KlangCommLink(capacity = 1024).backend
        val warehouse = ResourceWarehouse(sampleRate = sampleRate, blockFrames = blockFrames, samples = SampleStore(commLink))
        val context = AudioBackendContext(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink,
            ignitorRegistry = IgnitorRegistry().apply { registerDefaults() },
            katalystRegistry = KatalystRegistry(),
            clock = clock,
            performanceTimeMs = { 0.0 },
            warehouse = warehouse,
        )
        val dispatcher = PlaybackEngineDispatcher(context = context, clock = clock).also { it.setBackendStartTime(0.0) }

        return Fixture(dispatcher = dispatcher, shelf = warehouse.noteRings, out = StereoBuffer(blockFrames))
    }

    /** A pluck (one line) or a superpluck of three strings (three lines), its gate [gateSec] after [startSec]. */
    fun note(pid: String, sound: String, startSec: Double = 0.0, gateSec: Double = 0.05, cut: Int? = null) = ScheduledVoice(
        playbackId = pid,
        startTime = startSec,
        gateEndTime = startSec + gateSec,
        data = VoiceData.empty.copy(
            sound = sound, freqHz = 220.0, cut = cut,
            ignitorParams = klangMapOf("voices" to 3.0, "adsr.release" to 0.05),
        ),
        playbackStartTime = 0.0,
    )

    fun Fixture.schedule(pid: String, vararg voices: ScheduledVoice) {
        dispatcher.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = pid, voices = voices.toList()))
    }

    fun Fixture.render(blocks: Int): Double {
        var peak = 0.0

        repeat(blocks) {
            dispatcher.renderBlock(dispatcher.clockForTest.cursorFrame, out)
            peak = maxOf(peak, out.peak())
        }

        return peak
    }

    fun Fixture.voices(pid: String): Int = dispatcher.engine(pid)?.scheduler?.getActiveVoiceCount() ?: 0

    /** Every ring rented is back on the shelf (no stock: rented = allocated), once each. */
    fun Fixture.allBack(rented: Int) {
        withClue("rented") { shelf.allocations shouldBe rented }
        withClue("back on the shelf") { shelf.idleCount shouldBe rented }
        withClue("double returns") { shelf.doubleReturns shouldBe 0 }
    }

    "a voice that ends in its render gives its rings back, and the next note rents them again" {
        val f = fixture()

        f.schedule("song", note("song", "pluck"), note("song", "superpluck"))
        f.render(4) shouldBeGreaterThan 0.0
        f.voices("song") shouldBe 2
        f.shelf.allocations shouldBe 4
        f.shelf.idleCount shouldBe 0

        // Gate 50 ms, release 50 ms: both end in their render, the engine plays on
        f.render(200)
        f.voices("song") shouldBe 0
        f.allBack(rented = 4)
        withClue("housekept by the dispatcher's blocks") { f.shelf.isClean shouldBe true }

        // The next superpluck (0.7 s; 204 blocks are 0.59 s) rents the three clean lines: no allocation
        f.schedule("song", note("song", "superpluck", startSec = 0.7, gateSec = 1.0))
        f.render(80)
        f.voices("song") shouldBe 1
        f.shelf.allocations shouldBe 4
        f.shelf.hits shouldBe 3
    }

    "a cut voice gives its rings back when it leaves (one cut before it sounded, one after its fade)" {
        val f = fixture()

        // Two at the same onset: the second cuts the first before it sounds, which leaves at once. A pluck at 0.1 s
        // cuts the sounding one, which fades and leaves through the render loop.
        f.schedule(
            "song",
            note("song", "superpluck", gateSec = 1.0, cut = 1),
            note("song", "superpluck", gateSec = 1.0, cut = 1),
            note("song", "pluck", startSec = 0.1, gateSec = 1.0, cut = 1),
        )
        f.voices("song") shouldBe 1
        f.shelf.allocations shouldBe 6
        f.shelf.idleCount shouldBe 3

        // By the pluck's block (34) housekeeping has zeroed the first three lines: the pluck rents one of them
        f.render(60)
        f.voices("song") shouldBe 1
        f.shelf.allocations shouldBe 6
        f.shelf.hits shouldBe 1
        withClue("six lines, the pluck holds one") { f.shelf.idleCount shouldBe 5 }
        f.shelf.doubleReturns shouldBe 0
    }

    "a stop drops a voice that has not sounded yet, and its rings go back" {
        val f = fixture()

        // Promoted (built) at once, Pending until a block renders it
        f.schedule("song", note("song", "superpluck"))
        f.voices("song") shouldBe 1
        f.shelf.allocations shouldBe 3

        f.dispatcher.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
        f.voices("song") shouldBe 0
        f.allBack(rented = 3)
    }

    "a hard cleanup gives back the rings of the voices that still sound, once (the disposal after it finds none)" {
        val f = fixture()

        f.schedule("warm", note("warm", "pluck", gateSec = 2.0), note("warm", "superpluck", gateSec = 2.0))
        f.render(4)
        f.voices("warm") shouldBe 2

        f.dispatcher.cleanupHard("warm")
        f.allBack(rented = 4)
    }

    "an engine disposed with its voices still listed (a released one) gives their rings back" {
        val f = fixture()

        f.schedule("song", note("song", "pluck", gateSec = 30.0), note("song", "superpluck", gateSec = 30.0))
        f.render(4)

        val engine = f.dispatcher.engine("song").shouldNotBeNull()

        engine.scheduler.getActiveVoiceCount() shouldBe 2
        engine.dispose()
        engine.scheduler.getActiveVoiceCount() shouldBe 0
        f.allBack(rented = 4)
    }
})
