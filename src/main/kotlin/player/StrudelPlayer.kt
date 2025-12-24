package io.peekandpoke.player

import io.peekandpoke.dsp.*
import io.peekandpoke.samples.Samples
import io.peekandpoke.tones.StrudelTones
import io.peekandpoke.utils.MinHeap
import io.peekandpoke.utils.Numbers
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
                blockFrames: Int = 1024,
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

    private data class ScheduledEvent(
        val startFrame: Long,
        val endFrame: Long,
        val gateEndFrame: Long,  // When the note key is lifted
        val e: StrudelPatternEvent,
    )

    sealed interface Voice {
        class Envelope(
            val attackFrames: Double,
            val decayFrames: Double,
            val sustainLevel: Double,
            val releaseFrames: Double,
            var level: Double = 0.0,
        )

        class Delay(
            val amount: Double, // mix amount 0 .. 1
            val time: Double,
            val feedback: Double,
        )

        val orbit: Int
        val startFrame: Long
        val endFrame: Long
        val gateEndFrame: Long
        val gain: Double
        val filter: AudioFilter
        val envelope: Envelope
        val delay: Delay
    }

    private class SynthVoice(
        override val orbit: Int,
        override val startFrame: Long,
        override val endFrame: Long,
        override val gateEndFrame: Long,
        override val gain: Double,
        override val filter: AudioFilter,
        override val envelope: Voice.Envelope,
        override val delay: Voice.Delay,
        val osc: OscFn,
        val freqHz: Double,
        val phaseInc: Double,
        var phase: Double = 0.0,
    ) : Voice

    private class SampleVoice(
        override val orbit: Int,
        override val startFrame: Long,
        override val endFrame: Long,
        override val gateEndFrame: Long,
        override val gain: Double,
        override val filter: AudioFilter,
        override val envelope: Voice.Envelope,
        override val delay: Voice.Delay,
        val pcm: FloatArray,
        val pcmSampleRate: Int,
        val rate: Double,          // sampleFrames per outputFrame
        var playhead: Double = 0.0, // in sample frames
    ) : Voice

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Convenience accessors for RenderOptions
    private val sampleRate: Int get() = options.sampleRate
    private val oscillators: Oscillators get() = options.oscillators
    private val samples: Samples get() = options.samples
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
    // Events schedules to be created as Voices
    private val scheduled = MinHeap<ScheduledEvent> { a, b -> a.startFrame < b.startFrame }

    // Active voices
    private val activeVoices = ArrayList<Voice>(64)

    // Orbits
    private val orbits = Orbits(maxOrbits = maxOrbits, blockFrames = blockFrames, sampleRate = sampleRate)

    // Reusable scratchpad for single voice rendering
    private val voiceBuffer = DoubleArray(blockFrames)

    // Final mix buffer to sum all orbits
    private val masterMixBuffer = DoubleArray(blockFrames)

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Coroutine Jobs
    private var fetchJob: Job? = null // Job for fetching new events
    private var audioJob: Job? = null // Job for mixing audio

    // Scheduler-owned output channel; audio coroutine drains it.
    // We keep it buffered to absorb bursts from queryArc.
    private var eventChannel: Channel<ScheduledEvent>? = null

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

        val bufferMs = 500
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
                            // Drop events that should have already started ...
                            val res = channel.trySend(e.toScheduled())

                            if (res.isFailure) {
                                droppedEvents.incrementAndGet()
                            }

                            // If this is a sample sound make sure we load it
                            if (e.isSampleSound) {
                                samples.prefetch(e.sampleRequest)
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

        // Schedule events
        prefetched.forEach {
            scheduled.push(it.toScheduled())
        }

        // Preload samples
        prefetched.filter { it.isSampleSound }
            .map { e -> e.sampleRequest }
            .distinct()
            .forEach { sampleRequest ->
                samples.prefetch(sampleRequest)
            }
    }

    private fun fetchEventsSorted(from: Double, to: Double): List<StrudelPatternEvent> {
//        println("Fetching events $from -> $to")

        val events = pattern.queryArc(from, to, sampleRate)

        return events
            .filter { it.begin >= from && it.begin < to }
            .sortedBy { it.begin }
    }

    private fun StrudelPatternEvent.toScheduled(): ScheduledEvent {
        val startFrame = (begin * framesPerCycle).toLong()
        val durFrames = (dur * framesPerCycle).toLong().coerceAtLeast(1L)

        // If release is not specified, we use a tiny fade out (0.05s) to avoid clicks
        val releaseSec = release ?: 0.05
        val releaseFrames = (releaseSec * sampleRate).toLong()

        // The voice must live until: start + duration + release
        val gateEndFrame = startFrame + durFrames
        val endFrame = gateEndFrame + releaseFrames

        val scheduledEvent = ScheduledEvent(
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
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

            // Has this voice already ended?
            if (head.endFrame <= blockStart) continue

            makeVoice(scheduled = head, nowFrame = blockStart)?.let { voice ->
                activeVoices += voice
            }
        }
    }

    private fun makeVoice(scheduled: ScheduledEvent, nowFrame: Long): Voice? {
        // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Bake the filter for better performance
        val bakedFilters = combineFilters(scheduled.e.filters)

        // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Setup Envelope
        // Defaults: Attack=0.01s, Decay=0.0s, Sustain=1.0, Release=from scheduled logic
        val envelope = Voice.Envelope(
            attackFrames = (scheduled.e.attack ?: 0.01) * sampleRate,
            decayFrames = (scheduled.e.decay ?: 0.0) * sampleRate,
            sustainLevel = scheduled.e.sustain ?: 1.0,
            // We calculated total release frames in toScheduled, we can reverse it or just re-calc
            releaseFrames = (scheduled.endFrame - scheduled.gateEndFrame).toDouble()
        )

        // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Routing
        val orbit = scheduled.e.orbit ?: 0

        // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Delay
        val delay = Voice.Delay(
            amount = scheduled.e.delay ?: 0.0,
            time = scheduled.e.delayTime ?: 0.0,
            feedback = scheduled.e.delayFeedback ?: 0.0,
        )

        // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Tone or sound voice?
        val note = scheduled.e.note
        val sound = scheduled.e.sound

        val isOsci = scheduled.e.isOscillator && note != null
        val isSample = scheduled.e.isSampleSound && sound != null

        // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Switch
        val voice: Voice? = when {
            isOsci -> {
                val freqHz = StrudelTones.resolveFreq(note, scheduled.e.scale)
                val osc = oscillators.get(e = scheduled.e, freqHz = freqHz)
                // Pre-calculate increment once
                val phaseInc = Numbers.TWO_PI * freqHz / sampleRate.toDouble()

                SynthVoice(
                    orbit = orbit,
                    startFrame = scheduled.startFrame,
                    endFrame = scheduled.endFrame,
                    gateEndFrame = scheduled.gateEndFrame,
                    gain = scheduled.e.gain,
                    osc = osc,
                    filter = bakedFilters,
                    envelope = envelope,
                    delay = delay,
                    freqHz = freqHz,
                    phaseInc = phaseInc,
                    phase = 0.0,
                )
            }

            isSample -> {
                samples.getIfLoaded(scheduled.e.sampleRequest)?.let { (sampleId, decoded) ->
                    // Take the pitch of the sample as the basis
                    val baseSamplePitchHz = sampleId.sample.pitchHz

                    // Target pitch: if we have a note, use it; otherwise keep original pitch
                    val targetHz = scheduled.e.note?.let { note ->
                        StrudelTones.resolveFreq(note, scheduled.e.scale)
                    } ?: baseSamplePitchHz

                    val pitchRatio = (targetHz / baseSamplePitchHz).coerceIn(0.125, 8.0)

                    // print(pitchRatio.toString().padEnd(5) + " ") // ... seems to be ok

                    // sampleFrames per outputFrame, including pitch
                    val rate = (decoded.sampleRate.toDouble() / sampleRate.toDouble()) * pitchRatio

                    // If event is late, start inside the sample
                    val lateFrames = (nowFrame - scheduled.startFrame).coerceAtLeast(0L)
                    val playhead0 = lateFrames.toDouble() * rate

                    val pcmSize = decoded.pcm.size
                    if (pcmSize <= 1) return null
                    if (playhead0 >= (pcmSize - 1).toDouble()) return null

                    val remainingOutFrames =
                        ((pcmSize.toDouble() - playhead0) / rate).toLong().coerceAtLeast(1L)

                    val endFrame = nowFrame + remainingOutFrames

                    SampleVoice(
                        orbit = orbit,
                        startFrame = nowFrame,
                        endFrame = endFrame,
                        gateEndFrame = scheduled.gateEndFrame,
                        gain = scheduled.e.gain,
                        filter = bakedFilters,
                        envelope = envelope,
                        delay = delay,
                        pcm = decoded.pcm,
                        pcmSampleRate = decoded.sampleRate,
                        rate = rate,
                        playhead = playhead0,
                    )
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
        masterMixBuffer.fill(0.0)
        // Clear orbits
        orbits.clearAll()

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

            // Apply routing, get orbit and init orbit if needed
            val orbit = orbits.getOrInit(voice.orbit, voice)

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
                    // 2. Apply Filters
                    voice.filter.process(voiceBuffer, bufferOffset, length)
                    // 3. Apply env and mix down
                    applyEnvelopeAndMix(voice, vStart, length, bufferOffset, orbit)
                }

                is SampleVoice -> {
                    // Fill voiceBuffer with sample data (linear interpolation)
                    val pcm = voice.pcm
                    val pcmMax = pcm.size - 1
                    val gain = voice.gain

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
                            val a = pcm[base] * gain
                            val b = pcm[base + 1] * gain
                            voiceBuffer[idxOut] = a + (b - a) * frac
                        }

                        ph += voice.rate
                    }
                    // Update playhead!
                    voice.playhead = ph
                    // Process filters
                    voice.filter.process(voiceBuffer, bufferOffset, length)
                    // Apply env and mix down
                    applyEnvelopeAndMix(voice, vStart, length, bufferOffset, orbit)
                }
            }

            // goto next voice ...
            v++
        }

        // Process Global delay
        orbits.processAndMix(masterMixBuffer)

        // Convert Double Mix to Byte Output
        for (i in 0 until blockFrames) {
            val sample = masterMixBuffer[i].coerceIn(-1.0, 1.0)
            val pcm = (sample * Short.MAX_VALUE).toInt()
            out[i * 2] = (pcm and 0xff).toByte()
            out[i * 2 + 1] = ((pcm ushr 8) and 0xff).toByte()
        }

        cursorFrame.value = blockEndExclusive
    }

    private fun applyEnvelopeAndMix(
        voice: Voice,
        vStart: Long,
        length: Int,
        bufferOffset: Int,
        orbit: Orbit,
    ) {
        // Orbit buffers ... get them once so we do not need to deref on every int
        val orbitMixBuffer = orbit.mixBuffer
        val orbitDelaySendBuffer = orbit.delaySendBuffer

        val env = voice.envelope
        val attRate = if (env.attackFrames > 0) 1.0 / env.attackFrames else 1.0
        val decRate = if (env.decayFrames > 0) (1.0 - env.sustainLevel) / env.decayFrames else 0.0
        val relRate = if (env.releaseFrames > 0) env.sustainLevel / env.releaseFrames else 1.0

        var absPos = vStart - voice.startFrame
        val gateEndPos = voice.gateEndFrame - voice.startFrame
        var currentEnv = env.level

        // Optimization: check if we need to write to send buffer
        val delayAmount = voice.delay.amount
        val sendToDelay = delayAmount > 0.0

        for (i in 0 until length) {
            val idx = bufferOffset + i
            val s = voiceBuffer[idx]

            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Envelope Logic
            if (absPos >= gateEndPos) {
                // Release Phase
                val relPos = absPos - gateEndPos
                currentEnv = env.sustainLevel - (relPos * relRate)
            } else {
                // Gate Open
                if (absPos < env.attackFrames) {
                    // Attack
                    currentEnv = absPos * attRate
                } else if (absPos < env.attackFrames + env.decayFrames) {
                    // Decay
                    val decPos = absPos - env.attackFrames
                    currentEnv = 1.0 - (decPos * decRate)
                } else {
                    // Sustain
                    currentEnv = env.sustainLevel
                }
            }

            if (currentEnv < 0.0) currentEnv = 0.0


            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            // 1. Master Mix (Dry)
            val dryVal = s * voice.gain * currentEnv
            orbitMixBuffer[idx] += dryVal

            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            // 2. Delay Send (Wet)

            if (sendToDelay) {
                val wetVal = dryVal * delayAmount
                orbitDelaySendBuffer[idx] += wetVal
            }

            // Move ahead ...
            absPos++
        }

        // Save the last level back to the envelope state
        env.level = currentEnv
    }

    private fun combineFilters(filters: List<AudioFilter>): AudioFilter {
        if (filters.isEmpty()) return NoOpAudioFilter
        if (filters.size == 1) return filters[0]
        return ChainAudioFilter(filters)
    }
}
