package io.peekandpoke

import org.graalvm.polyglot.Value
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Real-time-ish audio renderer:
 * - fetches events from Strudel in a lookahead window (scheduler thread)
 * - enqueues time-stamped events (startFrame/endFrame)
 * - renders audio in fixed blocks (audio thread)
 *
 * Note: Uses SourceDataLine (blocking). This is good enough for now and matches your current setup.
 */
class StrudelAudioRenderer(
    private val strudel: Strudel,
    val sampleRate: Int = 48_000,
    val oscillators: Oscillators,
    val cps: Double = 0.5,
    /** How far ahead we query Strudel (seconds). */
    val lookaheadSec: Double = 0.5,
    /** How often we query Strudel (milliseconds). */
    val fetchPeriodMs: Long = 25L,
    /** Fixed render quantum in frames. */
    val blockFrames: Int = 512,
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

    private val running = AtomicBoolean(false)

    private val queueLock = ReentrantLock()
    private val scheduled = PriorityQueue<ScheduledEvent>(compareBy { it.startFrame })

    private val activeVoices = ArrayList<Voice>(64)

    private var audioThread: Thread? = null
    private var fetchThread: Thread? = null

    // Audio time cursor (frames since start)
    @Volatile
    private var cursorFrame: Long = 0L

    fun extractEvents(pattern: Value, fromCycles: Double, toCycles: Double): List<StrudelEvent> {
        val result = pattern.invokeMember("queryArc", fromCycles, toCycles)

        val events = mutableListOf<StrudelEvent>()
        val topN = result.arraySize
        for (i in 0 until topN) {
            val item = result.getArrayElement(i)

            println(strudel.prettyFormat(item))

            events += StrudelEvent.of(item, sampleRate)
        }
        return events.sortedBy { it.begin }
    }

    /**
     * Starts streaming audio until [stop] is called.
     */
    fun start(pattern: Value) {
        if (!running.compareAndSet(false, true)) return

        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.start()

        val secPerCycle = 1.0 / cps
        val framesPerCycle = secPerCycle * sampleRate.toDouble()

        // Scheduler state in Strudel-time (cycles)
        var queryCursorCycles = 0.0

        fetchThread = thread(name = "strudel-fetch", isDaemon = true) {
            // Keep a small overlap to avoid missing boundary events.
            val overlapSec = 0.10
            val overlapCycles = overlapSec / secPerCycle

            while (running.get()) {
                // Audio cursor -> current cycle
                val nowFrame = cursorFrame
                val nowSec = nowFrame.toDouble() / sampleRate.toDouble()
                val nowCycles = nowSec / secPerCycle

                val fromCycles = maxOf(queryCursorCycles, nowCycles - overlapCycles)
                val toCycles = nowCycles + (lookaheadSec / secPerCycle)

                try {
                    val events = extractEvents(pattern, fromCycles, toCycles)

                    // Enqueue only events that start at/after fromCycles
                    // (crude de-dupe by time; overlap is small so this works acceptably for now)
                    val due = events.filter { it.begin >= fromCycles }

                    queueLock.withLock {
                        for (e in due) {
                            val startFrame = (e.begin * framesPerCycle).toLong()
                            val durFrames = (e.dur * framesPerCycle).toLong().coerceAtLeast(1L)
                            val endFrame = startFrame + durFrames
                            scheduled.add(ScheduledEvent(startFrame = startFrame, endFrame = endFrame, e = e))
                        }
                    }

                    // Move cursor forward but keep some overlap implicitly via fromCycles computation
                    queryCursorCycles = toCycles
                } catch (t: Throwable) {
                    // Keep going; missing one fetch should be tolerated by lookahead.
                    t.printStackTrace()
                }

                try {
                    Thread.sleep(fetchPeriodMs)
                } catch (_: InterruptedException) {
                    // ignore
                }
            }
        }

        audioThread = thread(name = "strudel-audio", isDaemon = true) {
            val out = ByteArray(blockFrames * 2) // mono int16 LE

            while (running.get()) {
                renderBlockInto(out, frames = blockFrames)
                line.write(out, 0, out.size)
            }

            line.drain()
            line.stop()
            line.close()
        }
    }

    fun stop() {
        running.set(false)
        audioThread?.interrupt()
        fetchThread?.interrupt()
        audioThread = null
        fetchThread = null

        queueLock.withLock {
            scheduled.clear()
        }
        activeVoices.clear()
        cursorFrame = 0L
    }

    override fun close() = stop()

    private fun renderBlockInto(out: ByteArray, frames: Int) {
        val secPerCycle = 1.0 / cps

        // 1) Move scheduled events that are due into active voices
        val blockStart = cursorFrame
        val blockEndExclusive = blockStart + frames.toLong()

        queueLock.withLock {
            while (true) {
                val head = scheduled.peek() ?: break
                if (head.startFrame >= blockEndExclusive) break

                scheduled.poll()

                // If it already ended, skip
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
        }

        // 2) Render + mix
        // Very simple envelope like the demo: short fade in/out in samples
        val attackFrames = 256
        val releaseFrames = 512

        for (i in 0 until frames) {
            val frameIndex = blockStart + i.toLong()

            var mix = 0.0

            // Iterate backwards so we can remove dead voices cheaply
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

            // Soft clamp (still crude, but prevents instant overload)
            val sample = mix.coerceIn(-1.0, 1.0)
            val pcm = (sample * Short.MAX_VALUE).toInt()

            out[i * 2] = (pcm and 0xff).toByte()
            out[i * 2 + 1] = ((pcm ushr 8) and 0xff).toByte()
        }

        cursorFrame += frames.toLong()
    }
}
