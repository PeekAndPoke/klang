/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.runtime.KlangScriptTypeError

/**
 * Applies a door's `configure` lambda to the builder it just created and enforces the contract
 * of `docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`: the lambda receives the builder and must return it
 * (or a builder of the same type, since every knob returns a new one).
 *
 * The lambda arrives from KlangScript as a Kotlin function whose declared return type is a
 * promise the interpreter cannot keep: a script lambda may return null (block body without
 * `return`) or anything else. So the result is read as `Any?` and checked here, once, with a
 * script-level error that names the door, instead of a cast failure deep inside the native.
 *
 * @param door the script-facing door name for the error message, e.g. `"Osc.supersaw"`.
 */
internal fun <B : Any> B.configuredBy(door: String, configure: ((B) -> B)?): B {
    if (configure == null) {
        return this
    }
    @Suppress("UNCHECKED_CAST")
    val result: Any? = (configure as (B) -> Any?)(this)
    if (result == null) {
        throw KlangScriptTypeError(
            message = "the configure lambda of $door returned nothing; return the builder it received (`x => x.analog(3)`)",
            operation = door,
        )
    }
    if (!this::class.isInstance(result)) {
        throw KlangScriptTypeError(
            message = "the configure lambda of $door must return the ${this::class.simpleName} it received, " +
                    "got ${result::class.simpleName}; processing (`.lowpass()`, `.adsr()`, ...) goes outside the lambda",
            operation = door,
        )
    }
    @Suppress("UNCHECKED_CAST")
    return result as B
}

/**
 * Runs [start] through script-supplied [stages] for a `through` door, first to last, checking every stage as
 * [configuredBy] checks a configure lambda: a script lambda's declared return type is a promise the interpreter cannot
 * keep (a block body without `return` returns null), so each result is read as `Any?` and checked here, with an error
 * that names the door and the stage, instead of a cast failure (JVM) or a silently wrong value (JS) further on.
 *
 * [stages] is read as `Any?` on purpose, see the null check. [returns] names what a stage must return, for the
 * message; [isResult] tests it. Not `this::class.isInstance` as in
 * [configuredBy]: an Osc stage hands back another node type (a `Times`, a `Lowpass`) than the one it received.
 */
internal fun <T : Any> runThroughStages(
    door: String,
    start: T,
    stages: Array<out Any?>,
    returns: String,
    isResult: (Any) -> Boolean,
): T {
    var current = start

    stages.forEachIndexed { index, stage ->
        val which = "stage ${index + 1} of $door"

        // A script `null` arrives in the vararg array despite the door's declared element type; read as `Any?` here,
        // or the JS compiler drops the check as senseless and the null fails in the cast below.
        if (stage == null) {
            throw KlangScriptTypeError(message = "$which is null; a stage is a function (`x => x.lowpass(800)`)", operation = door)
        }

        @Suppress("UNCHECKED_CAST")
        val result: Any? = (stage as (T) -> Any?)(current)

        if (result == null) {
            throw KlangScriptTypeError(
                message = "$which returned nothing; return the $returns it received (`x => x.lowpass(800)`)",
                operation = door,
            )
        }

        if (!isResult(result)) {
            throw KlangScriptTypeError(message = "$which must return $returns, got ${result::class.simpleName}", operation = door)
        }

        @Suppress("UNCHECKED_CAST")
        current = result as T
    }

    return current
}
