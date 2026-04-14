package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangPlaybackController.Companion.MIN_RPM
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Core scheduling controller for pattern playback.
 * Handles pattern querying, event scheduling, sample management, and cycle tracking.
 * Designed to be reused by different playback lifecycle policies (continuous, one-shot, etc.).
 *
 * This is an internal implementation detail. Users should interact with [KlangCyclicPlayback] interface.
 */
internal class KlangPlaybackController(
    private val playbackId: String,
    private var pattern: KlangPattern,
    context: KlangPlaybackContext,
    private val signals: StreamSource<KlangPlaybackSignal>,
    private val onStarted: () -> Unit = {},
    private val onStopped: () -> Unit = {},
) {
    // Extract dependencies from context for convenience
    private val playerOptions = context.playerOptions
    private val samplePreloader = context.samplePreloader
    private val sendControl = context.sendControl
    private val scope = context.scope
    private val fetcherDispatcher = context.fetcherDispatcher
    private val callbackDispatcher = context.callbackDispatcher
    private val backendReady = context.backendReady

    companion object {
        /**
         * Minimum allowed RPM. Values below this are clamped — a near-zero cps would make
         * `secPerCycle` explode and time effectively stand still (or compute NaN anchors).
         */
        const val MIN_RPM: Double = 1.0
    }

    // ===== State Management =====
    private val running = KlangAtomicBool(false)
    private val jobLock = KlangLock()
    private var fetcherJob: Job? = null

    // ===== Playback Parameters =====
    private var cyclesPerSecond: Double = 0.5
    private var lookaheadCycles: Double = 2.0
    private val secPerCycle get() = 1.0 / cyclesPerSecond

    // ===== Fetcher State =====
    private val sampleSoundLookAheadCycles = 8.0
    private var sampleSoundLookAheadPointer = 0.0
    private var queryCursorCycles = 0.0
    private val fetchChunk = 1.0

    // ===== Autonomous Progression =====
    private var startTimeMs = 0.0
    private var localFrameCounter = 0

    // ===== Cycle Tracking (for CycleCompleted signal) =====
    private var lastEmittedCycle = -1

    // ===== Resources =====
    private val klangTime = KlangTime.create()

    // ===== Latency Compensation =====
    /** Measured transport latency in milliseconds. Applied to signals. */
    private var backendLatencyMs: Double = 100.0
    private val largeDriftThresholdMs = 500.0

    // ===== Resync =====
    /** Grace window in seconds: voices within this window are preserved during resync */
    private val resyncGraceWindowSec = 0.05

    // ===== Public API =====
    @Suppress("unused")
    val isRunning: Boolean get() = running.get()

    /**
     * Update the pattern being played
     */
    fun updatePattern(pattern: KlangPattern) {
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
        val safeRpm = rpm.coerceAtLeast(MIN_RPM)
        val cps = safeRpm / 60.0
        // The grace window (same as in resyncCurrentCycle) defines the switchover point:
        // voices before it keep the old tempo, voices after it use the new tempo.
        val nowMs = klangTime.internalMsNow()
        val cutoffMs = nowMs + resyncGraceWindowSec * 1000.0
        // Cycle position at the cutoff under the OLD cps
        val cutoffElapsedSec = (cutoffMs - startTimeMs) / 1000.0
        val cutoffCycle = cutoffElapsedSec * cyclesPerSecond
        // Update tempo
        this.cyclesPerSecond = cps
        // Recalculate startTimeMs so cutoffCycle maps to the same wall-clock time under new cps
        startTimeMs = cutoffMs - (cutoffCycle / cyclesPerSecond) * 1000.0
        // Re-request current cycle with new tempo
        resyncCurrentCycle()
    }

    fun start(options: KlangCyclicPlayback.Options = KlangCyclicPlayback.Options()) {
        // Atomically transition from stopped to running
        if (!running.compareAndSet(expect = false, update = true)) {
            // Already running
            return
        }

        onStarted()

        // Update playback parameters
        this.cyclesPerSecond = options.rpm.coerceAtLeast(MIN_RPM) / 60.0
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

        // Send clean up command to backend
        cleanUpBackend()

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

            is KlangCommLink.Feedback.Diagnostics -> {
                val latency = feedback.outputLatencyMs
                val rawOffset = (feedback.backendNowMs - klangTime.internalMsNow()) + latency
                val drift = abs(rawOffset - backendLatencyMs)

                backendLatencyMs = if (drift > largeDriftThresholdMs) {
                    // Large clock discontinuity (hibernate, AudioContext suspension, etc.), Snap immediately
                    rawOffset
                } else {
                    // Normal case: EMA α=0.05: ~1 second convergence at 20 Hz; smooths message-transit jitter
                    backendLatencyMs * 0.95 + rawOffset * 0.05
                }
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
            withTimeout(5000) {
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
            withTimeout(2000) { backendReady.await() }
        } catch (e: TimeoutCancellationException) {
            // Proceed anyway — warmup never arrived, but we'd rather play late than not at all.
            println("KlangPlaybackController: backendReady timed out after 2s — proceeding cold")
        }

        // ===== PRELOAD PHASE =====
        preloadSamples(prefetchCycles)

        // ===== RECORD START TIME =====
        // Must be set BEFORE scheduling first cycle so voices have correct playbackStartTime
        startTimeMs = klangTime.internalMsNow()

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
            // Update local frame counter based on elapsed time
            updateLocalFrameCounter()
            // Emit completed cycles (stall-safe)
            emitCompletedCycles()
            // Look ahead for sample sound
            lookAheadForSampleSounds(from = queryCursorCycles + sampleSoundLookAheadCycles, dur = 1.0)
            // Request the next cycles from the source
            requestNextCyclesAndAdvanceCursor()
            // roughly 60 FPS
            delay(16)
        }

        println("KlangPlaybackController stopped")
    }

    private fun updateLocalFrameCounter() {
        val elapsedMs = klangTime.internalMsNow() - startTimeMs
        val elapsedSec = elapsedMs / 1000.0
        localFrameCounter = (elapsedSec * playerOptions.sampleRate).toInt()
    }

    /**
     * Emit CycleCompleted signals for all cycles that have been completed since last emission.
     * Stall-safe: if CPU stalls and multiple cycles pass, all are emitted in order.
     */
    private fun emitCompletedCycles() {
        // Check if still running before emitting (prevent emissions after stop)
        if (!running.get()) {
            return
        }

        val elapsedMs = klangTime.internalMsNow() - startTimeMs
        val elapsedSec = elapsedMs / 1000.0
        val completedCycle = floor(elapsedSec / secPerCycle).toInt() - 1

        if (completedCycle > lastEmittedCycle) {
            // Emit all missing cycles in order
            val cyclesToEmit = (lastEmittedCycle + 1)..completedCycle

            // Emit on callback dispatcher to avoid blocking scheduler
            scope.launch(callbackDispatcher) {
                // Double-check still running inside coroutine (may have stopped while launching)
                if (!running.get()) return@launch

                for (cycle in cyclesToEmit) {
                    // Calculate boundary time for this cycle
                    val playbackStartTimeSec = startTimeMs / 1000.0
                    val latencyOffsetSec = backendLatencyMs / 1000.0
                    val boundaryTimeSec = playbackStartTimeSec + ((cycle + 1) * secPerCycle) + latencyOffsetSec

                    signals(KlangPlaybackSignal.CycleCompleted(cycleIndex = cycle, atTimeSec = boundaryTimeSec))
                }
            }

            lastEmittedCycle = completedCycle
        }
    }

    /**
     * Query events from the pattern and convert to ScheduledVoice.
     */
    private fun queryEvents(from: Double, to: Double, sendSignals: Boolean): List<ScheduledVoice> {
        val events = pattern.queryEvents(fromCycles = from, toCycles = to, cps = cyclesPerSecond)

        // Transform to ScheduledVoice using absolute time from KlangTime epoch
        val secPerCycle = 1.0 / cyclesPerSecond
        val playbackStartTimeSec = startTimeMs / 1000.0

        // Latency compensation for UI signals
        val latencyOffsetSec = backendLatencyMs / 1000.0

        // Build voice signal events for callbacks
        val signalEvents = mutableListOf<KlangPlaybackSignal.VoicesScheduled.VoiceEvent>()

        val voices = events.map { event ->
            val relativeStartTime = event.startCycles * secPerCycle
            val duration = event.durationCycles * secPerCycle

            // Absolute times for UI callbacks
            val absoluteStartTime = playbackStartTimeSec + relativeStartTime
            val absoluteEndTime = absoluteStartTime + duration

            // Convert to VoiceData
            val voiceData = event.toVoiceData()

            // Collect signal event
            signalEvents.add(
                KlangPlaybackSignal.VoicesScheduled.VoiceEvent(
                    startTime = absoluteStartTime + latencyOffsetSec,
                    endTime = absoluteEndTime + latencyOffsetSec,
                    data = voiceData,
                    sourceLocations = event.sourceLocations,
                )
            )

            ScheduledVoice(
                playbackId = playbackId,
                data = voiceData,
                startTime = relativeStartTime,
                gateEndTime = relativeStartTime + duration,
                playbackStartTime = playbackStartTimeSec,
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
     * Get the current cycle position accounting for backend latency.
     */
    private fun getNowCycle(): Double {
        val nowMs = klangTime.internalMsNow()
        val elapsedSec = (nowMs - startTimeMs) / 1000.0
        return elapsedSec / secPerCycle
    }

    private fun requestNextCyclesAndAdvanceCursor() {
        // Use local frame counter for autonomous progression
        val nowFrame = localFrameCounter
        val nowSec = nowFrame.toDouble() / playerOptions.sampleRate.toDouble()
        val nowCycles = nowSec / secPerCycle
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
     * Uses a 50ms grace window: voices about to play within the window are preserved,
     * only voices beyond the cutoff are replaced.
     */
    private fun resyncCurrentCycle() {
        val nowCycle = getNowCycle()
        val cutoffCycle = nowCycle + (resyncGraceWindowSec * cyclesPerSecond)

        val from = floor(nowCycle)
        val to = ceil(queryCursorCycles)

        try {
            val voices = queryEvents(from = from, to = to, sendSignals = true)
            // Only send voices beyond the grace window
            val futureVoices = voices.filter { it.startTime / secPerCycle >= cutoffCycle }

            sendControl(
                KlangCommLink.Cmd.ReplaceVoices(
                    playbackId = playbackId,
                    voices = futureVoices,
                    afterTimeSec = cutoffCycle * secPerCycle,
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
