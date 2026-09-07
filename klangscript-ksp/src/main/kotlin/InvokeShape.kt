/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.peekandpoke.klang.script.annotations.KlangScript

/**
 * The shape rules of `@KlangScript.Invoke`, kept free of KSP types so they can be unit-tested.
 *
 * A callable object has exactly one call form: KlangScript has no overloads, and the Kotlin door
 * only exists through `operator fun invoke`, so the annotated member must be that operator and
 * must sit inside an `@Object` or `@TypeExtensions` class.
 */
object InvokeShape {

    /**
     * The problems with one annotated function, in the order they should be reported; empty when
     * the shape is right.
     *
     * @param functionName The Kotlin name of the annotated function.
     * @param isOperator Whether the function carries the `operator` modifier.
     * @param insideRegisteredClass Whether its parent is an `@Object` or `@TypeExtensions` class.
     * @param invokeCountInClass How many `@Invoke` members that class declares, this one included.
     */
    fun problems(
        functionName: String,
        isOperator: Boolean,
        insideRegisteredClass: Boolean,
        invokeCountInClass: Int,
    ): List<String> {
        val problems = mutableListOf<String>()

        if (!insideRegisteredClass) {
            problems.add(
                "@KlangScript.Invoke '$functionName' must be declared inside an @KlangScript.Object " +
                        "or @KlangScript.TypeExtensions class."
            )
        }

        if (functionName != KlangScript.Invoke.NAME) {
            problems.add(
                "@KlangScript.Invoke must sit on 'operator fun ${KlangScript.Invoke.NAME}', " +
                        "not on '$functionName': the Kotlin call form only exists through the operator."
            )
        }

        if (!isOperator) {
            problems.add(
                "@KlangScript.Invoke '$functionName' must be an 'operator fun', so that the Kotlin " +
                        "door and the KlangScript door share one call form."
            )
        }

        if (invokeCountInClass > 1) {
            problems.add(
                "@KlangScript.Invoke '$functionName' is one of $invokeCountInClass in its class; " +
                        "KlangScript has no overloads, a callable object has exactly one call form."
            )
        }

        return problems
    }

    /**
     * The problem with a `@KlangScript.Method` that would register under the invoke name, or null
     * when [scriptName] is another name: the call form has exactly one spelling.
     */
    fun methodSpelledInvoke(functionName: String, scriptName: String): String? {
        if (scriptName != KlangScript.Invoke.NAME) {
            return null
        }

        return "@KlangScript.Method '$functionName' registers as '${KlangScript.Invoke.NAME}'; the call form " +
                "of a callable object is @KlangScript.Invoke on 'operator fun invoke', nothing else."
    }
}
