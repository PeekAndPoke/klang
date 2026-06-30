/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.wire.decode_ScheduledVoice
import io.peekandpoke.klang.audio_bridge.wire.encode_ScheduledVoice
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.ultra.common.toFixed
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private var serBenchSink: Int = 0

/** What `postMessage` does internally to copy the command object to the worklet thread. */
private fun structuredCloneJs(@Suppress("unused") obj: dynamic): dynamic = js("structuredClone(obj)")

/**
 * Per-voice `ScheduledVoice` serialization cost (JS only) for the **KSP-generated** wire codec
 * (`encode_ScheduledVoice`/`decode_ScheduledVoice`, what `WorkletContract` uses) plus the `structuredClone`
 * that `postMessage` performs. The worklet-side decode is the cost that matters (audio thread). The kotlinx
 * JSON-dynamic baseline this replaced is gone (the wire types are no longer `@Serializable`); the ~174×
 * win it demonstrated is recorded in `docs/tasks-archive/2026-06/20260607-worklet-codec-ksp.md`.
 */
fun runWorkletSerializationBenchmark() {
    val voiceData = createSprudelVoiceData {
        note = "c3"; freqHz = 130.81; scale = "e minor"; gain = 0.7; velocity = 0.9; postGain = 0.8
        sound = SoundValue.Named("supersaw"); soundIndex = 1
        oscParams = mapOf("voices" to 7.0, "spread" to 0.3)
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

    val genEncoded = encode_ScheduledVoice(sv)
    val genOk = decode_ScheduledVoice(genEncoded) == sv

    val genEncodeNs = medianNsPerOp {
        val e = encode_ScheduledVoice(sv); serBenchSink = serBenchSink xor (if (e == null) 0 else 1)
    }
    val cloneNs = medianNsPerOp {
        val c = structuredCloneJs(genEncoded); serBenchSink = serBenchSink xor (if (c == null) 0 else 1)
    }
    val genDecodeNs = medianNsPerOp {
        val d = decode_ScheduledVoice(genEncoded); serBenchSink = serBenchSink xor (d.data.soundIndex ?: 0)
    }

    println("=== Worklet ScheduledVoice serialization cost (per voice, JS) ===")
    println("Platform: ${platformInfo()}")
    println("$iterations ops x $trials trials (median). generated codec round-trips == original: $genOk")
    println()
    println("KSP-generated wire codec (used by WorkletContract):")
    println("  encode (frontend send) : ${genEncodeNs.toFixed(0)} ns/op")
    println("  structuredClone (postMessage)   : ${cloneNs.toFixed(0)} ns/op")
    println("  decode (worklet recv)  : ${genDecodeNs.toFixed(0)} ns/op")
    println("  sink=$serBenchSink")
    println()
}
