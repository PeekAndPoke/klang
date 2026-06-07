@file:OptIn(ExperimentalSerializationApi::class)

package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.ultra.common.toFixed
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private var serBenchSink: Int = 0

/** What `postMessage` does internally to copy the command object to the worklet thread. */
private fun structuredCloneJs(obj: dynamic): dynamic = js("structuredClone(obj)")

private fun jsEmpty(): dynamic = js("({})")
private fun jsArray(): dynamic = js("([])")

// ---------------------------------------------------------------------------------------------------------
// Hand-rolled "trust the input" codec (what a KSP processor would generate). Direct property writes/reads +
// constructor calls — no kotlinx decode driver, no name→index matching, no polymorphic discriminator dispatch.
// Same shape both ways (we own both ends), so the worklet decode just constructs. AdsrCurve as ordinal Int;
// FilterDef variant as a small `t` tag.
// ---------------------------------------------------------------------------------------------------------

private fun encEnv(e: FilterEnvDef): dynamic {
    val o = jsEmpty()
    o.a = e.attack; o.d = e.decay; o.s = e.sustain; o.r = e.release; o.dep = e.depth
    return o
}

private fun decEnv(o: dynamic): FilterEnvDef = FilterEnvDef(
    attack = o.a, decay = o.d, sustain = o.s, release = o.r, depth = o.dep,
)

private fun encFilter(f: FilterDef): dynamic {
    val o = jsEmpty()
    when (f) {
        is FilterDef.LowPass -> {
            o.t = 0; o.c = f.cutoffHz; o.q = f.q; o.env = f.envelope?.let { encEnv(it) }
        }

        is FilterDef.HighPass -> {
            o.t = 1; o.c = f.cutoffHz; o.q = f.q; o.env = f.envelope?.let { encEnv(it) }
        }

        is FilterDef.BandPass -> {
            o.t = 2; o.c = f.cutoffHz; o.q = f.q; o.env = f.envelope?.let { encEnv(it) }
        }

        is FilterDef.Notch -> {
            o.t = 3; o.c = f.cutoffHz; o.q = f.q; o.env = f.envelope?.let { encEnv(it) }
        }

        is FilterDef.Formant -> {
            o.t = 4
            val arr = jsArray()
            for (b in f.bands) {
                val bo = jsEmpty(); bo.f = b.freq; bo.db = b.db; bo.q = b.q; arr.push(bo)
            }
            o.bands = arr
        }
    }
    return o
}

private fun decFilter(o: dynamic): FilterDef {
    val env: FilterEnvDef? = if (o.env == null) null else decEnv(o.env)
    return when (o.t as Int) {
        0 -> FilterDef.LowPass(cutoffHz = o.c, q = o.q, envelope = env)
        1 -> FilterDef.HighPass(cutoffHz = o.c, q = o.q, envelope = env)
        2 -> FilterDef.BandPass(cutoffHz = o.c, q = o.q, envelope = env)
        3 -> FilterDef.Notch(cutoffHz = o.c, q = o.q, envelope = env)
        else -> {
            val bands = ArrayList<FilterDef.Formant.Band>()
            val arr = o.bands
            val n = arr.length as Int
            for (i in 0 until n) {
                val b = arr[i]
                bands.add(FilterDef.Formant.Band(freq = b.f, db = b.db, q = b.q))
            }
            FilterDef.Formant(bands = bands)
        }
    }
}

private fun encAdsr(a: AdsrDef): dynamic {
    a as AdsrDef.Std
    val o = jsEmpty()
    o.at = a.attack; o.de = a.decay; o.su = a.sustain; o.re = a.release
    o.ac = a.attackCurve?.ordinal; o.dc = a.decayCurve?.ordinal; o.rc = a.releaseCurve?.ordinal
    return o
}

private fun decAdsr(o: dynamic): AdsrDef {
    val curves = AdsrCurve.entries
    val ac: Int? = o.ac;
    val dc: Int? = o.dc;
    val rc: Int? = o.rc
    return AdsrDef.Std(
        attack = o.at, decay = o.de, sustain = o.su, release = o.re,
        attackCurve = ac?.let { curves[it] },
        decayCurve = dc?.let { curves[it] },
        releaseCurve = rc?.let { curves[it] },
    )
}

private fun encOscParams(m: Map<String, Double>): dynamic {
    val o = jsEmpty()
    for ((k, v) in m) o[k] = v
    return o
}

private fun decOscParams(o: dynamic): Map<String, Double> {
    val keys = js("Object.keys(o)").unsafeCast<Array<String>>()
    val m = LinkedHashMap<String, Double>(keys.size)
    for (k in keys) m[k] = o[k] as Double
    return m
}

