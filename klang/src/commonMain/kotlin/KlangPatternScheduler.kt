/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangAtomicBool
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds

/**
 * Core scheduling controller for pattern playback.
 * Handles pattern querying, event scheduling, sample management, and cycle tracking.
 * Designed to be reused by different playback lifecycle policies (continuous, one-shot, etc.).
 *
 * This is an internal implementation detail. Users should interact with [KlangCyclicPlayback] interface.
 */
internal class KlangPatternScheduler(
    private val playbackId: String,
    private var pattern: KlangPattern,
    context: KlangPlaybackContext,
    private val signals: StreamSource<KlangPlaybackSignal>,
    /**
     * Per-playback inline-DSL bookkeeping, owned by the PLAYBACK and handed down — the realtime
     * path needs the same object, and the scheduler is not its natural home.
     */
    private val registrar: InlineDslRegistrar,
    private val onStarted: () -> Unit = {},
    private val onStopped: () -> Unit = {},
    /**
     * The UI's clock in milliseconds ([uiClockMs]): the signals' times are on it. Not the playback's time: that is the
     * backend's ([PlaybackClock]). The specs pass their own.
     */
    private val uiMs: () -> Double = ::uiClockMs,
    /** The pause between two rounds of the fetch loop. The specs pass their own, to step the loop by hand. */
    private val pause: suspend () -> Unit = { delay(16.milliseconds) },
) {
    companion object {
        /**
         * Minimum allowed RPM. Values below this are clamped — a near-zero cps would make
         * `secPerCycle` explode and time effectively stand still (or compute NaN anchors).
         */
        const val MIN_RPM: Double = 1.0

        /** How often the pattern's work is posted: as the diagnostics read it, four times a second. */
        const val RUNTIME_WINDOW_MS = 250.0
    }

    // Extract dependencies from context for convenience
    private val samplePreloader = context.samplePreloader
    private val sendControl = context.sendControl
    private val scope = context.scope
    private val fetcherDispatcher = context.fetcherDispatcher
    private val callbackDispatcher = context.callbackDispatcher
    private val backendReady = context.backendReady
    private val diagnostics = context.diagnostics

    /**
     * The pattern's work at runtime (KLANG-00385; the maintainer, 2026-10-28: "measure how long the sprudel part takes
     * at runtime"): the milliseconds the pattern queries took, added up, and posted every [RUNTIME_WINDOW_MS] as the
     * share of real time they took. The metric is this playback's, registered at the start, closed at the stop.
     */
    private var runtime: KlangDiagnostics.Metric? = null
    private var runtimeSpentMs = 0.0
    private var runtimeWindowStartMs = 0.0

    /** Measures the pattern's work: a monotonic clock, fine-grained. */
    private val workClock = KlangTime.create()

    // ===== State Management =====
    private val running = KlangAtomicBool(false)
    private val jobLock = KlangLock()
    private var fetcherJob: Job? = null

    // ===== Playback Parameters =====
    /**
     * Cycles to seconds from the playback's start; a tempo change swaps in a map with one piece more (KLANG-00399).
     * A reader takes it once and keeps that map for its whole computation.
     */
    private var tempo = TempoMap(cyclesPerSecond = 0.5)
    private var lookaheadCycles: Double = 2.0

    // ===== Fetcher State =====
    private val sampleSoundLookAheadCycles = 8.0
    private var sampleSoundLookAheadPointer = 0.0
    private var queryCursorCycles = 0.0
    private val fetchChunk = 1.0

    // ===== Autonomous Progression =====
    /**
     * The playback's second 0 on the backend's timeline (KLANG-00399): asked for at the start (the clock's now, sent as
     * every voice's `playbackStartTime`), then the backend's own word, [KlangCommLink.Feedback.PlaybackAnchored]: it
     * starts a playback at the later of the second asked for and its now.
     */
    private var epochSec = 0.0
    private var requestedEpochSec = 0.0

    /** Whether [epochSec] is set: the playback's origin, from which the backend counts every voice. */
    private var begun = false

    // ===== Cycle Tracking (for CycleCompleted signal) =====
    private var lastEmittedCycle = -1

    // ===== Time =====
    /** The backend's timeline, the player's one: the playback's time is read on it, never on a frontend clock. */
    private val clock = context.clock

    // ===== Resync =====
    /**
     * Grace window in seconds: on a live update, voices starting within this window are preserved
     * (not replaced) so imminent notes keep playing; only voices beyond it are re-sent. Wider = fewer
     * FE/BE-skew races (see the scheduler's replace-path dedup) at the cost of edits applying later.
     */
    private val resyncGraceWindowSec = 0.2

    // ===== Public API =====
    @Suppress("unused")
    val isRunning: Boolean get() = running.get()

    /**
     * Update the pattern being played
     */
    fun updatePattern(pattern: KlangPattern) {
        // Old-pattern highlights are already scheduled ahead in the UI; tell it to
        // cancel them BEFORE the new pattern's VoicesScheduled signals arrive
        signals(KlangPlaybackSignal.PatternUpdated)
        // Update pattern
        this.pattern = pattern
        // Re-request current cycle to repopulate backend
        resyncCurrentCycle()
        // Pre-fetch samples for new pattern
        lookAheadForSampleSounds(queryCursorCycles, sampleSoundLookAheadCycles)
    }

    /**
     * Update the RPM (revolutions per minute = tempo).
     * Internally converts to CPS for cycle-based math.
     *
     * Values below [MIN_RPM] are clamped — a zero or negative cps would make time stand
     * still (or run backwards), causing the scheduler to spin or produce NaN anchors.
     */
    fun updateRpm(rpm: Double) {
        val cps = rpm.coerceAtLeast(MIN_RPM) / 60.0

        // Nothing is timed yet: the playback starts at the new tempo
        if (!begun) {
            tempo = TempoMap(cyclesPerSecond = cps)

            return
        }

        // The grace window (same as in resyncCurrentCycle) defines the switchover point:
        // voices before it keep the old tempo, voices after it use the new tempo.
        // The origin stays: the backend counts every voice from it, so only the map changes (KLANG-00399).
        // One reading of the clock for the switch and the resync's cutoff, so the two meet exactly.
        val nowSec = elapsedSec()
        val old = tempo
        val cutoffCycle = old.cycleAt(nowSec + resyncGraceWindowSec)
        // The cycles the counter has not reported yet still need their pieces
        val foldBefore = minOf(floor(old.cycleAt(nowSec)), (lastEmittedCycle + 1).toDouble())

        tempo = old.changed(atCycle = cutoffCycle, cyclesPerSecond = cps, foldBefore = foldBefore)
        // The old tempo's highlights are already scheduled ahead in the UI; cancel them before the new ones arrive
        signals(KlangPlaybackSignal.PatternUpdated)
        // Re-request current cycle with new tempo
        resyncCurrentCycle(nowSec = nowSec)
    }

    fun start(options: KlangCyclicPlayback.Options = KlangCyclicPlayback.Options()) {
        // Atomically transition from stopped to running
        if (!running.compareAndSet(expect = false, update = true)) {
            // Already running
            return
        }

        onStarted()

        // Update playback parameters
        this.tempo = TempoMap(cyclesPerSecond = options.rpm.coerceAtLeast(MIN_RPM) / 60.0)
        this.lookaheadCycles = options.lookaheadCycles

        // Start the fetcher job
        val prefetchCycles = options.prefetchCycles?.toDouble() ?: 2.0
        jobLock.withLock {
            fetcherJob = scope.launch(fetcherDispatcher.limitedParallelism(1)) {
                runPlayback(this, prefetchCycles)
            }
        }
    }

    fun stop() {
        // Atomically transition from running to stopped
        if (!running.compareAndSet(expect = true, update = false)) {
            // Already stopped
            return
        }

        // Cancel the fetcher job
        jobLock.withLock {
            fetcherJob?.cancel()
            fetcherJob = null
        }

        begun = false

        // Send clean up command to backend
        cleanUpBackend()

        // The pattern's work metric is this playback's: closed with it
        runtime?.close()
        runtime = null

        // Emit stopped signal
        signals(KlangPlaybackSignal.PlaybackStopped)

        onStopped()
    }

    /**
     * Called to deliver feedback messages.
     */
    fun handleFeedback(feedback: KlangCommLink.Feedback) {
        when (feedback) {
            is KlangCommLink.Feedback.RequestSample -> {
                requestAndSendSample(feedback.req)
            }

            is KlangCommLink.Feedback.PlaybackAnchored -> {
                // The backend started the playback here: from now on the playback's time counts from it
                epochSec = feedback.epochSec
            }

            is KlangCommLink.Feedback.Diagnostics -> {
                // The backend's timeline is the player's ([PlaybackClock]): Diagnostics carry SYSTEM_PLAYBACK_ID and
                // are consumed at the player, so this never reaches a scheduler; the branch keeps the `when` exhaustive.
            }

            is KlangCommLink.Feedback.SampleReceived -> {
                // Ignore - sample acknowledgements are handled at player level
            }

            is KlangCommLink.Feedback.BackendReady -> {
                // Handled at player level
            }
        }
    }

    // ===== Private Implementation =====

    /**
     * Cleans up backend state for this playback.
     */
    private fun cleanUpBackend() {
        scope.launch(fetcherDispatcher) {
            sendControl(KlangCommLink.Cmd.Cleanup(playbackId))
        }
    }

    /**
     * Pre-fetches samples needed for the first cycles of the pattern using the centralized preloader.
     * Waits for backend acknowledgement to ensure samples are ready before playback starts.
     */
    private suspend fun preloadSamples(prefetchCycles: Double) {
        val preloadVoices = try {
            queryEvents(from = 0.0, to = prefetchCycles, sendSignals = false)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        // Identify unique sample requests (non-oscillator sounds)
        val sampleRequests = preloadVoices
            .map { it.data.asSampleRequest() }
            .toSet()

        if (sampleRequests.isEmpty()) {
            return
        }

        // Use centralized preloader with deferred (waits for backend ack)
        // Emits signals and caches across playbacks
        val deferred = samplePreloader
            .ensureLoadedDeferred(requests = sampleRequests, signals = signals)

        // Wait for backend acknowledgement (with timeout to prevent hanging)
        try {
            withTimeout(5000.milliseconds) {
                deferred.await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue anyway - samples may have been sent even if ack wasn't received
        }
    }

    private suspend fun runPlayback(scope: CoroutineScope, prefetchCycles: Double) {
        // Reset state
        queryCursorCycles = 0.0
        sampleSoundLookAheadPointer = 0.0
        lastEmittedCycle = -1

        // ===== WAIT FOR BACKEND WARMUP =====
        // Ensures the audio thread's hot path is JIT'd before the first real voice hits.
        // Completes immediately if the backend already signalled ready earlier in the session.
        try {
            withTimeout(2000.milliseconds) { backendReady.await() }
        } catch (_: TimeoutCancellationException) {
            // Proceed anyway — warmup never arrived, but we'd rather play late than not at all.
            println("KlangPatternScheduler: backendReady timed out after 2s — proceeding cold")
        }

        // ===== PRELOAD PHASE =====
        preloadSamples(prefetchCycles)

        // ===== RECORD START TIME =====
        // Must be set BEFORE scheduling first cycle so voices have correct playbackStartTime
        requestedEpochSec = clock.nowSec()
        epochSec = requestedEpochSec
        begun = true

        // ===== THE PATTERN'S WORK, MEASURED FROM HERE =====
        runtime?.close()
        // One line for every playback, under one id: played, stopped and played again it goes on where it stopped, not a
        // line per play (the maintainer, 2026-10-29: "maybe a single sprudel entry is better and simpler"); two at once
        // share it, the newer post showing
        runtime = diagnostics.metric(
            id = "sprudel",
            name = "Sprudel runtime",
            category = "CPU",
            unit = "%",
            min = 0.0,
            max = 100.0,
        )
        runtimeSpentMs = 0.0
        runtimeWindowStartMs = workClock.internalMsNow()

        // ===== SCHEDULE FIRST CYCLE IMMEDIATELY =====
        // Sent as a single batched command so all voices share one nowSec snapshot at the
        // backend; per-voice sends would otherwise interleave with audio blocks and let later
        // voices in the batch slip into the past.
        try {
            val firstCycleEvents = queryEvents(from = 0.0, to = fetchChunk, sendSignals = true)
            if (firstCycleEvents.isNotEmpty()) {
                sendControl(KlangCommLink.Cmd.ScheduleVoices(playbackId = playbackId, voices = firstCycleEvents))
            }
            queryCursorCycles = fetchChunk
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Emit started signal
        signals(KlangPlaybackSignal.PlaybackStarted)

        while (scope.isActive) {
            // Emit completed cycles (stall-safe)
            emitCompletedCycles()
            // Look ahead for sample sound
            lookAheadForSampleSounds(from = queryCursorCycles + sampleSoundLookAheadCycles, dur = 1.0)
            // Request the next cycles from the source
            requestNextCyclesAndAdvanceCursor()
            // The share of real time the pattern's queries took, now and then
            postRuntime()
            // roughly 60 FPS
            pause()
        }

        println("KlangPatternScheduler stopped")
    }

    /** The seconds since the playback's start, on the backend's timeline. */
    private fun elapsedSec(): Double = clock.nowSec() - epochSec

    /**
     * What turns a playback second into the UI's: the wall second at which it is heard. The UI times its highlights on
     * the wall clock; the playback runs on the backend's timeline, heard the output latency later.
     */
    private fun uiOffsetSec(): Double = uiMs() / 1000.0 - clock.nowSec() + epochSec + clock.outputLatencySec

    /**
     * Emit CycleCompleted signals for all cycles that have been completed since last emission.
     * Stall-safe: if CPU stalls and multiple cycles pass, all are emitted in order.
     */
    private fun emitCompletedCycles() {
        // Check if still running before emitting (prevent emissions after stop)
        if (!running.get()) {
            return
        }

        val map = tempo
        val completedCycle = floor(map.cycleAt(elapsedSec())).toInt() - 1

        if (completedCycle > lastEmittedCycle) {
            // Emit all missing cycles in order
            val cyclesToEmit = (lastEmittedCycle + 1)..completedCycle

            val uiOffsetSec = uiOffsetSec()

            // Emit on callback dispatcher to avoid blocking scheduler
            scope.launch(callbackDispatcher) {
                // Double-check still running inside coroutine (may have stopped while launching)
                if (!running.get()) return@launch

                for (cycle in cyclesToEmit) {
                    // The cycle's end, when it is heard
                    val boundaryTimeSec = uiOffsetSec + map.secondsAt((cycle + 1).toDouble())

                    signals(KlangPlaybackSignal.CycleCompleted(cycleIndex = cycle, atTimeSec = boundaryTimeSec))
                }
            }

            lastEmittedCycle = completedCycle
        }
    }

    /**
     * Query events from the pattern and convert to ScheduledVoice.
     */
    /** Every pattern query, timed: the time goes into the pattern's work ([postRuntime]). */
    private fun queryEvents(from: Double, to: Double, sendSignals: Boolean): List<ScheduledVoice> {
        val startedMs = workClock.internalMsNow()

        try {
            return queryEventsUntimed(from = from, to = to, sendSignals = sendSignals)
        } finally {
            runtimeSpentMs += workClock.internalMsNow() - startedMs
        }
    }

    /**
     * Posts the share of real time the pattern's queries took since the last post, once [RUNTIME_WINDOW_MS] has passed;
     * plain numbers, no object made.
     */
    private fun postRuntime() {
        val now = workClock.internalMsNow()
        val elapsedMs = now - runtimeWindowStartMs

        if (elapsedMs < RUNTIME_WINDOW_MS) {
            return
        }

        runtime?.post(runtimeSpentMs / elapsedMs * 100.0)
        runtimeSpentMs = 0.0
        runtimeWindowStartMs = now
    }

    private fun queryEventsUntimed(from: Double, to: Double, sendSignals: Boolean): List<ScheduledVoice> {
        val map = tempo

        // A part per tempo: the pattern reads the tempo it is queried at (sprudel's `cps`, `beats`)
        val events = buildList {
            var at = from

            while (at < to) {
                val until = minOf(to, map.nextChangeAfter(at) ?: to)

                addAll(pattern.queryEvents(fromCycles = at, toCycles = until, cps = map.cyclesPerSecondAt(at)))
                at = until
            }
        }

        // Announce every inline DSL these events reference BEFORE they are scheduled, so the
        // synthetic names resolve on the backend by the time voices referencing them arrive.
        // The registrar belongs to the playback (it is shared with the realtime path); the
        // announce-once gate and the sweep itself live there.
        registrar.announceAll(events)

        // Transform to ScheduledVoice: seconds from the playback's start, through the tempo map; the UI's when heard
        val uiOffsetSec = uiOffsetSec()

        // Build voice signal events for callbacks
        val signalEvents = mutableListOf<KlangPlaybackSignal.VoicesScheduled.VoiceEvent>()

        val voices = events.map { event ->
            val relativeStartTime = map.secondsAt(event.startCycles)
            val duration = map.secondsAt(event.startCycles + event.durationCycles) - relativeStartTime

            // The UI's times: when it is heard, on the wall clock
            val uiStartTime = uiOffsetSec + relativeStartTime
            val uiEndTime = uiStartTime + duration

            // Convert to VoiceData. Inline ignitors resolve their synthetic name via
            // the global IgnitorDsl.uniqueId() map (pre-registered above).
            val voiceData = event.toVoiceData()

            // Collect signal event — control-only events (a `master(…)` carrier) are engine
            // configuration, not notes: they must not show up as phantom voices in the UI.
            if (voiceData.control != true) {
                signalEvents.add(
                    KlangPlaybackSignal.VoicesScheduled.VoiceEvent(
                        startTime = uiStartTime,
                        endTime = uiEndTime,
                        data = voiceData,
                        sourceLocations = event.sourceLocations,
                    )
                )
            }

            ScheduledVoice(
                playbackId = playbackId,
                data = voiceData,
                startTime = relativeStartTime,
                gateEndTime = relativeStartTime + duration,
                playbackStartTime = requestedEpochSec,
            )
        }

        // Fire signals on separate dispatcher
        if (sendSignals && signalEvents.isNotEmpty()) {
            scope.launch(callbackDispatcher) {
                signals(
                    KlangPlaybackSignal.VoicesScheduled(voices = signalEvents)
                )
            }
        }

        return voices
    }

    private fun lookAheadForSampleSounds(from: Double, dur: Double) {
        val to = from + dur

        if (to <= sampleSoundLookAheadPointer) {
            return
        }

        sampleSoundLookAheadPointer = to

        // Lookup events
        val events = queryEvents(from = from, to = to, sendSignals = false)

        // Get unique sample requests
        val sampleRequests = events
            .map { it.data.asSampleRequest() }
            .toSet()

        // Use preloader async (fire-and-forget, doesn't wait for backend ack)
        // No signals emitted here since this is lookahead, not initial preload
        samplePreloader.ensureLoadedSilently(requests = sampleRequests)
    }

    /**
     * The cycle playing now, on the scheduler's clock, through the tempo map.
     */
    private fun getNowCycle(): Double = tempo.cycleAt(elapsedSec())

    private fun requestNextCyclesAndAdvanceCursor() {
        val nowCycles = tempo.cycleAt(elapsedSec())
        val targetCycles = nowCycles + lookaheadCycles

        // Fetch as many new cycles as needed
        while (queryCursorCycles < targetCycles) {
            val from = queryCursorCycles
            val to = from + fetchChunk

            try {
                val events = queryEvents(from = from, to = to, sendSignals = true)

                if (events.isNotEmpty()) {
                    sendControl(KlangCommLink.Cmd.ScheduleVoices(playbackId = playbackId, voices = events))
                }

                queryCursorCycles = to
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.printStackTrace()
            }
        }
    }

    /**
     * Re-emits VoicesScheduled signals for all events in the current lookahead window.
     * Does not touch the audio backend — only fires UI signals.
     */
    fun reemitVoiceSignals() {
        if (!running.get()) {
            return
        }
        val nowCycle = getNowCycle()
        val from = floor(nowCycle)
        val to = queryCursorCycles
        if (from >= to) {
            return
        }
        try {
            queryEvents(from = from, to = to, sendSignals = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Re-requests the current cycle and sends events to backend.
     * Uses the [resyncGraceWindowSec] grace window: voices about to play within the window are
     * preserved, only voices beyond the cutoff are replaced. [nowSec] is a caller's reading of the clock, so its own
     * cut and this one meet exactly.
     */
    private fun resyncCurrentCycle(nowSec: Double = elapsedSec()) {
        val cutoffSec = nowSec + resyncGraceWindowSec

        val from = floor(tempo.cycleAt(nowSec))
        val to = ceil(queryCursorCycles)

        try {
            val voices = queryEvents(from = from, to = to, sendSignals = true)
            // Only send voices beyond the grace window
            val futureVoices = voices.filter { it.startTime >= cutoffSec }

            sendControl(
                KlangCommLink.Cmd.ReplaceVoices(
                    playbackId = playbackId,
                    voices = futureVoices,
                    afterTimeSec = cutoffSec,
                )
            )

            // Update cursor so the fetch loop continues from where resync left off
            queryCursorCycles = to
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestAndSendSample(req: SampleRequest) {
        // Use preloader async for backend-requested samples (fire-and-forget)
        samplePreloader.ensureLoadedSilently(requests = setOf(req))
    }
}
