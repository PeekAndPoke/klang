package io.peekandpoke.klang.strudel

import de.peekandpoke.ultra.common.maths.Ease
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangAtomicBool
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangLock
import io.peekandpoke.klang.audio_bridge.infra.withLock
import io.peekandpoke.klang.audio_engine.KlangPlayback
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignals
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.ValueRamp
import kotlinx.coroutines.*

/**
 * Strudel-specific playback implementation.
 * Combines pattern querying, event fetching, and lifecycle management.
 */
class StrudelPlayback internal constructor(
    /** Unique identifier for this playback */
    override val playbackId: String,
    /** Strudel pattern to play */
    private var pattern: StrudelPattern,
    /** The player options */
    private val playerOptions: KlangPlayer.Options,
    /** Function to send control messages to the backend */
    private val sendControl: (KlangCommLink.Cmd) -> Unit,
    /** The coroutines scope on which the player runs */
    private val scope: CoroutineScope,
    /** The dispatcher used for the event fetcher */
    private val fetcherDispatcher: CoroutineDispatcher,
    /** The dispatcher used for frontend callbacks */
    private val callbackDispatcher: CoroutineDispatcher,
    /** Callback invoked when this playback is stopped */
    private val onStopped: (KlangPlayback) -> Unit = {},
) : KlangPlayback {

    data class Options(
        /** Amount of time to look ahead when scheduling events */
        val lookaheadSec: Double = 1.0,
        /** Cycles per second (tempo) */
        val cyclesPerSecond: Double = 0.5,
        /** Initial cycles prefetch, so that the audio starts flawlessly. Auto-calculated if null. */
        val prefetchCycles: Int? = null,
    )

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

    // ===== Resources =====
    private val samples: Samples = playerOptions.samples
    private val samplesAlreadySent = mutableSetOf<SampleRequest>()
    private val klangTime = KlangTime.create()

    // ===== Solo / Mute Smooth Transitions =====
    /** Smooth solo transitions for other voices gains */
    private val soloGainRamp = ValueRamp(
        initialValue = 1.0,
        duration = 0.1,
        ease = Ease.InOut.cubic
    )

    // ===== Latency Compensation =====
    /** Measured transport latency in milliseconds. Applied to VoicesScheduled signal times. */
    private var backendLatencyMs: Double = 0.0

    // ===== Signal Bus =====

    /**
     * Signal bus for playback lifecycle signals.
     */
    override val signals = KlangPlaybackSignals()

    // ===== Public API =====

    /**
     * Update the pattern being played
     */
    fun updatePattern(pattern: StrudelPattern) {
        this.pattern = pattern
        lookAheadForSampleSounds(queryCursorCycles, sampleSoundLookAheadCycles)
    }

    /**
     * Update the cycles per second (tempo)
     */
    fun updateCyclesPerSecond(cps: Double) {
        this.cyclesPerSecond = cps
    }

    override fun start() {
        start(Options())
    }

    fun start(options: Options = Options()) {
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

    override fun stop() {
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

        // Emit stopped signal and clear listeners
        signals.emit(KlangPlaybackSignal.PlaybackStopped)
        signals.clear()

        // Notify the player that this playback has stopped
        onStopped(this)
    }

    // ===== Private Implementation =====


    /**
     * Cleans up backend state for this playback.
     */
    private fun cleanUpBackend() {
        // Notify backend to clean up this playback session
        scope.launch(fetcherDispatcher) {
            sendControl(KlangCommLink.Cmd.Cleanup(playbackId))
        }
    }

    /**
     * Pre-fetches samples needed for the first cycles of the pattern.
     *
     * Queries the pattern for the first cycles, identifies sample sounds,
     * loads their PCM data, and sends them to the backend BEFORE any voices are scheduled.
     *
     * This ensures the backend has sample data available when the first ScheduleVoice
     * commands arrive, preventing silent first notes.
     */
    private suspend fun preloadSamples() {
        val preloadStartMs = klangTime.internalMsNow()

        // Query first 2 cycles for sample discovery
        val prefetchCycles = 2.0
        val preloadVoices = try {
            queryEvents(from = 0.0, to = prefetchCycles, sendEvents = false)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        // Identify unique sample requests (non-oscillator sounds)
        val sampleRequests = preloadVoices
            .map { it.data.asSampleRequest() }
            .toSet()

        if (sampleRequests.isEmpty()) return

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
                    KlangCommLink.Cmd.Sample.NotFound(
                        playbackId = playbackId,
                        req = req,
                    )
                } else {
                    KlangCommLink.Cmd.Sample.Complete(
                        playbackId = playbackId,
                        req = req,
                        note = loadedSample.note,
                        pitchHz = loadedSample.pitchHz,
                        sample = loadedPcm,
                    )
                }

                // Send to backend (this goes into the ring buffer, processed in order)
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
    }

    private suspend fun run(scope: CoroutineScope) {
        // Reset state
        queryCursorCycles = 0.0
        sampleSoundLookAheadPointer = 0.0

        // Reset solo transition state
        soloGainRamp.reset(1.0)

        // ===== PRELOAD PHASE =====
        // Query first cycles to discover and preload samples before any voices are scheduled.
        // This ensures the backend has sample data before it tries to play them.
        preloadSamples()

        // NOW record start time — after preloading, so playback time starts fresh
        startTimeMs = klangTime.internalMsNow()

        // Emit started signal
        signals.emit(KlangPlaybackSignal.PlaybackStarted)

        lookAheadForSampleSounds(0.0, sampleSoundLookAheadCycles)

        while (scope.isActive) {
            // Update local frame counter based on elapsed time
            updateLocalFrameCounter()
            // Look ahead for sample sound
            lookAheadForSampleSounds(queryCursorCycles + sampleSoundLookAheadCycles, 1.0)
            // Request the next cycles from the source
            requestNextCyclesAndAdvanceCursor()
            // roughly 60 FPS
            delay(16)
        }

        println("StrudelPlayback stopped")
    }

    private fun updateLocalFrameCounter() {
        val elapsedMs = klangTime.internalMsNow() - startTimeMs
        val elapsedSec = elapsedMs / 1000.0
        localFrameCounter = (elapsedSec * playerOptions.sampleRate).toLong()
    }

    /**
     * Query events from the Strudel pattern and convert to ScheduledVoice.
     * Returns absolute times from KlangTime epoch.
     */
    private fun queryEvents(from: Double, to: Double, sendEvents: Boolean): List<ScheduledVoice> {
        // Convert Double time to Rational for exact pattern arithmetic
        val fromRational = Rational(from)
        val toRational = Rational(to)

        val ctx = QueryContext {
            set(QueryContext.cpsKey, cyclesPerSecond)
        }

        val events: List<StrudelPatternEvent> =
            pattern.queryArcContextual(from = fromRational, to = toRational, ctx = ctx)
                .filter { it.part.begin >= fromRational && it.part.begin < toRational }
                .filter { it.isOnset }  // Allow continuous OR onset events
                .sortedBy { it.part.begin }
                // Implement SOLO logic:
                // If any event in this batch is "soloed", drop all events that are NOT soloed.
                .let { rawEvents ->
                    applySoloLogicSmoothed(
                        rawEvents = rawEvents,
                        fromCycles = fromRational.toDouble(),
                        toCycles = toRational.toDouble()
                    )
                }

        // Transform to ScheduledVoice using absolute time from KlangTime epoch
        val secPerCycle = 1.0 / cyclesPerSecond
        val playbackStartTimeSec = startTimeMs / 1000.0

        // Latency compensation for UI signals (shift highlights to match actual audio playback)
        val latencyOffsetSec = backendLatencyMs / 1000.0

        // Build voice signal events for callbacks (collected first to avoid blocking audio scheduling)
        val signalEvents = mutableListOf<KlangPlaybackSignal.VoicesScheduled.VoiceEvent>()

        val voices = events.map { event ->
            // Use whole for scheduling (complete event), not part (clipped portion)
            val timeSpan = event.whole
            val relativeStartTime = (timeSpan.begin * secPerCycle).toDouble()
            val duration = (timeSpan.duration * secPerCycle).toDouble()

            // Absolute times for UI callbacks (need wall-clock for setTimeout highlighting)
            val absoluteStartTime = playbackStartTimeSec + relativeStartTime
            val absoluteEndTime = absoluteStartTime + duration

            // Convert to VoiceData once (used by both ScheduledVoice and signal)
            val voiceData = event.data.toVoiceData()

            // Collect signal event (don't invoke yet - audio scheduling is critical path)
            // Apply latency offset to sync highlights with actual audio output
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
                startTime = relativeStartTime,               // ← CHANGED: relative, not absolute
                gateEndTime = relativeStartTime + duration,  // ← CHANGED: relative, not absolute
            )
        }

        // Fire signals on separate dispatcher to avoid blocking audio scheduling
        if (sendEvents && signalEvents.isNotEmpty()) {
            scope.launch(callbackDispatcher) {
                signals.emit(
                    KlangPlaybackSignal.VoicesScheduled(voices = signalEvents)
                )
            }
        }

        return voices
    }

    private fun applySoloLogicSmoothed(
        rawEvents: List<StrudelPatternEvent>,
        fromCycles: Double,
        toCycles: Double,
    ): List<StrudelPatternEvent> {
        // 1. Identify Solo State for this batch
        // Simplified logic: If ANY event in this batch is marked as solo,
        // we consider this entire batch window to be in "solo mode".
        // The ramp will smooth out the transition between batches.
        val isSoloActiveInBatch = rawEvents.any { it.data.solo == true }

        // Determine target gain based on solo presence
        val targetGain = if (isSoloActiveInBatch) 0.05 else 1.0

        var batchCurrentTime = fromCycles

        // 2. Process events
        val smoothedEvents = rawEvents.map { evt ->
            val eventTime = evt.part.begin.toDouble()
            val dtCycles = eventTime - batchCurrentTime
            val dtSec = dtCycles * secPerCycle

            // Advance gain ramp towards the target for this batch
            soloGainRamp.step(targetGain, dtSec)
            batchCurrentTime = eventTime

            // Apply the current smoothed gain
            if (evt.data.solo != true) {
                evt.copy(data = evt.data.copy(gain = (evt.data.gain ?: 1.0) * soloGainRamp.current))
            } else {
                evt
            }
        }

        // 3. Advance state to the end of the batch
        // This ensures the envelope continues to evolve even if there are no more events in this chunk
        val tailDtCycles = toCycles - batchCurrentTime
        val tailDtSec = tailDtCycles * secPerCycle
        soloGainRamp.step(targetGain, tailDtSec)

        return smoothedEvents
    }

    private fun lookAheadForSampleSounds(from: Double, dur: Double) {
        val to = from + dur

        if (to <= sampleSoundLookAheadPointer) return

        sampleSoundLookAheadPointer = to

        // println("[Song: $playbackId] Sample Look ahead $from - $to")

        // Lookup events
        val events = queryEvents(from = from, to = to, sendEvents = false)

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

            try {
                val events = try {
                    queryEvents(from = from, to = to, sendEvents = true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }

                for (voice in events) {
                    // Schedule the voice
                    sendControl(
                        KlangCommLink.Cmd.ScheduleVoice(
                            playbackId = playbackId,
                            voice = voice,
                        )
                    )
                }

                queryCursorCycles = to
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.printStackTrace()
                break
            }
        }
    }

    /**
     * Called by the player to deliver feedback messages.
     * Note: playbackId is already filtered by the player.
     */
    override fun handleFeedback(feedback: KlangCommLink.Feedback) {
        when (feedback) {
            is KlangCommLink.Feedback.RequestSample -> {
                requestAndSendSample(feedback.req)
            }

            is KlangCommLink.Feedback.Diagnostics -> {
                // Ignore - diagnostics are handled at player level
            }

            is KlangCommLink.Feedback.PlaybackLatency -> {
                // Compute transport latency:
                // Both KlangTime clocks are seeded from Date.now(), so they share the same epoch.
                // backendTimestampMs is when the backend first saw our voices.
                // startTimeMs is when we started this playback.
                // The difference is how long it took for our voices to reach the backend.
                backendLatencyMs = feedback.backendTimestampMs - startTimeMs
                // Clamp to reasonable range (0 to 500ms). Negative means clock skew, treat as 0.
                backendLatencyMs = backendLatencyMs.coerceIn(0.0, 5000.0)
            }
        }
    }

    @Deprecated("Use handleFeedback instead - now called by player dispatcher")
    private fun processFeedbackEvents() {
        // This method is no longer used - feedback is now dispatched by KlangPlayer
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