private fun encVoiceData(vd: VoiceData): dynamic {
    val o = jsEmpty()
    o.note = vd.note; o.freqHz = vd.freqHz; o.scale = vd.scale
    o.gain = vd.gain; o.velocity = vd.velocity; o.postGain = vd.postGain; o.legato = vd.legato
    o.bank = vd.bank; o.sound = vd.sound; o.soundIndex = vd.soundIndex
    o.oscParams = vd.oscParams?.let { encOscParams(it) }
    val fil = jsArray(); for (f in vd.filters) fil.push(encFilter(f)); o.filters = fil
    o.adsr = encAdsr(vd.adsr)
    o.accelerate = vd.accelerate; o.vibrato = vd.vibrato; o.vibratoMod = vd.vibratoMod
    o.pAttack = vd.pAttack; o.pDecay = vd.pDecay; o.pRelease = vd.pRelease; o.pEnv = vd.pEnv; o.pCurve = vd.pCurve; o.pAnchor = vd.pAnchor
    o.fmh = vd.fmh; o.fmAttack = vd.fmAttack; o.fmDecay = vd.fmDecay; o.fmSustain = vd.fmSustain; o.fmEnv = vd.fmEnv
    o.distort = vd.distort; o.distortShape = vd.distortShape; o.distortOversample = vd.distortOversample
    o.coarse = vd.coarse; o.coarseOversample = vd.coarseOversample; o.crush = vd.crush; o.crushOversample = vd.crushOversample
    o.phaser = vd.phaser; o.phaserDepth = vd.phaserDepth; o.phaserCenter = vd.phaserCenter; o.phaserSweep = vd.phaserSweep
    o.tremoloSync = vd.tremoloSync; o.tremoloDepth = vd.tremoloDepth; o.tremoloSkew = vd.tremoloSkew; o.tremoloPhase =
        vd.tremoloPhase; o.tremoloShape = vd.tremoloShape
    o.duckCylinder = vd.duckCylinder; o.duckAttack = vd.duckAttack; o.duckDepth = vd.duckDepth
    o.cutoff = vd.cutoff; o.hcutoff = vd.hcutoff; o.bandf = vd.bandf; o.resonance = vd.resonance
    o.cylinder = vd.cylinder; o.pan = vd.pan
    o.delay = vd.delay; o.delayTime = vd.delayTime; o.delayFeedback = vd.delayFeedback
    o.room = vd.room; o.roomSize = vd.roomSize; o.roomFade = vd.roomFade; o.roomLp = vd.roomLp; o.roomDim = vd.roomDim; o.iResponse =
        vd.iResponse
    o.begin = vd.begin; o.end = vd.end; o.speed = vd.speed; o.loop = vd.loop; o.cut = vd.cut; o.loopBegin = vd.loopBegin; o.loopEnd =
        vd.loopEnd
    o.compressor = vd.compressor; o.solo = vd.solo; o.sourceId = vd.sourceId; o.engine = vd.engine
    return o
}

private fun decVoiceData(o: dynamic): VoiceData {
    val fil = ArrayList<FilterDef>()
    val arr = o.filters;
    val n = arr.length as Int
    for (i in 0 until n) fil.add(decFilter(arr[i]))
    return VoiceData(
        note = o.note,
        freqHz = o.freqHz,
        scale = o.scale,
        gain = o.gain,
        velocity = o.velocity,
        postGain = o.postGain,
        legato = o.legato,
        bank = o.bank,
        sound = o.sound,
        soundIndex = o.soundIndex,
        oscParams = if (o.oscParams == null) null else decOscParams(o.oscParams),
        filters = FilterDefs(fil),
        adsr = decAdsr(o.adsr),
        accelerate = o.accelerate,
        vibrato = o.vibrato,
        vibratoMod = o.vibratoMod,
        pAttack = o.pAttack,
        pDecay = o.pDecay,
        pRelease = o.pRelease,
        pEnv = o.pEnv,
        pCurve = o.pCurve,
        pAnchor = o.pAnchor,
        fmh = o.fmh,
        fmAttack = o.fmAttack,
        fmDecay = o.fmDecay,
        fmSustain = o.fmSustain,
        fmEnv = o.fmEnv,
        distort = o.distort,
        distortShape = o.distortShape,
        distortOversample = o.distortOversample,
        coarse = o.coarse,
        coarseOversample = o.coarseOversample,
        crush = o.crush,
        crushOversample = o.crushOversample,
        phaser = o.phaser,
        phaserDepth = o.phaserDepth,
        phaserCenter = o.phaserCenter,
        phaserSweep = o.phaserSweep,
        tremoloSync = o.tremoloSync,
        tremoloDepth = o.tremoloDepth,
        tremoloSkew = o.tremoloSkew,
        tremoloPhase = o.tremoloPhase,
        tremoloShape = o.tremoloShape,
        duckCylinder = o.duckCylinder,
        duckAttack = o.duckAttack,
        duckDepth = o.duckDepth,
        cutoff = o.cutoff,
        hcutoff = o.hcutoff,
        bandf = o.bandf,
        resonance = o.resonance,
        cylinder = o.cylinder,
        pan = o.pan,
        delay = o.delay,
        delayTime = o.delayTime,
        delayFeedback = o.delayFeedback,
        room = o.room,
        roomSize = o.roomSize,
        roomFade = o.roomFade,
        roomLp = o.roomLp,
        roomDim = o.roomDim,
        iResponse = o.iResponse,
        begin = o.begin,
        end = o.end,
        speed = o.speed,
        loop = o.loop,
        cut = o.cut,
        loopBegin = o.loopBegin,
        loopEnd = o.loopEnd,
        compressor = o.compressor,
        solo = o.solo,
        sourceId = o.sourceId,
        engine = o.engine,
    )
}

