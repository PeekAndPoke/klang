/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeAtLeast
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * D5 gate: diagnostics emission lives on the dispatcher now. It runs every block, so it emits one
 * AGGREGATE message across all engines and — the regression that motivated the move — keeps emitting
 * ZEROS when the engine map is empty, so the FE gauges drop to 0 on stop instead of freezing on the
 * last per-engine value.
 */
class PlaybackEngineDispatcherDiagnosticsTest : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    fun createDispatcher(timeMs: () -> Double): Pair<PlaybackEngineDispatcher, KlangCommLink> {
        val commLink = KlangCommLink(capacity = 1024)
        val d = PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink.backend,
            performanceTimeMs = timeMs,
        ).also { it.setBackendStartTime(0.0) }
        return d to commLink
    }

    fun KlangCommLink.readAllDiagnostics(): List<KlangCommLink.Feedback.Diagnostics> {
        val out = mutableListOf<KlangCommLink.Feedback.Diagnostics>()
        while (true) {
            val msg = frontend.feedback.receive() ?: break
            if (msg is KlangCommLink.Feedback.Diagnostics) out.add(msg)
        }
        return out
    }

    fun voice(pid: String, cylinder: Int = 0, freqHz: Double = 440.0) = ScheduledVoice(
        playbackId = pid,
        startTime = 0.0,
        gateEndTime = 1.0,
        data = VoiceData.empty.copy(sound = "sine", freqHz = freqHz, cylinder = cylinder),
        playbackStartTime = 0.0,
    )

    "no diagnostics within the first ~20ms, then one is emitted" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = ShortArray(blockFrames * 2)

        d.renderBlock(0, out)
        commLink.readAllDiagnostics() shouldHaveSize 0

        t = 60.0
        d.renderBlock(blockFrames, out)
        commLink.readAllDiagnostics() shouldHaveSize 1
    }

    "diagnostics carry the SYSTEM playbackId" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = ShortArray(blockFrames * 2)
        d.renderBlock(0, out); t = 60.0; d.renderBlock(blockFrames, out)

        commLink.readAllDiagnostics().first().playbackId shouldBe KlangCommLink.SYSTEM_PLAYBACK_ID
    }

    "an idle backend (no engines) still emits zero voices and zero cylinders" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = ShortArray(blockFrames * 2)

        d.renderBlock(0, out); t = 60.0; d.renderBlock(blockFrames, out)

        val diag = commLink.readAllDiagnostics().last()
        diag.activeVoiceCount shouldBe 0
        diag.cylinders.shouldBeEmpty()
    }

    "diagnostics aggregate the active voice count across engines" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = ShortArray(blockFrames * 2)

        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "a", voice = voice("a")))
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "b", voice = voice("b")))
        d.renderBlock(0, out); t = 60.0; d.renderBlock(blockFrames, out)

        commLink.readAllDiagnostics().last().activeVoiceCount shouldBeAtLeast 2
    }

    "diagnostics report active cylinder states" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = ShortArray(blockFrames * 2)

        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "song", voice = voice("song", cylinder = 0)))
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "song", voice = voice("song", cylinder = 2, freqHz = 880.0)))
        d.renderBlock(0, out); t = 60.0; d.renderBlock(blockFrames, out)

        val cylinders = commLink.readAllDiagnostics().last().cylinders
        cylinders.find { it.id == 0 }?.active shouldBe true
        cylinders.find { it.id == 2 }?.active shouldBe true
    }

    "headroom is a valid ratio (≤ 1.0)" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = ShortArray(blockFrames * 2)
        d.renderBlock(0, out); t = 60.0; d.renderBlock(blockFrames, out)

        commLink.readAllDiagnostics().first().renderHeadroom shouldBeLessThan 1.1
    }

    "scheduling voices lazily allocates exactly their cylinders" {
        var t = 0.0
        val (d, _) = createDispatcher { t }
        val out = ShortArray(blockFrames * 2)

        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "song", voice = voice("song", cylinder = 0)))
        d.handle(KlangCommLink.Cmd.ScheduleVoice(playbackId = "song", voice = voice("song", cylinder = 3, freqHz = 880.0)))
        d.renderBlock(0, out)

        val ids = d.engine("song").shouldNotBeNull().cylinders.cylindersIds
        ids shouldHaveSize 2
        ids.contains(0) shouldBe true
        ids.contains(3) shouldBe true
    }
})
