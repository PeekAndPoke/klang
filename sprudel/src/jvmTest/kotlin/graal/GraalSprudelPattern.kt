/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.graal

import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.graal.GraalJsHelpers.safeGetMember
import io.peekandpoke.klang.sprudel.graal.GraalJsHelpers.safeNumber
import io.peekandpoke.klang.sprudel.graal.GraalJsHelpers.safeNumberOrNull
import io.peekandpoke.klang.sprudel.graal.GraalJsHelpers.safeStringOrNull
import io.peekandpoke.klang.sprudel.graal.GraalJsHelpers.safeToStringOrNull
import io.peekandpoke.klang.tones.Tones
import org.graalvm.polyglot.Value

class GraalSprudelPattern(
    val value: Value,
    val graal: GraalSprudelCompiler,
) : SprudelPattern.FixedWeight {
    // Graal patterns are treated as opaque units from the JS side, so we default to weight 1.0.
    override val weight: Double = 1.0

    override val numSteps: Double = 1.0

    override fun estimateCycleDuration(): Double = 1.0

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        val arc = graal.queryPattern(value, from.toCycles(), to.toCycles())
            ?: return emptyList()

        val events = mutableListOf<SprudelPatternEvent>()
        val count = arc.arraySize

        for (i in 0 until count) {
            val item = arc.getArrayElement(i)

//            println("---------------------------------------------------------------------------------------------")
//            println(graal.prettyFormat(item))

            val event = item.toSprudelEvent()
            events += event

//            println("${event.note} ${event.scale}")
        }

        return events.sortedBy { it.part.begin }
    }

    /**
     * Converts the js-value into a SprudelEvent.
     */
    fun Value.toSprudelEvent(): SprudelPatternEvent {
        val event = this

        val partJs = event.safeGetMember("part")
        val wholeJs = event.safeGetMember("whole")

        // Extract part CycleTimeSpan
        val partBegin = CycleTime.ofCycles(partJs?.safeGetMember("begin")?.safeNumber(0.0) ?: 0.0)
        val partEnd = CycleTime.ofCycles(partJs?.safeGetMember("end")?.safeNumber(0.0) ?: 0.0)
        val part = CycleTimeSpan(partBegin, partEnd)

        // Extract whole CycleTimeSpan (null for continuous patterns)
        val whole = wholeJs?.let {
            val wholeBegin = CycleTime.ofCycles(it.safeGetMember("begin")?.safeNumber(0.0) ?: 0.0)
            val wholeEnd = CycleTime.ofCycles(it.safeGetMember("end")?.safeNumber(0.0) ?: 0.0)
            CycleTimeSpan(wholeBegin, wholeEnd)
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
        // Get Oscillator parameters → build oscParams map
        val oscParams = buildMap {
            value.safeGetMember("density").safeNumberOrNull()?.let { put("density", it) }
            value.safeGetMember("unison").safeNumberOrNull()?.let { put("voices", it) }
            value.safeGetMember("spread").safeNumberOrNull()?.let { put("panSpread", it) }
            value.safeGetMember("spread").safeNumberOrNull()?.let { put("spread", it) }
            value.safeGetMember("warmth").safeNumberOrNull()?.let { put("warmth", it) }
        }.ifEmpty { null }

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
        return SprudelPatternEvent(
            // Timing
            part = part,
            whole = whole ?: part,
            // Voice data
            data = createSprudelVoiceData().also {
                // Frequency and note
                it.note = note
                it.scale = scale
                it.chord = chord
                it.freqHz = freq
                // Gain / Dynamics
                it.gain = gain
                it.legato = legato
                it.velocity = velocity
                it.postGain = postGain
                // Sound samples
                it.bank = bank
                it.sound = sound?.let(SoundValue::Named)
                it.soundIndex = soundIndex
                // Oscillator parameters
                it.oscParams = oscParams
                // ADSR (flat fields)
                it.attack = attack
                it.decay = decay
                it.sustain = sustain
                it.release = release
                it.attackCurve = null
                it.decayCurve = null
                it.releaseCurve = null
                // Pitch / Glisando
                it.accelerate = accelerate
                // Vibrato
                it.vibrato = vibrato
                it.vibratoMod = vibratoMod
                // Pitch envelope
                it.pAttack = pAttack
                it.pDecay = pDecay
                it.pRelease = pRelease
                it.pEnv = pEnv
                it.pCurve = pCurve
                it.pAnchor = pAnchor
                // FM Synthesis
                it.fmh = fmh
                it.fmAttack = fmAttack
                it.fmDecay = fmDecay
                it.fmSustain = fmSustain
                it.fmEnv = fmEnv
                // Effects
                it.distort = distort
                it.distortShape = null
                it.coarse = coarse
                it.crush = crush
                // Phaser
                it.phaserRate = phaserRate
                it.phaserDepth = phaserDepth
                it.phaserCenter = phaserCenter
                it.phaserSweep = phaserSweep
                // Tremolo
                it.tremoloSync = tremoloSync
                it.tremoloDepth = tremoloDepth
                it.tremoloSkew = tremoloSkew
                it.tremoloPhase = tremoloPhase
                it.tremoloShape = tremoloShape
                // Ducking / Sidechain
                it.duckCylinder = duckOrbit
                it.duckAttack = duckAttack
                it.duckDepth = duckDepth
                // Filters (flat fields) - each filter has its own resonance
                it.cutoff = cutoff
                it.resonance = resonance
                it.hcutoff = hcutoff
                it.hresonance = hresonance
                it.bandf = bandf
                it.bandq = bandq
                it.notchf = notchf
                it.nresonance = nresonance
                // Lowpass filter envelope
                it.lpattack = lpattack
                it.lpdecay = lpdecay
                it.lpsustain = lpsustain
                it.lprelease = lprelease
                it.lpenv = lpenv
                // Highpass filter envelope
                it.hpattack = hpattack
                it.hpdecay = hpdecay
                it.hpsustain = hpsustain
                it.hprelease = hprelease
                it.hpenv = hpenv
                // Bandpass filter envelope
                it.bpattack = bpattack
                it.bpdecay = bpdecay
                it.bpsustain = bpsustain
                it.bprelease = bprelease
                it.bpenv = bpenv
                // Notch filter envelope (not in original Strudel JS implementation)
                it.nfattack = nfattack
                it.nfdecay = nfdecay
                it.nfsustain = nfsustain
                it.nfrelease = nfrelease
                it.nfenv = nfenv
                // Routing
                it.cylinder = orbit
                // Pan
                it.pan = pan
                // Delay
                it.delay = delay
                it.delayTime = delayTime
                it.delayFeedback = delayFeedback
                // Reverb
                it.room = room
                it.roomSize = roomSize
                it.roomFade = roomFade
                it.roomLp = roomLp
                it.roomDim = roomDim
                it.iResponse = iResponse
                // Sample manipulation
                it.begin = sampleBeginPos
                it.end = sampleEndPos
                it.speed = sampleSpeed
                it.unit = sampleUnit
                it.loop = sampleLoop
                it.cut = sampleCut
                it.loopBegin = null
                it.loopEnd = null
                // Voice / Singing
                it.vowel = vowel
                // Dynamics / Compression
                it.compressor = compressor
                // Playback control
                it.solo = null
                // Value
                it.value = when {
                    value?.isString == true -> value.asString().asVoiceValue()

                    value?.isNumber == true -> value.asDouble().asVoiceValue()

                    value?.isBoolean == true -> value.asBoolean().asVoiceValue()

                    value?.hasArrayElements() == true -> value.`as`(List::class.java).mapNotNull {
                        it?.asVoiceValue()
                    }.asVoiceValue()

                    else -> null
                }
            },
        )
    }
}
