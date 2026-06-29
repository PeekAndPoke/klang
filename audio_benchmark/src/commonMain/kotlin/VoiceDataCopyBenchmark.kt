/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.ultra.common.toFixed
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Global sink. A field of every produced copy is folded into this so the compiler / JIT / JS optimizer
 * cannot dead-code-eliminate the allocation we are trying to measure. Printed at the end so it is "used".
 */
private var copyBenchSink: Int = 0

/**
 * Micro-benchmark for the cost of shallow-copying a `SprudelVoiceData` — the per-event leaf clone that
 * dominates the query hot path. Reports `copy()` (the generated data-class copy = constructor) and
 * `clone()` (which is currently just `copy()`), so it doubles as a regression guard: if `clone()` ever
 * diverges from `copy()` again it shows up here.
 *
 * History: a native `Object.assign(Object.create(proto), src)` ("fastCopy") was benchmarked here and came
 * out ~22x SLOWER on Kotlin/JS (V8 falls back to a slow dictionary-mode object; the constructor `copy()`
 * is the optimized path). It was removed — do not reintroduce it. See the task doc.
 *
 * ```
 * ./gradlew :audio_benchmark:jsNodeProductionRun    # JS, optimized
 * ./gradlew :audio_benchmark:jvmRun                 # JVM
 * ```
 */
fun runVoiceDataCopyBenchmark() {
    // LEAF-like data: only the core fields an atom carries at emission; every group is null. This is the
    // realistic per-event clone hotspot — with grouping the ~35 null adsr/filter fields collapse to 5 null refs.
    val leaf = createSprudelVoiceData {
        note = "c3"; freqHz = 130.81; sound = SoundValue.Named("supersaw"); soundIndex = 1
    }
    // Typical voice: adsr + low/high-pass filter + distortion groups set (a few non-null → deep-cloned).
    val voice = createSprudelVoiceData {
        note = "c3"; freqHz = 130.81; scale = "e3 minor"; gain = 0.7; velocity = 0.95; postGain = 0.8
        sound = SoundValue.Named("supersaw"); soundIndex = 1
        oscParams = mapOf("voices" to 7.0, "detune" to 0.3)
        attack = 0.005; decay = 3.0; sustain = 0.0; release = 0.05
        cutoff = 1625.0; resonance = 1.2; lpenv = 1.0; lpattack = 0.005
        hcutoff = 1350.0; distort = 0.3; pan = 0.3
    }
    // WORST case: at least one field set in EVERY group, so clone() must deep-copy all 15 nested objects.
    val full = createSprudelVoiceData {
        note = "c3"; freqHz = 130.81; scale = "e3 minor"; gain = 0.7; velocity = 0.95; postGain = 0.8
        sound = SoundValue.Named("supersaw"); soundIndex = 1; oscParams = mapOf("voices" to 7.0)
        attack = 0.005; decay = 3.0; sustain = 0.0; release = 0.05                 // adsr
        cutoff = 1625.0; resonance = 1.2; lpenv = 1.0                              // lpf
        hcutoff = 1350.0; hresonance = 0.8                                         // hpf
        bandf = 800.0; bandq = 1.0                                                 // bpf
        notchf = 500.0; nresonance = 0.7                                          // notch
        accelerate = 0.1; vibrato = 5.0                                            // pitchMod
        pAttack = 0.01; pEnv = 12.0                                                // pitchEnv
        fmh = 2.0; fmEnv = 0.5                                                     // fm
        distort = 0.3; coarse = 2.0; crush = 8.0                                   // distortion
        phaserRate = 0.5; phaserDepth = 0.6                                        // phaser
        tremoloSync = 4.0; tremoloDepth = 0.4                                      // tremolo
        duckDepth = 0.5; duckAttack = 0.05                                         // duck
        delay = 0.3; delayTime = 0.25; delayFeedback = 0.4                         // delayFx
        room = 0.5; roomSize = 0.8                                                 // reverb
        begin = 0.0; end = 1.0; speed = 1.0; loop = true                          // sample
    }

    val warmupOps = 100_000
    val iterations = 300_000
    val trials = 5

    fun medianNsPerOp(op: () -> SprudelVoiceData): Double {
        repeat(warmupOps) { copyBenchSink = copyBenchSink xor (op().soundIndex ?: 0) }
        val nsPerOp = DoubleArray(trials) {
            val mark = TimeSource.Monotonic.markNow()
            repeat(iterations) { copyBenchSink = copyBenchSink xor (op().soundIndex ?: 0) }
            mark.elapsedNow().toDouble(DurationUnit.NANOSECONDS) / iterations
        }
        nsPerOp.sort()
        return nsPerOp[trials / 2]
    }

    val leafClone = medianNsPerOp { leaf.clone() }
    val leafCopy = medianNsPerOp { leaf.copy() }
    val voiceClone = medianNsPerOp { voice.clone() }
    val voiceCopy = medianNsPerOp { voice.copy() }
    val fullClone = medianNsPerOp { full.clone() }
    val fullCopy = medianNsPerOp { full.copy() }

    println("=== SprudelVoiceData clone() cost — grouped storage (all optional clusters) ===")
    println("Platform: ${platformInfo()}")
    println("Warmup $warmupOps ops; $iterations ops x $trials trials (median).")
    println("clone() = deep (groups copied); copy() = shallow (group refs shared — shown as the floor).")
    println()
    println("  leaf  (0 groups)      clone() : ${leafClone.toFixed(2)} ns/op   copy() : ${leafCopy.toFixed(2)} ns/op")
    println("  voice (4 groups)      clone() : ${voiceClone.toFixed(2)} ns/op   copy() : ${voiceCopy.toFixed(2)} ns/op")
    println("  full  (all 15 groups) clone() : ${fullClone.toFixed(2)} ns/op   copy() : ${fullCopy.toFixed(2)} ns/op")
    println("  reference: pre-grouping flat 105-field copy()/clone() ≈ 820 ns/op (JS)")
    println("  sink=$copyBenchSink")
    println()
}
