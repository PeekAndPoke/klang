/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

/**
 * A tempo change on [KlangPatternScheduler] (KLANG-00399, step 1): the playback's start stays, the backend's epoch with
 * it, and the voices after the change follow the tempo map. Before the map, a change moved the start, and every later
 * voice landed displaced by the move.
 */
class KlangPatternSchedulerTempoSpec : StringSpec({

    val eps = 1e-9

    /** At 30 rpm a cycle is two seconds. */
    val rpm30 = KlangCyclicPlayback.Options(rpm = 30.0, prefetchCycles = 0)

    "a tempo change keeps the playback's start, and the voices after it follow the new tempo" {
        withScheduler(rpm30) {
            round(atSec = 20.0)

            val start = scheduled(cycle = 0).playbackStartTime
            val seenBefore = seen.size

            scheduler.updateRpm(60.0)

            // The switch is 0.2 s from now, at cycle 10.1; from there a cycle is one second
            val replace = replaces.single()
            replace.afterTimeSec shouldBe (20.2 plusOrMinus eps)
            replace.voices.map { it.data.note } shouldBe listOf("11")
            replace.voices.single().playbackStartTime shouldBe start
            replace.voices.single().startTime shouldBe (21.1 plusOrMinus eps)
            replace.voices.single().gateEndTime shouldBe (22.1 plusOrMinus eps)
            // Queried at the tempo it plays at
            replace.voices.single().data.freqHz shouldBe 1.0
            // Cycle 10 plays across the switch: its highlight ends on the map, a tenth on the old tempo, the rest on the new
            val highlighted = seen.drop(seenBefore).filterIsInstance<KlangPlaybackSignal.VoicesScheduled>().flatMap { it.voices }
            highlighted.single { it.data.note == "10" }.endTime - START_SEC shouldBe (21.1 plusOrMinus eps)
            // The old tempo's highlights are cancelled first
            seen[seenBefore] shouldBe KlangPlaybackSignal.PatternUpdated

            round(atSec = 21.0)
            scheduled(cycle = 12).playbackStartTime shouldBe start
            scheduled(cycle = 12).startTime shouldBe (22.1 plusOrMinus eps)
            scheduled(cycle = 12).data.freqHz shouldBe 1.0
        }
    }

    "a tempo change during a stall keeps the tempo of the cycles the counter has not reported yet" {
        withScheduler(rpm30) {
            // Reported up to cycle 8; then the loop stalls through two changes, at cycle 10.5 and at cycle 19.7
            round(atSec = 19.8)
            nowSec = 20.8
            scheduler.updateRpm(60.0)
            nowSec = 30.0
            scheduler.updateRpm(120.0)

            round(atSec = 30.0)

            // Cycle 9 ends at 20 s on the first tempo, cycle 10 at 21.5 s on the second
            val completed = seen.filterIsInstance<KlangPlaybackSignal.CycleCompleted>()
            val ends = completed.filter { it.cycleIndex in 9..10 }.map { it.atTimeSec - START_SEC }
            ends.size shouldBe 2
            ends.zip(listOf(20.0, 21.5)).forEach { (end, expected) -> end shouldBe (expected plusOrMinus eps) }
        }
    }

    "a tempo change before the first voice is the playback's tempo from its start" {
        val ready = CompletableDeferred<Unit>()

        withScheduler(
            options = rpm30,
            backendReady = ready,
            beforeFirstRound = {
                scheduler.updateRpm(60.0)
                ready.complete(Unit)
            },
        ) {
            scheduled(cycle = 1).startTime shouldBe (1.0 plusOrMinus eps)
            replaces shouldBe emptyList()
            seen.filterIsInstance<KlangPlaybackSignal.PatternUpdated>() shouldBe emptyList()
        }
    }

    "two tempo changes in a row add up" {
        withScheduler(rpm30) {
            round(atSec = 20.0)
            scheduler.updateRpm(60.0)

            nowSec = 20.1
            scheduler.updateRpm(120.0)

            // The second switch is at 20.3 s, cycle 10.2; from there a cycle is half a second
            val replace = replaces.last()
            replace.afterTimeSec shouldBe (20.3 plusOrMinus eps)
            replace.voices.single().startTime shouldBe (20.7 plusOrMinus eps)
        }
    }

    "an edit after a tempo change cuts at the right second" {
        withScheduler(rpm30) {
            round(atSec = 20.0)
            scheduler.updateRpm(60.0)
            round(atSec = 22.0)

            scheduler.updatePattern(everyCycle)

            // Now is cycle 11.9; the voices from 22.2 s on are replaced: cycle 13 only
            val replace = replaces.last()
            replace.afterTimeSec shouldBe (22.2 plusOrMinus eps)
            replace.voices.map { it.data.note } shouldBe listOf("13")
            replace.voices.single().startTime shouldBe (23.1 plusOrMinus eps)
        }
    }

    "the cycle counter runs on steadily across a tempo change" {
        withScheduler(rpm30) {
            round(atSec = 20.0)
            scheduler.updateRpm(60.0)
            round(atSec = 21.0)
            round(atSec = 21.2)
            round(atSec = 23.2)

            val completed = seen.filterIsInstance<KlangPlaybackSignal.CycleCompleted>()
            completed.map { it.cycleIndex } shouldBe (0..12).toList()

            // Each at the end of its cycle on the map; no report, no output latency
            val ends = completed.drop(10).map { it.atTimeSec - START_SEC }
            ends.zip(listOf(21.1, 22.1, 23.1)).forEach { (end, expected) -> end shouldBe (expected plusOrMinus eps) }
        }
    }
}) {
    private companion object {
        const val START_MS = 1_000_000.0
        const val START_SEC = START_MS / 1000.0

        /** One note on every cycle, a cycle long, named after its cycle; it keeps the tempo it was queried at. */
        val everyCycle = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> =
                (ceil(fromCycles).toInt() until ceil(toCycles).toInt()).map { Note(cycle = it, cps = cps) }
        }

        /**
         * Runs [test] after the scheduler's first round. [beforeFirstRound] runs right after the start, while the
         * scheduler waits for [backendReady]; a [backendReady] still open there has to be completed by it.
         */
        private suspend fun withScheduler(
            options: KlangCyclicPlayback.Options,
            backendReady: CompletableDeferred<Unit> = CompletableDeferred(Unit),
            beforeFirstRound: suspend Rig.() -> Unit = {},
            test: suspend Rig.() -> Unit,
        ) {
            val rig = Rig()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val nowMs = { START_MS + rig.nowSec * 1000.0 }

            val playerOptions = KlangPlayer.Options(
                samples = Samples(
                    index = Samples.Index(banks = emptyList()),
                    loader = object : AssetLoader {
                        override suspend fun download(uri: String): ByteArray? = null
                    },
                    decoder = NullAudioDecoder(),
                ),
            )

            val context = KlangPlaybackContext(
                playerOptions = playerOptions,
                // The sample look-ahead runs on its own: what it sends is not this spec's
                samplePreloader = SamplePreloader(
                    samples = playerOptions.samples,
                    sendControl = {},
                    scope = scope,
                    dispatcher = Dispatchers.Default,
                ),
                sendControl = { rig.sent += it },
                scope = scope,
                fetcherDispatcher = Dispatchers.Default,
                // The signals go out at once, inside the round that sends them
                callbackDispatcher = Dispatchers.Unconfined,
                backendReady = backendReady,
                // No report: the clock reads the spec's clock, the backend's timeline as the frontend found it
                clock = PlaybackClock(localMs = nowMs),
                diagnostics = KlangDiagnostics(
                    stream = StreamSource<KlangCommLink.Feedback.Diagnostics?>(null),
                    ticks = StreamSource(Unit),
                    capacity = 8,
                    nowMs = nowMs,
                ),
            )

            val signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)
            val listening = signals.subscribeToStream { rig.seen += it }

            rig.scheduler = KlangPatternScheduler(
                playbackId = "tempo",
                pattern = everyCycle,
                context = context,
                signals = signals,
                registrar = context.registrarFor("tempo"),
                uiMs = nowMs,
                pause = {
                    rig.idle.send(Unit)
                    rig.go.receive()
                },
            )

            try {
                // A loop that never parks again fails the spec instead of hanging it
                withTimeout(10.seconds) {
                    rig.scheduler.start(options)
                    rig.beforeFirstRound()
                    // The first round: the first cycle and the look-ahead, at second 0
                    rig.idle.receive()
                    rig.test()
                }
            } finally {
                rig.scheduler.stop()
                listening()
                scope.cancel()
            }
        }
    }

    /** The tempo it was queried at rides along as its frequency. */
    private class Note(cycle: Int, cps: Double) : KlangPatternEvent {
        override val startCycles = cycle.toDouble()
        override val durationCycles = 1.0
        override val sourceLocations: SourceLocationChain? = null
        private val data = VoiceData.empty.copy(note = "$cycle", freqHz = cps)

        override fun toVoiceData() = data
    }

    /**
     * A scheduler on a clock the spec sets ([nowSec], seconds since the start), its loop stepped by hand: it runs a
     * round and parks in its pause until [round] lets it go. What it sends is in [sent], its signals in [seen].
     */
    private class Rig {
        var nowSec = 0.0
        val sent = mutableListOf<KlangCommLink.Cmd>()
        val seen = mutableListOf<KlangPlaybackSignal>()
        val idle = Channel<Unit>()
        val go = Channel<Unit>()
        lateinit var scheduler: KlangPatternScheduler

        val replaces get() = sent.filterIsInstance<KlangCommLink.Cmd.ReplaceVoices>()

        /** The voice the loop scheduled for [cycle]. */
        fun scheduled(cycle: Int): ScheduledVoice = sent
            .filterIsInstance<KlangCommLink.Cmd.ScheduleVoices>()
            .flatMap { it.voices }
            .single { it.data.note == "$cycle" }

        /** Sets the clock to [atSec] and lets the loop run one round. */
        suspend fun round(atSec: Double) {
            nowSec = atSec
            go.send(Unit)
            idle.receive()
        }
    }
}
