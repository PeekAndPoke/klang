/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

fun main() {
    // The allocation check's V8 half: the JVM driver (`KLANG_BENCH_MODE=alloc`) runs this bundle once per case,
    // round and block count. Command-line arguments do not reach `main`, so the case comes from the environment.
    val allocCase = envVar("KLANG_ALLOC_CASE")

    if (allocCase != null) {
        runAllocationCase(
            name = allocCase,
            warm = envVar("KLANG_ALLOC_WARM")?.toIntOrNull() ?: 0,
            count = envVar("KLANG_ALLOC_COUNT")?.toIntOrNull() ?: 0,
        )

        return
    }

    println("=== Klang Audio Benchmark (JS) ===")
    println()
    runWorkletSerializationBenchmark()
    runBenchmark()
}
