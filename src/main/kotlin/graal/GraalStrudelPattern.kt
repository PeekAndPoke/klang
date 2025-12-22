package io.peekandpoke.graal

import io.peekandpoke.StrudelEvent
import io.peekandpoke.StrudelPattern
import io.peekandpoke.dsp.AudioFilter
import io.peekandpoke.dsp.SimpleFilters
import io.peekandpoke.graal.GraalJsHelpers.safeNumber
import io.peekandpoke.graal.GraalJsHelpers.safeNumberOrNull
import io.peekandpoke.graal.GraalJsHelpers.safeStringOrNull
import io.peekandpoke.graal.GraalJsHelpers.safeTpString
import org.graalvm.polyglot.Value

class GraalStrudelPattern(val value: Value, val graal: GraalStrudelCompiler) : StrudelPattern {

    override fun queryArc(from: Double, to: Double, sampleRate: Int): List<StrudelEvent> {
        val arc = graal.queryPattern(value, from, to)
            ?: return emptyList()

        val events = mutableListOf<StrudelEvent>()
        val count = arc.arraySize

        for (i in 0 until count) {
            val item = arc.getArrayElement(i)
            val event = item.toStrudelEvent(sampleRate)
            events += event

//            println(graal.prettyFormat(item))
//            println("${event.note} ${event.scale}")
        }

        return events
    }

    /**
     * Converts the js-value into a StrudelEvent.
     */
    fun Value.toStrudelEvent(sampleRate: Int): StrudelEvent {
        val event = this

        val filters = mutableListOf<AudioFilter>()

        val part = event.getMember("part")

        // Begin
        val begin = part?.getMember("begin")?.safeNumber(0.0) ?: 0.0
        // End
        val end = part?.getMember("end")?.safeNumber(0.0) ?: 0.0
        // Get duration
        val dur = end - begin

        // Get details from "value" field
        val value = event.getMember("value")
        // Get note
        val note = value.getMember("note").safeTpString("")
        // scale
        val scale = event.getMember("context")?.getMember("scale").safeStringOrNull()
        // Get gain
        val gain = value.getMember("gain").safeNumberOrNull()
            ?: value.getMember("amp").safeNumberOrNull()
            ?: 1.0
        // Get Oscillator parameters
        val osc = value.getMember("s").safeStringOrNull()
            ?: value.getMember("wave").safeStringOrNull()
            ?: value.getMember("sound").safeStringOrNull()
        val density = value.getMember("density").safeNumberOrNull()
        val spread = value.getMember("spread").safeNumberOrNull()
        val detune = value.getMember("detune").safeNumberOrNull()
        val unison = value.getMember("unison").safeNumberOrNull()
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
            begin = begin,
            end = end,
            dur = dur,
            // Frequency and note
            note = note,
            scale = scale,
            gain = gain,
            // Oscilator
            osc = osc,
            density = density,
            unison = unison,
            detune = detune,
            spread = spread,
            // Filters
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
