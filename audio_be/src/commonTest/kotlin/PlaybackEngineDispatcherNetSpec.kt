/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * The dispatcher's net (audit L8-03, L9-01; `PlaybackEngineDispatcher`'s KDoc, "The net"): a throw from ONE playback's
 * engine, in its block or in a command, costs that playback only. The engine leaves the render and is disposed, the
 * frontend gets one `PlaybackFailed`, every other playback plays on, the failed playback ignores its commands until
 * its `Cleanup`, and then its id may start afresh.
 *
 * The throw comes from a [VoiceWatch] whose `started` throws for the playback "bad": the scheduler calls it when it
 * activates a voice, which happens in the block that reaches the voice's onset (a render throw) or, for a voice due in
 * the current block, inside `ScheduleVoices` itself (a command throw). Before the net, either throw left
 * `renderBlock` or `handle`, and no host survived it.
 */
class PlaybackEngineDispatcherNetSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    class ThrowingWatch : VoiceWatch {
        var throws = 0

        override fun started(playbackId: String, data: VoiceData, atSec: Double) {
            if (playbackId == "bad") {
                throws++
                throw IllegalStateException("audit-net test throw")
            }
        }
    }

    class Rig {
        val watch = ThrowingWatch()
        val commLink = KlangCommLink(capacity = 1024)
        val dispatcher = PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink.backend,
            performanceTimeMs = { 0.0 },
            voiceWatch = watch,
        ).also { it.setBackendStartTime(0.0) }
        val out = StereoBuffer(blockFrames)
        val failures = ArrayList<KlangCommLink.Feedback.PlaybackFailed>()

        /** Renders one block; the output's peak. */
        fun block(): Double {
            dispatcher.renderBlock(dispatcher.clockForTest.cursorFrame, out)
            readFeedback()

            return maxOf(out.left.maxOf { abs(it) }, out.right.maxOf { abs(it) })
        }

        fun send(cmd: KlangCommLink.Cmd) {
            dispatcher.handle(cmd)
            readFeedback()
        }

        private fun readFeedback() {
            while (true) {
                val msg = commLink.frontend.feedback.receive() ?: break

                if (msg is KlangCommLink.Feedback.PlaybackFailed) {
                    failures.add(msg)
                }
            }
        }
    }

    fun voice(pid: String, startTime: Double, gateEndTime: Double) = ScheduledVoice(
        playbackId = pid,
        startTime = startTime,
        gateEndTime = gateEndTime,
        data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0),
        playbackStartTime = 0.0,
    )

    fun schedule(pid: String, startTime: Double, gateEndTime: Double) =
        KlangCommLink.Cmd.ScheduleVoices(playbackId = pid, voices = listOf(voice(pid, startTime, gateEndTime)))

    "a throw in one playback's block takes that playback out, reports it once, and the other plays on" {
        val rig = Rig()

        rig.send(schedule(pid = "good", startTime = 0.0, gateEndTime = 2.0))
        // Due in block 11 or so: it activates inside a block, so the throw comes from the render
        rig.send(schedule(pid = "bad", startTime = 0.03, gateEndTime = 2.0))

        withClue("the throw has not happened yet: the bad voice is not due") {
            rig.watch.throws shouldBe 0
        }

        var peakAfter = 0.0

        for (b in 0 until 40) {
            val peak = rig.block()

            if (b >= 20) {
                peakAfter = maxOf(peakAfter, peak)
            }
        }

        withClue("engagement: the bad playback's voice did throw, inside a block") {
            rig.watch.throws shouldBe 1
        }

        withClue("one report, naming the playback and the render") {
            rig.failures.size shouldBe 1
            rig.failures[0].playbackId shouldBe "bad"
            rig.failures[0].reason shouldContain "render"
            rig.failures[0].reason shouldContain "audit-net test throw"
        }

        withClue("the failed engine is out of the render and disposed, still filed under its id") {
            rig.dispatcher.renderedEngineCountForTest shouldBe 1
            rig.dispatcher.engine("bad").shouldNotBeNull().phase shouldBe PlaybackEngine.Phase.Disposed
        }

        withClue("the other playback is still heard in the blocks after the throw") {
            peakAfter shouldBeGreaterThan 0.1
        }
    }

    "a throw in a command takes that playback out; it ignores its commands until its Cleanup, then starts afresh" {
        val rig = Rig()

        rig.send(schedule(pid = "good", startTime = 0.0, gateEndTime = 2.0))
        // Due in the current block: the scheduler activates it inside ScheduleVoices, so the throw comes from the command
        rig.send(schedule(pid = "bad", startTime = 0.0, gateEndTime = 2.0))

        withClue("engagement: the voice threw inside the command") {
            rig.watch.throws shouldBe 1
        }

        withClue("one report, naming the command") {
            rig.failures.size shouldBe 1
            rig.failures[0].playbackId shouldBe "bad"
            rig.failures[0].reason shouldContain "ScheduleVoices"
        }

        // The rest of the failed playback's commands land on its dead engine and are ignored: no fresh engine replays
        // half a song, no second voice is activated, no second report
        rig.send(schedule(pid = "bad", startTime = 0.0, gateEndTime = 2.0))
        rig.send(KlangCommLink.Cmd.SetGain(playbackId = "bad", gain = 0.5, rampSec = 0.0))

        withClue("ignored until the Cleanup") {
            rig.watch.throws shouldBe 1
            rig.failures.size shouldBe 1
            rig.dispatcher.engine("bad").shouldNotBeNull().phase shouldBe PlaybackEngine.Phase.Disposed
        }

        withClue("the other playback plays (past the house limiter's lookahead)") {
            (0 until 10).maxOf { rig.block() } shouldBeGreaterThan 0.1
        }

        rig.send(KlangCommLink.Cmd.Cleanup(playbackId = "bad"))

        withClue("the Cleanup forgets the failed engine") {
            rig.dispatcher.engine("bad") shouldBe null
        }

        // After the stop the id starts afresh: a new engine, Playing (its first voice is due later, so it is not activated)
        rig.send(schedule(pid = "bad", startTime = 5.0, gateEndTime = 6.0))

        withClue("a fresh engine under the id") {
            rig.dispatcher.engine("bad").shouldNotBeNull().phase shouldBe PlaybackEngine.Phase.Playing
            rig.dispatcher.renderedEngineCountForTest shouldBe 2
        }
    }
})
