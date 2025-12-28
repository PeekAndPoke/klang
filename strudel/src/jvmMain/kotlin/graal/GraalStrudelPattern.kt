package io.peekandpoke.klang.strudel.graal

import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.safeNumber
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.safeNumberOrNull
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.safeStringOrNull
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.safeToStringOrNull
import io.peekandpoke.klang.tones.Tones
import org.graalvm.polyglot.Value

class GraalStrudelPattern(val value: Value, val graal: GraalStrudelCompiler) : StrudelPattern {

    override fun queryArc(from: Double, to: Double): List<StrudelPatternEvent> {
        val arc = graal.queryPattern(value, from, to)
            ?: return emptyList()

        val events = mutableListOf<StrudelPatternEvent>()
        val count = arc.arraySize

        for (i in 0 until count) {
            val item = arc.getArrayElement(i)
            val event = item.toStrudelEvent()
            events += event

//            println(graal.prettyFormat(item))
//            println("${event.note} ${event.scale}")
        }

        return events.sortedBy { it.begin }
    }

    /**
     * Converts the js-value into a StrudelEvent.
     */
    fun Value.toStrudelEvent(): StrudelPatternEvent {
        val event = this

        val filters = mutableListOf<FilterDef>()

        val part = event.getMember("part")

        // Begin
        val begin = part?.getMember("begin")?.safeNumber(0.0) ?: 0.0
        // End
        val end = part?.getMember("end")?.safeNumber(0.0) ?: 0.0
        // Get duration
        val dur = end - begin

        // Get details from "value" field
        val value = event.getMember("value")

        // ///////////////////////////////////////////////////////////////////////////////////
        // Get note
        val note = value.getMember("note").safeToStringOrNull()
        // scale
        val scale = event.getMember("context")?.getMember("scale").safeStringOrNull()
        // Get or calculate the frequency
        val freq = value.getMember("freq").safeNumberOrNull()
            ?: note?.let { Tones.resolveFreq(note, scale) }

        // ////////////////////////////////////////////////////////////////////////////////////////
        // Get gain
        val gain = value.getMember("gain").safeNumberOrNull()
            ?: value.getMember("amp").safeNumberOrNull()
            ?: 1.0

        // ///////////////////////////////////////////////////////////////////////////////////
        // Get sound parameters / sample bank and index
        val bank = value.getMember("bank").safeStringOrNull()
        val sound = value.getMember("s").safeStringOrNull()
            ?: value.getMember("wave").safeStringOrNull()
            ?: value.getMember("sound").safeStringOrNull()
        val soundIndex = value.getMember("n").safeNumberOrNull()?.toInt()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Get Oscillator parameters
        val density = value.getMember("density").safeNumberOrNull()
        val spread = value.getMember("spread").safeNumberOrNull()
        val detune = value.getMember("detune").safeNumberOrNull()
        val unison = value.getMember("unison").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // ADRS
        val attack = value.getMember("attack").safeNumberOrNull()
        val decay = value.getMember("decay").safeNumberOrNull()
        val sustain = value.getMember("sustain").safeNumberOrNull()
        val release = value.getMember("release").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Vibrato
        val vibrato = value.getMember("vib").safeNumberOrNull()
            ?: value.getMember("vibrato").safeNumberOrNull()
        val vibratoMod = value.getMember("vibmod").safeNumberOrNull()
            ?: value.getMember("vibratoMod").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Routing
        val orbit = value.getMember("orbit").safeNumberOrNull()?.toInt()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Pan
        val pan = value.getMember("pan").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Delay
        val delay = value.getMember("delay").safeNumberOrNull()
        val delayTime = value.getMember("delaytime").safeNumberOrNull()
        val delayFeedback = value.getMember("delayfeedback").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Reverb See https://strudel.cc/learn/effects/#roomsize
        //
        // Room is between [0 and 1]
        // Room size is between [0 and 10]
        val room = value.getMember("room").safeNumberOrNull()
        val roomSize = value.getMember("roomsize").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Distortion
        val distort = value.getMember("distort").safeNumberOrNull()
            ?: value.getMember("shape").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Crush
        val crush = value.getMember("crush").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Coarse
        val coarse = value.getMember("coarse").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Apply low pass filter?
        val resonance = value.getMember("resonance").safeNumberOrNull()

        val cutoff = value.getMember("cutoff").safeNumberOrNull()
        cutoff?.let { filters.add(FilterDef.LowPass(cutoffHz = it, q = resonance)) }

        // Apply high pass filter?
        val hcutoff = value.getMember("hcutoff").safeNumberOrNull()
        hcutoff?.let { filters.add(FilterDef.HighPass(cutoffHz = it, q = resonance)) }

        // add event
        return StrudelPatternEvent(
            // Strudel Timing
            begin = begin,
            end = end,
            dur = dur,
            // Voice data
            data = VoiceData(
                // Frequency and note
                note = note,
                freqHz = freq,
                scale = scale,
                // Gain
                gain = gain,
                // Sound samples
                bank = bank,
                sound = sound,
                soundIndex = soundIndex,
                // Oscilator
                density = density,
                unison = unison,
                detune = detune,
                spread = spread,
                // Filters
                filters = filters,
                // ADSR envelope
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
                // Vibrato
                vibrato = vibrato,
                vibratoMod = vibratoMod,
                // Effects
                distort = distort,
                coarse = coarse,
                crush = crush,
                // HPF / LPF
                cutoff = cutoff,
                hcutoff = hcutoff,
                resonance = resonance,
                // Routing
                orbit = orbit,
                // Pan
                pan = pan,
                // Delay
                delay = delay,
                delayTime = delayTime,
                delayFeedback = delayFeedback,
                // Reverb
                room = room,
                roomsize = roomSize,
                // ???
                bandf = null, // TODO ...
            ),
        )
    }
}
