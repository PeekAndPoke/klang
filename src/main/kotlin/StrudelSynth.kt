package io.peekandpoke

import io.peekandpoke.GraalJsBridge.safeNumber
import io.peekandpoke.GraalJsBridge.safeNumberOrNull
import io.peekandpoke.GraalJsBridge.safeString
import io.peekandpoke.GraalJsBridge.safeStringOrNull
import org.graalvm.polyglot.Value
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.sin

/**
 * Very small, blocking demo synth that:
 * - queries one cycle via `queryPattern`
 * - extracts sub-events (notesExpanded)
 * - converts note names to Hz
 * - renders simple sine tones sequentially and plays via system audio
 *
 * This is only for an initial audible check. No real-time scheduling yet.
 */
class StrudelSynth(
    private val strudel: Strudel,
    val sampleRate: Int = 48_000,
) {
    companion object {

        fun simpleOsc(name: String?): OscFn = when (name) {
            "saw", "sawtooth" -> {
                { SimpleOsc.oscSaw(it) * 0.6 }
            }

            "sqr", "square" -> {
                { SimpleOsc.oscSquare(it) * 0.5 }
            }

            "tri", "triangle" -> {
                { SimpleOsc.oscTri(it) * 0.7 }
            }

            else -> ::sin
        }
    }

    // Flatten sub-events from notesExpanded
    data class StrudelEvent(
        val t: Double,
        val dur: Double,
        val note: String,
        val gain: Double,
        val osc: OscFn,
        val filters: List<FilterFn>,
    )

    fun extractEvents(pattern: Value, from: Double, to: Double): List<StrudelEvent> {
        val result = strudel.queryPattern(pattern, from, to)

        val events = mutableListOf<StrudelEvent>()
        val topN = result.arraySize

        for (i in 0 until topN) {
            val item = result.getArrayElement(i)
            if (item.hasMember("notesExpanded")) {
                val sub = item.getMember("notesExpanded")
                val m = sub.arraySize
                for (j in 0 until m) {
                    val filters = mutableListOf<FilterFn>()

                    // Get element
                    val event = sub.getArrayElement(j)
                    // Get timing
                    val t = event.getMember("t").safeNumber(0.0)
                    // Get duration
                    val dur = event.getMember("dur").safeNumber(0.0)
                    // Get details
                    val value = event.getMember("value")
                    // Get note
                    val note = value.getMember("note").safeString("")
                    // Get gain
                    val gain = value.getMember("gain").safeNumberOrNull()
                        ?: value.getMember("amp").safeNumberOrNull()
                        ?: 1.0
                    // Get waveform
                    val osc = simpleOsc(value.getMember("s").safeStringOrNull())
                    // get LPF/HPF resonance
                    val resonance = value.getMember("resonance").safeNumberOrNull()
                    // Apply low pass filter?
                    value.getMember("cutoff").safeNumberOrNull()?.let {
                        filters.add(SimpleFilters.createLPF(cutoffHz = it, q = resonance, sampleRate.toDouble()))
                    }
                    // Apply high pass filter?
                    value.getMember("hcutoff").safeNumberOrNull()?.let {
                        filters.add(SimpleFilters.createHPF(cutoffHz = it, q = resonance, sampleRate.toDouble()))
                    }

                    // add event
                    events += StrudelEvent(
                        t = t,
                        dur = dur,
                        note = note,
                        gain = gain,
                        osc = osc,
                        filters = filters,
                    )
                }
            }
        }

        return events.sortedBy { it.t }
    }

    fun play(pattern: Value, cps: Double = 0.5) {
        val events = extractEvents(pattern, 0.0, 10.0)

        println("[StrudelSynth] gathered ${events.size} sub-events in window")

        // Simple audio output
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.start()

        // Map cycles -> seconds for this demo (adjust if you want it faster/slower)
        val secPerCycle = 1.0 / cps

        var cursorSec = 0.0

        for (e in events) {
            val midi = StrudelNotes.noteNameToMidi(e.note)
            val hz = StrudelNotes.midiToFreq(midi)

            val startSec = e.t * secPerCycle
            val durSec = (e.dur * secPerCycle).coerceAtLeast(0.10)

            // write silence for gap
            val gapSec = (startSec - cursorSec).coerceAtLeast(0.0)
            if (gapSec > 0.0) {
                val gapFrames = (gapSec * sampleRate).toInt()
                if (gapFrames > 0) {
                    val silence = ByteArray(gapFrames * 2)
                    line.write(silence, 0, silence.size)
                }
                cursorSec += gapSec
            }

            val buf = renderTone(freq = hz, seconds = durSec, gain = e.gain, osc = e.osc, filters = e.filters)
            line.write(buf, 0, buf.size)
            cursorSec += durSec

            println(
                "[StrudelSynth] note=${e.note} gain=${e.gain} osc=${e.osc.name()} filters=${e.filters.size} " +
                        "midi=${"%.1f".format(midi)} hz=${"%.2f".format(hz)} " +
                        "tCyc=${"%.3f".format(e.t)} durCyc=${"%.3f".format(e.dur)}"
            )
        }

        line.drain()
        line.stop()
        line.close()
    }


    private fun renderTone(
        freq: Double,
        seconds: Double,
        gain: Double,
        osc: OscFn,
        filters: List<FilterFn>,
    ): ByteArray {
        val frames = (seconds * sampleRate).toInt().coerceAtLeast(1)
        val out = ByteArray(frames * 2) // mono int16 LE
        var phase = 0.0
        val inc = 2.0 * Math.PI * freq / sampleRate

        for (i in 0 until frames) {
            val env = when {
                i < 256 -> i / 256.0
                i > frames - 512 -> (frames - i) / 512.0
                else -> 1.0
            }

            // create sample through oscillator
            var sample = osc(phase) * gain * env

            // apply all filters
            for (filter in filters) {
                sample = filter(sample)
            }

            // Progress phase
            phase += inc
            if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI

            val s = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
            out[i * 2] = (s and 0xff).toByte()
            out[i * 2 + 1] = ((s ushr 8) and 0xff).toByte()
        }
        return out
    }
}
