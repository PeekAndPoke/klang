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
 * @param door the script-facing door name for the error message, e.g. `"Ignitor.supersaw"`.
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
 * Runs [start] through script-supplied [stages] for a `serial` door, first to last, each stage checked by [runStage].
 */
internal fun <T : Any> runSerialStages(
    door: String,
    start: T,
    stages: Array<out Any?>,
    returns: String,
    example: String,
    isResult: (Any) -> Boolean,
): T {
    var current = start

    stages.forEachIndexed { index, stage ->
        current = runStage(
            door = door,
            noun = "stage",
            index = index,
            stage = stage,
            input = current,
            returns = returns,
            example = example,
            isResult = isResult,
        )
    }

    return current
}

/**
 * Calls one script-supplied [stage] (the [index]th of [door], from 0) on [input] and checks what it returns, as
 * [configuredBy] checks a configure lambda: a script lambda's declared return type is a promise the interpreter cannot
 * keep (a block body without `return` returns null), so the result is read as `Any?` and checked here, with an error
 * that names the door and the stage, instead of a cast failure (JVM) or a silently wrong value (JS) further on. The
 * `serial` and `parallel` doors call it for every stage and branch.
 *
 * [stage] is read as `Any?` on purpose, see the null check. [noun] is what the door calls it (a `stage`, a `branch`),
 * [returns] names what it must return and [example] shows one, for the message; [isResult] tests it. Not `this::class.isInstance` as in
 * [configuredBy]: an Ignitor stage hands back another node type (a `Times`, a `Lowpass`) than the one it received.
 */
internal fun <I : Any, T : Any> runStage(
    door: String,
    noun: String,
    index: Int,
    stage: Any?,
    input: I,
    returns: String,
    example: String,
    isResult: (Any) -> Boolean,
): T {
    val which = "$noun ${index + 1} of $door"

    // A script `null` arrives in the vararg array despite the door's declared element type; read as `Any?` here,
    // or the JS compiler drops the check as senseless and the null fails in the cast below.
    if (stage == null) {
        throw KlangScriptTypeError(message = "$which is null; a $noun is a function (`$example`)", operation = door)
    }

    @Suppress("UNCHECKED_CAST")
    val result: Any? = (stage as (I) -> Any?)(input)

    if (result == null) {
        throw KlangScriptTypeError(
            message = "$which returned nothing; return the $returns it received (`$example`)",
            operation = door,
        )
    }

    if (!isResult(result)) {
        throw KlangScriptTypeError(message = "$which must return $returns, got ${result::class.simpleName}", operation = door)
    }

    @Suppress("UNCHECKED_CAST")
    return result as T
}
