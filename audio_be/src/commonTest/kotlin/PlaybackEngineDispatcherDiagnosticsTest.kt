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
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
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

    "a report carries the engine's own latency plus the host's: the house lookahead, an authored master's, the line (KLANG-00431 D3)" {
        val (d, commLink) = createDispatcher { 0.0 }
        val out = StereoBuffer(blockFrames)
        val houseFrames = HouseStage(sampleRate = sampleRate, blockFrames = blockFrames).latencyFrames
        fun ms(frames: Int) = frames * 1000.0 / sampleRate

        // No host figure yet: the house limiter's lookahead alone (220 frames at 44.1 kHz)
        d.render(out)
        commLink.readAllDiagnostics().single().outputLatencyMs shouldBe (ms(houseFrames) plusOrMinus 1e-9)

        // What the JVM host sets, its line's buffer (audit L8-08), comes on top
        d.outputLatencyMs = 250.0
        d.render(out)
        commLink.readAllDiagnostics().single().outputLatencyMs shouldBe (250.0 + ms(houseFrames) plusOrMinus 1e-9)

        // A master chain with an authored 10 ms lookahead in service: 441 frames more, which the UI's clock must hear
        d.handle(
            KlangCommLink.Cmd.RegisterKatalyst(
                playbackId = "song",
                name = "late",
                dsl = KatalystDsl.of(KatalystStageDsl.Compressor(lookahead = 0.01)),
            )
        )
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(
                    ScheduledVoice(
                        playbackId = "song",
                        startTime = 0.0,
                        gateEndTime = 0.1,
                        data = VoiceData.empty.copy(master = "late", control = true),
                        playbackStartTime = 0.0,
                    ),
                    voice("song"),
                ),
            )
        )
        d.render(out, blocks = 2 * blocksPerReport)
        d.latencyFrames shouldBe houseFrames + 441
        commLink.readAllDiagnostics().last().outputLatencyMs shouldBe (250.0 + ms(houseFrames + 441) plusOrMinus 1e-9)
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
    "the dropped and soundless voice counts stay after the stop: monotone since the backend started (KLANG-00431 D6)" {
        val (d, commLink) = createDispatcher { 0.0 }
        val out = StereoBuffer(blockFrames)

        // A voice whose start was rendered past is dropped at admission; a voice whose sample never came is soundless
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(voice("song"))))
        d.render(out, blocks = 4)
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "song", voices = listOf(voice("song", freqHz = 330.0))))
        d.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = "song",
                voices = listOf(
                    ScheduledVoice(
                        playbackId = "song",
                        startTime = 0.03,
                        gateEndTime = 0.1,
                        data = VoiceData.empty.copy(sound = "never-sent", freqHz = 440.0),
                        playbackStartTime = 0.0,
                    ),
                ),
            )
        )
        d.render(out, blocks = 2 * blocksPerReport)

        val playing = commLink.readAllDiagnostics().last()

        playing.warehouse.droppedVoices shouldBe 1
        playing.soundlessVoices shouldBe 1

        // The stop drops the playback's context; the reports keep its counts, through the engine's disposal
        d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
        d.render(out, blocks = 400)

        d.engine("song") shouldBe null

        val stopped = commLink.readAllDiagnostics().last()

        stopped.warehouse.droppedVoices shouldBe 1
        stopped.soundlessVoices shouldBe 1

        // A second playback adds its own; a second stop of the first adds nothing
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "next", voices = listOf(voice("next"))))
        d.render(out, blocks = 4)
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "next", voices = listOf(voice("next", freqHz = 330.0))))
        d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "song"))
        d.handle(KlangCommLink.Cmd.Cleanup(playbackId = "next"))
        d.render(out, blocks = blocksPerReport)

        commLink.readAllDiagnostics().last().warehouse.droppedVoices shouldBe 2

        // The warm-up's teardown (a hard cleanup) is left out
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "warmup", voices = listOf(voice("warmup"))))
        d.render(out, blocks = 4)
        d.handle(KlangCommLink.Cmd.ScheduleVoices(playbackId = "warmup", voices = listOf(voice("warmup", freqHz = 330.0))))
        d.cleanupHard("warmup")
        d.render(out, blocks = blocksPerReport)

        commLink.readAllDiagnostics().last().warehouse.droppedVoices shouldBe 2
    }

    "a report carries the feedback sends the full ring refused, monotone (KLANG-00431 D10)" {
        // A ring of 4 holds 3: the frontend reads nothing for six reports, so three are refused
        val commLink = KlangCommLink(capacity = 4)
        val d = PlaybackEngineDispatcher.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = commLink.backend,
            performanceTimeMs = { 0.0 },
        ).also { it.setBackendStartTime(0.0) }
        val out = StereoBuffer(blockFrames)

        d.render(out)
        commLink.readAllDiagnostics().single().refusedFeedbackSends shouldBe 0.0

        d.render(out, blocks = 6 * blocksPerReport)
        commLink.readAllDiagnostics() shouldHaveSize 3
        commLink.backend.refusedFeedbackSends shouldBe 3.0

        d.render(out)
        commLink.readAllDiagnostics().single().refusedFeedbackSends shouldBe 3.0
    }

    "a report carries the house stage's crushed blocks: 0 on a loud mix, more once a huge value sat on it (KLANG-00432 L8-02 (c))" {
        val (d, commLink) = createDispatcher { 0.0 }
        val out = StereoBuffer(blockFrames)

        fun play(sound: String, level: Double, startTime: Double) {
            val req = SampleRequest(bank = null, sound = sound, index = null, note = null)

            d.handle(
                KlangCommLink.Cmd.Sample.Complete(
                    req = req, note = null, pitchHz = 440.0,
                    sample = MonoSamplePcm(sampleRate = sampleRate, pcm = DoubleArray(4410) { level }),
                )
            )
            d.handle(
                KlangCommLink.Cmd.ScheduleVoices(
                    playbackId = sound,
                    voices = listOf(
                        ScheduledVoice(
                            playbackId = sound,
                            startTime = startTime,
                            gateEndTime = startTime + 0.05,
                            data = VoiceData.empty.copy(sound = sound, freqHz = 440.0),
                            playbackStartTime = 0.0,
                        ),
                    ),
                ),
            )
        }

        // A thousand times too loud (+60 dBFS) is limited, not crushed
        play(sound = "loud", level = 1e3, startTime = 0.03)
        d.render(out, blocks = 4 * blocksPerReport)
        commLink.readAllDiagnostics().last().crushedBlocks shouldBe 0.0

        play(sound = "huge", level = 1e15, startTime = 0.03)
        d.render(out, blocks = 8 * blocksPerReport)

        val reports = commLink.readAllDiagnostics()

        reports.last().crushedBlocks shouldBeGreaterThan 0.0
        // Monotone: every report carries the total so far
        reports.zipWithNext().all { (a, b) -> b.crushedBlocks >= a.crushedBlocks } shouldBe true
    }
})
