package io.peekandpoke

import io.peekandpoke.Numbers.TWO_PI
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.graalvm.polyglot.Value
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
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
    private val strudel: Strudel,
    val sampleRate: Int = 48_000,
    val oscillators: Oscillators,
    val cps: Double = 0.5,
    /** How far ahead we query Strudel (cycles->seconds via cps). */
    val lookaheadSec: Double = 1.0,
    /** How often we query Strudel (milliseconds). */
    val fetchPeriodMs: Long = 25L,
    /** Fixed render quantum in frames. */
    val blockFrames: Int = 512,
    /**
     * External scope makes lifecycle control easier (tests/apps).
     * Default is fine for a small demo.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AutoCloseable {

    private data class ScheduledEvent(
        val startFrame: Long,
        val endFrame: Long,
        val e: StrudelEvent,
    )

    private class Voice(
        val startFrame: Long,
        val endFrame: Long,
        val gain: Double,
        val osc: OscFn,
        val filter: Filter,
        val freqHz: Double,
        val phaseInc: Double,
        var phase: Double = 0.0,
    )

    private val running = atomic(false)
    private val cursorFrame = atomic(0L)

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

    private val droppedEvents = atomic(0)

    fun extractEvents(pattern: Value, fromCycles: Double, toCycles: Double): List<StrudelEvent> {
        val result = pattern.invokeMember("queryArc", fromCycles, toCycles)

        val events = mutableListOf<StrudelEvent>()
        val count = result.arraySize

        for (i in 0 until count) {
            val item = result.getArrayElement(i)
            events += StrudelEvent.of(item, sampleRate).also {
//            println(strudel.prettyFormat(item))
                println("${it.note} ${it.scale}")
            }
        }

        return events.sortedBy { it.begin }
    }

    private fun StrudelEvent.toScheduled(framesPerCycle: Double): ScheduledEvent {
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

    fun start(pattern: Value) {
        if (!running.compareAndSet(expect = false, update = true)) return

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

        val secPerCycle = 1.0 / cps
        val framesPerCycle = secPerCycle * sampleRate.toDouble()

        // Scheduler state in Strudel-time (cycles)
        val initialEvents = extractEvents(pattern, 0.0, 2.0)
        for (e in initialEvents) {
            // Ensure we don't double-schedule if initialEvents returns things outside (unlikely but safe)
            if (e.begin >= 0.0 && e.begin < 2.0) {
                val scheduledEvent = e.toScheduled(framesPerCycle)
                scheduled.push(scheduledEvent)
            }
        }

        var queryCursorCycles = 2.0

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
                        val events = extractEvents(pattern, from, to)

                        // Crucial: Filter events to only schedule those that *start* within this window.
                        // queryArc returns all events active in the window, so without this,
                        // a note spanning multiple cycles would be scheduled multiple times.
                        val newEvents = events.filter { it.begin >= from && it.begin < to }

                        // println("Scheduling [${from}..${to}): found ${newEvents.size} events")

                        for (e in newEvents) {
                            val scheduledEvent = e.toScheduled(framesPerCycle)
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
                            break;
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

    private fun createVoices() {
        val blockStart = cursorFrame.value
        val blockEndExclusive = blockStart + blockFrames

        // 1) Promote scheduled events due in this block to active voices
        while (true) {
            val head = scheduled.peek() ?: break
            if (head.startFrame >= blockEndExclusive) break
            scheduled.pop()

            if (head.endFrame <= blockStart) continue

            val freqHz =  StrudelNotes.resolveFreq(head.e.note, head.e.scale)
            val osc = oscillators.get(e = head.e, freqHz = freqHz)

            // Bake the filter for better performance
            val bakedFilters = combineFilters(head.e.filters)

            // Pre-calculate increment once
            val phaseInc = TWO_PI * freqHz / sampleRate.toDouble()

            activeVoices += Voice(
                startFrame = head.startFrame,
                endFrame = head.endFrame,
                gain = head.e.gain,
                osc = osc,
                filter = bakedFilters,
                freqHz = freqHz,
                phaseInc = phaseInc,
                phase = 0.0,
            )
        }
    }

    private fun renderBlockInto(out: ByteArray) {
        val blockStart = cursorFrame.value
        val blockEndExclusive = blockStart + blockFrames

        // Clear the mix buffer
        mixBuffer.fill(0.0)

        var v = 0
        while (v < activeVoices.size) {
            val voice = activeVoices[v]

            if (blockEndExclusive <= voice.startFrame) {
                // Voice hasn't started yet (shouldn't happen with current logic but safe to check)
                v++
                continue
            }

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

    private fun combineFilters(filters: List<Filter>): Filter {
        if (filters.isEmpty()) return NoOpFilter
        if (filters.size == 1) return filters[0]
        return ChainFilter(filters)
    }

    private class MinHeap<T>(private val less: (T, T) -> Boolean) {
        private val data = ArrayList<T>()

        fun clear() = data.clear()
        fun peek(): T? = data.firstOrNull()

        fun push(x: T) {
            data.add(x)
            siftUp(data.lastIndex)
        }

        fun pop(): T? {
            if (data.isEmpty()) return null
            val root = data[0]
            val last = data.removeAt(data.lastIndex)
            if (data.isNotEmpty()) {
                data[0] = last
                siftDown(0)
            }
            return root
        }

        private fun siftUp(i0: Int) {
            var i = i0
            while (i > 0) {
                val p = (i - 1) / 2
                if (!less(data[i], data[p])) break
                val tmp = data[i]
                data[i] = data[p]
                data[p] = tmp
                i = p
            }
        }

        private fun siftDown(i0: Int) {
            var i = i0
            while (true) {
                val l = i * 2 + 1
                val r = i * 2 + 2
                var m = i

                if (l < data.size && less(data[l], data[m])) m = l
                if (r < data.size && less(data[r], data[m])) m = r
                if (m == i) break

                val tmp = data[i]
                data[i] = data[m]
                data[m] = tmp
                i = m
            }
        }
    }
}
