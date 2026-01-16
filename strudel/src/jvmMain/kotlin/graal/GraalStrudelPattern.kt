package io.peekandpoke.klang.strudel.graal

import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.safeGetMember
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.safeNumber
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.safeNumberOrNull
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.safeStringOrNull
import io.peekandpoke.klang.strudel.graal.GraalJsHelpers.safeToStringOrNull
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.tones.Tones
import org.graalvm.polyglot.Value

class GraalStrudelPattern(
    val value: Value,
    val graal: GraalStrudelCompiler,
) : StrudelPattern.FixedWeight {
    // Graal patterns are treated as opaque units from the JS side, so we default to weight 1.0.
    override val weight: Double = 1.0

    override val steps: Rational = Rational.ONE

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val arc = graal.queryPattern(value, from.toDouble(), to.toDouble())
            ?: return emptyList()

        val events = mutableListOf<StrudelPatternEvent>()
        val count = arc.arraySize

        for (i in 0 until count) {
            val item = arc.getArrayElement(i)

//            println(graal.prettyFormat(item))

            val event = item.toStrudelEvent()
            events += event

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

        val part = event.safeGetMember("part")

        // Begin
        val begin = Rational(part?.safeGetMember("begin")?.safeNumber(0.0) ?: 0.0)
        // End
        val end = Rational(part?.safeGetMember("end")?.safeNumber(0.0) ?: 0.0)
        // Get duration
        val dur = end - begin

        // Get details from "value" field
        val value = event.safeGetMember("value")

        // ///////////////////////////////////////////////////////////////////////////////////
        // Get note
        val note = value.safeGetMember("note").safeToStringOrNull()
        // scale
        val scale = event.safeGetMember("context")?.safeGetMember("scale").safeStringOrNull()
        // Get or calculate the frequency
        val freq = value.safeGetMember("freq").safeNumberOrNull()
            ?: note?.let { Tones.resolveFreq(note, scale) }

        // ////////////////////////////////////////////////////////////////////////////////////////
        // Gain / Dynamics
        val gain = value.safeGetMember("gain").safeNumberOrNull()
            ?: value.safeGetMember("amp").safeNumberOrNull()
            ?: 1.0

        val legato = value.safeGetMember("clip").safeNumberOrNull()
            ?: value.safeGetMember("legator").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Get sound parameters / sample bank and index
        val bank = value.safeGetMember("bank").safeStringOrNull()
        val sound = value.safeGetMember("s").safeStringOrNull()
            ?: value.safeGetMember("wave").safeStringOrNull()
            ?: value.safeGetMember("sound").safeStringOrNull()
        val soundIndex = value.safeGetMember("n").safeNumberOrNull()?.toInt()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Get Oscillator parameters
        val density = value.safeGetMember("density").safeNumberOrNull()
        val voices = value.safeGetMember("unison").safeNumberOrNull()
        val panSpread = value.safeGetMember("spread").safeNumberOrNull()
        val freqSpread = value.safeGetMember("detune").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // ADRS
        val attack = value.safeGetMember("attack").safeNumberOrNull()
        val decay = value.safeGetMember("decay").safeNumberOrNull()
        val sustain = value.safeGetMember("sustain").safeNumberOrNull()
        val release = value.safeGetMember("release").safeNumberOrNull()

        val adsr = AdsrEnvelope(
            attack = attack,
            decay = decay,
            sustain = sustain,
            release = release,
        )

        // ///////////////////////////////////////////////////////////////////////////////////
        // Pitch / Glisando
        val accelerate = value.safeGetMember("accelerate").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Vibrato
        val vibrato = value.safeGetMember("vib").safeNumberOrNull()
            ?: value.safeGetMember("vibrato").safeNumberOrNull()
        val vibratoMod = value.safeGetMember("vibmod").safeNumberOrNull()
            ?: value.safeGetMember("vibratoMod").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Routing
        val orbit = value.safeGetMember("orbit").safeNumberOrNull()?.toInt()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Pan
        val pan = value.safeGetMember("pan").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Delay
        val delay = value.safeGetMember("delay").safeNumberOrNull()
        val delayTime = value.safeGetMember("delaytime").safeNumberOrNull()
        val delayFeedback = value.safeGetMember("delayfeedback").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Reverb See https://strudel.cc/learn/effects/#roomsize
        //
        // Room is between [0 and 1]
        // Room size is between [0 and 10]
        val room = value.safeGetMember("room").safeNumberOrNull()
        val roomSize = value.safeGetMember("roomsize").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Distortion
        val distort = value.safeGetMember("distort").safeNumberOrNull()
            ?: value.safeGetMember("shape").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Crush
        val crush = value.safeGetMember("crush").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Coarse
        val coarse = value.safeGetMember("coarse").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Apply low pass filter?
        val resonance = value.safeGetMember("resonance").safeNumberOrNull()

        val cutoff = value.safeGetMember("cutoff").safeNumberOrNull()
        cutoff?.let { filters.add(FilterDef.LowPass(cutoffHz = it, q = resonance)) }

        // Apply high pass filter?
        val hcutoff = value.safeGetMember("hcutoff").safeNumberOrNull()
        hcutoff?.let { filters.add(FilterDef.HighPass(cutoffHz = it, q = resonance)) }

        // Apply band pass filter?
        val bandf = value.safeGetMember("bandf").safeNumberOrNull()
            ?: value.safeGetMember("bandpass").safeNumberOrNull()
        bandf?.let { filters.add(FilterDef.BandPass(cutoffHz = it, q = resonance)) }

        // Apply notch filter?
        val notchf = value.safeGetMember("notchf").safeNumberOrNull()
            ?: value.safeGetMember("notch").safeNumberOrNull()
        notchf?.let { filters.add(FilterDef.Notch(cutoffHz = it, q = resonance)) }

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
                scale = scale,
                freqHz = freq,
                // Gain / Dynamics
                gain = gain,
                legato = legato,
                // Sound samples
                bank = bank,
                sound = sound,
                soundIndex = soundIndex,
                // Oscilator
                density = density,
                voices = voices,
                panSpread = panSpread,
                freqSpread = freqSpread,
                // Filters
                filters = FilterDefs(filters),
                // ADSR envelope
                adsr = adsr,
                // Pitch / Glisando
                accelerate = accelerate,
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
                bandf = bandf,
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
                roomSize = roomSize,
                // Value
                value = when {
                    value?.isString == true ->
                        value.asString().asVoiceValue()

                    value?.isNumber == true ->
                        value.asDouble().asVoiceValue()

                    value?.hasArrayElements() == true ->
                        value.`as`(List::class.java).mapNotNull {
                            it?.asVoiceValue()
                        }.asVoiceValue()

                    else -> null
                }
            ),
        )
    }
}
