package io.peekandpoke

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.graalvm.polyglot.Value
import java.util.PriorityQueue
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
    val blockFrames: Int = 2048,
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

    private data class Voice(
        val startFrame: Long,
        val endFrame: Long,
        val gain: Double,
        val osc: OscFn,
        val filters: List<FilterFn>,
        val freqHz: Double,
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

    private var fetchJob: Job? = null
    private var audioJob: Job? = null

    private val droppedEvents = atomic(0)

    fun extractEvents(pattern: Value, fromCycles: Double, toCycles: Double): List<StrudelEvent> {
        val result = pattern.invokeMember("queryArc", fromCycles, toCycles)

        val events = mutableListOf<StrudelEvent>()
        val topN = result.arraySize
        for (i in 0 until topN) {
            val item = result.getArrayElement(i)
            events += StrudelEvent.of(item, sampleRate)
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
        for(e in initialEvents) {
            val scheduledEvent = e.toScheduled(framesPerCycle)
            scheduled.push(scheduledEvent)
        }

        var queryCursorCycles = 2.0

        // prefetch the first notes... otherwiese thay are missing


        fetchJob = scope.launch(Dispatchers.Default.limitedParallelism(1)) {
            val overlapSec = 0.00
            val overlapCycles = overlapSec / secPerCycle

            while (isActive && running.value) {
                val nowFrame = cursorFrame.value
                val nowSec = nowFrame.toDouble() / sampleRate.toDouble()
                val nowCycles = nowSec / secPerCycle

                val fromCycles = maxOf(queryCursorCycles, nowCycles - overlapCycles)
                val toCycles = nowCycles + (lookaheadSec / secPerCycle)

                try {
                    val events = extractEvents(pattern, fromCycles, toCycles)
                    val due = events.filter { it.begin >= fromCycles }

                    println("num events: ${events.size}, due: ${due.size}, dropped: ${events.size - due.size}")

                    for (e in due) {
                        val startFrame = (e.begin * framesPerCycle).toLong()
                        val durFrames = (e.dur * framesPerCycle).toLong().coerceAtLeast(1L)
                        val endFrame = startFrame + durFrames

                        val scheduledEvent = ScheduledEvent(
                            startFrame = startFrame,
                            endFrame = endFrame,
                            e = e,
                        )

                        val res = channel.trySend(scheduledEvent)

                        if (res.isFailure) {
                            droppedEvents.incrementAndGet()
                        }
                    }

                    queryCursorCycles = toCycles
                } catch (t: Throwable) {
                    t.printStackTrace()
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

                    renderBlockInto(out, frames = blockFrames)
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

    private fun renderBlockInto(out: ByteArray, frames: Int) {
        val blockStart = cursorFrame.value
        val blockEndExclusive = blockStart + frames.toLong()

        // 1) Promote scheduled events due in this block to active voices
        while (true) {
            val head = scheduled.peek() ?: break
            if (head.startFrame >= blockEndExclusive) break
            scheduled.pop()

            if (head.endFrame <= blockStart) continue

            val midi = StrudelNotes.noteNameToMidi(head.e.note)
            val freqHz = StrudelNotes.midiToFreq(midi)
            val osc = oscillators.get(e = head.e, freqHz = freqHz)

            activeVoices += Voice(
                startFrame = head.startFrame,
                endFrame = head.endFrame,
                gain = head.e.gain,
                osc = osc,
                filters = head.e.filters,
                freqHz = freqHz,
                phase = 0.0,
            )
        }

        val attackFrames = 256
        val releaseFrames = 512

        for (i in 0 until frames) {
            val frameIndex = blockStart + i.toLong()
            var mix = 0.0

            var v = 0
            while (v < activeVoices.size) {
                val voice = activeVoices[v]

                if (frameIndex < voice.startFrame) {
                    v++
                    continue
                }
                if (frameIndex >= voice.endFrame) {
                    activeVoices.removeAt(v)
                    continue
                }

                val local = (frameIndex - voice.startFrame).toInt()
                val total = (voice.endFrame - voice.startFrame).toInt().coerceAtLeast(1)

                val env = when {
                    local < attackFrames -> local / attackFrames.toDouble()
                    local > total - releaseFrames -> (total - local) / releaseFrames.toDouble()
                    else -> 1.0
                }.coerceIn(0.0, 1.0)

                val inc = 2.0 * Math.PI * voice.freqHz / sampleRate.toDouble()
                var s = voice.osc(voice.phase) * voice.gain * env

                for (filter in voice.filters) {
                    s = filter(s)
                }

                voice.phase += inc
                if (voice.phase >= 2.0 * Math.PI) voice.phase -= 2.0 * Math.PI

                mix += s
                v++
            }

            val sample = mix.coerceIn(-1.0, 1.0)
            val pcm = (sample * Short.MAX_VALUE).toInt()

            out[i * 2] = (pcm and 0xff).toByte()
            out[i * 2 + 1] = ((pcm ushr 8) and 0xff).toByte()
        }

        cursorFrame.value = blockEndExclusive
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
