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
 * must sit inside an `@Object` class, whose docs symbol carries the call form as its second variant.
 * The script registers it under the internal symbol `KlangScript.Invoke.NAME` (`__invoke__`).
 */
object InvokeShape {

    /** Kotlin's operator word: the only Kotlin function an `@Invoke` may sit on. */
    const val KOTLIN_OPERATOR = "invoke"

    /**
     * The problems with one annotated function, in the order they should be reported; empty when
     * the shape is right.
     *
     * @param functionName The Kotlin name of the annotated function.
     * @param isOperator Whether the function carries the `operator` modifier.
     * @param insideObjectClass Whether its parent is an `@Object` class.
     * @param invokeCountInClass How many `@Invoke` members that class declares, this one included.
     */
    fun problems(
        functionName: String,
        isOperator: Boolean,
        insideObjectClass: Boolean,
        invokeCountInClass: Int,
    ): List<String> {
        val problems = mutableListOf<String>()

        if (!insideObjectClass) {
            problems.add(
                "@KlangScript.Invoke '$functionName' must be declared inside an @KlangScript.Object " +
                        "class: the call form is documented on the object's own symbol."
            )
        }

        if (functionName != KOTLIN_OPERATOR) {
            problems.add(
                "@KlangScript.Invoke must sit on 'operator fun $KOTLIN_OPERATOR', " +
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
     * The problem with a `@KlangScript.Method` whose script name no method may have, or null when [scriptName]
     * is fine. Refused are the call form's spellings, the internal symbol `__invoke__` and Kotlin's word
     * `invoke` (a `@Method` on `operator fun invoke` would register a plain method `invoke` and leave the
     * object not callable), and any other operator symbol, a name of the form `__x__`: a script never reaches
     * one by name, so such a method would be dead on arrival.
     */
    fun methodNameProblem(functionName: String, scriptName: String): String? {
        if (scriptName == KlangScript.Invoke.NAME || scriptName == KOTLIN_OPERATOR) {
            return "@KlangScript.Method '$functionName' registers as '$scriptName'; the call form " +
                    "of a callable object is @KlangScript.Invoke on 'operator fun $KOTLIN_OPERATOR', nothing else."
        }

        if (isOperatorShaped(scriptName)) {
            return "@KlangScript.Method '$functionName' registers as '$scriptName'; a name of the form __x__ is an " +
                    "operator symbol, which a script never reaches by name."
        }

        return null
    }

    /** The `__x__` shape of an operator symbol, the rule of the runtime's `NativeOperatorNames.isOperatorName`. */
    fun isOperatorShaped(name: String): Boolean =
        name.length > 4 && name.startsWith("__") && name.endsWith("__")
}
