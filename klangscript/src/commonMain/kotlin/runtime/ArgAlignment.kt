/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

/**
 * Positional argument alignment shared by the interpreter ([resolveByParamSpec]) and the
 * editor analyzer (`intel/AnalyzedAst`). Both MUST agree on which parameter a positional
 * argument binds to, otherwise the editor types a lambda parameter differently from how
 * the runtime binds it. Keep the rule here and nowhere else.
 *
 * The rule is identity mapping with ONE exception, the trailing-lambda rule:
 *
 * > When the LAST positional argument is a function, the parameter at its index is not
 * > function-typed, and exactly one function-typed parameter follows, the argument binds
 * > to that parameter instead. The skipped slots take their defaults.
 *
 * This is what lets `Osc.supersaw(x => x.voices(9))` land the lambda in the trailing
 * `configure` parameter although `freq` comes first. Only the last argument floats, so
 * `f(lambda, 2)` stays a positional mismatch (and errors downstream) instead of a guess.
 * Two or more function-typed candidates mean no float: the call is ambiguous and the
 * caller must name the parameter.
 *
 * Known, deliberate asymmetries between the two callers:
 * - The interpreter recognises any callable VALUE (a script function, a native function, or a
 *   Kotlin lambda returned by a native such as `rev()`, including any of them held in a
 *   variable); the analyzer only recognises a literal arrow at the call site, because it
 *   cannot know the type of `let cfg = x => ...`. A lambda passed via a variable therefore
 *   binds correctly at runtime but is untyped in the editor.
 * - Neither caller floats when the parameter list ends in a vararg: the interpreter's vararg
 *   branch maps positionally, and the analyzer mirrors that.
 */
object ArgAlignment {

    /**
     * Returns, for each positional argument index `0 until argCount`, the index of the
     * parameter it binds to.
     *
     * When [argCount] exceeds [paramCount] (too many arguments, or a vararg tail) the
     * mapping is identity and the caller decides what that means.
     *
     * @param argCount Number of positional arguments at the call site.
     * @param paramCount Number of declared parameters.
     * @param isFunctionArg Whether the argument at the given index is a function value.
     * @param isFunctionParam Whether the parameter at the given index is function-typed.
     */
    fun positionalTargets(
        argCount: Int,
        paramCount: Int,
        isFunctionArg: (Int) -> Boolean,
        isFunctionParam: (Int) -> Boolean,
    ): List<Int> {
        val targets = MutableList(argCount) { it }
        if (argCount == 0 || argCount > paramCount) {
            return targets
        }
        val last = argCount - 1
        if (!isFunctionArg(last) || isFunctionParam(last)) {
            return targets
        }
        val candidates = (last + 1 until paramCount).filter { isFunctionParam(it) }
        if (candidates.size == 1) {
            targets[last] = candidates.single()
        }
        return targets
    }
}
