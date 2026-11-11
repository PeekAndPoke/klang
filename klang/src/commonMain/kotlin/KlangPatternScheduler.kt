/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.ignitor.builtInSoundNames
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangAtomicBool
import io.peekandpoke.klang.common.infra.KlangLock
import io.peekandpoke.klang.common.infra.withLock
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

        /**
         * The least look-ahead, in seconds, whatever the tempo (KLANG-00399, step 3b): a main thread that stalls for less
         * loses no voice. Two cycles are one second at 120 rpm and 0.12 s at 999 rpm, too little for a busy phone.
         */
        const val LOOKAHEAD_MIN_SEC = 1.5

        /**
         * How many of the cycles a catch-up skips are read for the state they set: a main thread that stalled for longer
         * than this gets the state of the last ones only, and the catch-up stays short.
         */
        const val CATCH_UP_MAX_CYCLES = 64.0

        /** The look-ahead in cycles when nothing else is asked for: the options' default. */
        val DEFAULT_LOOKAHEAD_CYCLES: Double = KlangCyclicPlayback.Options().lookaheadCycles
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
    /** The look-ahead in cycles, at least [LOOKAHEAD_MIN_SEC] seconds of them. */
    private var lookaheadCycles: Double = DEFAULT_LOOKAHEAD_CYCLES

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

    /**
     * Started by [startDriven]: its holder renders, so a pattern query that fails is the holder's error, not one to
     * print and play on past.
     */
    private var driven = false

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

    /**
     * The playback's output gain, as last asked for ([setGain], KLANG-00406 step 5): kept while stopped, and sent at
     * the next start.
     */
    var gain = 1.0
        private set

    /**
     * Orders every send of this playback against the start's and the stop's flip of [running]: [setGain], the
     * announces of its inline DSLs, its voices ([sendWhileStarted]) and the stop's `Cleanup`, which the stop sends
     * under it. A command sent while started is on its way before the stop's cleanup is, and none follows the cleanup
     * until the next start (on the JVM, where the fetch loop and the stop run on two threads; audit L9-02).
     */
    private val sendLock = KlangLock()

    /**
     * Counts the starts. A fetch loop belongs to the start that launched it and sends only while that start is the
     * current one ([sendWhileStarted]): a loop the stop cancelled in the middle of a pattern query cannot send its
     * cycle into the next start either.
     */
    private var generation = 0

    // ===== Public API =====
    @Suppress("unused")
    val isRunning: Boolean get() = running.get()

    /**
     * Moves the playback's output gain to [gain] over [rampSec] seconds. The value is always kept, but SENT only while
     * the playback is started: this is the gate `KlangCommLink.Cmd.SetGain`'s KDoc names. Sent after the stop's
     * cleanup, it would create an engine on the backend that nothing ever stops.
     */
    fun setGain(gain: Double, rampSec: Double) {
        sendLock.withLock {
            this.gain = gain

            if (running.get()) {
                sendControl(KlangCommLink.Cmd.SetGain(playbackId = playbackId, gain = gain, rampSec = rampSec))
            }
        }
    }

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
        // Pre-fetch the new pattern's samples over the whole look-ahead the old one was fetched for (audit N-R4: through
        // the look-ahead's pointer, which the tick had already moved past this span, it fetched nothing). Not while
        // stopped (the start preloads) nor driven (the holder loaded every sample before the first block).
        if (running.get() && !driven) {
            prefetchSampleSounds(
                from = queryCursorCycles,
                to = maxOf(queryCursorCycles + sampleSoundLookAheadCycles, sampleSoundLookAheadPointer),
            )
        }
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
        // This start's number, taken inside the lock that flips [running]: read after it, a stop and another start in
        // between would hand this start the next one's number
        val started = sendLock.withLock {
            // Atomically transition from stopped to running
            if (!running.compareAndSet(expect = false, update = true)) {
                // Already running
                return
            }

            generation++

            // The gain kept goes first, at once: one port keeps the order, so the first block that sounds has it.
            // Sent at 1 too, as the backend may still hold this id's engine at another gain. [begin] sends it once more
            // right before the first voices, for an engine the backend disposed in between (audit L9-04)
            sendControl(KlangCommLink.Cmd.SetGain(playbackId = playbackId, gain = gain, rampSec = 0.0))

            generation
        }

        // The backend's registry forks die with the engine, which a start after a stop may find disposed: every inline
        // DSL is announced again for this start (audit L1-03, L9-04)
        registrar.forgetAnnounced()

        onStarted()

        // Start the fetcher job: only while this start is still the current one. A start that was stopped (and perhaps
        // started again) before it got here launches no loop and touches no state: the loop of the start that is
        // current is the one [fetcherJob] holds, so the next stop cancels it. Checked under [jobLock], which the stop
        // takes after its flip, so a stop that comes after the check finds the job set and cancels it
        val prefetchCycles = options.prefetchCycles?.toDouble() ?: 2.0
        jobLock.withLock {
            if (!isCurrent(started)) {
                return
            }

            driven = false
            reset(cyclesPerSecond = options.rpm.coerceAtLeast(MIN_RPM) / 60.0, lookaheadCycles = options.lookaheadCycles)

            fetcherJob = scope.launch(fetcherDispatcher.limitedParallelism(1)) {
                runPlayback(scope = this, prefetchCycles = prefetchCycles, started = started)
            }
        }
    }

    /** True while the start [started] is the current one: started and not stopped or started again since. */
    private fun isCurrent(started: Int): Boolean = sendLock.withLock { running.get() && started == generation }

    fun stop() {
        sendLock.withLock {
            // Atomically transition from running to stopped
            if (!running.compareAndSet(expect = true, update = false)) {
                // Already stopped
                return
            }

            // The cleanup goes out here, under the lock every send of this playback takes: what was sent while started
            // is ahead of it, and nothing follows it (audit L9-02; it was launched, and a cycle fetched while the stop
            // ran could follow it and revive the engine for good)
            sendControl(KlangCommLink.Cmd.Cleanup(playbackId))
        }

        // Cancel the fetcher job
        jobLock.withLock {
            fetcherJob?.cancel()
            fetcherJob = null
        }

        begun = false

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

            is KlangCommLink.Feedback.PlaybackFailed -> {
                // Handled at player level (logged there); the branch keeps the `when` exhaustive
            }
        }
    }

    // ===== Private Implementation =====

    /**
     * Sends [cmd] only while the start [started] is the current one, ordered by [sendLock] against the stop's flip and
     * its `Cleanup`: true when it was sent. Every voice the fetch path sends goes through here (audit L9-02, N-R3).
     */
    private fun sendWhileStarted(started: Int, cmd: KlangCommLink.Cmd): Boolean = sendLock.withLock {
        val current = running.get() && started == generation

        if (current) {
            sendControl(cmd)
        }

        current
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
        val sampleRequests = sampleRequestsOf(preloadVoices)

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
            // A stop cancels this loop while it waits: that ends the start here, rethrown (audit N-R3; swallowed, the
            // start went on and sent its first cycle after the stop). On Kotlin/JS and the JVM a CancellationException
            // is an Exception, so it is told apart by this coroutine's own state, not by its type: the timeout is one too
            currentCoroutineContext().ensureActive()
            e.printStackTrace()
            // Continue anyway - samples may have been sent even if ack wasn't received
        }
    }

    private suspend fun runPlayback(scope: CoroutineScope, prefetchCycles: Double, started: Int) {
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

        begin(started = started)

        // The live driver: asks the scheduler every round with the playback clock's time
        while (scope.isActive) {
            tick(nowSec = elapsedSec(), started = started)
            // roughly 60 FPS
            pause()
        }

        println("KlangPatternScheduler stopped")
    }

    /**
     * Sets the playback's start, sends its first cycle and says it started: the same live and driven. Only while the
     * start [started] is the current one: a stop that came first leaves nothing behind it, no origin, no open metric,
     * no voice (audit N-R3).
     */
    private fun begin(started: Int) {
        val current = sendLock.withLock {
            if (!running.get() || started != generation) {
                return@withLock false
            }

            // ===== RECORD START TIME =====
            // Must be set BEFORE scheduling first cycle so voices have correct playbackStartTime
            requestedEpochSec = clock.nowSec()
            epochSec = requestedEpochSec
            begun = true

            // ===== THE PATTERN'S WORK, MEASURED FROM HERE =====
            runtime?.close()
            // One line for every playback, under one id: played, stopped and played again it goes on where it stopped,
            // not a line per play (the maintainer, 2026-10-29: "maybe a single sprudel entry is better and simpler");
            // two at once share it, the newer post showing
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

            true
        }

        if (!current) {
            return
        }

        // ===== SCHEDULE FIRST CYCLE IMMEDIATELY =====
        // Sent as a single batched command so all voices share one nowSec snapshot at the
        // backend; per-voice sends would otherwise interleave with audio blocks and let later
        // voices in the batch slip into the past.
        try {
            val firstCycleEvents = queryEvents(from = 0.0, to = fetchChunk, sendSignals = true)
            // A first cycle with nothing in it still opens the playback, so the backend starts it now, where it was
            // asked to, not at the first sound (KLANG-00399, step 3c): a silent control event at second 0
            val opening = firstCycleEvents.ifEmpty { listOf(openingEvent()) }
            // The gain kept, once more, right before the first voices: the start's own SetGain may have reached an engine
            // the backend disposed before these voices came (a stopped one that went idle during the preload), and the
            // fresh engine would play at 1 (audit L9-04)
            val sent = sendLock.withLock {
                val stillCurrent = running.get() && started == generation

                if (stillCurrent) {
                    sendControl(KlangCommLink.Cmd.SetGain(playbackId = playbackId, gain = gain, rampSec = 0.0))
                    sendControl(KlangCommLink.Cmd.ScheduleVoices(playbackId = playbackId, voices = opening))
                }

                stillCurrent
            }

            if (!sent) {
                return
            }

            queryCursorCycles = fetchChunk
        } catch (e: Exception) {
            if (driven) {
                throw e
            }

            e.printStackTrace()
        }

        // Emit started signal
        signals(KlangPlaybackSignal.PlaybackStarted)
    }

    /**
     * Starts the playback driven by its holder, not by its own loop (KLANG-00399, step 4): nothing is awaited and
     * nothing preloaded, the first cycle goes out at once, and the holder then calls [tick] with the playback's time.
     * The offline renderer drives it so, block by block, in lockstep with its backend.
     */
    internal fun startDriven(cyclesPerSecond: Double) {
        // The flip and this start's number in one lock, as [start] takes them
        val started = sendLock.withLock {
            if (!running.compareAndSet(expect = false, update = true)) {
                return
            }

            ++generation
        }

        onStarted()

        driven = true
        // The tempo as given, not through rpm; the holder counts its length on the same floor
        reset(cyclesPerSecond = cyclesPerSecond.coerceAtLeast(MIN_RPM / 60.0), lookaheadCycles = DEFAULT_LOOKAHEAD_CYCLES)
        // The holder loads every sample before the first block: no look-ahead for samples
        sampleSoundLookAheadPointer = Double.POSITIVE_INFINITY

        begin(started = started)
    }

    /** The playback's tempo and look-ahead, and its state as at a start. */
    private fun reset(cyclesPerSecond: Double, lookaheadCycles: Double) {
        tempo = TempoMap(cyclesPerSecond = cyclesPerSecond)
        this.lookaheadCycles = lookaheadCycles
        queryCursorCycles = 0.0
        sampleSoundLookAheadPointer = 0.0
        lastEmittedCycle = -1
    }

    /**
     * One round at [nowSec], the playback's time (KLANG-00399, choice B): the scheduler keeps no time of its own, it is
     * asked. The live loop asks every 16 ms with the playback clock's time; an offline driver can ask with the
     * backend's exact position. It runs for the start that is current now, its number read under [sendLock]: a holder
     * that ticks a stopped playback sends nothing.
     */
    internal fun tick(nowSec: Double) {
        tick(nowSec = nowSec, started = sendLock.withLock { generation })
    }

    /** [tick] for the start [started]: what it sends goes out only while that start is the current one. */
    private fun tick(nowSec: Double, started: Int) {
        // Emit completed cycles (stall-safe)
        emitCompletedCycles()
        // Behind now after a stall: skip what has gone by, keep the state it set
        catchUp(nowSec = nowSec, started = started)
        // Look ahead for sample sound
        lookAheadForSampleSounds(from = queryCursorCycles + sampleSoundLookAheadCycles, dur = 1.0)
        // Request the next cycles from the source
        requestNextCyclesAndAdvanceCursor(nowSec = nowSec, started = started)
        // The share of real time the pattern's queries took, now and then
        postRuntime()
    }

    /**
     * When the query cursor is behind [nowSec] (the main thread stalled for longer than the look-ahead), the cycles up
     * to now have gone by: their voices would come late, and the backend would drop them. They are skipped, but the
     * state they set holds from now on, so it is sent as control events, each carrying one kind of state: the last
     * `master`, the last `katalyst` of each cylinder, and every solo control event that has not ended yet (a note's own
     * solo never sets the end, `SoloTracker`, so a skipped note's is not sent). They keep their own
     * times, which have gone by: the backend applies a control event's state even when it comes late, and in time
     * order, so the current cycle's newer state, sent next, still wins. At most [CATCH_UP_MAX_CYCLES] cycles are read.
     */
    private fun catchUp(nowSec: Double, started: Int) {
        val nowCycle = floor(tempo.cycleAt(nowSec))

        if (queryCursorCycles >= nowCycle) {
            return
        }

        val from = maxOf(queryCursorCycles, nowCycle - CATCH_UP_MAX_CYCLES)
        queryCursorCycles = nowCycle

        // In time order: the events come grouped by cycle, in no order within one
        val skipped = try {
            queryEvents(from = from, to = nowCycle, sendSignals = false).sortedBy { it.startTime }
        } catch (t: Throwable) {
            if (t is CancellationException || driven) {
                throw t
            }

            t.printStackTrace()

            return
        }

        val master = skipped.lastOrNull { it.data.master != null }
            ?.let { it.control(it.data.copy(katalyst = null, solo = null)) }
        val katalysts = skipped.filter { it.data.katalyst != null }
            .associateBy { it.data.cylinder ?: 0 }.values
            .map { it.control(it.data.copy(master = null, solo = null)) }
        val solos = skipped.filter { it.data.control == true && it.data.solo != null && it.gateEndTime > nowSec }
            .map { it.control(it.data.copy(master = null, katalyst = null)) }

        val controls = (listOfNotNull(master) + katalysts + solos).sortedBy { it.startTime }

        if (controls.isEmpty()) {
            return
        }

        sendWhileStarted(started = started, cmd = KlangCommLink.Cmd.ScheduleVoices(playbackId = playbackId, voices = controls))
    }

    /** This event as a control event carrying [data]: it sets state, it sounds no note. */
    private fun ScheduledVoice.control(data: VoiceData): ScheduledVoice = copy(data = data.copy(control = true))

    /** See [KlangCyclicPlayback.heardSec]. */
    fun heardSec(): Double? = when {
        begun -> maxOf(0.0, clock.reportedSec() - epochSec - clock.outputLatencySec)
        else -> null
    }

    /** A control event that sets nothing, at the playback's second 0: it only makes the backend start the playback. */
    private fun openingEvent(): ScheduledVoice = ScheduledVoice(
        playbackId = playbackId,
        data = VoiceData.empty.copy(control = true),
        startTime = 0.0,
        gateEndTime = 0.0,
        playbackStartTime = requestedEpochSec,
    )

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
        // On what the backend reported, not on the run-on past it: a cycle is completed when it has surely been
        // rendered, so a one-shot that stops on its last cycle loses nothing to a stalled backend (KLANG-00399)
        val completedCycle = floor(map.cycleAt(clock.reportedSec() - epochSec)).toInt() - 1

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
        // announce-once gate and the sweep itself live there. Only while started, under the send lock: a
        // `RegisterIgnitor` after the stop's cleanup would revive the engine for good (audit L9-02); the next start
        // announces everything again
        sendLock.withLock {
            if (running.get()) {
                registrar.announceAll(events)
            }
        }

        // Transform to ScheduledVoice: seconds from the playback's start, through the tempo map; the UI's when heard
        val uiOffsetSec = uiOffsetSec()

        // Build voice signal events for callbacks
        val signalEvents = mutableListOf<KlangPlaybackSignal.VoicesScheduled.VoiceEvent>()

        val voices = events.map { event ->
            val relativeStartTime = map.secondsAt(event.startCycles)
            val duration = map.durationSec(startCycle = event.startCycles, durationCycles = event.durationCycles)

            // The UI's times: when it is heard, on the wall clock. The end is the event's slot, not its gate: the highlight
            // marks where the event sits in the pattern, whatever its `legato` makes of the sound.
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
                // The pattern language's gate (sprudel's `legato`) folded in here: only seconds cross the wire
                gateEndTime = relativeStartTime + event.gateSeconds(duration),
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

        prefetchSampleSounds(from = from, to = to)
    }

    /** Loads the samples of the cycles [from] to [to], without waiting for the backend. */
    private fun prefetchSampleSounds(from: Double, to: Double) {
        // Lookup events; a cycle whose query fails has no samples to load, and the playback plays on
        val events = try {
            queryEvents(from = from, to = to, sendSignals = false)
        } catch (t: Throwable) {
            if (t is CancellationException) {
                throw t
            }

            t.printStackTrace()

            return
        }

        // Get unique sample requests
        val sampleRequests = sampleRequestsOf(events)

        // Use preloader async (fire-and-forget, doesn't wait for backend ack)
        // No signals emitted here since this is lookahead, not initial preload
        samplePreloader.ensureLoadedSilently(requests = sampleRequests)
    }

    /**
     * The sample requests of [voices]: only the voices whose sound the backend plays as a sample, by the engine's own
     * rule (`VoiceScheduler.prefetchSampleSound`): not a registered instrument, so no sound (the default instrument), no
     * built-in sound and no inline instrument this playback announced. Every other voice became a sample request that
     * was not found, kept for the session in four process-wide maps, one entry per note and per edit of an inline
     * instrument (audit L9-05).
     */
    private fun sampleRequestsOf(voices: List<ScheduledVoice>): Set<SampleRequest> = voices
        .filter { voice -> playsAsSample(voice.data.sound) }
        .map { voice -> voice.data.asSampleRequest() }
        .toSet()

    private fun playsAsSample(sound: String?): Boolean {
        if (sound == null) {
            return false
        }

        return sound.lowercase() !in builtInSoundNames && !registrar.ignitors.isAnnounced(sound)
    }

    /**
     * The cycle playing now, on the scheduler's clock, through the tempo map.
     */
    private fun getNowCycle(): Double = tempo.cycleAt(elapsedSec())

    private fun requestNextCyclesAndAdvanceCursor(nowSec: Double, started: Int) {
        // The look-ahead in seconds: its cycles, but never less than LOOKAHEAD_MIN_SEC
        val map = tempo
        val nowCycles = map.cycleAt(nowSec)
        val lookaheadSec = maxOf(LOOKAHEAD_MIN_SEC, map.secondsAt(nowCycles + lookaheadCycles) - nowSec)
        val targetCycles = map.cycleAt(nowSec + lookaheadSec)

        // Fetch as many new cycles as needed
        while (queryCursorCycles < targetCycles) {
            val from = queryCursorCycles
            val to = from + fetchChunk

            try {
                val events = queryEvents(from = from, to = to, sendSignals = true)

                val stopped = events.isNotEmpty() &&
                    !sendWhileStarted(started = started, cmd = KlangCommLink.Cmd.ScheduleVoices(playbackId = playbackId, voices = events))

                if (stopped) {
                    return
                }

                queryCursorCycles = to
            } catch (t: Throwable) {
                if (t is CancellationException || driven) {
                    throw t
                }

                t.printStackTrace()
                // On past the chunk that failed: asked again, it would fail again, in a loop that never yields
                queryCursorCycles = to
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

        // Cycle by cycle, as the fetch asks: a cycle whose query fails is left out, the others are still replaced
        val voices = ArrayList<ScheduledVoice>()
        var cycle = from

        while (cycle < to) {
            try {
                voices.addAll(queryEvents(from = cycle, to = cycle + fetchChunk, sendSignals = true))
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    throw t
                }

                t.printStackTrace()
            }

            cycle += fetchChunk
        }

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
    }

    private fun requestAndSendSample(req: SampleRequest) {
        // Use preloader async for backend-requested samples (fire-and-forget)
        samplePreloader.ensureLoadedSilently(requests = setOf(req))
    }
}
