/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeAtLeast
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleRequest
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

    /**
     * Diagnostics go out every 20 ms of audio rendered (KLANG-00399): 882 frames at 44.1 kHz, so the seventh block.
     * Pinned, not computed from the constant: the frontend's clock caps at a few report gaps (`PlaybackClock`).
     */
    val blocksPerReport = 7

    /** Renders [blocks] blocks from the dispatcher's clock on. */
    fun PlaybackEngineDispatcher.render(out: StereoBuffer, blocks: Int = blocksPerReport) {
        repeat(blocks) {
            renderBlock(clockForTest.cursorFrame, out)
        }
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

    "one report per 20 ms of audio rendered, whatever the wall clock says" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = StereoBuffer(blockFrames)

        // A wall clock that leaps (or steps back) changes nothing: the frames rendered count
        t = 60_000.0
        d.render(out, blocks = blocksPerReport - 1)
        commLink.readAllDiagnostics() shouldHaveSize 0

        t = -5_000.0
        d.render(out, blocks = 1)
        commLink.readAllDiagnostics() shouldHaveSize 1

        d.render(out, blocks = 2 * blocksPerReport)
        commLink.readAllDiagnostics() shouldHaveSize 2
    }

    "a report carries the timeline at the end of the block just rendered, never the wall clock" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        d.setBackendStartTime(1000.0)
        val out = StereoBuffer(blockFrames)

        t = 123_456.0
        d.render(out)

        val endSec = 1000.0 + blocksPerReport * blockFrames.toDouble() / sampleRate
        commLink.readAllDiagnostics().single().backendNowMs shouldBe (endSec * 1000.0 plusOrMinus 1e-6)
    }

    "a report carries the output latency its host set, 0 until it sets one (audit L8-08)" {
        val (d, commLink) = createDispatcher { 0.0 }
        val out = StereoBuffer(blockFrames)

        d.render(out)
        commLink.readAllDiagnostics().single().outputLatencyMs shouldBe 0.0

        // What the JVM host sets: its line's buffer plus the house limiter's lookahead
        d.outputLatencyMs = 255.0
        d.render(out)
        commLink.readAllDiagnostics().single().outputLatencyMs shouldBe 255.0
    }

    "a report carries the house stage's poison count: 0 on a clean mix, more once a voice put a NaN on it (KLANG-00425 L8-04 (c))" {
        val (d, commLink) = createDispatcher { 0.0 }
        val out = StereoBuffer(blockFrames)
        val req = SampleRequest(bank = null, sound = "poison", index = null, note = null)
        // A sample with one NaN in it: nothing between a voice and the house limiter scrubs it (L6-01 (a)), the
        // limiter's ring stores it as 0 and counts it.
        val pcm = DoubleArray(4410) { if (it == 300) Double.NaN else 0.1 * kotlin.math.sin(0.05 * it) }

        d.handle(KlangCommLink.Cmd.Sample.Complete(req = req, note = null, pitchHz = 440.0, sample = MonoSamplePcm(sampleRate = sampleRate, pcm = pcm)))

        d.render(out)
        commLink.readAllDiagnostics().single().nonFiniteSamples shouldBe 0.0

        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(
                    ScheduledVoice(
                        playbackId = "song",
                        startTime = 0.03,
                        gateEndTime = 0.1,
                        data = VoiceData.empty.copy(sound = "poison", freqHz = 440.0),
                        playbackStartTime = 0.0,
                    ),
                ),
            ),
        )

        d.render(out, blocks = 4 * blocksPerReport)

        val reports = commLink.readAllDiagnostics()

        reports.last().nonFiniteSamples shouldBeGreaterThan 0.0
        // Monotone: every report carries the total so far.
        reports.zipWithNext().all { (a, b) -> b.nonFiniteSamples >= a.nonFiniteSamples } shouldBe true
    }

    "diagnostics carry the SYSTEM playbackId" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = StereoBuffer(blockFrames)
        d.render(out)

        commLink.readAllDiagnostics().first().playbackId shouldBe KlangCommLink.SYSTEM_PLAYBACK_ID
    }

    "an idle backend (no engines) still emits zero voices and zero cylinders" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = StereoBuffer(blockFrames)

        d.render(out)

        val diag = commLink.readAllDiagnostics().last()
        diag.activeVoiceCount shouldBe 0
        diag.cylinders.shouldBeEmpty()
    }

    "diagnostics aggregate the active voice count across engines" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = StereoBuffer(blockFrames)

        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "a", voices = listOf(voice("a"))))
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "b", voices = listOf(voice("b"))))
        d.render(out)

        commLink.readAllDiagnostics().last().activeVoiceCount shouldBeAtLeast 2
    }

    "diagnostics report active cylinder states" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = StereoBuffer(blockFrames)

        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(voice("song", cylinder = 0))))
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(voice("song", cylinder = 2, freqHz = 880.0))))
        d.render(out)

        val cylinders = commLink.readAllDiagnostics().last().cylinders
        cylinders.find { it.id == 0 }?.active shouldBe true
        cylinders.find { it.id == 2 }?.active shouldBe true
    }

    "headroom is a valid ratio (≤ 1.0)" {
        var t = 0.0
        val (d, commLink) = createDispatcher { t }
        val out = StereoBuffer(blockFrames)
        d.render(out)

        commLink.readAllDiagnostics().first().renderHeadroom shouldBeLessThan 1.1
    }

    "scheduling voices lazily allocates exactly their cylinders" {
        var t = 0.0
        val (d, _) = createDispatcher { t }
        val out = StereoBuffer(blockFrames)

        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(voice("song", cylinder = 0))))
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(voice("song", cylinder = 3, freqHz = 880.0))))
        d.renderBlock(0.0, out)

        val ids = d.engine("song").shouldNotBeNull().cylinders.cylindersIds
        ids shouldHaveSize 2
        ids.contains(0) shouldBe true
        ids.contains(3) shouldBe true
    }
})
