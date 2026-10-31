/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBackend
import io.peekandpoke.klang.audio_be.OfflineBackend
import io.peekandpoke.klang.audio_be.VoiceWatch
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
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
 * The frontend and the backend on one clock (KLANG-00399, step 3a): a [KlangPatternScheduler] wired to an
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
}) {
    private companion object {
        const val SAMPLE_RATE = 48000
        const val BLOCK = 128

        /** One note on every cycle, a cycle long, named after its cycle. */
        val everyCycle = object : KlangPattern {
            override fun queryEvents(fromCycles: Double, toCycles: Double, cps: Double): List<KlangPatternEvent> =
                (ceil(fromCycles).toInt() until ceil(toCycles).toInt()).map { Note(cycle = it) }
        }

        private suspend fun withSync(test: suspend (Sync) -> Unit) {
            val sync = Sync()

            try {
                withTimeout(30.seconds) {
                    test(sync)
                }
            } finally {
                sync.close()
            }
        }
    }

    private class Note(cycle: Int) : KlangPatternEvent {
        override val startCycles = cycle.toDouble()
        override val durationCycles = 1.0
        override val sourceLocations: SourceLocationChain? = null
        private val data = VoiceData.empty.copy(note = "$cycle")

        override fun toVoiceData() = data
    }

    /**
     * A scheduler and a backend on one link. [localMs] is the frontend's wall clock; the backend renders behind it by
     * [lagSec] while both run, and falls further behind when it stands still. The scheduler's loop is stepped by hand:
     * one round per 16 ms step of [run], after the feedback is delivered.
     */
    private class Sync {
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
                pattern = everyCycle,
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
         * [ms] pass in steps of 16 ms. Each step the backend renders up to the wall clock less [lagSec], or stands
         * still ([backendRuns] false: the lag grows), the feedback reaches the frontend, and the scheduler runs a round.
         */
        suspend fun run(ms: Double, backendRuns: Boolean = true) {
            var left = ms

            while (left > 0.0) {
                val step = min(16.0, left)
                localMs += step
                left -= step

                if (backendRuns) {
                    backend.renderUntil(localMs / 1000.0 - lagSec)
                } else {
                    lagSec += step / 1000.0
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

        /** The backend second the voice of [note] started at. */
        fun startedAt(note: String): Double = started.getValue(note)

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
