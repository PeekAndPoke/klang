/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.RealtimeVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/**
 * **The realtime path: [KlangCommLink.Cmd.StartRealtimeVoice] promotes straight to active.**
 *
 * Realtime voices (MIDI keyboard & friends) carry no start time on the wire — the backend stamps
 * "now" at receipt and the voice must sound in the very next rendered block, without touching the
 * scheduled heap or the epoch machinery of the timeline path (see docs/tasks/midi-keyboard-playground.md).
 */
class RealtimeVoiceSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun newDispatcher() = PlaybackEngineDispatcher.create(
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        commLink = KlangCommLink(capacity = 1024).backend,
        performanceTimeMs = { 0.0 },
    ).also { it.setBackendStartTime(0.0) }

    // Sustain pinned to 1.0 so "still sounding" is a statement about the GATE, not about a
    // default envelope's decay tail.
    val sustained = VoiceData.empty.copy(
        sound = "sine",
        freqHz = 440.0,
        adsr = AdsrDef.Std(attack = 0.001, decay = 0.01, sustain = 1.0, release = 0.01),
    )

    fun hasAudio(out: ShortArray): Boolean = out.any { abs(it.toInt()) > 200 }

    /**
     * Renders [warmupBlocks] to move the cursor, sends the voice, renders on and reports which
     * of the following blocks sounded (true per block).
     */
    fun renderAround(
        voice: RealtimeVoice,
        warmupBlocks: Int = 4,
        blocksAfter: Int,
    ): List<Boolean> {
        val d = newDispatcher()
        val out = ShortArray(blockFrames * 2)
        var frame = 0.0

        repeat(warmupBlocks) {
            d.renderBlock(cursorFrame = frame, out = out)
            frame += blockFrames
        }

        d.handle(KlangCommLink.Cmd.StartRealtimeVoice(playbackId = "rt", voice = voice))

        return (0 until blocksAfter).map {
            d.renderBlock(cursorFrame = frame, out = out)
            frame += blockFrames
            hasAudio(out)
        }
    }

    "a realtime voice sounds in the very next rendered block" {
        val heard = renderAround(
            voice = RealtimeVoice(liveId = 1, data = sustained, gateDurSec = 0.5),
            blocksAfter = 3,
        )
        heard.first().shouldBeTrue()
    }

    "a fixed gate ends the voice: audible while gated, silent after gate + release" {
        // 50 ms gate + 10 ms release ≈ 21 blocks at 44.1k/128; render 80 to see the silence.
        val heard = renderAround(
            voice = RealtimeVoice(liveId = 2, data = sustained, gateDurSec = 0.05),
            blocksAfter = 80,
        )
        heard.first().shouldBeTrue()
        heard.last().shouldBeFalse()
    }

    "a held voice (gateDurSec = null) still sounds after seconds of rendering" {
        // 2 s ≈ 690 blocks — far past every envelope stage; only the open gate keeps it alive.
        val heard = renderAround(
            voice = RealtimeVoice(liveId = 3, data = sustained, gateDurSec = null),
            blocksAfter = 690,
        )
        heard.first().shouldBeTrue()
        heard.last().shouldBeTrue()
    }

    "a control-only realtime event never sounds" {
        val heard = renderAround(
            voice = RealtimeVoice(liveId = 4, data = sustained.copy(control = true), gateDurSec = 0.5),
            blocksAfter = 10,
        )
        heard.any { it }.shouldBeFalse()
    }
})
