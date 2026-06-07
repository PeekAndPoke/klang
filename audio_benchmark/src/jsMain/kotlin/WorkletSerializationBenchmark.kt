@file:OptIn(ExperimentalSerializationApi::class)

package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.wire.decode_ScheduledVoice
import io.peekandpoke.klang.audio_bridge.wire.encode_ScheduledVoice
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

/**
 * Per-voice `ScheduledVoice` serialization cost (JS only): the **KSP-generated** wire codec
 * (`encode_ScheduledVoice`/`decode_ScheduledVoice`, what `WorkletContract` now uses) vs the old kotlinx
 * JSON-dynamic path it replaced. The worklet-side decode is the cost that matters (audio thread).
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

    val kxEncoded = codec.encodeToDynamic(ScheduledVoice.serializer(), sv)
    val genEncoded = encode_ScheduledVoice(sv)
    val genOk = decode_ScheduledVoice(genEncoded) == sv

    val kxEncodeNs = medianNsPerOp {
        val e = codec.encodeToDynamic(ScheduledVoice.serializer(), sv); serBenchSink = serBenchSink xor (if (e == null) 0 else 1)
    }
    val cloneNs = medianNsPerOp {
        val c = structuredCloneJs(kxEncoded); serBenchSink = serBenchSink xor (if (c == null) 0 else 1)
    }
    val kxDecodeNs = medianNsPerOp {
        val d = codec.decodeFromDynamic(ScheduledVoice.serializer(), kxEncoded); serBenchSink = serBenchSink xor (d.data.soundIndex ?: 0)
    }
    val genEncodeNs = medianNsPerOp {
        val e = encode_ScheduledVoice(sv); serBenchSink = serBenchSink xor (if (e == null) 0 else 1)
    }
    val genDecodeNs = medianNsPerOp {
        val d = decode_ScheduledVoice(genEncoded); serBenchSink = serBenchSink xor (d.data.soundIndex ?: 0)
    }

    println("=== Worklet ScheduledVoice serialization cost (per voice, JS) ===")
    println("Platform: ${platformInfo()}")
    println("$iterations ops x $trials trials (median). generated codec round-trips == original: $genOk")
    println()
    println("kotlinx JSON-dynamic (old):")
    println("  encodeToDynamic   (frontend send) : ${kxEncodeNs.toFixed(0)} ns/op")
    println("  structuredClone   (postMessage)   : ${cloneNs.toFixed(0)} ns/op")
    println("  decodeFromDynamic (worklet recv)  : ${kxDecodeNs.toFixed(0)} ns/op")
    println()
    println("KSP-generated wire codec (now used by WorkletContract):")
    println("  encode (frontend send) : ${genEncodeNs.toFixed(0)} ns/op")
    println("  decode (worklet recv)  : ${genDecodeNs.toFixed(0)} ns/op")
    println("  sink=$serBenchSink")
    println()
}