private fun encScheduledVoice(sv: ScheduledVoice): dynamic {
    val o = jsEmpty()
    o.playbackId = sv.playbackId
    o.data = encVoiceData(sv.data)
    o.startTime = sv.startTime
    o.gateEndTime = sv.gateEndTime
    o.playbackStartTime = sv.playbackStartTime
    return o
}

private fun decScheduledVoice(o: dynamic): ScheduledVoice = ScheduledVoice(
    playbackId = o.playbackId,
    data = decVoiceData(o.data),
    startTime = o.startTime,
    gateEndTime = o.gateEndTime,
    playbackStartTime = o.playbackStartTime,
)

/**
 * Disambiguation + trust-codec proof for the `sendCmd` path (JS only). Per voice the frontend does
 * `encodeToDynamic(ScheduledVoice.serializer())` then `postMessage` (≈ `structuredClone`); the worklet does
 * the mirror `decodeFromDynamic` on the audio thread. The worklet-side decode is the dominant cost.
 *
 * Compares the current kotlinx JSON-dynamic codec against a hand-rolled trust-the-input codec (the shape a
 * KSP processor would generate) to confirm a direct read→construct codec lands at µs-level.
 */
fun runWorkletSerializationBenchmark() {
    val codec = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val voiceData = createSprudelVoiceData {
        note = "c3"; freqHz = 130.81; scale = "e minor"; gain = 0.7; velocity = 0.9; postGain = 0.8
        sound = SoundValue.Named("supersaw"); soundIndex = 1
        oscParams = mapOf("voices" to 7.0, "freqSpread" to 0.3)
        attack = 0.005; decay = 0.2; sustain = 0.6; release = 0.05
        cutoff = 1625.0; resonance = 1.2; lpenv = 1.0; lpattack = 0.005
        hcutoff = 1350.0; distort = 0.3; pan = 0.3; cylinder = 1
    }.toVoiceData()
    val sv = ScheduledVoice("pb-1", voiceData, 1.0, 2.0, 0.5)

    val warmup = 50_000
    val iterations = 200_000
    val trials = 5

    fun medianNsPerOp(op: () -> Unit): Double {
        repeat(warmup) { op() }
        val ns = DoubleArray(trials) {
            val mark = TimeSource.Monotonic.markNow()
            repeat(iterations) { op() }
            mark.elapsedNow().toDouble(DurationUnit.NANOSECONDS) / iterations
        }
        ns.sort()
        return ns[trials / 2]
    }

    // Correctness: the hand codec must round-trip equal to the original (proves it's real work, not DCE).
    val handRoundTripOk = decScheduledVoice(encScheduledVoice(sv)) == sv

    val encoded = codec.encodeToDynamic(ScheduledVoice.serializer(), sv)
    val handEncoded = encScheduledVoice(sv)

    val encodeNs = medianNsPerOp {
        val e = codec.encodeToDynamic(ScheduledVoice.serializer(), sv); serBenchSink = serBenchSink xor (if (e == null) 0 else 1)
    }
    val cloneNs = medianNsPerOp {
        val c = structuredCloneJs(encoded); serBenchSink = serBenchSink xor (if (c == null) 0 else 1)
    }
    val decodeNs = medianNsPerOp {
        val d = codec.decodeFromDynamic(ScheduledVoice.serializer(), encoded); serBenchSink = serBenchSink xor (d.data.soundIndex ?: 0)
    }
    val handEncodeNs = medianNsPerOp {
        val e = encScheduledVoice(sv); serBenchSink = serBenchSink xor (if (e == null) 0 else 1)
    }
    val handDecodeNs = medianNsPerOp {
        val d = decScheduledVoice(handEncoded); serBenchSink = serBenchSink xor (d.data.soundIndex ?: 0)
    }

    println("=== Worklet ScheduledVoice serialization cost (per voice, JS) ===")
    println("Platform: ${platformInfo()}")
    println("$iterations ops x $trials trials (median). hand-codec round-trips == kotlinx: $handRoundTripOk")
    println()
    println("kotlinx JSON-dynamic (current):")
    println("  encodeToDynamic   (frontend send) : ${encodeNs.toFixed(0)} ns/op")
    println("  structuredClone   (postMessage)   : ${cloneNs.toFixed(0)} ns/op")
    println("  decodeFromDynamic (worklet recv)  : ${decodeNs.toFixed(0)} ns/op")
    println()
    println("hand-rolled trust-codec (→ JS object; what KSP would generate):")
    println("  encode (frontend send) : ${handEncodeNs.toFixed(0)} ns/op")
    println("  decode (worklet recv)  : ${handDecodeNs.toFixed(0)} ns/op")
    println("  sink=$serBenchSink")
    println()
}
