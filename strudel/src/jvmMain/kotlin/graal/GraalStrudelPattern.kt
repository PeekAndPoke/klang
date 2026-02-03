package io.peekandpoke.klang.strudel.graal

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.TimeSpan
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

    override val numSteps: Rational = Rational.ONE

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val arc = graal.queryPattern(value, from.toDouble(), to.toDouble())
            ?: return emptyList()

        val events = mutableListOf<StrudelPatternEvent>()
        val count = arc.arraySize

        for (i in 0 until count) {
            val item = arc.getArrayElement(i)

//            println("---------------------------------------------------------------------------------------------")
//            println(graal.prettyFormat(item))

            val event = item.toStrudelEvent()
            events += event

//            println("${event.note} ${event.scale}")
        }

        return events.sortedBy { it.part.begin }
    }

    /**
     * Converts the js-value into a StrudelEvent.
     */
    fun Value.toStrudelEvent(): StrudelPatternEvent {
        val event = this

        val partJs = event.safeGetMember("part")
        val wholeJs = event.safeGetMember("whole")

        // Extract part TimeSpan
        val partBegin = Rational(partJs?.safeGetMember("begin")?.safeNumber(0.0) ?: 0.0)
        val partEnd = Rational(partJs?.safeGetMember("end")?.safeNumber(0.0) ?: 0.0)
        val part = TimeSpan(partBegin, partEnd)

        // Extract whole TimeSpan (null for continuous patterns)
        val whole = wholeJs?.let {
            val wholeBegin = Rational(it.safeGetMember("begin")?.safeNumber(0.0) ?: 0.0)
            val wholeEnd = Rational(it.safeGetMember("end")?.safeNumber(0.0) ?: 0.0)
            TimeSpan(wholeBegin, wholeEnd)
        }

        // Get details from "value" field
        val value = event.safeGetMember("value")

        // ///////////////////////////////////////////////////////////////////////////////////
        // Get note
        val note = value.safeGetMember("note").safeToStringOrNull()
        // scale
        val scale = event.safeGetMember("context")?.safeGetMember("scale").safeStringOrNull()
        val chord = value.safeGetMember("chord").safeStringOrNull()
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

        val velocity = value.safeGetMember("velocity").safeNumberOrNull()
            ?: value.safeGetMember("vel").safeNumberOrNull()

        val postGain = value.safeGetMember("postgain").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Get sound parameters / sample bank and index
        val bank = value.safeGetMember("bank").safeStringOrNull()
        var sound = value.safeGetMember("s").safeStringOrNull()
            ?: value.safeGetMember("wave").safeStringOrNull()
            ?: value.safeGetMember("sound").safeStringOrNull()
        val soundIndex = value.safeGetMember("n").safeNumberOrNull()?.toInt()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Get Oscillator parameters
        val density = value.safeGetMember("density").safeNumberOrNull()
        val voices = value.safeGetMember("unison").safeNumberOrNull()
        val panSpread = value.safeGetMember("spread").safeNumberOrNull()
        val freqSpread = value.safeGetMember("detune").safeNumberOrNull()
        val warmth = value.safeGetMember("warmth").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // ADSR (flat fields)
        val attack = value.safeGetMember("attack").safeNumberOrNull()
        val decay = value.safeGetMember("decay").safeNumberOrNull()
        val sustain = value.safeGetMember("sustain").safeNumberOrNull()
        val release = value.safeGetMember("release").safeNumberOrNull()

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
        // Pitch envelope
        val pAttack = value.safeGetMember("pattack").safeNumberOrNull()
            ?: value.safeGetMember("patt").safeNumberOrNull()
        val pDecay = value.safeGetMember("pdecay").safeNumberOrNull()
            ?: value.safeGetMember("pdec").safeNumberOrNull()
        val pRelease = value.safeGetMember("prelease").safeNumberOrNull()
            ?: value.safeGetMember("prel").safeNumberOrNull()
        val pEnv = value.safeGetMember("penv").safeNumberOrNull()
            ?: value.safeGetMember("pamt").safeNumberOrNull()
        val pCurve = value.safeGetMember("pcurve").safeNumberOrNull()
            ?: value.safeGetMember("pcrv").safeNumberOrNull()
        val pAnchor = value.safeGetMember("panchor").safeNumberOrNull()
            ?: value.safeGetMember("panc").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // FM Synthesis
        val fmh = value.safeGetMember("fmh").safeNumberOrNull()
        val fmAttack = value.safeGetMember("fmattack").safeNumberOrNull()
        val fmDecay = value.safeGetMember("fmdecay").safeNumberOrNull()
        val fmSustain = value.safeGetMember("fmsustain").safeNumberOrNull()
        val fmEnv = value.safeGetMember("fmenv").safeNumberOrNull()

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
        val roomFade = value.safeGetMember("roomfade").safeNumberOrNull()
        val roomLp = value.safeGetMember("roomlp").safeNumberOrNull()
        val roomDim = value.safeGetMember("roomdim").safeNumberOrNull()
        val iResponse = value.safeGetMember("iresponse").safeStringOrNull()
            ?: value.safeGetMember("ir").safeStringOrNull()

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
        // Phaser
        val phaserRate = value.safeGetMember("phaserrate").safeNumberOrNull()
        val phaserDepth = value.safeGetMember("phaserdepth").safeNumberOrNull()
        val phaserCenter = value.safeGetMember("phasercenter").safeNumberOrNull()
        val phaserSweep = value.safeGetMember("phasersweep").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Tremolo
        val tremoloSync = value.safeGetMember("tremolosync").safeNumberOrNull()
        val tremoloDepth = value.safeGetMember("tremolodepth").safeNumberOrNull()
        val tremoloSkew = value.safeGetMember("tremoloskew").safeNumberOrNull()
        val tremoloPhase = value.safeGetMember("tremolophase").safeNumberOrNull()
        val tremoloShape = value.safeGetMember("tremoloshape").safeStringOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Ducking / Sidechain
        val duckOrbit = value.safeGetMember("duckorbit").safeNumberOrNull()?.toInt()
            ?: value.safeGetMember("duck").safeNumberOrNull()?.toInt()
        val duckAttack = value.safeGetMember("duckattack").safeNumberOrNull()
            ?: value.safeGetMember("duckatt").safeNumberOrNull()
        val duckDepth = value.safeGetMember("duckdepth").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Filters (flat fields) - each filter has its own cutoff and resonance
        val cutoff = value.safeGetMember("cutoff").safeNumberOrNull()
        val resonance = value.safeGetMember("resonance").safeNumberOrNull()
        val hcutoff = value.safeGetMember("hcutoff").safeNumberOrNull()
        val hresonance = value.safeGetMember("hresonance").safeNumberOrNull()
        val bandf = value.safeGetMember("bandf").safeNumberOrNull()
            ?: value.safeGetMember("bandpass").safeNumberOrNull()
        val bandq = value.safeGetMember("bandq").safeNumberOrNull()
        val notchf = value.safeGetMember("notchf").safeNumberOrNull()
            ?: value.safeGetMember("notch").safeNumberOrNull()
        val nresonance = value.safeGetMember("nresonance").safeNumberOrNull()

        // Lowpass filter envelope
        val lpattack = value.safeGetMember("lpattack").safeNumberOrNull()
            ?: value.safeGetMember("lpa").safeNumberOrNull()
        val lpdecay = value.safeGetMember("lpdecay").safeNumberOrNull()
            ?: value.safeGetMember("lpd").safeNumberOrNull()
        val lpsustain = value.safeGetMember("lpsustain").safeNumberOrNull()
            ?: value.safeGetMember("lps").safeNumberOrNull()
        val lprelease = value.safeGetMember("lprelease").safeNumberOrNull()
            ?: value.safeGetMember("lpr").safeNumberOrNull()
        val lpenv = value.safeGetMember("lpenv").safeNumberOrNull()
            ?: value.safeGetMember("lpe").safeNumberOrNull()

        // Highpass filter envelope
        val hpattack = value.safeGetMember("hpattack").safeNumberOrNull()
            ?: value.safeGetMember("hpa").safeNumberOrNull()
        val hpdecay = value.safeGetMember("hpdecay").safeNumberOrNull()
            ?: value.safeGetMember("hpd").safeNumberOrNull()
        val hpsustain = value.safeGetMember("hpsustain").safeNumberOrNull()
            ?: value.safeGetMember("hps").safeNumberOrNull()
        val hprelease = value.safeGetMember("hprelease").safeNumberOrNull()
            ?: value.safeGetMember("hpr").safeNumberOrNull()
        val hpenv = value.safeGetMember("hpenv").safeNumberOrNull()
            ?: value.safeGetMember("hpe").safeNumberOrNull()

        // Bandpass filter envelope
        val bpattack = value.safeGetMember("bpattack").safeNumberOrNull()
            ?: value.safeGetMember("bpa").safeNumberOrNull()
        val bpdecay = value.safeGetMember("bpdecay").safeNumberOrNull()
            ?: value.safeGetMember("bpd").safeNumberOrNull()
        val bpsustain = value.safeGetMember("bpsustain").safeNumberOrNull()
            ?: value.safeGetMember("bps").safeNumberOrNull()
        val bprelease = value.safeGetMember("bprelease").safeNumberOrNull()
            ?: value.safeGetMember("bpr").safeNumberOrNull()
        val bpenv = value.safeGetMember("bpenv").safeNumberOrNull()
            ?: value.safeGetMember("bpe").safeNumberOrNull()

        // Notch filter envelope (not in original Strudel JS implementation)
        val nfattack = value.safeGetMember("nfattack").safeNumberOrNull()
            ?: value.safeGetMember("nfa").safeNumberOrNull()
        val nfdecay = value.safeGetMember("nfdecay").safeNumberOrNull()
            ?: value.safeGetMember("nfd").safeNumberOrNull()
        val nfsustain = value.safeGetMember("nfsustain").safeNumberOrNull()
            ?: value.safeGetMember("nfs").safeNumberOrNull()
        val nfrelease = value.safeGetMember("nfrelease").safeNumberOrNull()
            ?: value.safeGetMember("nfr").safeNumberOrNull()
        val nfenv = value.safeGetMember("nfenv").safeNumberOrNull()
            ?: value.safeGetMember("nfe").safeNumberOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Vowel

        val vowel = value.safeGetMember("vowel").safeStringOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Dynamics / Compression

        val compressor = value.safeGetMember("compressor").safeStringOrNull()
            ?: value.safeGetMember("comp").safeStringOrNull()

        // ///////////////////////////////////////////////////////////////////////////////////
        // Sample Manipulation
        val loop = value.safeGetMember("loop")
        sound = loop?.safeGetMember("s")?.safeStringOrNull() ?: sound

        val sampleLoop = if (loop != null) true else null
        val sampleBeginPos = value.safeGetMember("begin").safeNumberOrNull()
        val sampleEndPos = value.safeGetMember("end").safeNumberOrNull()
        val sampleSpeed = value.safeGetMember("speed").safeNumberOrNull()
        val sampleUnit = value.safeGetMember("unit").safeStringOrNull()
        val sampleCut = value.safeGetMember("cut").safeNumberOrNull()?.toInt()

        // add event
        return StrudelPatternEvent(
            // Strudel Timing
            part = part,
            whole = whole ?: part,
            // Voice data
            data = StrudelVoiceData(
                // Frequency and note
                note = note,
                scale = scale,
                chord = chord,
                freqHz = freq,
                // Gain / Dynamics
                gain = gain,
                legato = legato,
                velocity = velocity,
                postGain = postGain,
                // Sound samples
                bank = bank,
                sound = sound,
                soundIndex = soundIndex,
                // Oscilator
                density = density,
                voices = voices,
                panSpread = panSpread,
                freqSpread = freqSpread,
                warmth = warmth,
                // ADSR (flat fields)
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
                // Pitch / Glisando
                accelerate = accelerate,
                // Vibrato
                vibrato = vibrato,
                vibratoMod = vibratoMod,
                // Pitch envelope
                pAttack = pAttack,
                pDecay = pDecay,
                pRelease = pRelease,
                pEnv = pEnv,
                pCurve = pCurve,
                pAnchor = pAnchor,
                // FM Synthesis
                fmh = fmh,
                fmAttack = fmAttack,
                fmDecay = fmDecay,
                fmSustain = fmSustain,
                fmEnv = fmEnv,
                // Effects
                distort = distort,
                coarse = coarse,
                crush = crush,
                // Phaser
                phaserRate = phaserRate,
                phaserDepth = phaserDepth,
                phaserCenter = phaserCenter,
                phaserSweep = phaserSweep,
                // Tremolo
                tremoloSync = tremoloSync,
                tremoloDepth = tremoloDepth,
                tremoloSkew = tremoloSkew,
                tremoloPhase = tremoloPhase,
                tremoloShape = tremoloShape,
                // Ducking / Sidechain
                duckOrbit = duckOrbit,
                duckAttack = duckAttack,
                duckDepth = duckDepth,
                // Filters (flat fields) - each filter has its own resonance
                cutoff = cutoff,
                resonance = resonance,
                hcutoff = hcutoff,
                hresonance = hresonance,
                bandf = bandf,
                bandq = bandq,
                notchf = notchf,
                nresonance = nresonance,
                // Lowpass filter envelope
                lpattack = lpattack,
                lpdecay = lpdecay,
                lpsustain = lpsustain,
                lprelease = lprelease,
                lpenv = lpenv,
                // Highpass filter envelope
                hpattack = hpattack,
                hpdecay = hpdecay,
                hpsustain = hpsustain,
                hprelease = hprelease,
                hpenv = hpenv,
                // Bandpass filter envelope
                bpattack = bpattack,
                bpdecay = bpdecay,
                bpsustain = bpsustain,
                bprelease = bprelease,
                bpenv = bpenv,
                // Notch filter envelope (not in original Strudel JS implementation)
                nfattack = nfattack,
                nfdecay = nfdecay,
                nfsustain = nfsustain,
                nfrelease = nfrelease,
                nfenv = nfenv,
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
                roomFade = roomFade,
                roomLp = roomLp,
                roomDim = roomDim,
                iResponse = iResponse,
                // Sample manipulation
                begin = sampleBeginPos,
                end = sampleEndPos,
                speed = sampleSpeed,
                unit = sampleUnit,
                loop = sampleLoop,
                cut = sampleCut,
                loopBegin = null,
                loopEnd = null,
                // Voice / Singing
                vowel = vowel,
                // Dynamics / Compression
                compressor = compressor,
                // Value
                value = when {
                    value?.isString == true ->
                        value.asString().asVoiceValue()

                    value?.isNumber == true ->
                        value.asDouble().asVoiceValue()

                    value?.isBoolean == true ->
                        value.asBoolean().asVoiceValue()

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
