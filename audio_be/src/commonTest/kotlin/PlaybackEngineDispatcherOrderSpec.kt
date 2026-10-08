/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The orders the dispatcher's per-block walks keep (tidy-up step 7, audit items B4.3 and B4.4): the engines render,
 * and sum into the mix, in the order they were created, and the diagnostics count every engine exactly once, the
 * cylinders listed in that order.
 *
 * The render order is read through the warehouse: two engines whose first voices start in the same block each rent
 * a cylinder in that block from one last-in, first-out shelf, so the engine that renders first takes the cylinder
 * returned last.
 */
class PlaybackEngineDispatcherOrderSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val blockSec = blockFrames.toDouble() / sampleRate

    class Rig {
        var timeMs = 0.0
        val commLink = KlangCommLink(capacity = 4096)
        val d = PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink.backend,
            performanceTimeMs = { timeMs },
        ).also { it.setBackendStartTime(0.0) }
        val out = StereoBuffer(blockFrames)
        var block = 0

        fun render(blocks: Int = 1) {
            repeat(blocks) {
                d.renderBlock(cursorFrame = block * blockFrames.toDouble(), out = out)
                block++
            }
        }

        /** Renders one block 60 ms of wall time later, so the dispatcher emits diagnostics; returns the last one. */
        fun diagnostics(): KlangCommLink.Feedback.Diagnostics {
            timeMs += 60.0
            render()

            var last: KlangCommLink.Feedback.Diagnostics? = null

            while (true) {
                val msg = commLink.frontend.feedback.receive() ?: break

                if (msg is KlangCommLink.Feedback.Diagnostics) {
                    last = msg
                }
            }

            return last.shouldNotBeNull()
        }

        fun schedule(pid: String, orbit: Int, startSec: Double = 0.0, durSec: Double = 5.0) {
            d.handle(
                KlangCommLink.Cmd.ScheduleVoice(
                    playbackId = pid,
                    voice = ScheduledVoice(
                        playbackId = pid,
                        startTime = startSec,
                        gateEndTime = startSec + durSec,
                        data = VoiceData.empty.copy(sound = "sine", freqHz = 220.0, gain = 0.1, cylinder = orbit),
                        playbackStartTime = 0.0,
                    ),
                )
            )
        }
    }

    "the engines render in the order they were created" {
        val rig = Rig()

        // A first playback rents two cylinders and is torn down: they go back to the shelf in rent order, so the
        // one rented second is on top.
        rig.schedule(pid = "warm", orbit = 1)
        rig.schedule(pid = "warm", orbit = 2, startSec = blockSec / 2)
        rig.render()

        val warm = rig.d.engine("warm").shouldNotBeNull().cylinders.cylinders.toList()

        warm.map { it.id } shouldBe listOf(1, 2)
        rig.d.cleanupHard("warm")

        // "a" is created before "b"; both first sound in the next block.
        rig.schedule(pid = "a", orbit = 0)
        rig.schedule(pid = "b", orbit = 0)
        rig.render()

        withClue("a rendered first and took the top of the shelf, b the one below") {
            rig.d.engine("a").shouldNotBeNull().cylinders.cylinders.single() shouldBeSameInstanceAs warm[1]
            rig.d.engine("b").shouldNotBeNull().cylinders.cylinders.single() shouldBeSameInstanceAs warm[0]
        }
    }

    "the diagnostics count every engine exactly, its cylinders listed in render order" {
        val rig = Rig()

        rig.schedule(pid = "a", orbit = 2)
        rig.schedule(pid = "a", orbit = 0, startSec = blockSec / 2)
        rig.schedule(pid = "b", orbit = 1)
        rig.render(4)

        // A voice whose start has been rendered past is dropped at admission, and counted.
        rig.schedule(pid = "b", orbit = 1, startSec = 0.0)

        val diag = rig.diagnostics()

        diag.activeVoiceCount shouldBe 3
        diag.cylinders.map { it.id } shouldBe listOf(2, 0, 1)
        diag.cylinders.map { it.active } shouldBe listOf(true, true, true)
        diag.warehouse.droppedVoices shouldBe 1

        withClue("the next emission counts afresh, it does not add to the last one") {
            val next = rig.diagnostics()

            next.activeVoiceCount shouldBe 3
            next.warehouse.droppedVoices shouldBe 1
        }
    }

    "a disposed engine leaves the render set, by a drain or by a hard cleanup" {
        val rig = Rig()

        rig.schedule(pid = "a", orbit = 0, durSec = 0.01)
        rig.schedule(pid = "b", orbit = 1)
        rig.schedule(pid = "c", orbit = 2)
        rig.render()
        rig.d.renderedEngineCountForTest shouldBe 3

        rig.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "a"))

        var guard = 0

        while (rig.d.engine("a") != null && guard < 2000) {
            rig.render()
            guard++
        }

        withClue("a drained and was disposed") {
            rig.d.engine("a") shouldBe null
            rig.d.renderedEngineCountForTest shouldBe 2
        }

        val b = rig.d.engine("b").shouldNotBeNull()

        rig.d.cleanupHard("b")

        withClue("b was cut at once, its voices killed with it") {
            rig.d.renderedEngineCountForTest shouldBe 1
            b.scheduler.getActiveVoiceCount() shouldBe 0
        }
    }

    "a detached engine still renders its release and still counts, after the attached ones" {
        val rig = Rig()

        // An endless delay on orbit 3 keeps the stopped playback ringing until the hold ends and the release starts.
        rig.d.handle(
            KlangCommLink.Cmd.ScheduleVoice(
                playbackId = "song",
                voice = ScheduledVoice(
                    playbackId = "song",
                    startTime = 0.0,
                    gateEndTime = 0.05,
                    data = VoiceData.empty.copy(
                        sound = "sine",
                        freqHz = 440.0,
                        gain = 0.5,
                        cylinder = 3,
                        katalystParams = mapOf("delay.wet" to 0.5, "delay.time" to 0.1, "delay.feedback" to 1.0),
                    ),
                    playbackStartTime = 0.0,
                ),
            )
        )
        // A second playback, created after "song", plays on through it all on orbit 4.
        rig.schedule(pid = "a", orbit = 4, durSec = 60.0)
        rig.render(10)
        rig.d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))

        val stopped = rig.d.engine("song").shouldNotBeNull()
        var guard = 0

        while (!stopped.releaseStarted && guard < 8000) {
            rig.render()
            guard++
        }

        stopped.releaseStarted shouldBe true

        // Scheduled again mid-release: the releasing engine is detached, the id gets a fresh one on orbit 5.
        rig.schedule(pid = "song", orbit = 5, startSec = 0.0)
        rig.d.detachedCountForTest shouldBe 1

        val diag = rig.diagnostics()

        withClue("the voices of a and of the fresh engine; the detached engine has none left") {
            diag.activeVoiceCount shouldBe 2
        }

        withClue("the attached engines first, a then the fresh one, then the detached one, still ringing") {
            diag.cylinders.map { it.id } shouldBe listOf(4, 5, 3)
            diag.cylinders.map { it.active } shouldBe listOf(true, true, true)
        }
    }
})
