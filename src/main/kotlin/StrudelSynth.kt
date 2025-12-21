package io.peekandpoke

import io.peekandpoke.GraalJsBridge.safeNumber
import io.peekandpoke.GraalJsBridge.safeNumberOrNull
import io.peekandpoke.GraalJsBridge.safeString
import io.peekandpoke.GraalJsBridge.safeStringOrNull
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

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
    // Flatten sub-events from notesExpanded
    data class StrudelEvent(
        val t: Double,
        val dur: Double,
        val note: String,
        val gain: Double,
        val wave: String?,
        val cutoff: Double?,
    )

    fun playOneCycleDemo() {
        val pattern = strudel.compile(
            """
            note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
              .sound("saw").lpf(500).gain(1.0)
            """.trimIndent()
        )

        val span = 32.0

        val result = strudel.queryPattern(pattern, 0.0, span)

        val events = mutableListOf<StrudelEvent>()
        val topN = result.arraySize

        for (i in 0 until topN) {
            val item = result.getArrayElement(i)
            if (item.hasMember("notesExpanded")) {
                val sub = item.getMember("notesExpanded")
                val m = sub.arraySize
                for (j in 0 until m) {
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
                    val wave = value.getMember("s").safeStringOrNull()
                    // Get Low pass filter
                    val cutoff = value.getMember("cutoff").safeNumberOrNull()

                    // add event
                    events += StrudelEvent(
                        t = t,
                        dur = dur,
                        note = note,
                        gain = gain,
                        wave = wave,
                        cutoff = cutoff,
                    )
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
        val cps = 0.5 // 2 cycles per second => 1 cycle = 0.5s
        val secPerCycle = 1.0 / cps

        var cursorSec = 0.0
        for (e in sorted) {
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

            val buf = renderTone(freq = hz, seconds = durSec, gain = e.gain, wave = e.wave, cutoffHz = e.cutoff)
            line.write(buf, 0, buf.size)
            cursorSec += durSec

            println(
                "[StrudelSynth] note=${e.note} gain=${e.gain} wave=${e.wave} cutoff=${e.cutoff} midi=${
                    "%.1f".format(
                        midi
                    )
                } hz=${
                    "%.2f".format(
                        hz
                    )
                } tCyc=${"%.3f".format(e.t)} durCyc=${"%.3f".format(e.dur)}"
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
                "saw", "sawtooth" -> oscSaw(phase) * (gain * 0.6)
                "square" -> oscSquare(phase) * (gain * 0.5)
                "tri", "triangle" -> oscTri(phase) * (gain * 0.7)
                else -> kotlin.math.sin(phase) * gain
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

    private fun oscTri(phase: Double): Double {
        // Triangle via asin(sin) normalized to [-1,1]
        return (2.0 / Math.PI) * kotlin.math.asin(kotlin.math.sin(phase))
    }
}
