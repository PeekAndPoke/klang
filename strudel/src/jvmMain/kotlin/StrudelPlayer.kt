package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_be.Oscillators
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.oscillators
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.samples.create
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.ceil
import kotlin.math.tanh

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
    private val prefetchCycles: Int get() = options.prefetchCycles
    private val lookaheadSec: Double get() = options.lookaheadSec
    private val fetchPeriodMs: Long get() = options.fetchPeriodMs
    private val blockFrames: Int get() = options.blockFrames
    private val maxOrbits: Int get() = options.maxOrbits

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // PLayback Rates
    val secPerCycle get() = 1.0 / cps
    val framesPerCycle get() = secPerCycle * sampleRate.toDouble()

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Playback state
    private val running = atomic(false)
    private val cursorFrame = atomic(0L)

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

    // Final mix buffer to sum all orbits
    private val masterMix = StereoBuffer(blockFrames)

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Coroutine Jobs
    private var fetchJob: Job? = null // Job for fetching new events
    private var audioJob: Job? = null // Job for mixing audio

    // Scheduler-owned output channel; audio coroutine drains it.
    // We keep it buffered to absorb bursts from queryArc.
    private var eventChannel: Channel<StrudelScheduledVoice>? = null

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Some stats
    private val droppedEvents = atomic(0)

    fun start() {
        startInternal()
    }

    fun stop() {
        if (!running.compareAndSet(expect = true, update = false)) return

        fetchJob?.cancel()
        audioJob?.cancel()
        fetchJob = null
        audioJob = null

        eventChannel?.close()
        eventChannel = null

        // Audio-owned state; safe to clear here for next start().
        voices.clear()
        cursorFrame.value = 0L
    }

    override fun close() = stop()

    private fun startInternal() {
        if (!running.compareAndSet(expect = false, update = true)) return

        val channel = Channel<StrudelScheduledVoice>(capacity = 8192)
        eventChannel = channel

        // CHANGE: 2 channels
        val format = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)
        val line = AudioSystem.getSourceDataLine(format)

        val bufferMs = 500
        val bytesPerFrame = 4 // Stereo (2) * 16-bit (2 bytes) = 4 bytes
        val bufferFrames = (sampleRate * bufferMs / 1000.0).toInt()
        val bufferBytes = bufferFrames * bytesPerFrame

        line.open(format, bufferBytes)
        line.start()

        //////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Prefetch some samples
        prefetchEventsAndSamples()

        var queryCursorCycles = prefetchCycles.toDouble()

        fetchJob = scope.launch(Dispatchers.Default.limitedParallelism(1)) {
            // We fetch strictly in chunks of 1.0 cycle to ensure consistency
            val fetchChunk = 1.0

            while (isActive && running.value) {
                val nowFrame = cursorFrame.value
                val nowSec = nowFrame.toDouble() / sampleRate.toDouble()
                val nowCycles = nowSec / secPerCycle

                // We want to maintain a buffer of future events up to this point
                val targetCycles = nowCycles + (lookaheadSec / secPerCycle)

                // Advance the cursor in full cycle steps until we meet the target
                while (queryCursorCycles < targetCycles) {
                    val from = queryCursorCycles
                    val to = from + fetchChunk

                    try {
                        // Query the arc for the next full cycle
                        val events = fetchEventsSorted(from, to)

                        for (e in events) {
                            val res = channel.trySend(e.toScheduled())
                            // Stats ...
                            if (res.isFailure) droppedEvents.incrementAndGet()
                        }

                        // Successfully processed this chunk, advance the cursor strictly
                        queryCursorCycles = to
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        // If fetching fails, break inner loop to retry after delay
                        break
                    }
                }

                delay(fetchPeriodMs)
            }
        }

        audioJob = scope.launch(Dispatchers.IO.limitedParallelism(1)) {
            val out = ByteArray(blockFrames * 4)

            try {
                while (isActive && running.value) {
                    // Drain channel to voices
                    while (true) {
                        val evt = channel.tryReceive().getOrNull() ?: break
                        voices.schedule(evt)
                    }
                    // Do the mixing
                    renderBlockInto(out)
                    // Write to the audio buffer
                    line.write(out, 0, out.size)
                }
            } finally {
                line.drain()
                line.stop()
                line.close()
            }
        }
    }

    private fun prefetchEventsAndSamples() {
        fetchEventsSorted(0.0, prefetchCycles.toDouble())
            .forEach { voices.schedule(it.toScheduled()) }
    }

    private fun fetchEventsSorted(from: Double, to: Double): List<StrudelPatternEvent> {
        return pattern.queryArc(from, to)
            .filter { it.begin >= from && it.begin < to }
            .sortedBy { it.begin }
    }

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

    private fun renderBlockInto(out: ByteArray) {
        val blockStart = cursorFrame.value

        // 1. Clear Global Buffers
        masterMix.clear()
        orbits.clearAll()

        // 2. Process Voices
        voices.process(blockStart)

        // 3. Process Global Delay & Sum Orbits
        orbits.processAndMix(masterMix)

        // 4. Output Stage (Interleave L/R)
        val masterMixL = masterMix.left
        val masterMixR = masterMix.right

        for (i in 0 until blockFrames) {
            // Left
            val rawL = masterMixL[i]
            val sampleL = tanh(rawL).coerceIn(-1.0, 1.0)
            val pcmL = (sampleL * Short.MAX_VALUE).toInt()

            // Right
            val rawR = masterMixR[i]
            val sampleR = tanh(rawR).coerceIn(-1.0, 1.0)
            val pcmR = (sampleR * Short.MAX_VALUE).toInt()

            // Interleaved: L, R
            val baseIdx = i * 4
            out[baseIdx] = (pcmL and 0xff).toByte()
            out[baseIdx + 1] = ((pcmL ushr 8) and 0xff).toByte()
            out[baseIdx + 2] = (pcmR and 0xff).toByte()
            out[baseIdx + 3] = ((pcmR ushr 8) and 0xff).toByte()
        }

        cursorFrame.value = blockStart + blockFrames
    }
}
