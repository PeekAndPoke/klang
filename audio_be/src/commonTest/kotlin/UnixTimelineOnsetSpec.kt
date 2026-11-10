/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_bridge.RealtimeVoice
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * A voice on the frame grid starts ON its frame on a live timeline, whose backend start is a Unix second (about
 * 1.8e9, where a double resolves 2.4e-7 s, a hundredth of a frame): audit L2-03 and L9-03. The scheduler's contract,
 * "a voice's first generate() has voiceElapsedFrames == 0", held in seconds and broke in frames: half of all realtime
 * notes and of all playbacks' first notes started a frame before their block, and a playback's notes on a dyadic
 * grid were then all a frame early. Every spec rig used to start the timeline at 0.0 or 1000.0.
 *
 * Read through the sound: a saw under the default 10 ms attack is exactly 0 on the frame its envelope starts and not
 * 0 on the next; a voice one frame early renders its first frame at envelope position 1, not 0.
 */
class UnixTimelineOnsetSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES
    val unixStarts = listOf(1794268800.5, 1800000000.123)
    val saw = VoiceData.empty.copy(sound = "saw", freqHz = 220.0, pan = 0.0)

    class Rig(startTimeSec: Double, cursorFrame: Double) {
        val clock = BackendClock(sampleRate).also {
            it.startTimeSec = startTimeSec
            it.cursorFrame = cursorFrame
        }
        val engine = PlaybackEngine.create(
            context = AudioBackendContext.create(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                commLink = KlangCommLink(capacity = 1024).backend,
                clock = clock,
                phasePoolSeed = 1,
            ),
            playbackId = "song",
        )
        private val mix = StereoBuffer(blockFrames)

        /** Renders the block at the clock's cursor and returns its left channel. */
        fun renderLeft(): DoubleArray {
            mix.clear()
            engine.renderInto(mix, clock.cursorFrame)
            clock.cursorFrame += blockFrames

            return mix.left.copyOf()
        }
    }

    /** True when the block's frame [i] is the envelope's first (exact 0) and the frame after it sounds. */
    fun startsAt(block: DoubleArray, i: Int): Boolean = block[i] == 0.0 && block[i + 1] != 0.0

    "the conversion is exact for a time on the frame grid, at a Unix-second start too (VoiceFactory.framesAt)" {
        val factory = VoiceFactory(sampleRate = sampleRate, blockFrames = blockFrames, scratchBuffers = ScratchBuffers(blockFrames))
        var off = 0

        for (k in 0 until 20000) {
            val frame = k.toDouble() * blockFrames + (k % 7)

            if (factory.framesAt(frame / sampleRate) != frame) {
                off++
            }
        }

        withClue("frames off the grid among 20000") { off shouldBe 0 }
        withClue("a time between two frames still floors") { factory.framesAt(100.5 / sampleRate) shouldBe 100.0 }
    }

    for (start in unixStarts) {
        "a playback's first note starts ON the block it is admitted for, at backend start $start" {
            for (k in 3 until 35) {
                val cursor = k.toDouble() * blockFrames
                val rig = Rig(startTimeSec = start, cursorFrame = cursor)

                // A start asked for in the past: the epoch snaps to "now", the cursor
                rig.engine.scheduler.scheduleVoice(
                    ScheduledVoice(playbackId = "song", data = saw, startTime = 0.0, gateEndTime = 0.5, playbackStartTime = start),
                )

                withClue("cursor block $k: the attack's first frame is the block's first") { startsAt(rig.renderLeft(), 0) shouldBe true }
            }
        }

        "a realtime note starts ON the cursor, at backend start $start" {
            for (k in 3 until 35) {
                val cursor = k.toDouble() * blockFrames
                val rig = Rig(startTimeSec = start, cursorFrame = cursor)

                rig.engine.scheduler.startRealtimeVoice("song", RealtimeVoice(liveId = 1, data = saw, gateDurSec = 0.5))

                withClue("cursor block $k: the attack's first frame is the block's first") { startsAt(rig.renderLeft(), 0) shouldBe true }
            }
        }

        "a playback's notes on a 16th grid (0.125 s, 6000 frames) start on their frames, at backend start $start" {
            val anchor = 3.0 * blockFrames
            val rig = Rig(startTimeSec = start, cursorFrame = anchor)
            val notes = 12

            for (n in 0 until notes) {
                rig.engine.scheduler.scheduleVoice(
                    ScheduledVoice(playbackId = "song", data = saw, startTime = n * 0.125, gateEndTime = n * 0.125 + 0.01, playbackStartTime = start),
                )
            }

            var checked = 0

            while (checked < notes) {
                val blockStart = rig.clock.cursorFrame
                val block = rig.renderLeft()
                val onset = anchor + checked * 6000.0

                if (onset < blockStart + blockFrames) {
                    val i = (onset - blockStart).toInt()

                    withClue("note $checked at frame $onset") { startsAt(block, i) shouldBe true }
                    checked++
                }
            }
        }
    }
})
