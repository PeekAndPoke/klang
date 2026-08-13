/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * **The engine must still make sound after a very long uptime.**
 *
 * `cursorFrame` advances every block for the life of the backend — whether or not anything is
 * playing — so it is a pure function of *uptime*, not of use. It used to be an `Int`, which
 * overflows after **12.4 h at 48 kHz** (13.5 h at 44.1). The failure was silent: audio simply
 * stopped, with no crash and no error, and a browser tab left open overnight was enough to hit it.
 *
 * Two things make this class of bug worth a dedicated spec:
 *
 *  1. **It is invisible to every other test**, because nobody runs a suite for twelve hours. The
 *     only way to see it is to place the cursor near the boundary deliberately, which is what this
 *     does.
 *  2. **The degradation starts before the wrap**, not at it: scheduling round-trips through seconds
 *     (`startTime = frame / sampleRate`), and the seconds→frames conversion loses its integers well
 *     before `cursorFrame` itself would flip.
 *
 * The fix is `Double`, which holds integers exactly to 2^53 (~5,950 years at 48 kHz) with no drift —
 * see `RenderClock.cursorFrame` for why `Double` and not `Long`.
 */
class LongRunningTimelineSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun newDispatcher() = PlaybackEngineDispatcher.create(
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        commLink = KlangCommLink(capacity = 1024).backend,
        performanceTimeMs = { 0.0 },
    ).also { it.setBackendStartTime(0.0) }

    /** Schedules one sustained voice just after [startFrame] and reports how many blocks sounded. */
    fun blocksWithAudio(startFrame: Double): Int {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)
        val startSec = startFrame / sampleRate + 0.01

        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "p",
                voices = listOf(
                    ScheduledVoice(
                        playbackId = "p",
                        data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0),
                        startTime = startSec,
                        gateEndTime = startSec + 1.0,
                        playbackStartTime = 0.0,
                    )
                ),
            )
        )

        var heard = 0
        var frame = startFrame
        repeat(60) {
            d.renderBlock(cursorFrame = frame, out = out)
            if (out.any { abs(it.toInt()) > 200 }) heard++
            frame += blockFrames
        }
        return heard
    }

    "a voice still sounds after an Int-overflow's worth of uptime" {
        // 2^31 frames is where the old Int cursor died — 13.5 h at this sample rate.
        val pastTheOldLimit = 2_200_000_000.0

        blocksWithAudio(pastTheOldLimit) shouldBe blocksWithAudio(0.0)
    }

    "and after a hundred years of uptime" {
        // 100 years ≈ 1.39e14 frames — far past Int, far inside Double's exact-integer range.
        val hundredYears = 100.0 * 365.25 * 24 * 3600 * sampleRate

        (blocksWithAudio(hundredYears) > 0) shouldBe true
    }

    "the clock stays exact at that magnitude — no drift, no lost frames" {
        // Double is exact for integers below 2^53; the whole fix rests on that. If someone widens a
        // relative offset to Double, or narrows an absolute frame back to Int, this is what breaks.
        val clock = BackendClock(sampleRate)
        val hundredYears = 100.0 * 365.25 * 24 * 3600 * sampleRate

        clock.cursorFrame = hundredYears
        (clock.cursorFrame + 1.0 != clock.cursorFrame) shouldBe true

        // Advancing a block at a time must land exactly where the arithmetic says.
        clock.cursorFrame = hundredYears
        repeat(1000) { clock.cursorFrame += blockFrames }
        clock.cursorFrame shouldBe hundredYears + 1000.0 * blockFrames
    }
})
