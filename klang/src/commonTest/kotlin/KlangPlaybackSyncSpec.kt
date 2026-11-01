/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBackend
import io.peekandpoke.klang.audio_be.OfflineBackend
import io.peekandpoke.klang.audio_be.VoiceWatch
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
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

/**
 * The frontend and the backend on one clock (KLANG-00399, steps 3a to 3c): a [KlangPatternScheduler] wired to an
 * [OfflineBackend], the frontend's time a fake and the backend rendering only when the spec says so. The playback's
 * time is the backend's, as [PlaybackClock] knows it from its reports, and counts from where the backend started the
 * playback ([KlangCommLink.Feedback.PlaybackAnchored]).
 */
class KlangPlaybackSyncSpec : StringSpec({

    /** At 60 rpm a cycle is one second, one note a cycle. */
    val rpm60 = KlangCyclicPlayback.Options(rpm = 60.0, prefetchCycles = 0)

    "after the backend stood still, a new playback starts at once, its highlights on the sound" {
        withSync { sync ->
            sync.run(ms = 500.0)
            // The audio thread starved for two seconds: the frontend's time ran on, the backend's did not
            sync.run(ms = 2000.0, backendRuns = false)
            val stoodAt = sync.backend.nowSec

            sync.start(rpm60)
            sync.run(ms = 4500.0)

            // At most the clock's cap after where the backend stood, not two seconds later
            sync.startedAt("0") - stoodAt shouldBeLessThan 0.15
            // The first round's highlights were timed while the backend stood; the later ones are on the sound, within
            // a report gap and a block (the backend renders whole blocks, a report comes every 20 ms of audio)
            sync.highlightAt("3") shouldBe (sync.heardAt("3") plusOrMinus 0.03)
        }
    }

    "when the backend stops, the playback's time stops with it: nothing is sent far ahead of it" {
        withSync { sync ->
            sync.run(ms = 500.0)
            sync.start(rpm60)
            sync.run(ms = 1000.0)

            sync.run(ms = 3000.0, backendRuns = false)

            // No note starts more than the look-ahead past the backend's now: two cycles of a second, whole cycles
            // fetched, so a capped clock gives 2 s; one that ran on through the 3 s stall would give 5 s
            val ahead = sync.latestSentSec() - (sync.backend.nowSec - sync.epochSec())
            ahead shouldBeLessThan 2.5
        }
    }

    "a playback counts from where the backend started it, not from the second it asked for" {
        withSync { sync ->
            sync.run(ms = 500.0)
            // The backend rendered on while its reports waited: the frontend thinks it is earlier than it is
            val blocks = 112
            sync.backend.render(blocks = blocks)
            sync.lagSec -= blocks * BLOCK / SAMPLE_RATE.toDouble()

            sync.start(rpm60)
            sync.run(ms = 4500.0)

            // Timed after the backend's word on where the playback started
            sync.highlightAt("3") shouldBe (sync.heardAt("3") plusOrMinus 0.03)
        }
    }

    "a main thread that stalls for 1.2 s at 120 rpm loses nothing: the look-ahead is at least 1.5 s" {
        withSync { sync ->
            sync.run(ms = 500.0)
            sync.start(KlangCyclicPlayback.Options(rpm = 120.0, prefetchCycles = 0))
            sync.run(ms = 2000.0)

            // Two cycles are one second at 120 rpm; the main thread stands still for longer, the backend plays on
            sync.run(ms = 1200.0, frontendRuns = false)
            sync.run(ms = 3000.0)

            sync.late shouldBe emptyList()
        }
    }

    "after a stall longer than the look-ahead, the cycles gone by are skipped, the state they set holds" {
        withSync(pattern = everyCycle(masters = true)) { sync ->
            sync.run(ms = 500.0)
            sync.start(rpm60)
            sync.run(ms = 1000.0)

            // The main thread stands still for five seconds: cycles 3 to 5 go by unsent
            sync.run(ms = 5000.0, frontendRuns = false)
            sync.run(ms = 1000.0)

            // Their sounds are not sent late; at most the note of the cycle now running comes late
            sync.late.filter { it in listOf("3", "4", "5") } shouldBe emptyList()
            sync.late.size shouldBeLessThanOrEqual 1
            // The master the last of them set is sent as a control event, so it holds from now on; at its own second,
            // before cycle 6's, so the current cycle's newer master, sent late, still comes after it
            val controls = sync.sent.filter { it.data.control == true }
            controls.map { it.data.master } shouldBe listOf("m5")
            controls.single().startTime shouldBe (5.0 plusOrMinus 1e-9)
        }
    }

    "after a stall longer than the look-ahead, a solo control still running holds on; an ended one and a note's do not" {
        // Solo control events: at cycle 3 one cycle long (over by now), at cycle 4 four cycles long (on to cycle 8);
        // and a soloed note at cycle 4, which never sets a solo's end (SoloTracker)
        val soloed = object : KlangPattern {
            private val notes = listOf(
                Note(cycle = 3, master = false, solo = 1.0, cycles = 1.0, control = true),
                Note(cycle = 4, master = false, solo = 1.0, cycles = 4.0, control = true),
                Note(cycle = 4, master = false, solo = 1.0, cycles = 5.0),
            )

            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> =
                notes.filter { it.startCycles >= fromCycles && it.startCycles < toCycles }
        }

        withSync(pattern = soloed) { sync ->
            sync.run(ms = 500.0)
            sync.start(rpm60)
            sync.run(ms = 1000.0)

            // The stall skips cycles 3 to 5
            sync.run(ms = 5000.0, frontendRuns = false)
            sync.run(ms = 1000.0)

            val solos = sync.sent.filter { it.data.control == true && it.data.solo != null }
            solos.map { it.startTime to it.gateEndTime } shouldBe listOf(4.0 to 8.0)
        }
    }

    "a cycle counts as completed only when the backend has rendered it, not when the clock runs on past a stall" {
        withSync { sync ->
            sync.run(ms = 500.0)
            sync.start(rpm60)
            sync.run(ms = 500.0)
            val epoch = sync.epochSec()

            // Cycle 2 ends at 3 s; the backend stops 50 ms before, the clock runs on past it by its cap
            sync.runUntilBackendAt(epoch + 2.95)
            sync.run(ms = 1000.0, backendRuns = false)
            sync.completed() shouldBe listOf(0, 1)

            sync.run(ms = 500.0)
            sync.completed() shouldBe listOf(0, 1, 2)
        }
    }

    "a playback that begins with rests starts when it is asked to, not at its first sound" {
        withSync(pattern = everyCycle(from = 3)) { sync ->
            sync.run(ms = 500.0)
            val askedAt = sync.backend.nowSec

            sync.start(rpm60)
            sync.run(ms = 5000.0)

            // Cycle 3 three seconds after the start, give or take the step the backend takes the start in
            sync.startedAt("3") - askedAt shouldBe (3.0 plusOrMinus 0.05)
        }
    }

    "a pattern whose query fails in one cycle: the playback plays on past it" {
        withSync(pattern = failingAt(cycle = 2)) { sync ->
            sync.run(ms = 500.0)
            sync.start(rpm60)
            sync.run(ms = 5000.0)

            // Cycle 2 is lost, the ones after it play
            sync.startedOrNull("2") shouldBe null
            sync.startedAt("3") shouldBeGreaterThan sync.startedAt("1")
            sync.startedAt("4") shouldBeGreaterThan sync.startedAt("3")
        }
    }

    "a pattern whose query fails where the sample look-ahead asks first: the playback plays on" {
        // The look-ahead for samples asks eight cycles ahead of the fetch: it meets cycle 12 first
        withSync(pattern = failingAt(cycle = 12)) { sync ->
            sync.run(ms = 500.0)
            sync.start(rpm60)
            sync.run(ms = 6000.0)

            listOf("1", "2", "3", "4", "5").forEach { sync.startedOrNull(it) shouldNotBe null }
        }
    }

    "the time heard counts what the backend played, less the output latency, and stands still with it" {
        withSync { sync ->
            sync.run(ms = 500.0)
            sync.start(rpm60)
            sync.run(ms = 2000.0)

            // On the backend's last report, from the playback's start, the 100 ms output latency off
            val played = sync.backend.nowSec - sync.epochSec()
            sync.heardSec() shouldBe (played - 0.1 plusOrMinus 0.03)

            // The backend stands still for three seconds: so does the time heard
            sync.run(ms = 3000.0, backendRuns = false)
            sync.heardSec() shouldBe (played - 0.1 plusOrMinus 0.03)
        }
    }

    "after half an hour asleep the playback goes on from where the backend stood: no flood, nothing late" {
        withSync { sync ->
            sync.run(ms = 500.0)
            sync.start(rpm60)
            sync.run(ms = 2000.0)

            // The audio stopped while the machine slept; the frontend's clock ran on
            sync.run(ms = 30 * 60 * 1000.0, backendRuns = false, stepMs = 1000.0)
            sync.run(ms = 3000.0)

            sync.late shouldBe emptyList()
            val ahead = sync.latestSentSec() - (sync.backend.nowSec - sync.epochSec())
            ahead shouldBeLessThan 2.5
        }
    }
}) {
    private companion object {
        const val SAMPLE_RATE = 48000
        const val BLOCK = 128

        /**
         * One note on every cycle from cycle [from] on, a cycle long, named after its cycle; with [masters], each sets
         * the master "m<cycle>".
         */
        fun everyCycle(from: Int = 0, masters: Boolean = false) = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> =
                (maxOf(from, ceil(fromCycles).toInt()) until ceil(toCycles).toInt()).map { Note(cycle = it, master = masters) }
        }

        /** [everyCycle], but a query that reaches [cycle] fails. */
        fun failingAt(cycle: Int) = object : KlangPattern {
            private val notes = everyCycle()

            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> {
                check(!(fromCycles <= cycle && cycle < toCycles)) { "cycle $cycle does not query" }

                return notes.queryEvents(fromCycles = fromCycles, toCycles = toCycles, cps = cps)
            }
        }

        private suspend fun withSync(pattern: KlangPattern = everyCycle(), test: suspend (Sync) -> Unit) {
            val sync = Sync(pattern)

            try {
                withTimeout(30.seconds) {
                    test(sync)
                }
            } finally {
                sync.close()
            }
        }
    }

    private class Note(
        cycle: Int,
        master: Boolean,
        solo: Double? = null,
        cycles: Double = 1.0,
        control: Boolean? = null,
    ) : KlangPatternEvent {
        override val startCycles = cycle.toDouble()
        override val durationCycles = cycles
        override val sourceLocations: SourceLocationChain? = null
        private val data = VoiceData.empty.copy(
            note = "$cycle",
            master = if (master) "m$cycle" else null,
            solo = solo,
            sourceId = if (solo != null) "s$cycle" else null,
            control = control,
        )

        override fun toVoiceData() = data
    }

    /**
     * A scheduler and a backend on one link. [localMs] is the frontend's wall clock; the backend renders behind it by
     * [lagSec] while both run, and falls further behind when it stands still. The scheduler's loop is stepped by hand:
     * one round per 16 ms step of [run], after the feedback is delivered.
     */
    private class Sync(pattern: KlangPattern) {
        var localMs = 1_000_000.0
        var lagSec = 0.0

        /**
         * From the backend's timeline to the speakers, as the JS backend adds it to the diagnostics. Larger than the
         * highlights' tolerance and its spread together, so a highlight without it is told apart at any timing.
         */
        val outputLatencyMs = 100.0

        private val link = KlangCommLink(capacity = 16384)
        private val started = mutableMapOf<String, Double>()
        private val sentStarts = mutableListOf<Double>()

        /** The notes the backend dropped as late. */
        val late = mutableListOf<String>()

        /** Every voice sent to the backend. */
        val sent = mutableListOf<ScheduledVoice>()
        private val seen = mutableListOf<KlangPlaybackSignal>()
        private val idle = Channel<Unit>()
        private val go = Channel<Unit>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val clock = PlaybackClock(localMs = { localMs })
        private var running = false

        val backend = OfflineBackend(
            config = AudioBackend.Config(commLink = link.backend, sampleRate = SAMPLE_RATE, blockSize = BLOCK),
            // The backend starts on the wall clock, as the live ones do
            startTimeSec = localMs / 1000.0,
            voiceWatch = object : VoiceWatch {
                override fun started(playbackId: String, data: VoiceData, atSec: Double) {
                    started[data.note ?: ""] = atSec
                }

                override fun late(playbackId: String, data: VoiceData, atSec: Double) {
                    late += data.note ?: ""
                }
            },
        )

        private val signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)
        private val listening = signals.subscribeToStream { seen += it }

        private val scheduler: KlangPatternScheduler

        init {
            val wallMs = { localMs }
            val playerOptions = KlangPlayer.Options(
                samples = Samples(
                    index = Samples.Index(banks = emptyList()),
                    loader = object : AssetLoader {
                        override suspend fun download(uri: String): ByteArray? = null
                    },
                    decoder = NullAudioDecoder(),
                ),
            )
            val sendControl = { cmd: KlangCommLink.Cmd ->
                if (cmd is KlangCommLink.Cmd.ScheduleVoices) {
                    sentStarts += cmd.voices.map { it.startTime }
                    sent += cmd.voices
                }

                link.frontend.control.send(cmd)
                Unit
            }
            val context = KlangPlaybackContext(
                playerOptions = playerOptions,
                samplePreloader = SamplePreloader(
                    samples = playerOptions.samples,
                    sendControl = {},
                    scope = scope,
                    dispatcher = Dispatchers.Default,
                ),
                sendControl = sendControl,
                scope = scope,
                fetcherDispatcher = Dispatchers.Default,
                callbackDispatcher = Dispatchers.Unconfined,
                backendReady = CompletableDeferred(Unit),
                clock = clock,
                diagnostics = KlangDiagnostics(
                    stream = StreamSource<KlangCommLink.Feedback.Diagnostics?>(null),
                    ticks = StreamSource(Unit),
                    capacity = 8,
                    nowMs = wallMs,
                ),
            )

            scheduler = KlangPatternScheduler(
                playbackId = "sync",
                pattern = pattern,
                context = context,
                signals = signals,
                registrar = context.registrarFor("sync"),
                uiMs = wallMs,
                pause = {
                    idle.send(Unit)
                    go.receive()
                },
            )
        }

        /** Starts the playback; returns after its first round. */
        suspend fun start(options: KlangCyclicPlayback.Options) {
            scheduler.start(options)
            running = true
            idle.receive()
        }

        /**
         * [ms] pass in steps of [stepMs]. Each step the backend renders up to the wall clock less [lagSec], or stands
         * still ([backendRuns] false: the lag grows), the feedback reaches the frontend, and the scheduler runs a round,
         * unless the main thread stands still ([frontendRuns] false: no feedback read, no round).
         */
        suspend fun run(ms: Double, backendRuns: Boolean = true, frontendRuns: Boolean = true, stepMs: Double = 16.0) {
            var left = ms

            while (left > 0.0) {
                val step = min(stepMs, left)
                localMs += step
                left -= step

                if (backendRuns) {
                    backend.renderUntil(localMs / 1000.0 - lagSec)
                } else {
                    lagSec += step / 1000.0
                }

                if (!frontendRuns) {
                    continue
                }

                deliver()

                if (running) {
                    go.send(Unit)
                    idle.receive()
                }
            }
        }

        private fun deliver() {
            while (true) {
                when (val feedback = link.frontend.feedback.receive() ?: break) {
                    is KlangCommLink.Feedback.Diagnostics -> clock.onDiagnostics(feedback.copy(outputLatencyMs = outputLatencyMs))
                    is KlangCommLink.Feedback.PlaybackAnchored -> scheduler.handleFeedback(feedback)
                    is KlangCommLink.Feedback.BackendReady,
                    is KlangCommLink.Feedback.RequestSample,
                    is KlangCommLink.Feedback.SampleReceived,
                    -> {}
                }
            }
        }

        /** Runs in steps until the backend has rendered up to [sec]. */
        suspend fun runUntilBackendAt(sec: Double) {
            while (backend.nowSec < sec) {
                run(ms = 16.0)
            }
        }

        /** The playback's seconds heard so far ([KlangPatternScheduler.heardSec]). */
        fun heardSec(): Double = scheduler.heardSec() ?: Double.NaN

        /** The cycles reported completed so far. */
        fun completed(): List<Int> = seen.filterIsInstance<KlangPlaybackSignal.CycleCompleted>().map { it.cycleIndex }

        /** The backend second the voice of [note] started at. */
        fun startedAt(note: String): Double = started.getValue(note)

        /** The backend second the voice of [note] started at; null when it never started. */
        fun startedOrNull(note: String): Double? = started[note]

        /** The wall second the voice of [note] is heard: the backend's second plus the lag and the output latency. */
        fun heardAt(note: String): Double = startedAt(note) + lagSec + outputLatencyMs / 1000.0

        /** The wall second the UI highlights the voice of [note] at. */
        fun highlightAt(note: String): Double = seen
            .filterIsInstance<KlangPlaybackSignal.VoicesScheduled>()
            .flatMap { it.voices }
            .single { it.data.note == note }
            .startTime

        /** The playback's second 0 on the backend's timeline: where its first voice, at second 0, started. */
        fun epochSec(): Double = startedAt("0")

        /** The latest start sent to the backend, in the playback's seconds. */
        fun latestSentSec(): Double = sentStarts.max()

        fun close() {
            scheduler.stop()
            listening()
            scope.cancel()
        }
    }
}
