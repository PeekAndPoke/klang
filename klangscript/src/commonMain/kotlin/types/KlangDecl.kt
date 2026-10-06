/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.types

import io.peekandpoke.klang.script.runtime.ArgAlignment

/** Base interface for a KlangScript declaration (callable or property). */
sealed interface KlangDecl {
    /** Human-readable description of this declaration. */
    val description: String

    /** Documentation for the return value. */
    val returnDoc: String

    /** Example code snippets. */
    val samples: List<KlangCodeSample>

    /** Rendered signature string (e.g., `note(pattern: String): Pattern`). */
    val signature: String

    /** The library this declaration belongs to (empty string for unset). */
    val library: String
}

/**
 * A callable declaration (function or method). A callable object's call form is one too: a
 * receiver-less callable named after the object, next to the object's [KlangProperty] on the same
 * symbol (`perlin(from, to)` beside `perlin`), see [KlangSymbol.callForm].
 *
 * @param name Function/method name
 * @param receiver Optional receiver type for extension methods
 * @param params Ordered list of parameter descriptors
 * @param returnType Optional return type
 * @param description Human-readable description
 * @param returnDoc Documentation for the return value
 * @param samples Example code snippets
 * @param library The library this declaration belongs to (empty string for unset)
 */
data class KlangCallable(
    val name: String,
    val receiver: KlangType? = null,
    val params: List<KlangParam>,
    val returnType: KlangType? = null,
    override val description: String = "",
    override val returnDoc: String = "",
    override val samples: List<KlangCodeSample> = emptyList(),
    override val library: String = "",
) : KlangDecl {
    override val signature: String
        get() = buildString {
            receiver?.let { append("${it.render()}.") }
            append(name)
            append("(")
            append(params.joinToString(", ") { it.render() })
            append(")")
            returnType?.let { append(": ${it.render()}") }
        }

    /**
     * The parameter an argument binds to: a named argument ([argName] not null) binds by name, a
     * positional one by [argIndex] through [ArgAlignment], the rule the interpreter and the analyzer
     * share (the trailing lambda floats to the one function-typed parameter after it), with a
     * trailing vararg parameter taking the overflow.
     *
     * [functionArgs] holds, per argument of the call, whether it is a function literal; its size is
     * the call's argument count. Null when nothing binds, including a negative index and a name no
     * parameter has; a named argument never falls back to its position.
     */
    fun paramForArgument(argIndex: Int, argName: String?, functionArgs: List<Boolean>): KlangParam? {
        if (argName != null) {
            return params.firstOrNull { it.name == argName }
        }

        if (argIndex < 0) {
            return null
        }

        val vararg = params.lastOrNull()?.takeIf { it.isVararg }
        // The interpreter's vararg branch maps positionally and never floats (see ArgAlignment).
        val target = if (vararg != null) {
            argIndex
        } else {
            ArgAlignment.positionalTargets(
                argCount = functionArgs.size,
                paramCount = params.size,
                isFunctionArg = { functionArgs[it] },
                isFunctionParam = { params[it].type.isFunction },
            ).getOrNull(argIndex) ?: argIndex
        }

        return params.getOrNull(target) ?: vararg
    }
}

/** Mutability mode for a [KlangProperty]. */
enum class KlangMutability { READ_ONLY, READ_WRITE, WRITE_ONLY }

/**
 * A property declaration.
 *
 * @param name Property name
 * @param owner Optional owning type
 * @param type The property's type
 * @param mutability Read/write access mode
 * @param description Human-readable description
 * @param returnDoc Documentation for the value
 * @param samples Example code snippets
 * @param library The library this declaration belongs to (empty string for unset)
 */
data class KlangProperty(
    val name: String,
    val owner: KlangType? = null,
    val type: KlangType,
    val mutability: KlangMutability = KlangMutability.READ_ONLY,
    override val description: String = "",
    override val returnDoc: String = "",
    override val samples: List<KlangCodeSample> = emptyList(),
    override val library: String = "",
) : KlangDecl {
    override val signature: String
        get() = buildString {
            when (mutability) {
                KlangMutability.READ_ONLY -> append("val ")
                KlangMutability.READ_WRITE -> append("var ")
                KlangMutability.WRITE_ONLY -> { /* no prefix — mutability shown as UI badge only */
                }
            }
            owner?.let { append("${it.render()}.") }
            append(name)
            append(": ${type.render()}")
        }
}

/**
 * One parameter per name across these declarations' callables, in first-seen order, as a parameter table shows
 * them: per name the first parameter that carries a description, else the first. A variant that documents a
 * parameter wins over an earlier one that does not (a call form `pan(amount)` sorted before
 * `SprudelPattern.pan(amount)`).
 */
fun List<KlangDecl>.parametersByName(): List<KlangParam> =
    filterIsInstance<KlangCallable>()
        .flatMap { it.params }
        .groupBy { it.name }
        .values
        .map { params -> params.firstOrNull { it.description.isNotBlank() } ?: params.first() }
