/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

/**
 * One V8 measurement of the allocation check: renders [warm] blocks of the case [name], then [count] more, and
 * prints `NS <ns per block of the counted window>` and `PEAK <the last block's peak>` (`OUTPUTS <n>` for a case that
 * makes no sound, [AllocationCases.Case.makesSound]). The bytes are not measured here: node's `--trace-gc-nvp` lines
 * carry them, and the driver (`AllocationCheck.kt`, JVM main) subtracts a run with `count = 0` from a run with
 * `count > 0`. An unknown case prints `ERROR` and sets a failing exit code.
 */
fun runAllocationCase(name: String, warm: Int, count: Int) {
    val case = AllocationCases.byName(name)

    if (case == null) {
        println("ERROR unknown allocation case \"$name\"")
        js("process.exitCode = 2")

        return
    }

    val rig = AllocationCases.rig(case)

    rig.run(warm)

    val t0 = nowMs()

    rig.run(count)

    val t1 = nowMs()
    val ns = if (count > 0) (t1 - t0) * 1.0e6 / count else 0.0

    val proof = if (case.makesSound) "PEAK ${rig.peak()}" else "OUTPUTS ${rig.outputs()}"

    writeStdout("NS $ns\n$proof\n")
}

/**
 * Writes [text] to stdout in ONE write. Kotlin/JS `println` is two writes (the text, then its newline), and a
 * scavenge that falls between them prints its `--trace-gc-nvp` line in the gap: the GC line is glued onto `NS ...` or
 * `PEAK ...`, the driver counts it as a GC line, and the run went red with "no PEAK line" on a healthy tree (engine
 * follow-up 6, review round 1). The driver sends stdout to a FILE, where node writes synchronously, and V8 prints a GC
 * trace line on the main thread inside its GC pause, so one write leaves a GC line only before or after it. On a pipe
 * node's stdout is an asynchronous stream, and this reasoning would need checking again.
 */
private fun writeStdout(text: String) {
    js("process.stdout.write(text)")
}

private fun nowMs(): Double = js("performance.now()").unsafeCast<Double>()

/** The environment variable [name] of the node process, or null when unset or blank. */
fun envVar(name: String): String? {
    val env: dynamic = js("(typeof process !== 'undefined' && process.env) || {}")
    val raw = env[name] as? String

    return raw?.trim()?.takeIf { it.isNotEmpty() }
}
