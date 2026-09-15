/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_NEVER
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Silence culling through the real path: a scheduled voice, the factory, the scheduler's render
 * loop. A percussive note with a one-second release stops rendering and is counted as culled long
 * before its scheduled end, but stays in the active list as a zombie until that end; the same
 * note with `noCull()` keeps rendering.
 */
class VoiceSchedulerCullingSpec : StringSpec({

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

    /** A 100 ms note that decays to silence in 50 ms and carries a one-second release. */
    fun percussive(cull: Double?, tremoloDepth: Double? = null) = ScheduledVoice(
        playbackId = pid,
        startTime = 0.0,
        gateEndTime = 0.1,
        data = VoiceData.empty.copy(
            sound = "sine",
            freqHz = 440.0,
            adsr = AdsrDef.Std(attack = 0.001, decay = 0.05, sustain = 0.0, release = 1.0),
            cull = cull,
            tremoloSync = if (tremoloDepth != null) 4.0 else null,
            tremoloDepth = tremoloDepth,
            tremoloShape = if (tremoloDepth != null) "square" else null,
        ),
        playbackStartTime = 0.0,
    )

    /** Drives the dispatcher forward by [seconds] of blocks, continuing from where the last call stopped. */
    class Clock(private val d: PlaybackEngineDispatcher) {
        private val out = ShortArray(blockFrames * 2)
        private var cursor = 0.0

        fun render(seconds: Double) {
            val blocks = (seconds * sampleRate / blockFrames).toInt()

            for (b in 0 until blocks) {
                d.renderBlock(cursorFrame = cursor, out = out)
                cursor += blockFrames
            }
        }
    }

    "a silent release stops rendering, is counted as culled, and stays listed until its scheduled end" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = pid, voice = percussive(cull = null)))
        val scheduler = d.engine(pid).shouldNotBeNull().scheduler
        val clock = Clock(d)

        clock.render(0.4)                              // the scheduled end is at 1.1 s

        scheduler.culledVoicesTotal() shouldBe 1
        scheduler.renderingVoiceCount() shouldBe 0
        scheduler.getActiveVoiceCount() shouldBe 1     // the zombie keeps its slot

        clock.render(0.6)                              // 1.0 s: still before the scheduled end

        scheduler.getActiveVoiceCount() shouldBe 1     // the zombie is still listed

        clock.render(0.2)                              // 1.2 s: past it

        scheduler.getActiveVoiceCount() shouldBe 0     // expired on schedule
        scheduler.culledVoicesTotal() shouldBe 1       // counted once, at the cull
    }

    "a voice with a tremolo is not culled: its gated release would be cut at the first off-half" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = pid, voice = percussive(cull = null, tremoloDepth = 1.0)))
        val scheduler = d.engine(pid).shouldNotBeNull().scheduler

        Clock(d).render(0.4)

        scheduler.culledVoicesTotal() shouldBe 0
        scheduler.renderingVoiceCount() shouldBe 1
    }

    "an explicit cull(...) on a tremolo voice is the author's call and wins" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = pid, voice = percussive(cull = 0.05, tremoloDepth = 1.0)))
        val scheduler = d.engine(pid).shouldNotBeNull().scheduler

        Clock(d).render(0.4)

        scheduler.culledVoicesTotal() shouldBe 1
    }

    "noCull keeps the voice rendering through its scheduled tail" {
        val d = newDispatcher()
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = pid, voice = percussive(cull = VOICE_CULL_NEVER)))
        val scheduler = d.engine(pid).shouldNotBeNull().scheduler

        Clock(d).render(0.4)

        scheduler.renderingVoiceCount() shouldBe 1
        scheduler.culledVoicesTotal() shouldBe 0
    }
})
