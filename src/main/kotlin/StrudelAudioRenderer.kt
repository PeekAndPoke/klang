package io.peekandpoke

import io.peekandpoke.dsp.*
import io.peekandpoke.samples.SampleRegistry
import io.peekandpoke.samples.SampleRequest
import io.peekandpoke.utils.MinHeap
import io.peekandpoke.utils.Numbers.TWO_PI
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

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
class StrudelAudioRenderer(
    /** The pattern to play */
    val pattern: StrudelPattern,
    /** Options on how to render the sound */
    val options: RenderOptions = RenderOptions(),
    /**
     * External scope makes lifecycle control easier (tests/apps).
     * Default is fine for a small demo.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AutoCloseable {

    companion object {
        val defaultSampleRegistry: SampleRegistry? = run {
            null
        }
    }

    data class RenderOptions(
        /** Playback sample rate */
        val sampleRate: Int = 48_000,
        /** Oscillator factory */
        val oscillators: Oscillators = oscillators(sampleRate),
        /** Sample registry for drum machines etc.*/
        val samples: SampleRegistry? = defaultSampleRegistry,
        /** Cycles per second */
        val cps: Double = 0.5,
        /** How far ahead we query Strudel (cycles->seconds via cps). */
        val lookaheadSec: Double = 1.0,
        /** How often we query Strudel (milliseconds). */
        val fetchPeriodMs: Long = 250L,
        /** Fixed render quantum in frames. */
        val blockFrames: Int = 1024,
        /** Number of cycles to prefetch before starting playback. Needed to not miss the start. */
        val prefetchCycles: Int = ceil(maxOf(2.0, cps * 2)).toInt(),
    ) {
        companion object {
            val default = RenderOptions()
        }
    }

    private data class ScheduledEvent(
        val startFrame: Long,
        val endFrame: Long,
        val e: StrudelEvent,
    )

    private sealed interface Voice {
        val startFrame: Long
        val endFrame: Long
        val gain: Double
        val filter: AudioFilter
    }

    private class SynthVoice(
        override val startFrame: Long,
        override val endFrame: Long,
        override val gain: Double,
        override val filter: AudioFilter,
        val osc: OscFn,
        val freqHz: Double,
        val phaseInc: Double,
        var phase: Double = 0.0,
    ) : Voice

    private class SampleVoice(
        override val startFrame: Long,
        override val endFrame: Long,
        override val gain: Double,
        override val filter: AudioFilter,
        val pcm: FloatArray,
        val pcmSampleRate: Int,
        val rate: Double,          // sampleFrames per outputFrame
        var playhead: Double = 0.0, // in sample frames
    ) : Voice

    // Convenience accessors for RenderOptions
    private val sampleRate: Int get() = options.sampleRate
    private val oscillators: Oscillators get() = options.oscillators
    private val samples: SampleRegistry? get() = options.samples
    private val cps: Double get() = options.cps
    private val prefetchCycles: Int get() = options.prefetchCycles
    private val lookaheadSec: Double get() = options.lookaheadSec
    private val fetchPeriodMs: Long get() = options.fetchPeriodMs
    private val blockFrames: Int get() = options.blockFrames

    // Playback state
    private val running = atomic(false)
    private val cursorFrame = atomic(0L)

    // rates
    val secPerCycle get() = 1.0 / cps
    val framesPerCycle get() = secPerCycle * sampleRate.toDouble()

    // Scheduler-owned output channel; audio coroutine drains it.
    // We keep it buffered to absorb bursts from queryArc.
    private var eventChannel: Channel<ScheduledEvent>? = null

    // These are audio-owned (only touched by audio coroutine) => no locks needed.
    private val scheduled = MinHeap<ScheduledEvent> { a, b -> a.startFrame < b.startFrame }
    private val activeVoices = ArrayList<Voice>(64)

    // Reusable buffers to avoid allocation in loop
    private val mixBuffer = DoubleArray(blockFrames)
    private val voiceBuffer = DoubleArray(blockFrames)

    // Coroutine Jobs
    private var fetchJob: Job? = null // Job for fetching new events
    private var audioJob: Job? = null // Job for mixing audio

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
        scheduled.clear()
        activeVoices.clear()
        cursorFrame.value = 0L
    }

    override fun close() = stop()

    private fun startInternal() {
        if (!running.compareAndSet(expect = false, update = true)) return

        //////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Fresh channel per run (makes stop/start robust)
        val channel = Channel<ScheduledEvent>(capacity = 8192)
        eventChannel = channel

        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val line = AudioSystem.getSourceDataLine(format)

        val bufferMs = 200
        val bytesPerFrame = 2 // mono, 16-bit
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
                            // TODO clean up /////////////////////////////////////////////////////////////////////////
                            samples?.let { reg ->
                                // Load sample ... when there is no note we assume we have a sample sound
                                // TODO: fix this in the StrudelEvent and introduce a sealed class
                                //    Sound -> Note:Sound | Sample: Sound
                                if (e.isSampleSound) {
                                    reg.prefetch(e.sampleRequest).start()
                                }
                            }
                            // ///////////////////////////////////////////////////////////////////////////////////////

                            val scheduledEvent = e.toScheduled()

                            // Drop events that should have already started ...
                            val res = channel.trySend(scheduledEvent)

                            if (res.isFailure) {
                                droppedEvents.incrementAndGet()
                            }
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
            val out = ByteArray(blockFrames * 2)

            try {
                while (isActive && running.value) {
                    while (true) {
                        val ev: ScheduledEvent = channel.tryReceive().getOrNull() ?: run {
                            delay(5.milliseconds)
                            break
                        }
                        scheduled.push(ev)
                    }

                    // Prepare new voices
                    createVoices()
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
        val prefetched = fetchEventsSorted(0.0, prefetchCycles.toDouble())

        for (e in prefetched) {
            // schedule events
            val scheduledEvent = e.toScheduled()
            scheduled.push(scheduledEvent)

            // prefetch samples
            samples?.let { reg ->
                if (e.isSampleSound) {
                    reg.prefetch(e.sampleRequest).start()
                }
            }
        }
    }

    private fun fetchEventsSorted(from: Double, to: Double): List<StrudelEvent> {
//        println("Fetching events $from -> $to")

        val events = pattern.queryArc(from, to, sampleRate)

        return events
            .filter { it.begin >= from && it.begin < to }
            .sortedBy { it.begin }
    }

    private fun StrudelEvent.toScheduled(): ScheduledEvent {
        val startFrame = (begin * framesPerCycle).toLong()
        val durFrames = (dur * framesPerCycle).toLong().coerceAtLeast(1L)
        val endFrame = startFrame + durFrames

        val scheduledEvent = ScheduledEvent(
            startFrame = startFrame,
            endFrame = endFrame,
            e = this,
        )

        return scheduledEvent
    }

    private fun createVoices() {
        val blockStart = cursorFrame.value
        val blockEndExclusive = blockStart + blockFrames

        // 1) Promote scheduled events due in this block to active voices
        while (true) {
            val head = scheduled.peek() ?: break
            if (head.startFrame >= blockEndExclusive) break
            scheduled.pop()

            if (head.startFrame < blockStart) continue

            makeVoice(scheduled = head)?.let { voice ->
                activeVoices += voice
            }
        }
    }

    private fun makeVoice(scheduled: ScheduledEvent): Voice? {
        // Bake the filter for better performance
        val bakedFilters = combineFilters(scheduled.e.filters)

        // Tone or sound voice?
        val reg = samples
        val note = scheduled.e.note
        val sound = scheduled.e.sound

        val voice: Voice? = when {
            note != null -> {
                // TODO: ... what if it is not a note ...
                val freqHz = StrudelNotes.resolveFreq(note, scheduled.e.scale)
                val osc = oscillators.get(e = scheduled.e, freqHz = freqHz)
                // Pre-calculate increment once
                val phaseInc = TWO_PI * freqHz / sampleRate.toDouble()

                SynthVoice(
                    startFrame = scheduled.startFrame,
                    endFrame = scheduled.endFrame,
                    gain = scheduled.e.gain,
                    osc = osc,
                    filter = bakedFilters,
                    freqHz = freqHz,
                    phaseInc = phaseInc,
                    phase = 0.0,
                )
            }

            scheduled.e.isSampleSound && sound != null -> {
                if (reg != null && reg.canResolve(scheduled.e.sampleRequest)) {
                    reg.getIfLoaded(SampleRequest(scheduled.e.bank, sound, scheduled.e.soundIndex))?.let { decoded ->

                        val startFrame = scheduled.startFrame
                        val rate = decoded.sampleRate.toDouble() / sampleRate.toDouble()
                        val totalOutFrames = (decoded.pcm.size / rate).toLong()
                        val endFrame = startFrame + totalOutFrames

                        SampleVoice(
                            startFrame = startFrame,
                            endFrame = endFrame,
                            gain = scheduled.e.gain,
                            filter = bakedFilters,
                            pcm = decoded.pcm,
                            pcmSampleRate = decoded.sampleRate,
                            rate = rate,
                            playhead = 0.0,
                        )
                    }
                } else {
                    null
                }
            }

            else -> null
        }

        return voice
    }

    private fun renderBlockInto(out: ByteArray) {
        val blockStart = cursorFrame.value
        val blockEndExclusive = blockStart + blockFrames

        // Clear the mix buffer
        mixBuffer.fill(0.0)

//        if (activeVoices.size > 2) {
//            println()
//            println("active voices: ${activeVoices.size} | schedule: ${scheduled.size()}")
//        }

        var v = 0
        while (v < activeVoices.size) {
            val voice = activeVoices[v]

            if (blockEndExclusive <= voice.startFrame) {
                // Voice hasn't started yet (shouldn't happen with current logic but safe to check)
                v++
                continue
            }

            // Voice has already ended, so we remove it
            if (blockStart >= voice.endFrame) {
                // Voice finished, Swap and Pop
                val lastIdx = activeVoices.size - 1
                if (v < lastIdx) {
                    activeVoices[v] = activeVoices[lastIdx]
                }
                activeVoices.removeAt(lastIdx)
                continue
            }

            // Calculate valid range for this voice within the block
            // e.g., if voice starts at frame 10 of this 512 block, offset is 10
            val vStart = maxOf(blockStart, voice.startFrame)
            val vEnd = minOf(blockEndExclusive, voice.endFrame)

            val bufferOffset = (vStart - blockStart).toInt()
            val length = (vEnd - vStart).toInt()

            when (voice) {
                is SynthVoice -> {
                    // 1. Generate Audio into voiceBuffer
                    // This call is now a single virtual call per block (fast!)
                    // The loop inside 'process' is tight and vectorizable
                    voice.phase = voice.osc.process(
                        buffer = voiceBuffer,
                        offset = bufferOffset,
                        length = length,
                        phase = voice.phase,
                        phaseInc = voice.phaseInc
                    )

                    // 2. Apply Filters (Block processing!)
                    // The filter mutates voiceBuffer in-place using tight loops
                    voice.filter.process(voiceBuffer, bufferOffset, length)

                    // Simple envelope logic (approximate for block)
                    // To do this perfectly accurately per sample, we'd calculate env inside loop
                    // For performance, simple logic:
                    val attackFrames = 256.0
                    val releaseFrames = 512.0 // Fade out over ~10ms
                    val invAttack = 1.0 / attackFrames
                    val invRelease = 1.0 / releaseFrames

                    // Calculate where the release phase should start for this voice
                    val totalFrames = (voice.endFrame - voice.startFrame).toInt()
                    val releaseStart = (totalFrames - releaseFrames).toInt().coerceAtLeast(0)

                    // Current position relative to the start of the voice
                    var relPos = (vStart - voice.startFrame).toInt()

                    for (i in 0 until length) {
                        val idx = bufferOffset + i
                        val s = voiceBuffer[idx]

                        // Envelope Logic
                        var env = 1.0

                        if (relPos < attackFrames) {
                            // Attack Phase
                            env = relPos * invAttack
                        } else if (relPos >= releaseStart) {
                            // Release Phase (Fade out)
                            val relInRelease = relPos - releaseStart
                            // Linear fade out: 1.0 -> 0.0
                            env = 1.0 - (relInRelease * invRelease)
                        }

                        // Safety clamp to avoid negative gain if we overshoot slightly
                        if (env < 0.0) env = 0.0

                        // Accumulate to mix
                        mixBuffer[idx] += s * voice.gain * env
                        relPos++
                    }
                }

                is SampleVoice -> {
                    // Fill voiceBuffer with sample data (linear interpolation)
                    val pcm = voice.pcm
                    val pcmMax = pcm.size - 1

                    val startRelFrames = (vStart - voice.startFrame)
                    var ph = voice.playhead

                    for (i in 0 until length) {
                        val idxOut = bufferOffset + i
                        val base = ph.toInt()

                        if (base >= pcmMax) {
                            // sample ended
//                            voice.endFrame = minOf(voice.endFrame, vStart + i.toLong())
                            voiceBuffer[idxOut] = 0.0
                        } else {
                            val frac = ph - base.toDouble()
                            val a = pcm[base].toDouble()
                            val b = pcm[base + 1].toDouble()
                            voiceBuffer[idxOut] = a + (b - a) * frac
                        }

                        ph += voice.rate
                    }

                    // Update playhead!
                    voice.playhead = ph

                    // Process filters
                    voice.filter.process(voiceBuffer, bufferOffset, length)

                    for (i in 0 until length) {
                        val idx = bufferOffset + i
                        mixBuffer[idx] += voiceBuffer[idx] * voice.gain
                    }
                }
            }

            // next ...
            v++
        }

        // Convert Double Mix to Byte Output
        for (i in 0 until blockFrames) {
            val sample = mixBuffer[i].coerceIn(-1.0, 1.0)
            val pcm = (sample * Short.MAX_VALUE).toInt()
            out[i * 2] = (pcm and 0xff).toByte()
            out[i * 2 + 1] = ((pcm ushr 8) and 0xff).toByte()
        }

        cursorFrame.value = blockEndExclusive
    }

    private fun combineFilters(filters: List<AudioFilter>): AudioFilter {
        if (filters.isEmpty()) return NoOpAudioFilter
        if (filters.size == 1) return filters[0]
        return ChainAudioFilter(filters)
    }
}
