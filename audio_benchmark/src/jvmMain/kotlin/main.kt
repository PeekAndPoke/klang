/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

import kotlin.system.exitProcess

fun main() {
    // `KLANG_BENCH_MODE=alloc`: the allocation check against the budgets, instead of the timing benchmark.
    if (System.getenv("KLANG_BENCH_MODE")?.trim() == "alloc") {
        exitProcess(AllocationCheck.run())
    }

    println("=== Klang Audio Benchmark (JVM) ===")
    println()
    runBenchmark()
}
