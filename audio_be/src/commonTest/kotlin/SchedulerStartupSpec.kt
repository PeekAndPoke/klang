/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * Block-framing **Track B**: the scheduler guarantees the contract the DSP was verified against.
 *
 * **B1 — the startup race.** In production, commands are handled BETWEEN renders (the worklet's
 * `port.onmessage`, the JVM backend's drain loop). Before B1, `clock.cursorFrame` was set on entry to
 * `renderBlock` and never advanced, so between renders it pointed at the block **just rendered**. A
 * new playback's epoch was anchored to it, its first voice (relative start 0) therefore fell inside
 * a block that had already gone by, and the next render found it exactly one block late — admitted
 * by the old 5-block tolerance window with its first 128 frames of attack silently skipped. Every
 * playback, every time. The realtime path had noticed and compensated with `+ blockFrames`; the
 * timeline path had not.
 *
 * The fix is a convention, not a floor: **between renders the clock is the NEXT block to be
 * rendered.** `renderBlock` advances it on exit. That is what every test rig had always assumed,
 * which is why this spec has to drive the real [PlaybackEngineDispatcher] rather than a rig — a rig
 * cannot tell the two conventions apart.
 *
 * **B2 — no late voices, ever.** Admission is `start >= the block being promoted for`; anything
 * older is dropped and counted. B2 cannot land before B1 or it would eat every first note, so they
 * are one change and one spec.
 */
class SchedulerStartupSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val pid = "song"

    fun newDispatcher(): PlaybackEngineDispatcher =
        PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink(capacity = 1024).backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }

    // A DC-ish source with a 10 ms attack and the VCA on, so the ENVELOPE is what gets measured:
    // an attack that starts at position 0 opens from exactly 0.0; one that lost its first block is
    // already ~0.03 in when its first frame renders.
    fun firstVoice(startTime: Double = 0.0) = ScheduledVoice(
        playbackId = pid,
        startTime = startTime,
        gateEndTime = startTime + 1.0,
        data = VoiceData.empty.copy(
            sound = "sine",
            freqHz = 440.0,
            adsr = AdsrDef.Std(attack = 0.01, decay = 0.0, sustain = 1.0, release = 0.01),
        ),
        // A start the frontend declared in the PAST — the ordinary case: by the time the message
        // lands, wall time has moved on. The epoch must snap forward, never backward.
        playbackStartTime = 0.0,
    )

    /** Renders one block through the dispatcher and returns the left channel's peak per frame. */
    fun renderOne(d: PlaybackEngineDispatcher, cursor: Double): DoubleArray {
        val out = ShortArray(blockFrames * 2)
        d.renderBlock(cursorFrame = cursor, out = out)
        return DoubleArray(blockFrames) { abs(out[it * 2].toDouble()) / Short.MAX_VALUE }
    }

    "B1: renderBlock leaves the clock on the NEXT block, so 'now' between renders is renderable" {
        val d = newDispatcher()

        renderOne(d, 0.0)

        // Before B1 this read 0.0 — the block just rendered. A command handled here would have
        // anchored a playback to a block that had already gone by.
        d.clockForTest.cursorFrame shouldBe blockFrames.toDouble()
    }

    "B1: a playback's first note, scheduled between two renders, is admitted for the very next block with its attack at zero" {
        val d = newDispatcher()

        // Block 0 renders with nothing scheduled: this is the production shape, where the worklet
        // has been running before the frontend's first batch arrives.
        renderOne(d, 0.0).max() shouldBeLessThan 1e-9

        // The first voice arrives BETWEEN renders, declaring a start (0.0) that has already passed.
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = pid, voice = firstVoice()))

        // Structural half: admitted (the eager promote saw it inside the next block's window) and
        // not dropped by B2. Under the old convention the epoch was block 0 — already rendered.
        val scheduler = d.engine(pid).shouldNotBeNull().scheduler
        scheduler.getActiveVoiceCount() shouldBe 1
        scheduler.droppedVoiceCount(pid) shouldBe 0

        // Acoustic half. The dispatcher path includes MasterStage, whose limiter lookahead delays
        // the output by ~220 frames, so the onset lands in block 2, not block 1 (that is why
        // SampleVoiceOnsetSpec drives a bare PlaybackEngine instead). Concatenate three blocks and
        // look at the first audible frames wherever they fall.
        val out = (1..3).flatMap { renderOne(d, (it * blockFrames).toDouble()).toList() }
        val first = out.indexOfFirst { it > 1e-9 }
        withClue("something must sound") { (first >= 0) shouldBe true }

        // The attack is 10 ms = 441 frames on the default (quadratic) curve. Started at position
        // 0, the envelope over the first 32 rendered frames stays <= (32/441)^2 = 0.005, and the
        // window MEASURES 0.0024. Under the old convention the voice was one block LATE: the
        // envelope opened at position 128, (128/441)^2 = 0.084 times the sine, and the same window
        // measured 0.055. A first cut at 0.12 sat above BOTH and passed the mutant; 0.02 sits
        // between them with 8x on this side and 2.7x on the other. (Measured, not assumed — the
        // linear-attack arithmetic that produced 0.12 was wrong about the curve.)
        val opening = (first until minOf(first + 32, out.size)).maxOf { out[it] }
        opening shouldBeLessThan 0.02
    }

    "B2: a voice whose start has already been rendered past is dropped and counted, never smeared" {
        val d = newDispatcher()
        // Establish the epoch with a first voice, render it well past.
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = pid, voice = firstVoice()))
        for (b in 0 until 10) renderOne(d, (b * blockFrames).toDouble())

        // A voice scheduled 3 blocks BEHIND the clock — inside the old 5-block window, which used to
        // admit it late (fresh oscillator phase, envelope three blocks in: neither on time nor
        // shifted). Now it must simply not exist.
        val scheduler = d.engine(pid).shouldNotBeNull().scheduler
        val before = scheduler.getActiveVoiceCount()
        val lateSec = (d.clockForTest.cursorFrame - 3 * blockFrames) / sampleRate
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = pid, voice = firstVoice(startTime = lateSec)))

        scheduler.getActiveVoiceCount() shouldBe before
        scheduler.droppedVoiceCount(pid) shouldBe 1
    }

    "B2: the boundary is inclusive — a start exactly AT the next block is on time" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = pid, voice = firstVoice()))
        for (b in 0 until 4) renderOne(d, (b * blockFrames).toDouble())

        // Exactly at the clock, i.e. the first frame of the block about to be rendered. The frontend
        // schedules on block boundaries all the time; an exclusive test here would drop all of them.
        val scheduler = d.engine(pid).shouldNotBeNull().scheduler
        val before = scheduler.getActiveVoiceCount()
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = pid, voice = firstVoice(startTime = d.clockForTest.cursorFrame / sampleRate)))

        withClue("admitted") { scheduler.getActiveVoiceCount() shouldBe before + 1 }
        scheduler.droppedVoiceCount(pid) shouldBe 0
    }

    // NOT a row here: "late STATE still applies — a master swap on a dropped voice is not lost".
    // promoteScheduled applies `data.master` BEFORE the admission test, on purpose, and B2 kept the
    // drop exactly where the old window check sat, so the ordering is unchanged. Observing it needs
    // a registered master chain (requestSwap on an unknown name is a silent no-op); the code comment
    // at the call site is the record for now.
})
