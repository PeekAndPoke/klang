package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangAtomicBool
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangLock
import io.peekandpoke.klang.audio_bridge.infra.withLock
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignals
import io.peekandpoke.klang.audio_engine.KlangPlayer
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
    private val playerOptions: KlangPlayer.Options,
    private val sendControl: (KlangCommLink.Cmd) -> Unit,
    private val scope: CoroutineScope,
    private val fetcherDispatcher: CoroutineDispatcher,
    private val callbackDispatcher: CoroutineDispatcher,
    private val onStopped: () -> Unit,
    private val signals: KlangPlaybackSignals,
) {
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
    private val samplesAlreadySent = mutableSetOf<SampleRequest>()
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
     * Pre-fetches samples needed for the first cycles of the pattern.
     */
    private suspend fun preloadSamples() {
        scope.async {
            val preloadStartMs = klangTime.internalMsNow()

            // Query first 2 cycles for sample discovery
            val prefetchCycles = 2.0
            val preloadVoices = try {
                queryEvents(from = 0.0, to = prefetchCycles, sendSignals = false)
            } catch (e: Exception) {
                e.printStackTrace()
                return@async
            }

            // Identify unique sample requests (non-oscillator sounds)
            val sampleRequests = preloadVoices
                .map { it.data.asSampleRequest() }
                .toSet()

            if (sampleRequests.isEmpty()) return@async

            // Emit preloading signal
            signals.emit(
                KlangPlaybackSignal.PreloadingSamples(
                    count = sampleRequests.size,
                    samples = sampleRequests.map { "${it.bank ?: ""}/${it.sound ?: ""}:${it.index ?: 0}" },
                )
            )

            // Load all samples concurrently and send to backend
            val jobs = sampleRequests.map { req ->
                scope.async(fetcherDispatcher) {
                    // Remember this sample so lookAheadForSampleSounds doesn't re-request it
                    samplesAlreadySent.add(req)

                    // Resolve and load
                    val loaded = samples.get(req)
                    val loadedSample = loaded?.sample
                    val loadedPcm = loaded?.pcm

                    val cmd = if (loadedSample == null || loadedPcm == null) {
                        KlangCommLink.Cmd.Sample.NotFound(playbackId = playbackId, req = req)
                    } else {
                        KlangCommLink.Cmd.Sample.Complete(
                            playbackId = playbackId,
                            req = req,
                            note = loadedSample.note,
                            pitchHz = loadedSample.pitchHz,
                            sample = loadedPcm,
                        )
                    }

                    // Send to backend
                    sendControl(cmd)
                }
            }

            // Wait for ALL samples to be loaded and sent
            jobs.forEach { it.await() }

            val durationMs = (klangTime.internalMsNow() - preloadStartMs).toLong()

            // Emit completion signal
            signals.emit(
                KlangPlaybackSignal.SamplesPreloaded(
                    count = sampleRequests.size,
                    durationMs = durationMs,
                )
            )
        }.await()
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

        // Figure out which samples we need to send to the backend
        val newSamples = events
            .map { it.data.asSampleRequest() }
            .toSet().minus(samplesAlreadySent)

        for (sample in newSamples) {
            // 1. Remember this one
            samplesAlreadySent.add(sample)
            // 2. Request the sample and send it to the backend
            requestAndSendSample(sample)
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
        samples.getWithCallback(req) { result ->
            val sample = result?.sample
            val pcm = result?.pcm

            val cmd = if (sample == null || pcm == null) {
                KlangCommLink.Cmd.Sample.NotFound(
                    playbackId = playbackId,
                    req = req,
                )
            } else {
                KlangCommLink.Cmd.Sample.Complete(
                    playbackId = playbackId,
                    req = req,
                    note = sample.note,
                    pitchHz = sample.pitchHz,
                    sample = pcm,
                )
            }

            sendControl(cmd)
        }
    }
}