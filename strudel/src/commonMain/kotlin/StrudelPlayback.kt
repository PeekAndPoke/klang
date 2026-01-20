package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangAtomicBool
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangLock
import io.peekandpoke.klang.audio_bridge.infra.withLock
import io.peekandpoke.klang.audio_engine.KlangPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.math.Rational
import kotlinx.coroutines.*

/**
 * Strudel-specific playback implementation.
 * Combines pattern querying, event fetching, and lifecycle management.
 */
class StrudelPlayback internal constructor(
    /** Unique identifier for this playback */
    private val playbackId: String,
    /** Strudel pattern to play */
    private var pattern: StrudelPattern,
    /** The player options */
    private val playerOptions: KlangPlayer.Options,
    /** Shared communication link to the backend */
    commLink: KlangCommLink,
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
    private val control = commLink.frontend.control
    private val feedback = commLink.frontend.feedback
    private val samplesAlreadySent = mutableSetOf<SampleRequest>()
    private val klangTime = KlangTime.create()

    // ===== Live Coding Callbacks =====

    /**
     * Callback fired when a voice is scheduled for playback
     *
     * Used for live code highlighting - provides timing and source location information
     * so the frontend can highlight the corresponding source code.
     */
    var onVoiceScheduled: ((event: ScheduledVoiceEvent) -> Unit)? = null

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

        // Notify the player that this playback has stopped
        onStopped(this)
    }

    // ===== Private Implementation =====

    private suspend fun run(scope: CoroutineScope) {
        // Record start time for autonomous progression
        startTimeMs = klangTime.internalMsNow()

        // Reset state
        queryCursorCycles = 0.0
        sampleSoundLookAheadPointer = 0.0

        lookAheadForSampleSounds(0.0, sampleSoundLookAheadCycles)

        while (scope.isActive) {
            // Update local frame counter based on elapsed time
            updateLocalFrameCounter()

            // Look ahead for sample sound
            lookAheadForSampleSounds(queryCursorCycles + sampleSoundLookAheadCycles, 1.0)

            // Request the next cycles from the source
            requestNextCyclesAndAdvanceCursor()

            // Query feedback-events from backend (only for sample requests now)
            processFeedbackEvents()

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

        val events = pattern.queryArcContextual(from = fromRational, to = toRational, QueryContext.empty)
            .filter { it.begin >= fromRational && it.begin < toRational }
            .sortedBy { it.begin }

        // Transform to ScheduledVoice using absolute time from KlangTime epoch
        val secPerCycle = 1.0 / cyclesPerSecond
        val playbackStartTimeSec = startTimeMs / 1000.0

        // Build voice events for callbacks (collected first to avoid blocking audio scheduling)
        val voiceEvents = mutableListOf<ScheduledVoiceEvent>()

        val voices = events.map { event ->
            val relativeStartTime = (event.begin * secPerCycle).toDouble()
            val duration = (event.dur * secPerCycle).toDouble()

            // Convert to absolute time
            val absoluteStartTime = playbackStartTimeSec + relativeStartTime
            val absoluteEndTime = absoluteStartTime + duration

            // Collect callback event (don't invoke yet - audio scheduling is critical path)
            voiceEvents.add(
                ScheduledVoiceEvent(
                    startTime = absoluteStartTime,
                    endTime = absoluteEndTime,
                    data = event.data,
                    sourceLocations = event.sourceLocations,
                )
            )

            ScheduledVoice(
                data = event.data,
                startTime = absoluteStartTime,
                gateEndTime = absoluteEndTime,
            )
        }

        // Fire callbacks on separate dispatcher to avoid blocking audio scheduling
        if (sendEvents && voiceEvents.isNotEmpty() && onVoiceScheduled != null) {
            scope.launch(callbackDispatcher) {
                voiceEvents.forEach { event ->
                    onVoiceScheduled?.invoke(event)
                }
            }
        }

        return voices
    }

    private fun lookAheadForSampleSounds(from: Double, dur: Double) {
        val to = from + dur

        if (to <= sampleSoundLookAheadPointer) return

        sampleSoundLookAheadPointer = to

        println("[Song: $playbackId] Sample Look ahead $from - $to")

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
                    control.send(
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

    private fun processFeedbackEvents() {
        while (true) {
            val evt = feedback.receive() ?: break

            // Only process feedback for this playback
            if (evt.playbackId != playbackId) {
                continue
            }

            when (evt) {
                is KlangCommLink.Feedback.RequestSample -> {
                    requestAndSendSample(evt.req)
                }

                is KlangCommLink.Feedback.UpdateCursorFrame -> {
                    // Ignore - event-fetcher is now autonomous
                }
            }
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

            control.send(cmd)
        }
    }
}
