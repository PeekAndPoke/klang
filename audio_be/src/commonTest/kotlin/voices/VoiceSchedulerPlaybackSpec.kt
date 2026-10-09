/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngine
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.RealtimeVoice
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * A scheduler serves ONE playback (tidy-up step 8, audit item B3.3): one context, made by the playback's first voice
 * (timeline or realtime) and kept for every later one, dropped by `cleanup`, and made afresh by the next voice (a
 * resume). The epoch is read through the admission law: a voice whose start, on the context's epoch, has already
 * been rendered past is dropped and counted.
 */
class VoiceSchedulerPlaybackSpec : StringSpec({

    val sampleRate = 48_000
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES

    fun blocksFor(seconds: Int): Int = (seconds * sampleRate + blockFrames - 1) / blockFrames

    class Rig {
        val clock = BackendClock(sampleRate)
        val context = AudioBackendContext.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            clock = clock,
            phasePoolSeed = 1,
        )
        val engine = PlaybackEngine.create(context = context, playbackId = "song")
        val scheduler = engine.scheduler
        private val mix = StereoBuffer(blockFrames)

        /** A timeline voice starting [startSec] after the playback's start (its epoch), 5 s long. */
        fun schedule(startSec: Double = 0.0) {
            scheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = "song",
                    startTime = startSec,
                    gateEndTime = startSec + 5.0,
                    data = VoiceData.empty.copy(sound = "sine", freqHz = 220.0),
                    playbackStartTime = 0.0,
                )
            )
        }

        fun press(liveId: Int) {
            scheduler.startRealtimeVoice(
                playbackId = "song",
                voice = RealtimeVoice(liveId = liveId, data = VoiceData.empty.copy(sound = "sine"), gateDurSec = 5.0),
            )
        }

        fun render(blocks: Int) {
            repeat(blocks) {
                mix.clear()
                engine.renderInto(mix, clock.cursorFrame)
                clock.cursorFrame += blockFrames
            }
        }
    }

    "the first voice makes the context and later voices keep its epoch" {
        val rig = Rig()

        rig.schedule()
        rig.render(10)

        // Start 0 on the first voice's epoch (block 0) was rendered past long ago: dropped. On a fresh epoch
        // ("now") it would be on time.
        rig.schedule()

        rig.scheduler.getActiveVoiceCount() shouldBe 1
        rig.scheduler.droppedVoiceCount() shouldBe 1
    }

    "cleanup drops the context: the next voice, a resume, makes a fresh one, with a fresh count" {
        val rig = Rig()

        rig.schedule()
        rig.render(10)
        rig.schedule()
        rig.scheduler.droppedVoiceCount() shouldBe 1

        rig.scheduler.cleanup()

        withClue("no context: nothing dropped is counted") { rig.scheduler.droppedVoiceCount() shouldBe 0 }

        rig.schedule()

        withClue("the resumed voice is on time on its fresh epoch, and plays beside the first one ringing out") {
            rig.scheduler.droppedVoiceCount() shouldBe 0
            rig.scheduler.getActiveVoiceCount() shouldBe 2
        }
    }

    "a timeline voice after a realtime one keeps the realtime context's epoch" {
        val rig = Rig()

        rig.render(4)
        rig.press(liveId = 1)
        rig.render(10)

        // The realtime context's epoch is block 4: start 0 on it lies ten blocks back.
        rig.schedule()

        rig.scheduler.droppedVoiceCount() shouldBe 1
    }

    "a realtime voice after a timeline one keeps the context, and its count" {
        val rig = Rig()

        rig.schedule()
        rig.render(10)
        rig.schedule()
        rig.scheduler.droppedVoiceCount() shouldBe 1

        rig.press(liveId = 1)

        rig.scheduler.droppedVoiceCount() shouldBe 1
        rig.scheduler.getActiveVoiceCount() shouldBe 2
    }

    "clearScheduled and replaceVoices without a cutoff drop every voice not yet promoted" {
        val rig = Rig()

        rig.schedule(startSec = 1.0)
        rig.schedule(startSec = 2.0)
        rig.scheduler.clearScheduled()
        rig.render(blocksFor(seconds = 3))

        rig.scheduler.getActiveVoiceCount() shouldBe 0

        rig.schedule(startSec = 4.0)
        rig.scheduler.replaceVoices(voices = emptyList())
        rig.render(blocksFor(seconds = 2))

        rig.scheduler.getActiveVoiceCount() shouldBe 0
    }

    "replaceVoices after a cutoff keeps the voices before it, on the context's epoch" {
        val rig = Rig()

        // The playback starts 100 blocks (0.267 s) into the backend's timeline: that is its epoch.
        rig.render(100)
        rig.schedule()
        rig.render(10)
        rig.schedule(startSec = 0.5)
        rig.schedule(startSec = 1.2)
        rig.schedule(startSec = 1.5)

        // The cutoff is 1.0 s after the epoch: the voice at 0.5 stays, those at 1.2 and 1.5 go. A cutoff read
        // without the epoch (at 1.0 s on the backend's own clock, 1.267 s) would keep the one at 1.2.
        rig.scheduler.replaceVoices(voices = emptyList(), afterTimeSec = 1.0)
        rig.render(blocksFor(seconds = 2))

        rig.scheduler.getActiveVoiceCount() shouldBe 2
    }
})
