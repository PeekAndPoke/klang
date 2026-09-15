/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.fastExp
import io.peekandpoke.klang.audio_be.fastExp2
import io.peekandpoke.klang.audio_be.fastSin
import io.peekandpoke.ultra.common.toFixed
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private var mathBenchSink: Double = 0.0

/**
 * Median ns per call of [f] over [input] into [out], [passes] passes per trial. Inline, so the
 * loop body IS the function under test: a non-inline `(Double) -> Double` would box every
 * argument and result on the JVM and measure the boxing.
 */
private inline fun medianNsPerCall(
    input: DoubleArray, out: DoubleArray, warmupPasses: Int, passes: Int, trials: Int, f: (Double) -> Double,
): Double {
    val n = input.size

    repeat(warmupPasses) { p ->
        for (k in 0 until n) {
            out[k] = f(input[k])
        }

        mathBenchSink += out[p and (n - 1)]
    }

    val nsPerCall = DoubleArray(trials) {
        val mark = TimeSource.Monotonic.markNow()

        repeat(passes) { p ->
            for (k in 0 until n) {
                out[k] = f(input[k])
            }

            mathBenchSink += out[p and (n - 1)]
        }

        mark.elapsedNow().toDouble(DurationUnit.NANOSECONDS) / (passes.toDouble() * n)
    }

    nsPerCall.sort()

    return nsPerCall[trials / 2]
}

/**
 * The per-sample transcendentals the engine replaced with polynomials on 2026-09-15, each against
 * the library function it replaced, on the argument sweep its callers produce: a wrapped phase
 * for the sine, a pitch ratio's octaves for `2^x`, an envelope curve's `k · x` for `e^x`. Prints
 * ns per call per platform; the point is the JS number, where `Math.sin`, `Math.pow` and
 * `Math.exp` are not intrinsics and the song benchmarks (JVM only) cannot see the difference.
 */
fun runMathBenchmark(platform: String) {
    val n = 1 shl 16
    val phases = DoubleArray(n) { TWO_PI * it / n }
    val octaves = DoubleArray(n) { -2.0 + 4.0 * it / n }
    val curve = DoubleArray(n) { 3.0 * it / n }
    val out = DoubleArray(n)
    val warmupPasses = 200
    val passes = 100
    val trials = 5

    val rows = listOf(
        Triple(
            "sin(phase)",
            medianNsPerCall(phases, out, warmupPasses, passes, trials) { sin(it) },
            medianNsPerCall(phases, out, warmupPasses, passes, trials) { fastSin(it) },
        ),
        Triple(
            "2^x, x in [-2, 2)",
            medianNsPerCall(octaves, out, warmupPasses, passes, trials) { 2.0.pow(it) },
            medianNsPerCall(octaves, out, warmupPasses, passes, trials) { fastExp2(it) },
        ),
        Triple(
            "e^x, x in [0, 3)",
            medianNsPerCall(curve, out, warmupPasses, passes, trials) { exp(it) },
            medianNsPerCall(curve, out, warmupPasses, passes, trials) { fastExp(it) },
        ),
    )

    println("=== Math benchmark: library versus polynomial, ns per call ===")
    println("Platform: $platform; $n arguments per pass, $passes passes per trial, median of $trials trials")
    println("${"Function".padEnd(22)} ${"library".padStart(10)} ${"polynomial".padStart(12)} ${"speedup".padStart(9)}")

    for ((name, lib, poly) in rows) {
        println("${name.padEnd(22)} ${lib.toFixed(2).padStart(10)} ${poly.toFixed(2).padStart(12)} ${(lib / poly).toFixed(2).padStart(8)}x")
    }

    // Printed so the sink, and with it every store the loops made, is used.
    println("(sink ${mathBenchSink.toFixed(1)})")
    println()
}
