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
