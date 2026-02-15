package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangAtomicBool
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangLock
import io.peekandpoke.klang.audio_bridge.infra.withLock
import io.peekandpoke.klang.audio_engine.KlangPlaybackContext
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignals
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.math.Rational
import kotlinx.coroutines.*
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Core scheduling controller for Strudel playback.
 * Handles pattern querying, event scheduling, sample management, and cycle tracking.
 * Designed to be reused by different playback lifecycle policies (continuous, one-shot, etc.).
 *
 * This is an internal implementation detail. Users should interact with [StrudelPlayback] interface.
 */
internal class StrudelPlaybackController(
    private val playbackId: String,
    private var pattern: StrudelPattern,
    private val context: KlangPlaybackContext,
    private val onStopped: () -> Unit,
    private val signals: KlangPlaybackSignals,
) {
    // Extract dependencies from context for convenience
    private val playerOptions = context.playerOptions
    private val samplePreloader = context.samplePreloader
    private val sendControl = context.sendControl
    private val scope = context.scope
    private val fetcherDispatcher = context.fetcherDispatcher
    private val callbackDispatcher = context.callbackDispatcher
    // ===== State Management =====
    private val running = KlangAtomicBool(false)
    private val jobLock = KlangLock()
    private var fetcherJob: Job? = null

    // ===== Playback Parameters =====
    private var cyclesPerSecond: Double = 0.5
    private var lookaheadSec: Double = 1.0
    private val secPerCycle get() = 1.0 / cyclesPerSecond

    // ===== Fetcher State =====
    private val sampleSoundLookAheadCycles = 8.0
    private var sampleSoundLookAheadPointer = 0.0
    private var queryCursorCycles = 0.0
    private val fetchChunk = 1.0

    // ===== Autonomous Progression =====
    private var startTimeMs = 0.0
    private var localFrameCounter = 0L

    // ===== Cycle Tracking (for CycleCompleted signal) =====
    private var lastEmittedCycle = -1L

    // ===== Resources =====
    private val samples: Samples = playerOptions.samples
    private val klangTime = KlangTime.create()

    // ===== Latency Compensation =====
    /** Measured transport latency in milliseconds. Applied to signals. */
    private var backendLatencyMs: Double = 0.0

    // ===== Public API =====

    val isRunning: Boolean get() = running.get()

    /**
     * Update the pattern being played
     */
    fun updatePattern(pattern: StrudelPattern) {
        // Update pattern
        this.pattern = pattern
        // Re-request current cycle to repopulate backend
        resyncCurrentCycle()
        // Pre-fetch samples for new pattern
        lookAheadForSampleSounds(queryCursorCycles, sampleSoundLookAheadCycles)
    }

    /**
     * Update the cycles per second (tempo)
     */
    fun updateCyclesPerSecond(cps: Double) {
        // Update tempo
        this.cyclesPerSecond = cps
        // Re-request current cycle with new tempo
        resyncCurrentCycle()
    }

    fun start(options: StrudelPlayback.Options = StrudelPlayback.Options()) {
        // Atomically transition from stopped to running
        if (!running.compareAndSet(expect = false, update = true)) {
            // Already running
            return
        }

        // Update playback parameters
        this.cyclesPerSecond = options.cyclesPerSecond
        this.lookaheadSec = options.lookaheadSec

        // Start the fetcher job
        jobLock.withLock {
            fetcherJob = scope.launch(fetcherDispatcher.limitedParallelism(1)) {
                run(this)
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
        signals.emit(KlangPlaybackSignal.PlaybackStopped)

        // Notify owner
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
                // Ignore - diagnostics are handled at player level
            }

            is KlangCommLink.Feedback.PlaybackLatency -> {
                backendLatencyMs = feedback.backendTimestampMs - startTimeMs
                backendLatencyMs = backendLatencyMs.coerceIn(0.0, 5000.0)
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
     */
    private suspend fun preloadSamples() {
        // Query first 2 cycles for sample discovery
        val prefetchCycles = 2.0
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

        if (sampleRequests.isEmpty()) return

        // Use centralized preloader (emits signals, caches across playbacks)
        samplePreloader.ensureLoaded(
            playbackId = playbackId,
            requests = sampleRequests,
            signals = signals,
        )
    }

    private suspend fun run(scope: CoroutineScope) {
        // Reset state
        queryCursorCycles = 0.0
        sampleSoundLookAheadPointer = 0.0
        lastEmittedCycle = -1L

        // ===== PRELOAD PHASE =====
        preloadSamples()

        // NOW record start time — after preloading
        startTimeMs = klangTime.internalMsNow()

        // Emit started signal
        signals.emit(KlangPlaybackSignal.PlaybackStarted)

        while (scope.isActive) {
            // Update local frame counter based on elapsed time
            updateLocalFrameCounter()
            // Emit completed cycles (stall-safe)
            emitCompletedCycles()
            // Look ahead for sample sound
            lookAheadForSampleSounds(queryCursorCycles + sampleSoundLookAheadCycles, 1.0)
            // Request the next cycles from the source
            requestNextCyclesAndAdvanceCursor()
            // roughly 60 FPS
            delay(16)
        }

        println("StrudelPlaybackController stopped")
    }

    private fun updateLocalFrameCounter() {
        val elapsedMs = klangTime.internalMsNow() - startTimeMs
        val elapsedSec = elapsedMs / 1000.0
        localFrameCounter = (elapsedSec * playerOptions.sampleRate).toLong()
    }

    /**
     * Emit CycleCompleted signals for all cycles that have been completed since last emission.
     * Stall-safe: if CPU stalls and multiple cycles pass, all are emitted in order.
     */
    private fun emitCompletedCycles() {
        // Check if still running before emitting (prevent emissions after stop)
        if (!running.get()) return

        val elapsedMs = klangTime.internalMsNow() - startTimeMs
        val elapsedSec = elapsedMs / 1000.0
        val completedCycle = floor(elapsedSec / secPerCycle).toLong() - 1

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

                    signals.emit(
                        KlangPlaybackSignal.CycleCompleted(
                            cycleIndex = cycle,
                            atTimeSec = boundaryTimeSec,
                        )
                    )
                }
            }

            lastEmittedCycle = completedCycle
        }
    }

    /**
     * Query events from the Strudel pattern and convert to ScheduledVoice.
     */
    private fun queryEvents(from: Double, to: Double, sendSignals: Boolean): List<ScheduledVoice> {
        // Convert Double time to Rational for exact pattern arithmetic
        val fromRational = Rational(from)
        val toRational = Rational(to)

        val ctx = QueryContext {
            set(QueryContext.cpsKey, cyclesPerSecond)
        }

        val events: List<StrudelPatternEvent> =
            pattern.queryArcContextual(from = fromRational, to = toRational, ctx = ctx)
                .filter { it.part.begin >= fromRational && it.part.begin < toRational }
                .filter { it.isOnset }
                .sortedBy { it.part.begin }

        // Transform to ScheduledVoice using absolute time from KlangTime epoch
        val secPerCycle = 1.0 / cyclesPerSecond
        val playbackStartTimeSec = startTimeMs / 1000.0

        // Latency compensation for UI signals
        val latencyOffsetSec = backendLatencyMs / 1000.0

        // Build voice signal events for callbacks
        val signalEvents = mutableListOf<KlangPlaybackSignal.VoicesScheduled.VoiceEvent>()

        val voices = events.map { event ->
            val timeSpan = event.whole
            val relativeStartTime = (timeSpan.begin * secPerCycle).toDouble()
            val duration = (timeSpan.duration * secPerCycle).toDouble()

            // Absolute times for UI callbacks
            val absoluteStartTime = playbackStartTimeSec + relativeStartTime
            val absoluteEndTime = absoluteStartTime + duration

            // Convert to VoiceData
            val voiceData = event.data.toVoiceData()

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
                signals.emit(
                    KlangPlaybackSignal.VoicesScheduled(voices = signalEvents)
                )
            }
        }

        return voices
    }

    private fun lookAheadForSampleSounds(from: Double, dur: Double) {
        val to = from + dur

        if (to <= sampleSoundLookAheadPointer) return

        sampleSoundLookAheadPointer = to

        // Lookup events
        val events = queryEvents(from = from, to = to, sendSignals = false)

        // Get unique sample requests
        val sampleRequests = events
            .map { it.data.asSampleRequest() }
            .toSet()

        // Use preloader to ensure samples are loaded (async, non-blocking)
        // No signals emitted here since this is lookahead, not initial preload
        scope.launch(fetcherDispatcher) {
            samplePreloader.ensureLoaded(
                playbackId = playbackId,
                requests = sampleRequests,
                signals = null, // Don't emit signals for lookahead
            )
        }
    }

    /**
     * Get the current cycle position accounting for backend latency.
     */
    private fun getNowCycle(): Double {
        val nowMs = klangTime.internalMsNow()
        val elapsedSec = ((nowMs - startTimeMs) - backendLatencyMs) / 1000.0
        return elapsedSec / secPerCycle
    }

    private fun requestNextCyclesAndAdvanceCursor() {
        // Use local frame counter for autonomous progression
        val nowFrame = localFrameCounter
        val nowSec = nowFrame.toDouble() / playerOptions.sampleRate.toDouble()
        val nowCycles = nowSec / secPerCycle
        val targetCycles = nowCycles + (lookaheadSec / secPerCycle)

        // Fetch as many new cycles as needed
        while (queryCursorCycles < targetCycles) {
            val from = queryCursorCycles
            val to = from + fetchChunk

            println("Advance: $from -> $to")

            try {
                val events = queryEvents(from = from, to = to, sendSignals = true)

                for (voice in events) {
                    // Schedule the voice
                    sendControl(
                        KlangCommLink.Cmd.ScheduleVoice(playbackId = playbackId, voice = voice)
                    )
                }

                queryCursorCycles = to
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.printStackTrace()
            }
        }
    }

    /**
     * Re-requests the current cycle and sends events to backend.
     */
    private fun resyncCurrentCycle() {
        val nowCycle = getNowCycle()

        val from = floor(nowCycle)
        val to = ceil(queryCursorCycles)

        try {
            val voices = queryEvents(from = from, to = to, sendSignals = false)

            println("Sync: $from -> $to (nowCycle=$nowCycle) --> ${voices.size} voices")

            sendControl(
                KlangCommLink.Cmd.ReplaceVoices(
                    playbackId = playbackId,
                    voices = voices,
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestAndSendSample(req: SampleRequest) {
        // Use preloader for backend-requested samples (keeps cache consistent)
        scope.launch(fetcherDispatcher) {
            samplePreloader.ensureLoaded(
                playbackId = playbackId,
                requests = setOf(req),
                signals = null, // Don't emit signals for backend requests
            )
        }
    }
}
