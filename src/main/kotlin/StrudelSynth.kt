package io.peekandpoke

import org.graalvm.polyglot.Value
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.pow

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

    fun playOneCycleDemo() {
        val pattern = strudel.compile(
            """
            note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
              .sound("square").lpf(800)
            """.trimIndent()
        )

        val span = 8.0

        val result = strudel.queryPattern(pattern, 0.0, span)

//        println("[AUDIO] top-level items: ${result.arraySize}")
//        var subCount = 0L
//        for (i in 0 until result.arraySize) {
//            val item = result.getArrayElement(i)
//            if (item.hasMember("notesExpanded")) {
//                subCount += item.getMember("notesExpanded").arraySize
//            }
//        }
//        println("[AUDIO] expanded sub-events: $subCount")

        // Flatten sub-events from notesExpanded
        data class Ev(val t: Double, val dur: Double, val note: String, val wave: String?, val cutoff: Double?)

        val events = mutableListOf<Ev>()
        val topN = result.arraySize

        for (i in 0 until topN) {
            val item = result.getArrayElement(i)
            if (item.hasMember("notesExpanded")) {
                val sub = item.getMember("notesExpanded")
                val m = sub.arraySize
                for (j in 0 until m) {
                    val ev = sub.getArrayElement(j)
                    val t = safeNumber(ev.getMember("t"))
                    val dur = safeNumber(ev.getMember("dur")) * 4
                    val v = ev.getMember("value")
                    val note = when {
                        v.hasMember("note") -> v.getMember("note").asString()
                        v.isString -> v.asString()
                        else -> "a4"
                    }
                    val wave = if (v.hasMember("s")) v.getMember("s").asString() else null
                    val cutoff = when {
                        v.hasMember("cutoff") && v.getMember("cutoff").isNumber -> v.getMember("cutoff").asDouble()
                        ev.hasMember("cutoff") && ev.getMember("cutoff").isNumber -> ev.getMember("cutoff").asDouble()
                        else -> null
                    }
                    // JS side already returns absolute t in cycles for sub-events; don't add parent base time
                    events += Ev(t, dur, note, wave, cutoff)
                }
            }
        }

        println("[StrudelSynth] gathered ${events.size} sub-events in window")

        // Simple audio output
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.start()

        // Sort by start time; render sequentially inserting silence so t/dur are respected (simple timing)
        val sorted = events.sortedBy { it.t }

        // Map cycles -> seconds for this demo (adjust if you want it faster/slower)
        val cps = 2.0 // 2 cycles per second => 1 cycle = 0.5s
        val secPerCycle = 1.0 / cps

        var cursorSec = 0.0
        for (e in sorted) {
            val midi = noteNameToMidi(e.note)
            val hz = midiToFreq(midi)

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

            val buf = renderTone(freq = hz, seconds = durSec, amp = 0.8, wave = e.wave, cutoffHz = e.cutoff)
            line.write(buf, 0, buf.size)
            cursorSec += durSec

            println(
                "[StrudelSynth] note=${e.note} wave=${e.wave} cutoff=${e.cutoff} midi=${"%.1f".format(midi)} hz=${"%.2f".format(hz)} tCyc=${"%.3f".format(e.t)} durCyc=${"%.3f".format(e.dur)}"
            )
        }

        line.drain()
        line.stop()
        line.close()
    }

    private fun safeNumber(v: Value): Double = try {
        when {
            v.isNumber -> v.asDouble()
            v.canInvokeMember("valueOf") -> v.invokeMember("valueOf").asDouble()
            else -> v.asDouble()
        }
    } catch (_: Exception) {
        0.0
    }

    private fun midiToFreq(m: Double): Double = 440.0 * 2.0.pow((m - 69.0) / 12.0)

    private val noteIndex = mapOf(
        'c' to 0, 'd' to 2, 'e' to 4, 'f' to 5, 'g' to 7, 'a' to 9, 'b' to 11
    )

    private fun noteNameToMidi(s: String): Double {
        if (s.isEmpty()) return 69.0
        val str = s.trim().lowercase()
        var i = 0
        val letter = str[i]
        val base = noteIndex[letter] ?: 9
        i++
        var acc = 0
        while (i < str.length && (str[i] == 'b' || str[i] == '#')) {
            acc += if (str[i] == '#') 1 else -1
            i++
        }
        val oct = if (i < str.length) str.substring(i).toIntOrNull() ?: 4 else 4
        val midi = (oct + 1) * 12 + base + acc

        return midi.toDouble()
    }

    private fun renderTone(
        freq: Double,
        seconds: Double,
        amp: Double = 0.2,
        wave: String? = null,
        cutoffHz: Double? = null,
    ): ByteArray {
        val frames = (seconds * sampleRate).toInt().coerceAtLeast(1)
        val out = ByteArray(frames * 2) // mono int16 LE
        var phase = 0.0
        val inc = 2.0 * Math.PI * freq / sampleRate

        // One-pole LPF state
        var y = 0.0
        val alpha = cutoffHz?.let { c ->
            val nyquist = 0.5 * sampleRate
            val cutoff = c.coerceIn(40.0, nyquist - 1.0)
            // one-pole LPF coefficient in exponential form
            1.0 - kotlin.math.exp(-2.0 * Math.PI * cutoff / sampleRate)
        }

        for (i in 0 until frames) {
            val env = when {
                i < 256 -> i / 256.0
                i > frames - 512 -> (frames - i) / 512.0
                else -> 1.0
            }
            val raw = when (wave?.lowercase()) {
                "saw", "sawtooth" -> oscSaw(phase) * (amp * 0.6)
                "square" -> oscSquare(phase) * (amp * 0.5)
                else -> kotlin.math.sin(phase) * amp
            } * env

            val sample = if (alpha != null) {
                y += alpha * (raw - y); y
            } else raw

            phase += inc
            if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI

            val s = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
            out[i * 2] = (s and 0xff).toByte()
            out[i * 2 + 1] = ((s ushr 8) and 0xff).toByte()
        }
        return out
    }

    private fun oscSaw(phase: Double): Double {
        val x = phase / (2.0 * Math.PI)
        return 2.0 * (x - kotlin.math.floor(x + 0.5))
    }

    private fun oscSquare(phase: Double): Double = if (kotlin.math.sin(phase) >= 0.0) 1.0 else -1.0
}
