package io.peekandpoke

import io.peekandpoke.GraalJsBridge.safeNumber
import io.peekandpoke.GraalJsBridge.safeNumberOrNull
import io.peekandpoke.GraalJsBridge.safeString
import io.peekandpoke.GraalJsBridge.safeStringOrNull
import org.graalvm.polyglot.Value
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
    val oscillators: Oscillators,
    val cps: Double = 0.5,
) {
    /**
     * Strudel sound event.
     *
     * Trying to stay as close as possible to the class DoughVoice:
     * https://codeberg.org/uzu/strudel/src/branch/main/packages/supradough/dough.mjs
     */
    data class StrudelEvent(
        val t: Double,
        val dur: Double,
        // Frequency and note
        val note: String,
        val gain: Double,
        // Oscilator
        val osc: String?,
        val filters: List<FilterFn>,
        // ADSR envelope
        val attack: Double?,
        val decay: Double?,
        val sustain: Double?,
        val release: Double?,
        // Vibrato
        val vibrato: Double?,
        val vibratoMod: Double?,
        // HPF / LPF
        val cutoff: Double?,
        val hcutoff: Double?,
        val resonance: Double?,
        // ???
        val bandf: Double?,
        val coarse: Double?,
        val crush: Double?,
        val distort: Double?,
    ) {
        companion object {
            fun of(event: Value, sampleRate: Int): StrudelEvent {
                val filters = mutableListOf<FilterFn>()

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
                val osc = value.getMember("s").safeStringOrNull()
                // get LPF/HPF resonance
                val resonance = value.getMember("resonance").safeNumberOrNull()
                // Apply low pass filter?
                val cutoff = value.getMember("cutoff").safeNumberOrNull()
                cutoff?.let {
                    filters.add(SimpleFilters.createLPF(cutoffHz = it, q = resonance, sampleRate.toDouble()))
                }
                // Apply high pass filter?
                val hcutoff = value.getMember("hcutoff").safeNumberOrNull()
                hcutoff?.let {
                    filters.add(SimpleFilters.createHPF(cutoffHz = it, q = resonance, sampleRate.toDouble()))
                }

                // add event
                return StrudelEvent(
                    t = t,
                    dur = dur,
                    // Frequency and note
                    note = note,
                    gain = gain,
                    // Oscilator
                    osc = osc,
                    filters = filters,
                    // ADSR envelope
                    attack = null, // TODO ...
                    decay = null, // TODO ...
                    sustain = null, // TODO ...
                    release = null, // TODO ...
                    // Vibrato
                    vibrato = null, // TODO ...
                    vibratoMod = null,  // TODO ...
                    // HPF / LPF
                    cutoff = cutoff,
                    hcutoff = hcutoff,
                    resonance = resonance,
                    // ???
                    bandf = null, // TODO ...
                    coarse = null, // TODO ...
                    crush = null, // TODO ...
                    distort = null, // TODO ...
                )
            }
        }
    }


    fun extractEvents(pattern: Value, from: Double, to: Double): List<StrudelEvent> {
        val result = strudel.queryPattern(pattern, from, to)

        val events = mutableListOf<StrudelEvent>()
        val topN = result.arraySize

        for (i in 0 until topN) {
            val item = result.getArrayElement(i)

            println(strudel.prettyFormat(item))

            events += StrudelEvent.of(item, sampleRate)
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

            val buf = renderTone(e)
            line.write(buf, 0, buf.size)
            cursorSec += durSec

            println(e.toString())
        }

        line.drain()
        line.stop()
        line.close()
    }


    private fun renderTone(
        e: StrudelEvent,
    ): ByteArray {
        val secPerCycle = 1.0 / cps

        val midi = StrudelNotes.noteNameToMidi(e.note)
        val freq = StrudelNotes.midiToFreq(midi)
        val seconds = (e.dur * secPerCycle).coerceAtLeast(0.0)
        val osc = oscillators.getByName(e.osc)

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
            var sample = osc(phase) * e.gain * env

            // apply all filters
            for (filter in e.filters) {
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
