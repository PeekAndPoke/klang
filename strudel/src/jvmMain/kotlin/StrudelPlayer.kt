package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.Oscillators
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_bridge.KlangPlayerState
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.samples.create
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.math.ceil

/**
 * Real-time-ish audio renderer:
 * - fetches events from Strudel in a lookahead window (scheduler coroutine)
 * - pushes time-stamped events into a channel
 * - audio coroutine drains channel, schedules voices, and renders blocks
 *
 * Important for glitch-free audio:
 * - avoid locks/suspension in the audio loop
 * - use a sufficiently large SourceDataLine buffer
 */
class StrudelPlayer(
    /** The pattern to play */
    val pattern: StrudelPattern,
    /** Options on how to render the sound */
    val options: Options,
    /**
     * External scope makes lifecycle control easier (tests/apps).
     * Default is fine for a small demo.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AutoCloseable {

    class Options private constructor(
        /** Playback sample rate */
        val sampleRate: Int,
        /** Oscillator factory */
        val oscillators: Oscillators,
        /** Sample registry for drum machines etc.*/
        val samples: Samples,
        /** Cycles per second */
        val cps: Double,
        /** How far ahead we query Strudel (cycles->seconds via cps). */
        val lookaheadSec: Double,
        /** How often we query Strudel (milliseconds). */
        val fetchPeriodMs: Long,
        /** Fixed render quantum in frames. */
        val blockFrames: Int,
        /** Number of cycles to prefetch before starting playback. Needed to not miss the start. */
        val prefetchCycles: Int,
        /** Maximum number of Orbits */
        val maxOrbits: Int,
    ) {
        companion object {
            suspend operator fun invoke(
                /** Playback sample rate */
                sampleRate: Int = 48_000,
                /** Oscillator factory */
                oscillators: Oscillators = oscillators(sampleRate),
                /** Sample registry for drum machines etc.*/
                samples: Samples? = null,
                /** Cycles per second */
                cps: Double = 0.5,
                /** How far ahead we query Strudel (cycles->seconds via cps). */
                lookaheadSec: Double = 1.0,
                /** How often we query Strudel (milliseconds). */
                fetchPeriodMs: Long = 250L,
                /** Fixed render quantum in frames. */
                blockFrames: Int = 512,
                /** Number of cycles to prefetch before starting playback. Needed to not miss the start. */
                prefetchCycles: Int = ceil(maxOf(2.0, cps * 2)).toInt(),
                /** Maximum number of Orbits */
                maxOrbits: Int = 16,
            ): Options {
                return Options(
                    sampleRate = sampleRate,
                    oscillators = oscillators,
                    samples = samples ?: Samples.create(),
                    cps = cps,
                    lookaheadSec = lookaheadSec,
                    fetchPeriodMs = fetchPeriodMs,
                    blockFrames = blockFrames,
                    prefetchCycles = prefetchCycles,
                    maxOrbits = maxOrbits,
                )
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Convenience accessors for RenderOptions
    private val sampleRate: Int get() = options.sampleRate
    private val cps: Double get() = options.cps
    private val blockFrames: Int get() = options.blockFrames
    private val maxOrbits: Int get() = options.maxOrbits

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // PLayback Rates
    val secPerCycle get() = 1.0 / cps
    val framesPerCycle get() = secPerCycle * sampleRate.toDouble()

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Playback state
    private val state = KlangPlayerState()

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Orbits
    private val orbits = Orbits(maxOrbits = maxOrbits, blockFrames = blockFrames, sampleRate = sampleRate)

    // Voices Container (Manages lifecycle, scheduling, rendering)
    private val voices = StrudelVoices(
        StrudelVoices.Options(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            oscillators = options.oscillators,
            samples = options.samples,
            orbits = orbits,
        )
    )

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Coroutine Jobs
    private var playerJob: Job? = null

    fun start() {
        if (!state.running(expect = false, update = true)) return

        playerJob = scope.launch {
            val channel = Channel<StrudelScheduledVoice>(capacity = 8192)

            // Prefetch
            pattern.queryArc(0.0, options.prefetchCycles.toDouble())
                .filter { it.begin < options.prefetchCycles }
                .forEach { voices.schedule(it.toScheduled()) }

            val fetcher = StrudelEventsFetcher(
                pattern = pattern, options = options, state = state, eventChannel = channel
            )

            val audio = StrudelAudioLoop(
                options = options, state = state, eventChannel = channel, voices = voices, orbits = orbits
            )

            launch(Dispatchers.Default.limitedParallelism(1)) {
                fetcher.runFetcher(this)
            }

            launch(Dispatchers.IO.limitedParallelism(1)) {
                audio.runLoop(this)
            }
        }
    }

    fun stop() {
        if (!state.running(expect = true, update = false)) return
        playerJob?.cancel()
        playerJob = null
        voices.clear()
        state.cursorFrame(0L)
    }

    override fun close() = stop()

    private fun StrudelPatternEvent.toScheduled(): StrudelScheduledVoice {
        val startFrame = (begin * framesPerCycle).toLong()
        val durFrames = (dur * framesPerCycle).toLong().coerceAtLeast(1L)

        // If release is not specified, we use a tiny fade out (0.05s) to avoid clicks
        val releaseSec = release ?: 0.05
        val releaseFrames = (releaseSec * sampleRate).toLong()

        // The voice must live until: start + duration + release
        val gateEndFrame = startFrame + durFrames
        val endFrame = gateEndFrame + releaseFrames

        val scheduledEvent = StrudelScheduledVoice(
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            evt = this,
        )

        return scheduledEvent
    }
}
