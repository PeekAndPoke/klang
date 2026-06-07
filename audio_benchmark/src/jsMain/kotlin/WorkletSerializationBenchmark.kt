@file:OptIn(ExperimentalSerializationApi::class)

package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.SoundValue
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
 * Disambiguates the `sendCmd` hot-path cost (JS only). Per scheduled voice the frontend does
 * `encodeToDynamic(ScheduledVoice.serializer())` then `postMessage` (≈ `structuredClone`); the worklet does
 * the mirror `decodeFromDynamic` on the audio thread. This measures all three in isolation so we know which
 * to attack: a hand-rolled codec only helps the encode/decode, NOT the structured-clone.
 *
 * Uses the worklet's exact codec config (`WorkletContract`: ignoreUnknownKeys + explicitNulls=false).
 */
fun runWorkletSerializationBenchmark() {
    val codec = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // A representative voice (a handful of groups populated, like a real Der Schmetterling voice).
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

    val encoded = codec.encodeToDynamic(ScheduledVoice.serializer(), sv)

    val encodeNs = medianNsPerOp {
        val e = codec.encodeToDynamic(ScheduledVoice.serializer(), sv)
        serBenchSink = serBenchSink xor (if (e == null) 0 else 1)
    }
    val cloneNs = medianNsPerOp {
        val c = structuredCloneJs(encoded)
        serBenchSink = serBenchSink xor (if (c == null) 0 else 1)
    }
    val decodeNs = medianNsPerOp {
        val d = codec.decodeFromDynamic(ScheduledVoice.serializer(), encoded)
        serBenchSink = serBenchSink xor (d.data.soundIndex ?: 0)
    }

    println("=== Worklet ScheduledVoice serialization cost (per voice, JS) ===")
    println("Platform: ${platformInfo()}")
    println("$iterations ops x $trials trials (median).")
    println()
    println("  encodeToDynamic   (frontend send) : ${encodeNs.toFixed(0)} ns/op")
    println("  structuredClone   (postMessage)   : ${cloneNs.toFixed(0)} ns/op")
    println("  decodeFromDynamic (worklet recv)  : ${decodeNs.toFixed(0)} ns/op")
    println("  → hand-rolled codec helps encode+decode; structuredClone needs a smaller/binary payload.")
    println("  sink=$serBenchSink")
    println()
}
