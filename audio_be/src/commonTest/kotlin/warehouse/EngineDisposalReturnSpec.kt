/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.warehouse

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.master.MasterRegistry
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * Resource warehouse step 2f: **the end of a playback is the return path.** Until now every ring
 * and reverb network a playback rented became garbage when its engine was disposed, and the next
 * playback allocated the same things again inside render. Now `PlaybackEngine.dispose()` hands
 * them back to the backend's one warehouse, and the next playback's first delay or room of that
 * class is a shelf hit. `docs/plans/resource-warehouse.md`.
 *
 * Through the real dispatcher: schedule, render, `Cleanup`, drain, observe the shelf.
 */
class EngineDisposalReturnSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES

    class Fixture(val dispatcher: PlaybackEngineDispatcher, val warehouse: ResourceWarehouse, val out: ShortArray)

    fun fixture(): Fixture {
        val clock = BackendClock(sampleRate)
        val commLink = KlangCommLink(capacity = 1024).backend
        val warehouse = ResourceWarehouse(sampleRate = sampleRate, blockFrames = blockFrames, samples = SampleStore(commLink))
        val context = AudioBackendContext(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink,
            ignitorRegistry = IgnitorRegistry().apply { registerDefaults() },
            pipelineRegistry = PipelineRegistry(),
            masterRegistry = MasterRegistry(),
            clock = clock,
            performanceTimeMs = { 0.0 },
            warehouse = warehouse,
        )
        val dispatcher = PlaybackEngineDispatcher(context = context, clock = clock).also { it.setBackendStartTime(0.0) }
        return Fixture(dispatcher, warehouse, ShortArray(blockFrames * 2))
    }

    /** A short sine on orbit [cylinder] with a delay and a room — both units get rented. */
    fun wetVoice(pid: String, cylinder: Int = 0) = ScheduledVoice(
        playbackId = pid,
        startTime = 0.0,
        gateEndTime = 0.05,
        data = VoiceData.empty.copy(
            sound = "sine", freqHz = 440.0, cylinder = cylinder,
            delay = 0.5, delayTime = 0.3, delayFeedback = 0.0,
            room = 0.5, roomSize = 0.6,
        ),
        playbackStartTime = 0.0,
    )

    fun Fixture.render(blocks: Int): Double {
        var peak = 0.0
        repeat(blocks) {
            dispatcher.renderBlock(dispatcher.clockForTest.cursorFrame, out)
            for (i in out.indices) peak = maxOf(peak, abs(out[i].toDouble()))
        }
        return peak
    }

    "a drained playback returns its ring and its reverb network to the shelf" {
        val f = fixture()
        f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "song", voice = wetVoice("song")))

        f.render(4) shouldBeGreaterThan 0.0 // positive control: the voice sounded
        val engine = f.dispatcher.engine("song").shouldNotBeNull()
        val cylinder = engine.cylinders.cylinders.single()
        val ring = cylinder.delay.delayLine.shouldNotBeNull().ring
        val unit = cylinder.reverb.reverb.shouldNotBeNull()
        f.warehouse.sized.shelfCount shouldBe 0
        f.warehouse.reverbs.idleCount shouldBe 0

        f.dispatcher.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
        f.render(2000) // ~5.8 s: the tails drain, the engine goes idle, the dispatcher disposes it

        f.dispatcher.engine("song").shouldBeNull()
        f.warehouse.sized.shelfCount shouldBe 1
        f.warehouse.reverbs.idleCount shouldBe 1
        // The cylinder forgot its units: whoever rents them next owns them.
        cylinder.delay.delayLine.shouldBeNull()
        cylinder.reverb.reverb.shouldBeNull()
        engine.cylinders.cylinders.size shouldBe 0
        // ...and they are the very instances, cleared.
        f.warehouse.sized.rent(1) shouldBeSameInstanceAs ring
        (ring.left.all { it == 0.0 }) shouldBe true
        f.warehouse.reverbs.rent() shouldBeSameInstanceAs unit
    }

    "the NEXT playback's first delay and room are shelf hits — no allocation in render" {
        val f = fixture()
        f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "first", voice = wetVoice("first")))
        f.render(4)
        f.dispatcher.handle(KlangCommLink.Cmd.Cleanup(playbackId = "first"))
        f.render(2000)
        f.warehouse.sized.allocations shouldBe 1
        f.warehouse.reverbs.allocations shouldBe 1

        f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "second", voice = wetVoice("second")))
        f.render(4) shouldBeGreaterThan 0.0

        f.warehouse.sized.allocations shouldBe 1 // still
        f.warehouse.sized.hits shouldBe 1
        f.warehouse.reverbs.allocations shouldBe 1
        f.warehouse.reverbs.hits shouldBe 1
    }

    "hard cleanup (warmup teardown) returns too" {
        val f = fixture()
        f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "warm", voice = wetVoice("warm")))
        f.render(4)

        f.dispatcher.cleanupHard("warm")

        f.dispatcher.engine("warm").shouldBeNull()
        f.warehouse.sized.shelfCount shouldBe 1
        f.warehouse.reverbs.idleCount shouldBe 1
    }

    "a disposed engine's master chains return their units as well" {
        val f = fixture()
        f.dispatcher.handle(
            KlangCommLink.Cmd.RegisterMaster(
                playbackId = "song", name = "wet",
                dsl = MasterDsl.of(
                    MasterStageDsl.Delay(wet = 0.5, timeSeconds = 0.3),
                    MasterStageDsl.Reverb(wet = 0.4, roomSize = 5.0),
                ),
            )
        )
        f.warehouse.sized.allocations shouldBe 1
        f.warehouse.reverbs.allocations shouldBe 1

        f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "song", voice = wetVoice("song", cylinder = 3)))
        f.render(4)
        f.dispatcher.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
        f.render(2000)

        f.dispatcher.engine("song").shouldBeNull()
        // Two rings (master + orbit), two networks (master + orbit) — all back.
        f.warehouse.sized.shelfCount shouldBe 2
        f.warehouse.reverbs.idleCount shouldBe 2
        f.warehouse.sized.doubleReturns shouldBe 0
        f.warehouse.reverbs.doubleReturns shouldBe 0
    }

    "the shelf serves a returned ring zeroed — a new playback cannot hear the old one's echo" {
        val f = fixture()
        f.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "loud", voice = wetVoice("loud")))
        f.render(20) // 58 ms of a 440 Hz sine into a 300 ms delay: the ring holds real audio
        f.dispatcher.cleanupHard("loud")

        val ring = f.warehouse.sized.rent(1).shouldNotBeNull()
        var energy = 0.0
        for (i in ring.left.indices) energy += abs(ring.left[i]) + abs(ring.right[i])
        energy shouldBe 0.0
        f.warehouse.sized.giveBack(ring)
    }
})
