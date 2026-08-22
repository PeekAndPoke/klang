/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

/**
 * One thing that went wrong while a pattern was being built, captured instead of thrown.
 *
 * Carries the raw [error] rather than a pre-formatted string: the error object already holds the
 * source location (a `KlangScriptError` is `SourceLocationAware`), and the frontend already knows
 * how to turn that into a clickable editor marker. Flattening it to text here is exactly what
 * used to lose the location.
 */
data class SprudelDiagnostic(
    /** Where in the pattern machinery this was caught, e.g. "layer transform". */
    val context: String,
    val error: Throwable,
) {
    /** Stable key for de-duplication: same place, same problem. */
    val dedupKey: String get() = "$context|${error::class.simpleName}|${error.message}"
}

/**
 * Collects errors that pattern building swallows, so they can be shown where the user is looking.
 *
 * ## Why this exists
 *
 * Pattern mappers run inside `try/catch` blocks that log and return the input unchanged. That
 * catch is deliberate and stays: a bad edit mid-performance must not kill the audio. But it also
 * made mistakes invisible — a single transposed letter (`.ocsp` for `.oscp`) silently discarded
 * an entire shape function, and the only trace was a stack trace in the browser console. It cost
 * a full debugging session.
 *
 * So the catch keeps swallowing, and this is where the swallowed thing goes.
 *
 * ## Usage
 *
 * ```
 * val found = mutableListOf<SprudelDiagnostic>()
 * val pattern = SprudelDiagnostics.collectingInto(found) { compileAndQuery() }
 * // `found` now holds anything that was swallowed, with locations intact
 * ```
 *
 * With no collector installed, [report] keeps printing, so the CLI and offline renders still
 * surface these the way they always did. The text differs slightly from the old per-site
 * messages (the mapper's `toString` is no longer included); nothing parses these strings.
 */
object SprudelDiagnostics {

    private var sink: ((SprudelDiagnostic) -> Unit)? = null

    /**
     * Hands a swallowed error to the active collector, or prints it when there is none.
     *
     * Must never throw: it is called from inside a catch block, and an exception here would
     * defeat the resilience the catch exists to provide.
     */
    fun report(context: String, error: Throwable) {
        val current = sink

        if (current == null) {
            println("Error in $context: ${error.stackTraceToString()}")
            return
        }

        try {
            current(SprudelDiagnostic(context, error))
        } catch (_: Throwable) {
            // A broken collector must not take the audio down with it.
            println("Error in $context: ${error.stackTraceToString()}")
        }
    }

    /**
     * Runs [block] with diagnostics collected into [into], de-duplicated by [SprudelDiagnostic.dedupKey].
     *
     * Restores the previous collector afterwards, including on exception, so nested or repeated
     * calls cannot leave a stale collector installed and leak diagnostics into someone else's list.
     *
     * NOTE this is a single global slot, which is exact on Kotlin/JS (single-threaded, and the
     * only consumer that needs collection is the browser editor). On the JVM two concurrent
     * collections would interleave; the JVM paths only print today, so that is accepted rather
     * than paid for with a thread-local.
     *
     * For the same reason [block] must be SYNCHRONOUS. Wrapping a suspending region would leave
     * the collector installed across the suspension and capture another coroutine's diagnostics.
     * The one production caller wraps `SprudelPattern.compile`, which does not suspend.
     *
     * Tests that call this mutate global state, so they rely on specs running sequentially (the
     * project has no kotest `AbstractProjectConfig` enabling concurrency). Enabling concurrent
     * specs would need a thread-local here first.
     */
    fun <T> collectingInto(into: MutableList<SprudelDiagnostic>, block: () -> T): T {
        val previous = sink
        val seen = mutableSetOf<String>()

        sink = { diagnostic ->
            if (seen.add(diagnostic.dedupKey)) {
                into.add(diagnostic)
            }
        }

        return try {
            block()
        } finally {
            sink = previous
        }
    }
}
