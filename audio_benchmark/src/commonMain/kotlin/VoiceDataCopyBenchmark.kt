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
    // A representative leaf voice (a spread of set + null fields, like Der Schmetterling's voices).
    val sample = createSprudelVoiceData {
        note = "c3"; freqHz = 130.81; scale = "e3 minor"; gain = 0.7; velocity = 0.95; postGain = 0.8
        sound = SoundValue.Named("supersaw"); soundIndex = 1
        oscParams = mapOf("voices" to 7.0, "freqSpread" to 0.3)
        attack = 0.005; decay = 3.0; sustain = 0.0; release = 0.05
        cutoff = 1625.0; resonance = 1.2; lpenv = 1.0; lpattack = 0.005
        hcutoff = 1350.0; distort = 0.3; pan = 0.3
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

    val copyNs = medianNsPerOp { sample.copy() }
    val cloneNs = medianNsPerOp { sample.clone() }

    println("=== SprudelVoiceData shallow-copy cost (copy() / clone()) ===")
    println("Platform: ${platformInfo()}")
    println("Warmup $warmupOps ops; $iterations ops x $trials trials (median), best of 2 passes.")
    println()
    println("  copy()  : ${copyNs.toFixed(2)} ns/op")
    println("  clone() : ${cloneNs.toFixed(2)} ns/op   (${(copyNs / cloneNs).toFixed(2)}x vs copy)")
    println("  sink=$copyBenchSink")
    println()
}
