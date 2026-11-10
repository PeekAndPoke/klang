/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * The two times of a scheduled voice that decide WHEN it plays, read as the wire's NaN rule says (a non-finite value
 * reads as unset; `audio/ref/numerical-safety.md`, "Non-finite inputs"):
 *
 * - **`startTime`** (audit L1-04): a voice with a non-finite start is refused before the heap and counted as dropped.
 *   In the heap a NaN start compares false both ways against every other, which broke the heap's order: in the batch
 *   5.0, NaN, 9.0, 0.6 the 0.6 voice stayed hidden under the NaN until second 5 and was then dropped as late.
 * - **`playbackStartTime`** of a playback's first voice (audit L1-05): a non-finite one reads as unset, "now". Raw, a
 *   NaN or a minus infinity made the epoch NaN and every voice of the playback was dropped; a plus infinity made it
 *   infinite, and nothing was ever promoted. The frontend was sent that epoch too.
 */
class VoiceSchedulerNonFiniteTimesSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    class Rig {
        val commLink = KlangCommLink(capacity = 1024)
        val dispatcher = PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink.backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }
        val out = StereoBuffer(blockFrames)
        val anchors = ArrayList<Double>()

        fun renderTo(sec: Double) {
            while (dispatcher.clockForTest.cursorFrame < sec * sampleRate) {
                dispatcher.renderBlock(dispatcher.clockForTest.cursorFrame, out)
            }

            while (true) {
                val msg = commLink.frontend.feedback.receive() ?: break

                if (msg is KlangCommLink.Feedback.PlaybackAnchored) {
                    anchors.add(msg.epochSec)
                }
            }
        }
    }

    fun voice(startTime: Double, playbackStartTime: Double = 0.0) = ScheduledVoice(
        playbackId = "song",
        startTime = startTime,
        gateEndTime = startTime + 0.2,
        data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0),
        playbackStartTime = playbackStartTime,
    )

    "a NaN start in a batch is dropped alone: a later voice pushed after it plays on time" {
        for (withNan in listOf(false, true)) {
            val rig = Rig()
            val starts = if (withNan) listOf(5.0, Double.NaN, 9.0, 0.6) else listOf(5.0, 9.0, 0.6)

            rig.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = starts.map { voice(startTime = it) }))
            rig.renderTo(0.7)

            val scheduler = rig.dispatcher.engine("song").shouldNotBeNull().scheduler

            withClue("${if (withNan) "with" else "without"} the NaN: the 0.6 s voice sounds at 0.7 s") {
                scheduler.getActiveVoiceCount() shouldBe 1
            }

            withClue("${if (withNan) "with" else "without"} the NaN: only the NaN voice is counted as dropped") {
                scheduler.droppedVoiceCount() shouldBe (if (withNan) 1 else 0)
            }
        }
    }

    "a non-finite playbackStartTime on the first voice reads as now: a finite epoch, and the voice plays" {
        for (asked in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0)) {
            val rig = Rig()

            rig.dispatcher.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(voice(startTime = 0.1, playbackStartTime = asked))))
            rig.renderTo(0.2)

            withClue("playbackStartTime $asked: the epoch sent to the frontend is now, the backend's second 0") {
                rig.anchors shouldBe listOf(0.0)
            }

            withClue("playbackStartTime $asked: the voice sounds at 0.2 s") {
                rig.dispatcher.engine("song").shouldNotBeNull().scheduler.getActiveVoiceCount() shouldBe 1
            }
        }
    }
})
